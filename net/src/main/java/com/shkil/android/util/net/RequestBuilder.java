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

import java.io.File;
import java.util.Map;

import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.ByteString;

import static com.shkil.android.util.Utils.isEmpty;
import static com.shkil.android.util.Utils.isNotEmpty;
import static com.shkil.android.util.net.AbstractNetClient.FLAG_DESIRED;
import static com.shkil.android.util.net.AbstractNetClient.FLAG_REQUIRED;
import static com.shkil.android.util.net.AbstractNetClient.HEADER_ACCEPT;
import static com.shkil.android.util.net.AbstractNetClient.HEADER_AUTHORIZATION;
import static com.shkil.android.util.net.AbstractNetClient.HEADER_AUTHORIZATION_FLAG;
import static com.shkil.android.util.net.AbstractNetClient.HEADER_HTTP_CLIENT_TYPE;

public class RequestBuilder {

    private final HttpMethod method;
    private final HttpUrl.Builder urlBuilder;
    private final Request.Builder requestBuilder;
    private RequestBody requestBody;
    private BodyBuilder requestBodyBuilder;
    private boolean inRequestBodyBuilder;

    private interface BodyBuilder {
        void addParam(String name, String value);
        RequestBody build();
    }

    private static class MultipartBodyBuilder implements BodyBuilder {
        private MultipartBody.Builder builder = new MultipartBody.Builder();
        @Override
        public void addParam(String name, String value) {
            builder.addFormDataPart(name, value);
        }

        @Override
        public RequestBody build() {
            return builder.build();
        }
    }

    private static class FormBodyBuilder implements BodyBuilder {
        private FormBody.Builder builder = new FormBody.Builder();
        @Override
        public void addParam(String name, String value) {
            builder.add(name, value);
        }

        @Override
        public RequestBody build() {
            return builder.build();
        }
    }

    public RequestBuilder(@NonNull HttpMethod method, @NonNull String uri) {
        this(method, null, uri);
    }

    public RequestBuilder(@NonNull HttpMethod method, @Nullable HttpUrl baseUrl, @NonNull String uri) {
        if (method == null) {
            throw new NullPointerException("method");
        }
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (baseUrl == null && isEmpty(uri)) {
            throw new IllegalArgumentException("uri");
        }
        this.method = method;
        this.urlBuilder = createUrlBuilder(baseUrl, uri);
        this.requestBuilder = new Request.Builder();
    }

    protected HttpUrl.Builder createUrlBuilder(@Nullable HttpUrl baseUrl, @NonNull String uri) {
        if (baseUrl == null || uri.startsWith("http://") || uri.startsWith("https://")) {
            HttpUrl url = HttpUrl.parse(uri);
            if (url == null) {
                throw new IllegalArgumentException("URL is not well-formed");
            }
            return url.newBuilder();
        }
        HttpUrl.Builder result = baseUrl.newBuilder(uri);
        if (result == null) {
            throw new IllegalArgumentException("URI is not well-formed");
        }
        return result;
    }

    public final RequestBuilder requireAuthToken() {
        return requireAuthToken(AccessType.DEFAULT);
    }

    public final RequestBuilder requireAuthToken(AccessType type) {
        addHeader(HEADER_AUTHORIZATION_FLAG, FLAG_REQUIRED + ":" + type);
        return this;
    }

    public final RequestBuilder provideAuthToken() {
        return provideAuthToken(AccessType.DEFAULT);
    }

    public final RequestBuilder provideAuthToken(AccessType type) {
        addHeader(HEADER_AUTHORIZATION_FLAG, FLAG_DESIRED + ":" + type.name());
        return this;
    }

    protected String convertToString(boolean value) {
        return String.valueOf(value);
    }

    public RequestBuilder beginMultipartBody() {
        checkMethodSupportsBody(method);
        if (inRequestBodyBuilder || requestBody != null) {
            throw new IllegalStateException();
        }
        if (requestBodyBuilder == null) {
            this.requestBodyBuilder = new MultipartBodyBuilder();
        } else if (!(requestBodyBuilder instanceof MultipartBodyBuilder)) {
            throw new IllegalStateException();
        }
        this.inRequestBodyBuilder = true;
        return this;
    }

    public RequestBuilder beginFormBody() {
        checkMethodSupportsBody(method);
        if (inRequestBodyBuilder || requestBody != null) {
            throw new IllegalStateException();
        }
        if (requestBodyBuilder == null) {
            this.requestBodyBuilder = new FormBodyBuilder();
        } else if (!(requestBodyBuilder instanceof FormBodyBuilder)) {
            throw new IllegalStateException();
        }
        this.inRequestBodyBuilder = true;
        return this;
    }

    public RequestBuilder endBody() {
        if (inRequestBodyBuilder) {
            this.inRequestBodyBuilder = false;
        } else {
            throw new IllegalStateException();
        }
        return this;
    }

    public RequestBuilder withBody(@NonNull RequestBody requestBody) {
        checkMethodSupportsBody(method);
        if (requestBody == null) {
            throw new NullPointerException("requestBody");
        }
        if (this.requestBody != null || requestBodyBuilder != null) {
            throw new IllegalStateException();
        }
        this.requestBody = requestBody;
        return this;
    }

