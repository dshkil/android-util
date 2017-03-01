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

    public enum Status {
        COMPLETED, INTERMEDIATE, INTERRUPTED
    }

    private final V value;
    private final Exception exception;
    private final Status status;

    public static <V> Result<V> success(V value) {
        return new Result<>(value, Status.COMPLETED);
    }

    public static <V> Result<V> intermediate(V value) {
        return new Result<>(value, Status.INTERMEDIATE);
    }

    public static <V> Result<V> failure(Exception ex) {
        if (ex instanceof InterruptedException || ex instanceof InterruptedIOException) {
            return interrupted(ex);
        }
        return new Result<>(ex, Status.COMPLETED);
    }

    public static <V> Result<V> interrupted(Exception ex) {
        return new Result<>(ex, Status.INTERRUPTED);
    }

    protected Result(V value, Status status) {
        this.value = value;
        this.exception = null;
        this.status = status;
    }

    protected Result(Exception exception, Status status) {
        this.value = null;
        this.exception = exception;
        this.status = status;
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

    public V getValueOrThrowRuntime() throws RuntimeException {
        if (exception != null) {
            throw new RuntimeException(exception);
        }
        return value;
    }

    public Exception getException() {
        return exception;
    }

    public boolean isSuccess() {
        return exception == null;
    }

    public boolean isNotSuccess() {
        return exception != null;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * @return true when result is not intermediate
     */
    public boolean isCompleted() {
        return status != Status.INTERMEDIATE;
    }

    public boolean isIntermediate() {
        return status == Status.INTERMEDIATE;
    }

    public boolean isInterrupted() {
        return status == Status.INTERRUPTED;
    }

    @Override
    public String toString() {
        return "Result[value=" + value + ",exception=" + exception + ",status=" + status + "]";
    }

}
