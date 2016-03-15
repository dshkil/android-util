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
package com.shkil.android.util;

import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;

public class Result<V> {

    private final V value;
    private final Exception exception;
    private final boolean interrupted;

    public static <V> Result<V> success(V value) {
        return new Result<V>(value);
    }

    public static <V> Result<V> failure(Exception ex) {
        return new Result<V>(ex, ex instanceof InterruptedException || ex instanceof InterruptedIOException);
    }

    public static <V> Result<V> interrupted(Exception ex) {
        return new Result<V>(ex, true);
    }

    protected Result(V value) {
        this.value = value;
        this.exception = null;
        this.interrupted = false;
    }

    protected Result(Exception exception, boolean interrupted) {
        this.value = null;
        this.exception = exception;
        this.interrupted = interrupted;
    }

    public V getValue() {
        return value;
    }

    public V getValueOrThrow() throws Exception {
        if (exception != null) {
            throw exception;
        }
        return value;
    }

    public V getValueOrThrowEx() throws ExecutionException {
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        return value;
    }

    public Exception getException() {
        return exception;
    }

    public boolean isSuccess() {
        return exception == null;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public String toString() {
        return "Result[value=" + value + ",exception=" + exception + ",interrupted=" + interrupted + "]";
    }

}
