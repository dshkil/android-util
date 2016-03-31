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

import com.shkil.android.util.net.exception.AuthenticationException;

import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

import static com.shkil.android.util.Utils.isEmpty;
import static com.shkil.android.util.Utils.isNotEmpty;

public abstract class RequestBuilder {

    private final HttpMethod method;
    private final HttpUrl.Builder urlBuilder;
    private final Request.Builder requestBuilder;
    private MultipartBody.Builder multipartBodyBuilder;
    private boolean inMultipartBody;

    public RequestBuilder(HttpMethod method, String uri) {
        this.method = method;
        this.urlBuilder = makeUrlBuilder(uri);
        this.requestBuilder = new Request.Builder();
    }

    protected abstract HttpUrl.Builder makeUrlBuilder(String uri);

    protected abstract void addAuthToken() throws AuthenticationException;

    public final RequestBuilder addAuthTokenOptional() {
        try {
            addAuthToken();
        } catch (AuthenticationException e) {
            // do nothing
        }
        return this;
    }

    public final RequestBuilder addAuthTokenMandatory() throws AuthenticationException {
        addAuthToken();
        return this;
    }

    protected String convertToString(boolean value) {
        return String.valueOf(value);
    }

    public RequestBuilder beginMultipartBody() {
        if (method != HttpMethod.POST) {
            throw new IllegalStateException("Not supported for method " + method);
        }
        if (inMultipartBody) {
            throw new IllegalStateException();
        }
        this.multipartBodyBuilder = new MultipartBody.Builder();
        this.inMultipartBody = true;
        return this;
    }

    public RequestBuilder endMultipartBody() {
        if (inMultipartBody) {
            this.inMultipartBody = false;
        } else {
            throw new IllegalStateException();
        }
        return this;
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
        if (inMultipartBody) {
            multipartBodyBuilder.addFormDataPart(name, value);
        } else {
            urlBuilder.addQueryParameter(name, value);
        }
        return this;
    }

    public RequestBuilder addParamMandatory(String name, String value) throws IllegalArgumentException {
        if (isEmpty(value)) {
            throw new IllegalArgumentException("Value of '" + name + "' is empty");
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

    public Request build() {
        if (inMultipartBody) {
            throw new IllegalStateException("Multipart body is not ended");
        }
        RequestBody requestBody;
        if (multipartBodyBuilder != null) {
            requestBody = multipartBodyBuilder.build();
        } else {
            requestBody = null;
        }
        HttpUrl httpUrl = urlBuilder.build();
        return requestBuilder
                .method(method.name(), requestBody)
                .url(httpUrl)
                .build();
    }

}
