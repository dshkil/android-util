/*
 * Copyright (C) 2017 Dmytro Shkil
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

import com.shkil.android.util.Cancellable;
import com.shkil.android.util.CompletionListener;
import com.shkil.android.util.ExceptionListener;
import com.shkil.android.util.Result;
import com.shkil.android.util.ValueMapper;
import com.shkil.android.util.ResultListener;
import com.shkil.android.util.ValueListener;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ResultFuture<V> extends Cancellable {

    boolean isResultReady();

    /**
     * Waits if necessary for the computation to complete, and then retrieves its result.
     *
     * @return the result
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    @Nonnull
    Result<V> await();

    V awaitValue();

    V awaitValueOrThrow() throws Exception;

    V awaitValueOrThrowEx() throws ExecutionException;

    V awaitValueOrThrowRuntime() throws RuntimeException;

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result
     * @throws InterruptedException if the current thread was interrupted
     *                              while waiting
     * @throws TimeoutException     if the wait timed out
     */
    @Nonnull
    Result<V> await(long timeout, TimeUnit unit) throws TimeoutException;

    /**
     * Immediately returns result, if available, or null
     */
    @Nullable
    Result<V> peekResult();

    /**
     * Immediately returns value, if available, or null
     */
    @Nullable
    V peekValue();

    @Nullable
    V peekValueOrThrow() throws Exception;

    @Nullable
    V peekValueOrThrowEx() throws ExecutionException;

    @Nullable
    V peekValueOrThrowRuntime() throws RuntimeException;

    /**
     * @return false if the task could not be cancelled, typically because it has already completed
     * normally; true otherwise
     */
    boolean cancel();

    boolean isCancelled();

    ResultFuture<V> onCancel(Runnable listener);

    ResultFuture<V> onCancel(Runnable listener, Executor listenerExecutor);

    ResultFuture<V> onResult(ResultListener<V> listener);

    ResultFuture<V> onResult(ResultListener<V> listener, Executor listenerExecutor);

    ResultFuture<V> onSuccess(ValueListener<V> listener);

    ResultFuture<V> onSuccess(ValueListener<V> listener, Executor listenerExecutor);

    ResultFuture<V> onError(ExceptionListener listener);

    ResultFuture<V> onError(ExceptionListener listener, Executor listenerExecutor);

    ResultFuture<V> onCompleted(CompletionListener listener);

    ResultFuture<V> onCompleted(CompletionListener listener, Executor listenerExecutor);

    <R> ResultFuture<R> map(ValueMapper<? super V, ? extends R> mapper);

    <R> ResultFuture<R> map(@Nullable Executor mapperExecutor, ValueMapper<? super V, ? extends R> mapper);

    Executor getDefaultResultExecutor();
}
