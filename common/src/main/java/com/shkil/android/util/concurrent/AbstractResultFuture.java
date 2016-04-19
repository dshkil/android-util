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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.GuardedBy;

abstract class AbstractResultFuture<V> implements ResultFuture<V> {

    private volatile Result<V> result;
    private volatile Executor resultExecutor;

    @GuardedBy("this")
    private ResultListener<V> listener;

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public AbstractResultFuture(Executor defaultResultExecutor) {
        this.resultExecutor = defaultResultExecutor;
    }

    @Override
    public final boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public final boolean cancel() {
        synchronized (this) {
            if (cancelled.getAndSet(true)) {
                return false;
            }
            this.listener = null;
            this.resultExecutor = null;
        }
        boolean result = onCancel();
        onDone();
        return result;
    }

    @Override
    public final Result<V> await() {
        try {
            return await(0L, null);
        } catch (TimeoutException ex) {
            return Result.failure(ex); // actually should never happens
        }
    }

    @Override
    public final Result<V> await(long time, TimeUnit units) throws TimeoutException {
        checkResultCallerThread();
        if (result != null) {
            return result;
        }
        try {
            return result = time > 0L ? this.fetchResult(time, units) : this.fetchResult();
        } catch (ExecutionException ex) {
            return result = Result.failure(ex);
        } catch (CancellationException ex) {
            return result = Result.interrupted(ex);
        } catch (InterruptedException ex) {
            return Result.interrupted(ex);
        }
    }

    @Override
    public final Result<V> peekResult() {
        return result;
    }

    /**
     * @return false if the task could not be cancelled, typically because it has already completed
     * normally; true otherwise
     */
    protected abstract boolean onCancel();

    protected abstract Result<V> fetchResult() throws ExecutionException, InterruptedException;

    protected abstract Result<V> fetchResult(long time, TimeUnit units) throws InterruptedException,
            TimeoutException, ExecutionException;

    protected final void onResult(Result<V> result) {
        try {
            if (isCancelled()) {
                return;
            }
            this.result = result;
            if (listener != null) {
                synchronized (this) {
                    if (listener != null) {
                        if (resultExecutor != null) {
                            resultExecutor.execute(new OnResultRunnable<>(listener, result, cancelled));
                        } else {
                            listener.onResult(result);
                        }
                    }
                }
            }
        } finally {
            onDone();
        }
    }

    protected abstract void onDone();

    @Override
    public final synchronized ResultFuture<V> setResultListener(ResultListener<V> listener) {
        if (this.listener != null) {
            throw new IllegalStateException("Listener was already set");
        }
        if (isCancelled()) {
            return this;
        }
        if (isResultReady()) {
            Result<V> result = this.result;
            if (result == null) {
                result = await();
            }
            if (result != null) {
                if (resultExecutor != null) {
                    resultExecutor.execute(new OnResultRunnable<>(listener, result, cancelled));
                } else {
                    listener.onResult(result);
                }
            }
        } else {
            this.listener = listener;
        }
        return this;
    }

    @Override
    public final synchronized ResultFuture<V> setResultListener(ResultListener<V> listener,
            Executor resultExecutor) {
        this.resultExecutor = resultExecutor;
        return setResultListener(listener);
    }

    static class OnResultRunnable<V> implements Runnable {
        private final ResultListener<V> listener;
        private final Result<V> result;
        private final AtomicBoolean cancelled;

        public OnResultRunnable(ResultListener<V> listener, Result<V> result, AtomicBoolean cancelled) {
            this.listener = listener;
            this.result = result;
            this.cancelled = cancelled;
        }

        @Override
        public void run() {
            if (cancelled.get()) {
                return;
            }
            listener.onResult(result);
        }
    }

    protected void checkResultCallerThread() {
    }

}
