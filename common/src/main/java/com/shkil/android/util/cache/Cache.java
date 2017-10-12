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
package com.shkil.android.util.cache;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface Cache<K, V> {

    V get(K key);

    @NonNull
    Result<V> get(K key, @Nullable CacheControl cacheControl);

    boolean isCacheControlSupported();

    V put(K key, V value);

    V remove(K key);

    int size();

    void clear();

    Object getSyncLock();

    boolean isQuick();

    enum Status {
        NONE, GOOD, STALE
    }

    class Result<V> {
        private final V value;
        private final Status status;
        private static final Result<?> NONE = new Result<>(null, Status.NONE);

        public static <T> Result<T> none() {
            return (Result<T>) NONE;
        }

        public static <T> Result<T> normal(T value) {
            return new Result<>(value, Status.GOOD);
        }

        public static <T> Result<T> normalOrNone(T value) {
            return value != null ? normal(value) : Result.<T>none();
        }

        public static <T> Result<T> stale(T value) {
            return new Result<>(value, Status.STALE);
        }

        private Result(V value, Status status) {
            this.value = value;
            this.status = status;
        }

        public V getValue() {
            return value;
        }

        public Status getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "status=" + status +
                    ", value=" + value +
                    '}';
        }
    }
}
