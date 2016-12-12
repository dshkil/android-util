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

import com.shkil.android.util.concurrent.MainThreadExecutor;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.concurrent.ResultFutures;
import com.shkil.android.util.net.exception.AccessTokenException;
import com.shkil.android.util.net.exception.AuthException;
import com.shkil.android.util.net.exception.ServerMessageException;
import com.shkil.android.util.net.exception.UnexpectedResponseException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

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

    private volatile OkHttpClient httpClient;
    private volatile ResponseParser responseParser;

    public AbstractNetClient(HttpLoggingInterceptor.Level logLevel) {
        this(logLevel, HttpLoggingInterceptor.Logger.DEFAULT);
    }

    public AbstractNetClient(HttpLoggingInterceptor.Level logLevel, @NonNull HttpLoggingInterceptor.Logger logger) {
        this.logLevel = logLevel;
        this.logger = logger;
    }

    protected final OkHttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = createHttpClientBuilder().build();
                }
            }
        }
        return httpClient;
    }

    protected OkHttpClient.Builder createHttpClientBuilder() {
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

    protected Call newCall(Request request) {
        return getHttpClient().newCall(request);
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
        return execute(request, false);
    }

    protected final Response execute(Request request, boolean skipResponseCheck) throws IOException {
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
        Response response = newCall(request).execute();
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

    protected final <T> T execute(Request request, Type resultType) throws IOException {
        Response response = execute(request);
        try {
            Object result = parseResponse(response, resultType);
            return onRequestResult(request, response.headers(), result, resultType);
        } finally {
            closeResponse(response);
        }
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

    protected final Response execute(RequestBuilder requestBuilder) throws IOException {
        return execute(requestBuilder.build());
    }

    protected final ResultFuture<Response> executeSerialAsync(RequestBuilder requestBuilder) {
        return executeSerialAsync(requestBuilder.build());
    }

    protected final ResultFuture<Response> executeAsync(RequestBuilder requestBuilder) {
        return executeAsync(requestBuilder.build());
    }

    protected final <T> T execute(RequestBuilder requestBuilder, Type resultType) throws IOException {
        return execute(requestBuilder.build(), resultType);
    }

    protected final <T> ResultFuture<T> executeSerialAsync(RequestBuilder requestBuilder, Type resultType) {
        return executeSerialAsync(requestBuilder.build(), resultType);
    }

    protected final <T> ResultFuture<T> executeAsync(RequestBuilder requestBuilder, Type resultType) {
        return executeAsync(requestBuilder.build(), resultType);
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

    protected <V> ResultFuture<V> executeAsync(Callable<V> task) {
        return ResultFutures.executeTask(task, getAsyncExecutor())
                .getResultFuture(MainThreadExecutor.getInstance(), true);
    }

    private static AccessType getAccessType(String authFlag) {
        if (isNotEmpty(authFlag)) {
            String type;
            if (authFlag.startsWith(FLAG_REQUIRED)) {
                type = authFlag.substring(FLAG_REQUIRED.length() + 1);
            }  else if (authFlag.startsWith(FLAG_DESIRED)) {
                type = authFlag.substring(FLAG_DESIRED.length() + 1);
            } else {
                throw new IllegalArgumentException();
            }
            return AccessType.valueOf(type);
        }
        return AccessType.DEFAULT;
    }

}
