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
package com.shkil.android.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

public class DirectByteArrayOutputStream extends ByteArrayOutputStream implements DirectByteBuffer {

    public static final int DEFAULT_BUFFER_SIZE = 1024;

    public DirectByteArrayOutputStream() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public DirectByteArrayOutputStream(int initialSize) {
        super(initialSize);
    }

    @Override
    public byte[] getDirectBuffer() {
        return buf;
    }

    @Override
    public ByteArrayInputStream asInputStream() {
        return new ByteArrayInputStream(getDirectBuffer(), 0, size());
    }

    @Override
    public String toString(int offset, int maxLength) {
        return new String(buf, offset, Math.min(size() - offset, maxLength));
    }

    @Override
    public String toString(int offset, int maxLength, String charsetName) throws UnsupportedEncodingException {
        return new String(buf, offset, Math.min(size() - offset, maxLength), charsetName);
    }

}
