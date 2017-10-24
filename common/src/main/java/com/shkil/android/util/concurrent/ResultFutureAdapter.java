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

import android.support.annotation.NonNull;

import com.shkil.android.util.CompletionListener;
import com.shkil.android.util.ExceptionListener;
import com.shkil.android.util.Result;
import com.shkil.android.util.ResultListener;
import com.shkil.android.util.ValueListener;
import com.shkil.android.util.ValueMapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

public abstract class ResultFutureAdapter<W, V> implements ResultFuture<V> {

    private final ResultFuture<W> sourceFuture;
    @GuardedBy("this")
    private Result<W> originalResult;
    @GuardedBy("this")
    private volatile Result<V> convertedResult;
    private final Executor converterExecutor;

    static <V, R> ResultFuture<R> map(ResultFuture<V> source, ValueMapper<? super V, ? extends R> mapper) {
        return map(source, mapper, null);
    }

    static <V, R> ResultFuture<R> map(ResultFuture<V> source, final ValueMapper<? super V, ? extends R> mapper, Executor mapperExecutor) {
        return new ResultFutureAdapter<V, R>(source, mapperExecutor) {
            @Override
            protected R convertValue(V value) throws Exception {
                return mapper.map(value);
            }
        };
    }

    protected ResultFutureAdapter(ResultFuture<W> source) {
        this(source, source.getDefaultResultExecutor());
    }

    protected ResultFutureAdapter(ResultFuture<W> source, Executor converterExecutor) {
        if (source == null) {
            throw new NullPointerException();
        }
        this.sourceFuture = source;
        this.converterExecutor = converterExecutor;
    }

    @SuppressWarnings({"unchecked"})
    protected Result<V> handleResult(@NonNull Result<W> result) {
        synchronized (this) {
            if (convertedResult != null && result == originalResult) {
                return convertedResult;
            }
            originalResult = result;
            if (result.isInterrupted()) {
                return convertedResult = (Result<V>) result;
            }
            Exception exception = result.getException();
            if (exception != null) {
                Result<V> exceptionResult = processException(result);
                if (exceptionResult != null) {
                    return convertedResult = exceptionResult;
                }
            }
            try {
                V value = convertValue(result.getValue());
                return convertedResult = Result.create(value, result.getStatus());
            } catch (Exception ex) {
                return Result.failure(ex);
            }
        }
    }

    void onResultCallback(Result<W> in, final ResultListener<V> listener, Executor resultExecutor) {
        final Result<V> out = handleResult(in);
        if (resultExecutor == null || resultExecutor == converterExecutor) {
            listener.onResult(out);
        } else {
            resultExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    listener.onResult(out);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    protected Result<V> processException(Result<W> result) {
        return (Result<V>) result;
    }

    protected abstract V convertValue(@Nullable W value) throws Exception;

    @Override
    public boolean isResultReady() {
        return sourceFuture.isResultReady();
    }

    @Nullable
    @Override
    public Result<V> peekResult() {
        Result<W> result = sourceFuture.peekResult();
        return result != null ? handleResult(result) : null;
    }

    @Nullable
    @Override
    public V peekValue() {
        Result<V> result = peekResult();
        return result != null ? result.getValue() : null;
    }

    @Nullable
    @Override
    public V peekValueOrThrow() throws Exception {
        Result<V> result = peekResult();
        return result != null ? result.getValueOrThrow() : null;
    }

    @Nullable
    @Override
    public V peekValueOrThrowEx() throws ExecutionException {
        Result<V> result = peekResult();
        return result != null ? result.getValueOrThrowEx() : null;
    }

    @Nullable
    @Override
    public V peekValueOrThrowRuntime() throws RuntimeException {
        Result<V> result = peekResult();
        return result != null ? result.getValueOrThrowRuntime() : null;
    }

    @Override
    public Result<V> await() {
        return handleResult(sourceFuture.await());
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
    public Result<V> await(long timeout, TimeUnit unit) throws TimeoutException {
        return handleResult(sourceFuture.await(timeout, unit));
    }

    @Override
    public boolean cancel() {
        return sourceFuture.cancel();
    }

    @Override
    public boolean isCancelled() {
        return sourceFuture.isCancelled();
    }

    @Override
    public ResultFuture<V> onCancel(Runnable listener) {
        sourceFuture.onCancel(listener);
        return this;
    }

    @Override
    public ResultFuture<V> onCancel(Runnable listener, Executor listenerExecutor) {
        sourceFuture.onCancel(listener, listenerExecutor);
        return this;
    }

    @Override
    public ResultFuture<V> onResult(final ResultListener<V> listener) {
        sourceFuture.onResult(new ResultListener<W>() {
            @Override
            public void onResult(Result<W> result) {
                onResultCallback(result, listener, getDefaultResultExecutor());
            }
        }, converterExecutor);
        return this;
    }

    @Override
    public ResultFuture<V> onResult(final ResultListener<V> listener, final Executor resultExecutor) {
        sourceFuture.onResult(new ResultListener<W>() {
            @Override
            public void onResult(Result<W> result) {
                onResultCallback(result, listener, resultExecutor);
            }
        }, converterExecutor);
        return this;
    }

    public ResultFuture<V> onSuccess(ValueListener<V> listener) {
        return onResult(ResultFutures.successAdapter(listener));
    }

    public ResultFuture<V> onSuccess(ValueListener<V> listener, Executor resultExecutor) {
        return onResult(ResultFutures.successAdapter(listener), resultExecutor);
    }

    @Override
    public ResultFuture<V> onError(ExceptionListener listener) {
        return onResult(ResultFutures.<V>errorAdapter(listener));
    }

    @Override
    public ResultFuture<V> onError(ExceptionListener listener, Executor resultExecutor) {
        return onResult(ResultFutures.<V>errorAdapter(listener), resultExecutor);
    }

    @Override
    public ResultFuture<V> onCompleted(CompletionListener listener) {
        sourceFuture.onCompleted(listener);
        return this;
    }

    @Override
    public ResultFuture<V> onCompleted(CompletionListener listener, Executor resultExecutor) {
        sourceFuture.onCompleted(listener, resultExecutor);
        return this;
    }

    @Override
    public <R> ResultFuture<R> map(ValueMapper<? super V, ? extends R> mapper) {
        return ResultFutureAdapter.map(this, mapper);
    }

    @Override
    public <R> ResultFuture<R> map(Executor mapperExecutor, ValueMapper<? super V, ? extends R> mapper) {
        return ResultFutureAdapter.map(this, mapper, mapperExecutor);
    }

    @Override
    public Executor getDefaultResultExecutor() {
        return sourceFuture.getDefaultResultExecutor();
    }
}
