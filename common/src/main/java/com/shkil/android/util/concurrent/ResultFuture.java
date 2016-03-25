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

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ResultFuture<V> {

    boolean isResultReady();

    /**
     * Waits if necessary for the computation to complete, and then retrieves its result.
     *
     * @return the result
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    @Nonnull
    Result<V> awaitResult();

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the result
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    @Nonnull
    Result<V> awaitResult(long timeout, TimeUnit unit) throws TimeoutException;

    /**
     * Immediately returns result, if available, or null
     */
    @Nullable
    Result<V> peekResult();

    /**
     * @return false if the task could not be cancelled, typically because it has already completed
     * normally; true otherwise
     */
    boolean cancel();

    boolean isCancelled();

    ResultFuture<V> setResultListener(ResultListener<V> listener);

    ResultFuture<V> setResultListener(ResultListener<V> listener, Executor resultExecutor);

}
