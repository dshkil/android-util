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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import okhttp3.Response;

public class SimpleResponseParser extends AbstractResponseParser {

    private static class InstanceHolder {
        static final ResponseParser INSTANCE = new SimpleResponseParser();
    }

    public static ResponseParser getInstance() {
        return InstanceHolder.INSTANCE;
    }

    protected SimpleResponseParser() {
    }

    public <T> T parseResponse(Response response, Type resultType) throws IOException {
        T object = readStandardType(response.body(), resultType);
        if (object != null) {
            return object;
        }
        throw new IllegalArgumentException("resultType");
    }

    @Override
    protected <T> T readObject(Reader reader, Type type) throws IOException {
        throw new RuntimeException("Shouldn't be invoked");
    }

}
