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
package com.shkil.android.util.net.exception;

import java.io.IOException;

import static com.shkil.android.util.Utils.isNotEmpty;

public class ServerMessageException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final Object payload;

    public ServerMessageException(String message) {
        this(message, null);
    }

    public ServerMessageException(String message, String code) {
        super(message);
        this.code = code;
        this.payload = null;
    }

    public ServerMessageException(String message, String code, Object payload) {
        super(message);
        this.code = code;
        this.payload = payload;
    }

    public String getCode() {
        return code;
    }

    public <T> T getPayload(Class<T> type, boolean mandatory) {
        if (mandatory || type.isInstance(payload)) {
            return (T) payload;
        }
        return null;
    }

    @Override
    public String getLocalizedMessage() {
        String message = super.getMessage();
        if (isNotEmpty(message)) {
            return message + " / Code=" + code;
        }
        return "Code=" + code;
    }

}
