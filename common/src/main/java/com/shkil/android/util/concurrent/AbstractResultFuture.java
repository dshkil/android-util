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

import com.shkil.android.util.ExceptionListener;
import com.shkil.android.util.Result;
import com.shkil.android.util.ResultListener;
import com.shkil.android.util.ValueListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.GuardedBy;

abstract class AbstractResultFuture<V> implements ResultFuture<V> {

    private volatile Executor defaultResultExecutor;

    private volatile Result<V> result;

    @GuardedBy("this")
    private ResultListener<V> listener;
    private volatile Executor resultExecutor;
    @GuardedBy("this")
    private Runnable cancellationListener;
    @GuardedBy("this")
    private Executor cancellationListenerExecutor;

    @GuardedBy("this")
    private List<ListenerEntry<V>> moreListeners;

    private final AtomicBoolean cancelled = new AtomicBoolean();

    private static class ListenerEntry<V> {
        final ResultListener<V> listener;
        final Executor resultExecutor;

        public ListenerEntry(ResultListener<V> listener, Executor resultExecutor) {
            this.listener = listener;
            this.resultExecutor = resultExecutor;
        }
    }

    public AbstractResultFuture(Executor defaultResultExecutor) {
        this.defaultResultExecutor = defaultResultExecutor;
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
            if (cancellationListener != null) {
                if (cancellationListenerExecutor != null) {
                    cancellationListenerExecutor.execute(cancellationListener);
                } else {
                    cancellationListener.run();
                }
            }
            this.listener = null;
            this.moreListeners = null;
            this.resultExecutor = null;
            this.defaultResultExecutor = null;
            this.cancellationListener = null;
            this.cancellationListenerExecutor = null;
        }
        boolean result = onCancel();
        onDone(result);
        return result;
    }

    @Override
    public final Result<V> await() {
        try {
            return await(0L, null);
        } catch (TimeoutException ex) {
            throw new RuntimeException(ex);
        }
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
    public final Result<V> await(long timeout, TimeUnit unit) throws TimeoutException {
        checkResultCallerThread();
        if (result != null) {
            return result;
        }
        try {
            return result = timeout > 0L ? this.fetchResult(timeout, unit) : this.fetchResult();
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

    protected abstract Result<V> fetchResult(long timeout, TimeUnit unit) throws InterruptedException,
            TimeoutException, ExecutionException;

    protected final void fireResult(Result<V> result) {
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
                    if (moreListeners != null) {
                        for (ListenerEntry<V> entry : moreListeners) {
                            Executor resultExecutor = entry.resultExecutor;
                            if (resultExecutor != null) {
                                resultExecutor.execute(new OnResultRunnable<>(entry.listener, result, cancelled));
                            } else {
                                entry.listener.onResult(result);
                            }
                        }
                    }
                }
            }
        } finally {
            onDone(false);
        }
    }

    protected abstract void onDone(boolean cancelled);

    @Override
    public final ResultFuture<V> onCancel(Runnable listener) {
        return onCancel(listener, defaultResultExecutor);
    }

    @Override
    public final synchronized ResultFuture<V> onCancel(Runnable listener, Executor resultExecutor) {
        if (cancellationListener != null) {
            throw new IllegalStateException("Only one cancellation listener is supported");
        }
        if (isCancelled()) {
            if (resultExecutor != null) {
                resultExecutor.execute(listener);
            } else {
                listener.run();
            }
        } else {
            this.cancellationListener = listener;
            this.cancellationListenerExecutor = resultExecutor;
        }
        return this;
    }

    @Override
    public final ResultFuture<V> onResult(ResultListener<V> listener) {
        return onResult(listener, defaultResultExecutor);
    }

    @Override
    public final synchronized ResultFuture<V> onResult(ResultListener<V> listener, Executor resultExecutor) {
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
        }
        if (this.listener == null) {
            this.listener = listener;
            this.resultExecutor = resultExecutor;
        } else {
            if (moreListeners == null) {
                moreListeners = new ArrayList<>(2);
            }
            moreListeners.add(new ListenerEntry<>(listener, resultExecutor));
        }
        return this;
    }

    @Override
    public ResultFuture<V> onSuccess(ValueListener<V> listener) {
        return onSuccess(listener, defaultResultExecutor);
    }

    @Override
    public ResultFuture<V> onSuccess(ValueListener<V> listener, Executor resultExecutor) {
        return onResult(ResultFutures.successAdapter(listener), resultExecutor);
    }

    @Override
    public ResultFuture<V> onError(ExceptionListener listener) {
        return onError(listener, defaultResultExecutor);
    }

    @Override
    public ResultFuture<V> onError(ExceptionListener listener, Executor resultExecutor) {
        return onResult(ResultFutures.<V>errorAdapter(listener), resultExecutor);
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
