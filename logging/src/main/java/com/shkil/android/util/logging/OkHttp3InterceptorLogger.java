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
package com.shkil.android.util.logging;

import android.util.Log;

import okhttp3.logging.HttpLoggingInterceptor;

public class OkHttp3InterceptorLogger implements HttpLoggingInterceptor.Logger {

    private final Logger logger;
    private final int level;

    public OkHttp3InterceptorLogger(Logger logger) {
        this(logger, Log.DEBUG);
    }

    public OkHttp3InterceptorLogger(Logger logger, int level) {
        if (logger == null) {
            throw new NullPointerException("logger");
        }
        if (level < Log.VERBOSE || level > Log.ERROR) {
            throw new IllegalArgumentException("level");
        }
        this.logger = logger;
        this.level = level;
    }

    @Override
    public void log(String message) {
        switch (level) {
            case Log.VERBOSE:
                logger.trace(message);
                break;
            case Log.DEBUG:
                logger.debug(message);
                break;
            case Log.INFO:
                logger.info(message);
                break;
            case Log.WARN:
                logger.warn(message);
                break;
            case Log.ERROR:
                logger.error(message);
                break;
        }
    }

}
