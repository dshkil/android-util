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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class ValueLatch<T> {

    @GuardedBy("this")
    private volatile T value;

    private final CountDownLatch latch = new CountDownLatch(1);

    public static <T> ValueLatch<T> create() {
        return new ValueLatch<T>();
    }

    public boolean isDone() {
        return latch.getCount() == 0;
    }

    public synchronized void setValue(T value) {
        if (isDone()) {
            return;
        }
        this.value = value;
        latch.countDown();
    }

    public T awaitValue() throws InterruptedException {
        latch.await();
        synchronized (this) {
            return value;
        }
    }

    public T awaitValue(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (latch.await(timeout, unit)) {
            synchronized (this) {
                return value;
            }
        }
        throw new TimeoutException();
    }

    public T awaitValue30Seconds() throws InterruptedException, TimeoutException {
        return awaitValue(30, TimeUnit.SECONDS);
    }

    public T awaitValue1Minute() throws InterruptedException, TimeoutException {
        return awaitValue(1, TimeUnit.MINUTES);
    }

    public T awaitValue3Minutes() throws InterruptedException, TimeoutException {
        return awaitValue(3, TimeUnit.MINUTES);
    }

    public T awaitValue5Minutes() throws InterruptedException, TimeoutException {
        return awaitValue(5, TimeUnit.MINUTES);
    }

}