    public RequestBuilder withBody(MediaType contentType, String content) {
        return withBody(RequestBody.create(contentType, content));
    }

    public RequestBuilder withBody(MediaType contentType, byte[] content) {
        return withBody(RequestBody.create(contentType, content));
    }

    public RequestBuilder withBody(MediaType contentType, byte[] content, int offset, int byteCount) {
        return withBody(RequestBody.create(contentType, content, offset, byteCount));
    }

    public RequestBuilder withBody(MediaType contentType, ByteString content) {
        return withBody(RequestBody.create(contentType, content));
    }

    public RequestBuilder withBody(MediaType contentType, File file) {
        return withBody(RequestBody.create(contentType, file));
    }

    private static void checkMethodSupportsBody(HttpMethod method) throws IllegalStateException {
        switch (method) {
            case POST:
            case PUT:
            case PATCH:
                return;
        }
        throw new IllegalStateException("Not supported for method " + method);
    }

    public RequestBuilder addParams(Map<String, String> map) {
        if (isNotEmpty(map)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                addParam(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public RequestBuilder addParam(String name, int value) {
        return addParam(name, String.valueOf(value));
    }

    public RequestBuilder addParam(String name, long value) {
        return addParam(name, String.valueOf(value));
    }

    public RequestBuilder addParam(String name, double value) {
        return addParam(name, String.valueOf(value));
    }

    public RequestBuilder addParam(String name, boolean value) {
        return addParam(name, convertToString(value));
    }

    public RequestBuilder addParam(String name, String value) {
        if (inRequestBodyBuilder) {
            requestBodyBuilder.addParam(name, value);
        } else {
            urlBuilder.addQueryParameter(name, value);
        }
        return this;
    }

    public RequestBuilder addParamNotEmpty(String name, String value) throws IllegalArgumentException {
        if (isEmpty(value)) {
            throw new IllegalArgumentException("Value of '" + name + "' is empty");
        }
        return addParam(name, value);
    }

    public RequestBuilder addParamNotZero(String name, long value) throws IllegalArgumentException {
        if (value == 0) {
            throw new IllegalArgumentException("Value of '" + name + "' is zero");
        }
        return addParam(name, value);
    }

    public RequestBuilder addParamNotZero(String name, int value) throws IllegalArgumentException {
        if (value == 0) {
            throw new IllegalArgumentException("Value of '" + name + "' is zero");
        }
        return addParam(name, value);
    }

    public RequestBuilder addParamIfNotEmpty(String key, String value) {
        if (isNotEmpty(value)) {
            addParam(key, value);
        }
        return this;
    }

    public RequestBuilder addParamIfNotZero(String key, long value) {
        if (value != 0) {
            addParam(key, value);
        }
        return this;
    }

    public RequestBuilder addParamIfPositive(String key, long value) {
        if (value > 0) {
            addParam(key, value);
        }
        return this;
    }


    public RequestBuilder addParamIfNotZero(String key, int value) {
        if (value != 0) {
            addParam(key, value);
        }
        return this;
    }

    public RequestBuilder addParamIfPositive(String key, int value) {
        if (value > 0) {
            addParam(key, value);
        }
        return this;
    }

    public RequestBuilder addHeaderIfNotEmpty(String name, String value) {
        if (isNotEmpty(value)) {
            addHeader(name, value);
        }
        return this;
    }

    public RequestBuilder header(String name, String value) {
        requestBuilder.header(name, value);
        return this;
    }

    public RequestBuilder addHeader(String name, String value) {
        requestBuilder.addHeader(name, value);
        return this;
    }

    public RequestBuilder addHeader(String name, long value) {
        addHeader(name, String.valueOf(value));
        return this;
    }

    public RequestBuilder addHeader(String name, int value) {
        addHeader(name, String.valueOf(value));
        return this;
    }

    public RequestBuilder removeHeader(String name) {
        requestBuilder.removeHeader(name);
        return this;
    }

    public RequestBuilder accept(String value) {
        requestBuilder.header(HEADER_ACCEPT, value);
        return this;
    }

    /**
     * Basic HTTP Authentication credentials
     */
    public RequestBuilder basicAuthentication(String username, String password) {
        requestBuilder.header(HEADER_AUTHORIZATION, Credentials.basic(username, password));
        return this;
    }

    public RequestBuilder httpClientType(String clientType) {
        requestBuilder.header(HEADER_HTTP_CLIENT_TYPE, clientType);
        return this;
    }

    public RequestBuilder tag(Object tag) {
        requestBuilder.tag(tag);
        return this;
    }

    public Request build() {
        if (inRequestBodyBuilder) {
            throw new IllegalStateException("Body is not ended");
        }
        if (requestBodyBuilder != null) {
            if (requestBody != null) {
                throw new IllegalStateException();
            }
            requestBody = requestBodyBuilder.build();
        }
        HttpUrl httpUrl = urlBuilder.build();
        return requestBuilder
                .method(method.name(), requestBody)
                .url(httpUrl)
                .build();
    }

}
