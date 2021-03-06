/*
 * Copyright (C) 2016 Dmytro Shkil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shkil.android.util.net;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.shkil.android.util.concurrent.Cancellator;
import com.shkil.android.util.concurrent.MainThreadExecutor;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.concurrent.ResultFutureAdapter;
import com.shkil.android.util.concurrent.ResultFutures;
import com.shkil.android.util.net.exception.AccessTokenException;
import com.shkil.android.util.net.exception.AuthException;
import com.shkil.android.util.net.exception.ServerMessageException;
import com.shkil.android.util.net.exception.UnexpectedResponseException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.GuardedBy;

import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

import static com.shkil.android.util.Utils.isNotEmpty;
import static com.shkil.android.util.Utils.newThreadFactory;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.MINUTES;

public abstract class AbstractNetClient {

    protected static final String HEADER_AUTHORIZATION_FLAG = "X-NetClient-Authorization";
    protected static final String HEADER_HTTP_CLIENT_TYPE = "X-NetClient-Client-Type";
    protected static final String FLAG_REQUIRED = "Required";
    protected static final String FLAG_DESIRED = "Desired";

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    public static long EXPIRATION_ADVANCE_MILLIS = MINUTES.toMillis(3);

    private final HttpLoggingInterceptor.Logger logger;
    private final Level logLevel;

    //  Exponential backoff params
    private static final long INITIAL_RETRY_INTERVAL_MILLIS = 1000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static class SerialExecutorLazyHolder {
        static final Executor EXECUTOR = newFixedThreadPool(1, newThreadFactory("net-client-serial"));
    }

    private static class ThreadPoolExecutorLazyHolder {
        static final Executor EXECUTOR = newFixedThreadPool(4, newThreadFactory("net-client-pool-{0}"));
    }

    private static class SchedulerLazyHolder {
        static final Scheduler SCHEDULER = Schedulers.from(ThreadPoolExecutorLazyHolder.EXECUTOR);
    }

    @GuardedBy("this")
    private final Map<String, OkHttpClient> httpClients = new HashMap<>();

    private volatile ResponseParser responseParser;

    public AbstractNetClient(HttpLoggingInterceptor.Level logLevel) {
        this(logLevel, HttpLoggingInterceptor.Logger.DEFAULT);
    }

    public AbstractNetClient(HttpLoggingInterceptor.Level logLevel, @NonNull HttpLoggingInterceptor.Logger logger) {
        this.logLevel = logLevel;
        this.logger = logger;
    }

    protected synchronized final OkHttpClient getHttpClient(@Nullable String type) {
        OkHttpClient httpClient = httpClients.get(type);
        if (httpClient == null) {
            httpClient = createHttpClientBuilder(type).build();
            httpClients.put(type, httpClient);
        }
        return httpClient;
    }

    protected OkHttpClient.Builder createHttpClientBuilder(@Nullable String type) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        if (logLevel != Level.NONE) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger)
                    .setLevel(logLevel);
            httpClientBuilder.addInterceptor(loggingInterceptor);
        }
        if (MAX_RETRY_ATTEMPTS > 0) {
//            httpClientBuilder.addNetworkInterceptor(new BackoffInterceptor());
        }
        return httpClientBuilder;
    }

    protected abstract RequestBuilder newRequestBuilder(HttpMethod method, String uri);

    protected final RequestBuilder newRequestBuilder(HttpMethod method, String uri, Object... args) {
        return newRequestBuilder(method, String.format(uri, args));
    }

    protected final RequestBuilder newGetRequestBuilder(String uri) {
        return newRequestBuilder(HttpMethod.GET, uri);
    }

    protected final RequestBuilder newGetRequestBuilder(String uri, Object... args) {
        return newRequestBuilder(HttpMethod.GET, String.format(uri, args));
    }

    protected final RequestBuilder newPostRequestBuilder(String uri) {
        return newRequestBuilder(HttpMethod.POST, uri);
    }

    protected final RequestBuilder newPostRequestBuilder(String uri, Object... args) {
        return newRequestBuilder(HttpMethod.POST, String.format(uri, args));
    }

    protected final RequestBuilder newPutRequestBuilder(String uri) {
        return newRequestBuilder(HttpMethod.PUT, uri);
    }

    protected final RequestBuilder newPutRequestBuilder(String uri, Object... args) {
        return newRequestBuilder(HttpMethod.PUT, String.format(uri, args));
    }

    protected final RequestBuilder newPatchRequestBuilder(String uri) {
        return newRequestBuilder(HttpMethod.PATCH, uri);
    }

    protected final RequestBuilder newPatchRequestBuilder(String uri, Object... args) {
        return newRequestBuilder(HttpMethod.PATCH, String.format(uri, args));
    }

    protected Call newCall(Request request) {
        String clientType = request.header(HEADER_HTTP_CLIENT_TYPE);
        return getHttpClient(clientType).newCall(request);
    }

    protected AccessToken getAccessToken(AccessType type) throws AccessTokenException {
        throw new UnsupportedOperationException("Should be overridden for concrete implementation");
    }

    protected AccessToken requestAccessToken(AccessType type) throws AccessTokenException {
        throw new UnsupportedOperationException("Should be overridden for concrete implementation");
    }

    protected boolean isTokenExpired(AccessToken accessToken) {
        long expiresAt = accessToken.getExpiresAt();
        if (expiresAt >= 0) {
            return expiresAt - EXPIRATION_ADVANCE_MILLIS <= currentTimeMillis();
        }
        return false;
    }

    protected final Response execute(Request request) throws IOException {
        return execute(request, (Cancellator) null);
    }

    protected final Response execute(Request request, Cancellator cancellator) throws IOException {
        return execute(request, false, cancellator);
    }

    protected final Response execute(Request request, boolean skipResponseCheck, Cancellator cancellator) throws IOException {
        String authFlag = request.header(HEADER_AUTHORIZATION_FLAG);
        boolean tokenRequired = authFlag != null && authFlag.startsWith(FLAG_REQUIRED);
        boolean tokenDesired = authFlag != null && authFlag.startsWith(FLAG_DESIRED);
        AccessType accessType = getAccessType(authFlag);
        if (tokenRequired || tokenDesired) {
            AccessToken accessToken = getAccessToken(accessType);
            try {
                Request.Builder requestBuilder = request.newBuilder();
                if (accessToken == null || isTokenExpired(accessToken) || accessToken == AccessToken.NULL) {
                    accessToken = requestAccessToken(accessType);
                }
                if (accessToken == null || accessToken == AccessToken.NULL) {
                    if (tokenRequired) {
                        throw new AccessTokenException("Access token is empty", AccessTokenException.CODE_INVALID);
                    }
                    accessToken = null;
                } else if (isTokenExpired(accessToken)) {
                    if (tokenRequired) {
                        throw new AccessTokenException("Access token is expired", AccessTokenException.CODE_EXPIRED);
                    }
                    accessToken = null;
                }
                if (accessToken != null) {
                    requestBuilder.header(HEADER_AUTHORIZATION, accessToken.toAuthorizationHeaderValue());
                }
                request = requestBuilder.build();
            } catch (AccessTokenException ex) {
                onAuthException(ex, accessType);
                if (tokenRequired) {
                    throw ex;
                }
            }
        }
        final Call call = newCall(request);
        if (cancellator != null) {
            cancellator.setOnCancelListener(new Cancellator.OnCancelListener() {
                @Override
                public void onCancel() {
                    call.cancel();
                }
            });
        }
        Response response;
        try {
            if (cancellator != null && cancellator.isCanceled()) {
                throw new InterruptedIOException("cancelled");
            }
            response = call.execute();
        } finally {
            if (cancellator != null) {
                cancellator.setOnCancelListener(null);
            }
        }
        if (skipResponseCheck) {
            return response;
        }
        try {
            checkResponse(response);
        } catch (AuthException | AccessTokenException ex) {
            closeResponse(response);
            onAuthException(ex, accessType);
            throw ex;
        } catch (IOException ex) {
            closeResponse(response);
            throw ex;
        }
        return response;
    }

    /**
     * @return result
     */
    protected <T> T onRequestResult(Request request, Headers responseHeaders, Object result, Type resultType) {
        if (result instanceof IResultHolder) {
            //noinspection unchecked
            return ((IResultHolder<T>) result).getResult();
        }
        //noinspection unchecked
        return (T) result;
    }

    protected void onAuthException(Exception ex, AccessType accessType) {
        // keep empty
    }

    protected void closeResponse(Response response) {
        response.body().close();
    }

    protected final <T> T parseResponse(Response response, Type resultType) throws IOException {
        return getResponseParser().parseResponse(response, resultType);
    }

    protected final ResponseParser getResponseParser() {
        if (responseParser == null) {
            synchronized (this) {
                if (responseParser == null) {
                    responseParser = createResponseParser();
                }
            }
        }
        return responseParser;
    }

    protected abstract ResponseParser createResponseParser();

    protected final ResultFuture<Response> executeSerialAsync(final Request request) {
        return executeSerialAsync(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return execute(request);
            }
        });
    }

    protected final ResultFuture<Response> executeAsync(final Request request) {
        return executeAsync(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return execute(request);
            }
        });
    }

    protected final Single<Response> executeAsyncRx(final Request request) {
        return executeAsyncRx(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return execute(request);
            }
        });
    }

    protected final <T> T execute(Request request, Class<T> resultType) throws IOException {
        return execute(request, (Type) resultType);
    }

    protected final <T> T execute(Request request, Type resultType) throws IOException {
        return execute(request, resultType, null);
    }

    protected final <T> T execute(Request request, Class<T> resultType, Cancellator cancellator) throws IOException {
        return execute(request, (Type) resultType, cancellator);
    }

    protected final <T> T execute(Request request, Type resultType, Cancellator cancellator) throws IOException {
        Response response = execute(request, cancellator);
        try {
            Object result = parseResponse(response, resultType);
            return onRequestResult(request, response.headers(), result, resultType);
        } finally {
            closeResponse(response);
        }
    }

    protected final <T> ResultFuture<T> executeSerialAsync(Request request, Class<T> resultType) {
        return executeSerialAsync(request, (Type) resultType);
    }

    protected final <T> ResultFuture<T> executeSerialAsync(final Request request, final Type resultType) {
        return executeSerialAsync(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return execute(request, resultType);
            }
        });
    }

    protected final <T> ResultFuture<T> executeAsync(final Request request, final Type resultType) {
        return executeAsync(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return execute(request, resultType);
            }
        });
    }

    protected final <T> Single<T> executeAsyncRx(final Request request, final Type resultType) {
        return executeAsyncRx(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return execute(request, resultType);
            }
        });
    }

    protected final Response execute(RequestBuilder requestBuilder) throws IOException {
        return execute(requestBuilder, (Cancellator) null);
    }

    protected final Response execute(RequestBuilder requestBuilder, Cancellator cancellator) throws IOException {
        return execute(requestBuilder.build(), cancellator);
    }

    protected final ResultFuture<Response> executeSerialAsync(RequestBuilder requestBuilder) {
        return executeSerialAsync(requestBuilder.build());
    }

    protected final ResultFuture<Response> executeAsync(RequestBuilder requestBuilder) {
        return executeAsync(requestBuilder.build());
    }

    protected final Single<Response> executeAsyncRx(RequestBuilder requestBuilder) {
        return executeAsyncRx(requestBuilder.build());
    }

    protected final <T> T execute(RequestBuilder requestBuilder, Class<T> resultType) throws IOException {
        return execute(requestBuilder, (Type) resultType);
    }

    protected final <T> T execute(RequestBuilder requestBuilder, Type resultType) throws IOException {
        return execute(requestBuilder, resultType, null);
    }

    protected final <T> T execute(RequestBuilder requestBuilder, Class<T> resultType, Cancellator cancellator) throws IOException {
        return execute(requestBuilder, (Type) resultType, cancellator);
    }

    protected final <T> T execute(RequestBuilder requestBuilder, Type resultType, Cancellator cancellator) throws IOException {
        return execute(requestBuilder.build(), resultType, cancellator);
    }

    protected final <T> ResultFuture<T> executeSerialAsync(RequestBuilder requestBuilder, Class<T> resultType) {
        return executeSerialAsync(requestBuilder, (Type) resultType);
    }

    protected final <T> ResultFuture<T> executeSerialAsync(RequestBuilder requestBuilder, Type resultType) {
        return executeSerialAsync(requestBuilder.build(), resultType);
    }

    protected final <T> ResultFuture<T> executeAsync(RequestBuilder requestBuilder, Class<T> resultType) {
        return executeAsync(requestBuilder, (Type) resultType);
    }

    protected final <T> Single<T> executeAsyncRx(RequestBuilder requestBuilder, Class<T> resultType) {
        return executeAsyncRx(requestBuilder, (Type) resultType);
    }

    protected final <T> ResultFuture<T> executeAsync(RequestBuilder requestBuilder, Type resultType) {
        return executeAsync(requestBuilder.build(), resultType);
    }

    protected final <T> Single<T> executeAsyncRx(RequestBuilder requestBuilder, Type resultType) {
        return executeAsyncRx(requestBuilder.build(), resultType);
    }

    protected final <T> ResultFuture<T> executeSerialAsync(RequestBuilder requestBuilder, T successValue) {
        return executeSerialAsync(requestBuilder.build(), successValue);
    }

    protected final <T> ResultFuture<T> executeAsync(RequestBuilder requestBuilder, T successValue) {
        return executeAsync(requestBuilder.build(), successValue);
    }

    protected final <T> ResultFuture<T> executeAsync(Request request, final T successValue) {
        ResultFuture<Response> future = executeAsync(request);
        return new ResultFutureAdapter<Response, T>(future) {
            @Override
            protected T convertValue(Response response) throws Exception {
                return successValue;
            }
        };
    }

    protected final <T> ResultFuture<T> executeSerialAsync(Request request, final T successValue) {
        ResultFuture<Response> future = executeSerialAsync(request);
        return new ResultFutureAdapter<Response, T>(future) {
            @Override
            protected T convertValue(Response response) throws Exception {
                return successValue;
            }
        };
    }

    protected void checkResponse(Response response) throws IOException {
        if (response.isSuccessful()) {
            return;
        }
        if (isServerError(response)) {
            ServerMessageException serverMessageException = parseErrorMessage(response);
            if (serverMessageException != null) {
                throw serverMessageException;
            }
        }
        throw new UnexpectedResponseException(response.code());
    }

    protected boolean isAuthError(Response response) {
        switch (response.code()) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
            case HttpURLConnection.HTTP_FORBIDDEN:
                return true;
        }
        return false;
    }

    protected boolean isServerError(Response response) {
        switch (response.code()) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
                return true;
        }
        return false;
    }

    protected ServerMessageException parseErrorMessage(Response response) throws IOException {
        return new ServerMessageException(response.message(), String.valueOf(response.code()));
    }

    protected Executor getSerialExecutor() {
        return SerialExecutorLazyHolder.EXECUTOR;
    }

    protected <V> ResultFuture<V> executeSerialAsync(Callable<V> task) {
        return ResultFutures.executeTask(task, getSerialExecutor())
                .getResultFuture(MainThreadExecutor.getInstance(), true);
    }

    protected Executor getAsyncExecutor() {
        return ThreadPoolExecutorLazyHolder.EXECUTOR;
    }

    protected Scheduler getAsyncScheduler() {
        return SchedulerLazyHolder.SCHEDULER;
    }

    protected <V> Single<V> executeAsyncRx(Callable<V> task) {
        return Single.fromCallable(task)
                .subscribeOn(getAsyncScheduler())
                .observeOn(AndroidSchedulers.mainThread());
    }

    protected <V> ResultFuture<V> executeAsync(Callable<V> task) {
        return ResultFutures.executeTask(task, getAsyncExecutor())
                .getResultFuture(MainThreadExecutor.getInstance(), true);
    }

    private static AccessType getAccessType(String authFlag) {
        if (isNotEmpty(authFlag)) {
            String type;
            if (authFlag.startsWith(FLAG_REQUIRED)) {
                type = authFlag.substring(FLAG_REQUIRED.length() + 1);
            } else if (authFlag.startsWith(FLAG_DESIRED)) {
                type = authFlag.substring(FLAG_DESIRED.length() + 1);
            } else {
                throw new IllegalArgumentException();
            }
            return AccessType.valueOf(type);
        }
        return AccessType.DEFAULT;
    }

}
