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

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

public class GsonResponseParser extends AbstractResponseParser {

    public static GsonResponseParser with(Gson gson) {
        return new GsonResponseParser(gson);
    }

    private final Gson gson;

    protected GsonResponseParser(@NonNull Gson gson) {
        if (gson == null) {
            throw new NullPointerException("gson");
        }
        this.gson = gson;
    }

    @Override
    protected <T> T readObject(Reader reader, Type type) throws IOException {
        try {
            return gson.fromJson(reader, type);
        } catch (RuntimeException ex) {
            throw new IOException(ex);
        }
    }

}
