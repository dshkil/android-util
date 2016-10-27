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
package com.shkil.android.util.net.parser;

import com.shkil.android.util.net.ResponseParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.ByteString;

public abstract class AbstractResponseParser implements ResponseParser {

    public <T> T parseResponse(Response response, Type resultType) throws IOException {
        ResponseBody responseBody = response.body();
        T object = readStandardType(responseBody, resultType);
        if (object != null) {
            return object;
        }
        Reader streamReader = responseBody.charStream();
        try {
            return readObject(streamReader, resultType);
        } finally {
            streamReader.close();
        }
    }

    protected <T> T readStandardType(ResponseBody body, Type resultType) throws IOException {
        if (resultType == String.class) {
            return (T) body.string();
        } else if (resultType == JSONObject.class) {
            try {
                return (T) new JSONObject(body.string());
            } catch (JSONException ex) {
                throw new IOException(ex);
            }
        } else if (resultType == byte[].class) {
            return (T) body.bytes();
        } else if (resultType == ByteString.class) {
            return (T) body.source().readByteString();
        }
        return null;
    }

    protected abstract <T> T readObject(Reader reader, Type type) throws IOException;

}
