package com.shkil.android.util.logging;

import okhttp3.logging.HttpLoggingInterceptor;

public class OkHttp3InterceptorLogger implements HttpLoggingInterceptor.Logger {

    private final Logger logger;

    public OkHttp3InterceptorLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(String message) {
        logger.debug(message);
    }

}
