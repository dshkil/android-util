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
package com.shkil.android.util.concurrent;

import com.shkil.android.util.Result;
import com.shkil.android.util.ResultListener;
import com.shkil.android.util.concurrent.AbstractResultFuture.OnResultRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResultFutures {

    public static <V> ResultFuture<V> result(Result<V> result) {
        return result(result, MainThreadExecutor.getInstance());
    }

    public static <V> ResultFuture<V> result(Result<V> result, Executor defaultResultExecutor) {
        return new ImmediateResultFuture<V>(result, defaultResultExecutor);
    }

    public static <V> ResultFuture<V> success(V value) {
        return success(value, MainThreadExecutor.getInstance());
    }

    public static <V> ResultFuture<V> success(V value, Executor defaultResultExecutor) {
        return result(Result.<V>success(value), defaultResultExecutor);
    }

    public static <V> ResultFuture<V> failure(Exception ex) {
        return failure(ex, MainThreadExecutor.getInstance());
    }

    public static <V> ResultFuture<V> failure(Exception ex, Executor defaultResultExecutor) {
        return result(Result.<V>failure(ex), defaultResultExecutor);
    }

    public static <V> ResultFutureTask<V> futureTask(Callable<V> task) {
        return ResultFutureTask.create(task);
    }

    public static <V> ResultFutureTask<V> executeTask(Callable<V> task, Executor taskExecutor) {
        return ResultFutureTask.execute(task, taskExecutor);
    }

    private static class ImmediateResultFuture<V> implements ResultFuture<V> {
        private final Result<V> result;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Executor resultExecutor;

        public static <V> ResultFuture<V> success(V value, Executor defaultResultExecutor) {
            return create(Result.success(value), defaultResultExecutor);
        }

        public static <V> ResultFuture<V> failure(Exception ex, Executor defaultResultExecutor) {
            return create(Result.<V>failure(ex), defaultResultExecutor);
        }

        public static <V> ResultFuture<V> create(Result<V> result, Executor defaultResultExecutor) {
            return new ImmediateResultFuture<V>(result, defaultResultExecutor);
        }

        protected ImmediateResultFuture(Result<V> result, Executor defaultResultExecutor) {
            this.result = result;
            this.resultExecutor = defaultResultExecutor;
        }

        @Override
        public boolean isResultReady() {
            return true;
        }

        @Override
        public Result<V> await() {
            return result;
        }

        @Override
        public V awaitValue() {
            return await().getValue();
        }

        @Override
        public V awaitValueOrThrow() throws Exception {
            return await().getValueOrThrow();
        }

        @Override
        public V awaitValueOrThrowEx() throws ExecutionException {
            return await().getValueOrThrowEx();
        }

        @Override
        public V awaitValueOrThrowRuntime() throws RuntimeException {
            return await().getValueOrThrowRuntime();
        }

        @Override
        public Result<V> await(long timeout, TimeUnit unit) {
            return result;
        }

        @Override
        public Result<V> peekResult() {
            return result;
        }

        @Override
        public boolean cancel() {
            return cancelled.compareAndSet(false, true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public ResultFuture<V> onResult(ResultListener<V> listener) {
            if (resultExecutor != null) {
                resultExecutor.execute(new OnResultRunnable<>(listener, result, cancelled));
            } else {
                listener.onResult(result);
            }
            return this;
        }

        @Override
        public ResultFuture<V> onResult(ResultListener<V> listener, Executor resultExecutor) {
            this.resultExecutor = resultExecutor;
            return onResult(listener);
        }

    }

}
