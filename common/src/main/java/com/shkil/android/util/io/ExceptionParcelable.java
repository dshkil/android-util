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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import static java.lang.Math.min;
import static java.util.Arrays.copyOf;

public class ExceptionParcelable implements Parcelable {

    private static final int DEFAULT_STACK_TRACE_LIMIT = 5;
    private static final int MAX_STACK_TRACE_LIMIT = 15;

    private static final ClassLoader CLASS_LOADER = ExceptionParcelable.class.getClassLoader();

    public static void throwIfException(Bundle bundle, String key) throws Exception {
        ClassLoader originalClassLoader = bundle.getClassLoader();
        bundle.setClassLoader(CLASS_LOADER);
        try {
            throwIfException(bundle.<ExceptionParcelable>getParcelable(key));
        } finally {
            bundle.setClassLoader(originalClassLoader);
        }
    }

    public static void throwIfException(ExceptionParcelable in) throws Exception {
        if (in != null) {
            throw in.getException();
        }
    }

    private final Exception exception;
    private final int stackTraceLimit;

    public ExceptionParcelable(Exception ex) {
        this(ex, DEFAULT_STACK_TRACE_LIMIT);
    }

    public ExceptionParcelable(Exception ex, int stackTraceLimit) {
        if (ex == null) {
            throw new NullPointerException();
        }
        this.exception = ex;
        this.stackTraceLimit = min(MAX_STACK_TRACE_LIMIT, stackTraceLimit);
    }

    public Exception getException() {
        return exception;
    }

    private static void trimStackTrace(Exception ex, int maxSize) {
        if (maxSize > 0) {
            ex.setStackTrace(copyOf(ex.getStackTrace(), maxSize));
        } else if (maxSize == 0) {
            ex.setStackTrace(new StackTraceElement[0]);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        trimStackTrace(exception, stackTraceLimit);
        out.writeString(exception.getMessage());
        out.writeString(exception.getClass().getName());
        out.writeSerializable(exception);
    }

    protected ExceptionParcelable(Parcel in) {
        String message = in.readString();
        String className = in.readString();
        Exception ex;
        try {
            ex = (Exception) in.readSerializable();
        } catch (RuntimeException e) {
            if (message == null) {
                ex = new Exception("Can't deserialize exception " + className, e);
            } else {
                ex = new Exception("Can't deserialize exception " + className + ": " + message, e);
            }
        }
        this.exception = ex;
        this.stackTraceLimit = -1;
    }

    public static final Creator<ExceptionParcelable> CREATOR = new Creator<ExceptionParcelable>() {
        public ExceptionParcelable createFromParcel(Parcel source) {
            return new ExceptionParcelable(source);
        }

        public ExceptionParcelable[] newArray(int size) {
            return new ExceptionParcelable[size];
        }
    };

}
