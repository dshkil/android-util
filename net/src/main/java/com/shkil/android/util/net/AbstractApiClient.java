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

import com.shkil.android.util.concurrent.MainThreadExecutor;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.concurrent.ResultFutures;
import com.shkil.android.util.net.exception.AccessTokenException;
import com.shkil.android.util.net.exception.ServerMessageException;
import com.shkil.android.util.net.exception.UnexpectedResponseException;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

import static com.shkil.android.util.Utils.newThreadFactory;
import static java.util.concurrent.Executors.newFixedThreadPool;

public abstract class AbstractApiClient {

    public static final HttpLoggingInterceptor.Level LOG_LEVEL
            = BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE;

    //  Exponential backoff params
    private static final long INITIAL_RETRY_INTERVAL_MILLIS = 1000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static class SerialExecutorLazyHolder {
        static final Executor EXECUTOR = newFixedThreadPool(1, newThreadFactory("net-client-serial"));
    }

    private static class ThreadPoolExecutorLazyHolder {
        static final Executor EXECUTOR = newFixedThreadPool(4, newThreadFactory("net-client-pool-{}"));
    }

    private volatile OkHttpClient httpClient;

    protected final OkHttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = createHttpClient();
                }
            }
        }
        return httpClient;
    }

    protected OkHttpClient createHttpClient() {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        if (LOG_LEVEL != Level.NONE) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor()
                    .setLevel(LOG_LEVEL);
            httpClientBuilder.addInterceptor(loggingInterceptor);
        }
        if (MAX_RETRY_ATTEMPTS > 0) {
//            httpClientBuilder.addNetworkInterceptor(new BackoffInterceptor());
        }
        return httpClientBuilder.build();
    }

    protected abstract RequestBuilder newRequestBuilder(HttpMethod method, String uri);

    protected RequestBuilder newGetRequestBuilder(String uri) {
        return newRequestBuilder(HttpMethod.GET, uri);
    }

    protected RequestBuilder newPostRequestBuilder(String uri) {
        return newRequestBuilder(HttpMethod.POST, uri);
    }

    protected Call newCall(Request request) {
        return getHttpClient().newCall(request);
    }

    protected Response execute(Request request) throws IOException {
        Response response = newCall(request).execute();
        try {
            checkResponse(response);
        } catch (AccessTokenException ex) {
            onAccessTokenException(ex);
            throw ex;
        }
        return response;
    }

    protected void onAccessTokenException(AccessTokenException ex) {
    }

    protected ResultFuture<Response> executeSerialAsync(final Request request) {
        return executeSerialAsync(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return execute(request);
            }
        });
    }

    protected ResultFuture<Response> executeAsync(final Request request) {
        return executeAsync(new Callable<Response>() {
            @Override
            public Response call() throws Exception {
                return execute(request);
            }
        });
    }

    protected <T> T execute(Request request, Type resultType) throws IOException {
        Response response = execute(request);
        return parseResponse(response, resultType);
    }

    protected <T> ResultFuture<T> executeSerialAsync(final Request request, final Type resultType) {
        return executeSerialAsync(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return execute(request, resultType);
            }
        });
    }

    protected <T> ResultFuture<T> executeAsync(final Request request, final Type resultType) {
        return executeAsync(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return execute(request, resultType);
            }
        });
    }

    protected Response execute(RequestBuilder requestBuilder) throws IOException {
        return execute(requestBuilder.build());
    }

    protected ResultFuture<Response> executeSerialAsync(RequestBuilder requestBuilder) {
        return executeSerialAsync(requestBuilder.build());
    }

    protected ResultFuture<Response> executeAsync(RequestBuilder requestBuilder) {
        return executeAsync(requestBuilder.build());
    }

    protected <T> T execute(RequestBuilder requestBuilder, Type resultType) throws IOException {
        return execute(requestBuilder.build(), resultType);
    }

    protected <T> ResultFuture<T> executeSerialAsync(RequestBuilder requestBuilder, Type resultType) {
        return executeSerialAsync(requestBuilder.build(), resultType);
    }

    protected <T> ResultFuture<T> executeAsync(RequestBuilder requestBuilder, Type resultType) {
        return executeAsync(requestBuilder.build(), resultType);
    }

    protected <T> T parseResponse(Response response, Type resultType) throws IOException {
        Reader inputReader = response.body().charStream();
        return parseJson(inputReader, resultType);
    }

    protected abstract <T> T parseJson(Reader inputReader, Type resultType);

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

    protected boolean isServerError(Response response) {
        return response.code() == HttpURLConnection.HTTP_BAD_REQUEST;
    }

    protected ServerMessageException parseErrorMessage(Response response) throws IOException {
        return new ServerMessageException(response.message(), String.valueOf(response.code()));
    }

    protected static <V> ResultFuture<V> executeSerialAsync(Callable<V> task) {
        return ResultFutures.executeTask(task, SerialExecutorLazyHolder.EXECUTOR)
                .getResultFuture(MainThreadExecutor.getInstance(), true);
    }

    protected static <V> ResultFuture<V> executeAsync(Callable<V> task) {
        return ResultFutures.executeTask(task, ThreadPoolExecutorLazyHolder.EXECUTOR)
                .getResultFuture(MainThreadExecutor.getInstance(), true);
    }

}
