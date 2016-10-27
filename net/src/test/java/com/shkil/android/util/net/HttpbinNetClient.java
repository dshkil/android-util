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

import com.fasterxml.jackson.databind.JsonNode;
import com.shkil.android.util.net.parser.JacksonDatabindResponseParser;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

class HttpbinNetClient extends AbstractNetClient {

    private static final HttpUrl BASE_URL = HttpUrl.parse("http://httpbin.org");

    public HttpbinNetClient() {
        super(HttpLoggingInterceptor.Level.BODY);
    }

    @Override
    protected RequestBuilder newRequestBuilder(HttpMethod method, String uri) {
        return new RequestBuilder(method, BASE_URL, uri);
    }

    @Override
    protected ResponseParser createResponseParser() {
        return JacksonDatabindResponseParser.create();
    }

    public JsonNode doGetRequest() throws IOException {
        RequestBuilder builder = newGetRequestBuilder("/get")
                .addParam("param1", "test1")
                .addParam("param2", "test2")
                .addHeader("Header1", "h1")
                .addHeader("header2", "h2");
        return execute(builder, JsonNode.class);
    }

    public ByteString doGetBytesRequest(int count) throws IOException {
        RequestBuilder builder = newGetRequestBuilder("/bytes/" + count);
        return execute(builder, ByteString.class);
    }

    public JsonNode doPostFormRequest() throws IOException {
        RequestBuilder builder = newPostRequestBuilder("/post")
                .beginFormBody()
                .addParam("param1", "test1")
                .addParam("param2", "test2")
                .addHeader("Header1", "h1")
                .addHeader("header2", "h2")
                .endBody();
        return execute(builder, JsonNode.class);
    }

    public JsonNode doPostMultipartRequest() throws IOException {
        RequestBuilder builder = newPostRequestBuilder("/post")
                .beginMultipartBody()
                .addParam("param1", "test1")
                .addParam("param2", "test2")
                .addHeader("Header1", "h1")
                .addHeader("header2", "h2")
                .endBody();
        return execute(builder, JsonNode.class);
    }

    public JsonNode doPutFormRequest() throws IOException {
        RequestBuilder request = newRequestBuilder(HttpMethod.PUT, "/put")
                .beginFormBody()
                .addParam("param1", "test1")
                .addParam("param2", "test2")
                .addHeader("Header1", "h1")
                .addHeader("header2", "h2")
                .endBody();
        return execute(request, JsonNode.class);
    }

    public JsonNode doPutMultipartRequest() throws IOException {
        RequestBuilder builder = newRequestBuilder(HttpMethod.PUT, "/put")
                .beginMultipartBody()
                .addParam("param1", "test1")
                .addParam("param2", "test2")
                .addHeader("Header1", "h1")
                .addHeader("header2", "h2")
                .endBody();
        return execute(builder, JsonNode.class);
    }

    public JsonNode doPatchFormRequest() throws IOException {
        RequestBuilder request = newRequestBuilder(HttpMethod.PATCH, "/patch")
                .beginFormBody()
                .addParam("param1", "test1")
                .addParam("param2", "test2")
                .addHeader("Header1", "h1")
                .addHeader("header2", "h2")
                .endBody();
        return execute(request, JsonNode.class);
    }

    public Response doHeadRequest() throws IOException {
        RequestBuilder request = newRequestBuilder(HttpMethod.HEAD, "/get");
        return execute(request);
    }

    public Response doDeleteRequest() throws IOException {
        RequestBuilder request = newRequestBuilder(HttpMethod.DELETE, "/delete");
        return execute(request);
    }

}
