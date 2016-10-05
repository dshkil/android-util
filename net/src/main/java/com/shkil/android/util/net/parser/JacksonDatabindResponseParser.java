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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

public class JacksonDatabindResponseParser extends AbstractResponseParser {

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

    protected <T> T readObject(Reader reader, Type type) throws IOException {
        try {
            JavaType javaType = objectMapper.constructType(type);
            return objectMapper.readValue(reader, javaType);
        } catch (RuntimeException ex) {
            throw new IOException(ex);
        }
    }

    public JacksonDatabindResponseParser addMixIn(Class<?> target, Class<?> mixinSource) {
        objectMapper.addMixIn(target, mixinSource);
        return this;
    }

    public JacksonDatabindResponseParser configure(DeserializationFeature feature, boolean state) {
        objectMapper.configure(feature, state);
        return this;
    }

    public JacksonDatabindResponseParser enableUnwrapRootValue() {
        configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        return this;
    }

}
