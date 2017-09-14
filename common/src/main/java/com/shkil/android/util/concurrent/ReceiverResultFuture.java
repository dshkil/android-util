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

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import com.shkil.android.util.Result;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.shkil.android.util.io.ExceptionParcelable.throwIfException;

public abstract class ReceiverResultFuture<V> extends LatchResultFuture<V> {

    private volatile ResultReceiver resultReceiver;

    public static <V> ReceiverResultFuture<V> plain(final String dataKey, final String exceptionKey) {
        return new ReceiverResultFuture<V>() {
            @Override
            protected Result<V> convertResult(int resultCode, Bundle resultBundle) throws Exception {
                throwIfException(resultBundle, exceptionKey);
                V result = resultBundle.getParcelable(dataKey);
                return Result.success(result);
            }
        };
    }

    public ReceiverResultFuture() {
        this(MainThreadExecutor.getInstance());
    }

    public ReceiverResultFuture(@Nullable Executor defaultResultExecutor) {
        this(null, defaultResultExecutor);
    }

    public ReceiverResultFuture(@Nullable Handler resultReceiverHandler,
            @Nullable Executor defaultResultExecutor) {
        super(defaultResultExecutor);
        resultReceiver = new ResultReceiver(resultReceiverHandler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultBundle) {
                Result<V> result;
                try {
                    result = convertResult(resultCode, resultBundle);
                } catch (Exception ex) {
                    result = Result.failure(ex);
                }
                setResult(result);
            }
        };
    }

    public ResultReceiver getResultReceiver() {
        return resultReceiver;
    }

    @Override
    protected void onCompleted(boolean cancelled) {
        super.onCompleted(cancelled);
        if (cancelled) {
            resultReceiver = null;
        }
    }

    @Override
    protected void checkResultCallerThread() {
        if (MainThread.isCurrent()) {
            throw new IllegalStateException("Method invoked from the main thread");
        }
    }

    protected abstract Result<V> convertResult(int resultCode, Bundle resultBundle) throws Exception;

}
