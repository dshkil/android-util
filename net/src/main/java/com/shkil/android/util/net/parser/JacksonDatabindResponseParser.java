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

import android.support.annotation.NonNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shkil.android.util.net.ResponseParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

import okhttp3.Response;

public class JacksonDatabindResponseParser implements ResponseParser {

    public static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper;
    }

    public static JacksonDatabindResponseParser create() {
        return new JacksonDatabindResponseParser(createObjectMapper());
    }

    public static JacksonDatabindResponseParser with(ObjectMapper objectMapper) {
        return new JacksonDatabindResponseParser(objectMapper);
    }

    private final ObjectMapper objectMapper;

    protected JacksonDatabindResponseParser(@NonNull ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new NullPointerException("objectMapper");
        }
        this.objectMapper = objectMapper;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public <T> T parseResponse(Response response, Type resultType) throws IOException {
        Reader streamReader = response.body().charStream();
        try {
            return readObject(streamReader, resultType);
        } finally {
            streamReader.close();
        }
    }

    protected <T> T readObject(Reader reader, Type type) throws IOException {
        if (type instanceof Class<?>) {
            return objectMapper.readValue(reader, (Class<T>) type);
        }
        if (type instanceof JavaType) {
            return objectMapper.readValue(reader, (JavaType) type);
        }
        throw new IllegalArgumentException("type");
    }

    public JacksonDatabindResponseParser addMixIn(Class<?> target, Class<?> mixinSource) {
        getObjectMapper().addMixIn(target, mixinSource);
        return this;
    }

    public JacksonDatabindResponseParser configure(DeserializationFeature feature, boolean state) {
        getObjectMapper().configure(feature, state);
        return this;
    }

    public JacksonDatabindResponseParser enableUnwrapRootValue() {
        configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        return this;
    }

}
