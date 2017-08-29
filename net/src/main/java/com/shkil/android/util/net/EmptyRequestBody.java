package com.shkil.android.util.net;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

class EmptyRequestBody extends RequestBody {

    public static final RequestBody INSTANCE = new EmptyRequestBody();

    @Override
    public MediaType contentType() {
        return null;
    }

    @Override
    public long contentLength() throws IOException {
        return 0;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
    }
}
