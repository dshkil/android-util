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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LatchResultFuture<V> extends AbstractResultFuture<V> {

    private final CountDownLatch resultLatch = new CountDownLatch(1);

    public LatchResultFuture(Executor defaultResultExecutor) {
        super(defaultResultExecutor);
    }

    @Override
    public boolean isResultReady() {
        return !isCancelled() && resultLatch.getCount() == 0;
    }

    public final void setResult(Result<V> result) {
        fireResult(result);
    }

    @Override
    protected final Result<V> fetchResult() throws ExecutionException, InterruptedException {
        resultLatch.await();
        return peekResult();
    }

    @Override
    protected final Result<V> fetchResult(long timeout, TimeUnit unit) throws InterruptedException,
            TimeoutException, ExecutionException {
        boolean reached = resultLatch.await(timeout, unit);
        if (!reached) {
            return Result.failure(new TimeoutException());
        }
        if (isCancelled()) {
            return Result.interrupted(new CancellationException());
        }
        Result<V> result = peekResult();
        if (result != null) {
            return result;
        }
        return Result.failure(new IllegalStateException());
    }

    @Override
    protected boolean onCancel() {
        if (resultLatch.getCount() <= 0) {
            return false;
        }
        return true;
    }

    @Override
    protected void onCompleted(boolean cancelled) {
        resultLatch.countDown();
    }

    @Override
    public String toString() {
        return "LatchResultFuture{" +
                "latchCount=" + resultLatch.getCount() +
                ", peek=" + peekResult() +
                '}';
    }
}
