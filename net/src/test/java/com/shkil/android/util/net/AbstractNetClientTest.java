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

import org.junit.Test;

import okhttp3.Response;
import okio.ByteString;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNotSame;

public class AbstractNetClientTest {

    @Test
    public void testGetRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        JsonNode response = netClient.doGetRequest();
        assertNotNull(response);
        JsonNode argsNode = response.get("args");
        assertEquals("test1", argsNode.get("param1").asText());
        assertEquals("test2", argsNode.get("param2").asText());
        JsonNode headersNode = response.get("headers");
        assertEquals("h1", headersNode.get("Header1").asText());
        assertEquals("h2", headersNode.get("Header2").asText());
        assertEquals("http://httpbin.org/get?param1=test1&param2=test2", response.get("url").asText());
    }

    @Test
    public void testGetBytesRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        ByteString response = netClient.doGetBytesRequest(123);
        assertNotNull(response);
        assertEquals(123, response.size());
    }

    @Test
    public void testPostFormRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        JsonNode response = netClient.doPostFormRequest();
        assertNotNull(response);
        JsonNode formNode = response.get("form");
        assertEquals("test1", formNode.get("param1").asText());
        assertEquals("test2", formNode.get("param2").asText());
        JsonNode headersNode = response.get("headers");
        assertEquals("h1", headersNode.get("Header1").asText());
        assertEquals("h2", headersNode.get("Header2").asText());
        assertEquals("http://httpbin.org/post", response.get("url").asText());
    }

    @Test
    public void testPostMultipartRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        JsonNode response = netClient.doPostMultipartRequest();
        assertNotNull(response);
        JsonNode dataNode = response.get("data");
        assertNotSame("", dataNode.asText());
        JsonNode headersNode = response.get("headers");
        assertEquals("h1", headersNode.get("Header1").asText());
        assertEquals("h2", headersNode.get("Header2").asText());
        assertEquals("http://httpbin.org/post", response.get("url").asText());
    }

    @Test
    public void testPutFormRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        JsonNode response = netClient.doPutFormRequest();
        assertNotNull(response);
        JsonNode formNode = response.get("form");
        assertEquals("test1", formNode.get("param1").asText());
        assertEquals("test2", formNode.get("param2").asText());
        JsonNode headersNode = response.get("headers");
        assertEquals("h1", headersNode.get("Header1").asText());
        assertEquals("h2", headersNode.get("Header2").asText());
        assertEquals("http://httpbin.org/put", response.get("url").asText());
    }

    @Test
    public void testPutMultipartRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        JsonNode response = netClient.doPutMultipartRequest();
        assertNotNull(response);
        JsonNode dataNode = response.get("data");
        assertNotSame("", dataNode.asText());
        JsonNode headersNode = response.get("headers");
        assertEquals("h1", headersNode.get("Header1").asText());
        assertEquals("h2", headersNode.get("Header2").asText());
        assertEquals("http://httpbin.org/put", response.get("url").asText());
    }

    @Test
    public void testPatchFormRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        JsonNode response = netClient.doPatchFormRequest();
        assertNotNull(response);
        JsonNode formNode = response.get("form");
        assertEquals("test1", formNode.get("param1").asText());
        assertEquals("test2", formNode.get("param2").asText());
        JsonNode headersNode = response.get("headers");
        assertEquals("h1", headersNode.get("Header1").asText());
        assertEquals("h2", headersNode.get("Header2").asText());
        assertEquals("http://httpbin.org/patch", response.get("url").asText());
    }

    @Test
    public void testHeadRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        Response response = netClient.doHeadRequest();
        assertNotNull(response);
        assertEquals(200, response.code());
        assertEquals(196, response.body().contentLength());
    }

    @Test
    public void testDeleteRequest() throws Exception {
        HttpbinNetClient netClient = new HttpbinNetClient();
        Response response = netClient.doDeleteRequest();
        assertNotNull(response);
        assertEquals(200, response.code());
        assertEquals(262, response.body().contentLength());
    }

}
