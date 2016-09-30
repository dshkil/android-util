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

public class AccessTokenException extends IOException {

    private static final long serialVersionUID = 1L;

    public static final String CODE_INVALID = "token-invalid";
    public static final String CODE_EXPIRED = "token-expired";

    private final String errorCode;

    public AccessTokenException(String message) {
        this(message, null, null);
    }

    public AccessTokenException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public AccessTokenException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public AccessTokenException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
