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

    public ServerMessageException(String serverMessage) {
        this(serverMessage, null);
    }

    public ServerMessageException(String serverMessage, long code) {
        this(serverMessage, String.valueOf(code));
    }

    public ServerMessageException(String serverMessage, String code) {
        super(serverMessage);
        this.code = code;
        this.payload = null;
    }

    public ServerMessageException(String serverMessage, long code, Object payload) {
        this(serverMessage, String.valueOf(code), payload);
    }

    public ServerMessageException(String serverMessage, String code, Object payload) {
        super(serverMessage);
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

    public String getServerMessage() {
        return super.getMessage();
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (isNotEmpty(message)) {
            return message + " / Code=" + code;
        }
        return "Code=" + code;
    }

    @Override
    public String getLocalizedMessage() {
        return super.getMessage();
    }

}
