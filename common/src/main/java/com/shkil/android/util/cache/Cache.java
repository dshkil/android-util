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

import static java.lang.System.currentTimeMillis;

public interface Cache<K, V> {

    V get(K key);

    @Nullable
    Entry<V> getEntry(K key);

    boolean isCacheControlSupported();

    V put(K key, V value);

    V put(K key, Entry<V> entry);

    V remove(K key);

    int size();

    void clear();

    Object getSyncLock();

    boolean isQuick();

    class Entry<T> {
        public enum Status {
            GOOD, STALE, BAD
        }

        private final T value;

        @Nullable
        public static <T> Entry<T> of(@Nullable T value) {
            return value != null ? new Entry<>(value) : null;
        }

        protected Entry(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }

        public long getTimestamp() {
            return 0;
        }

        @NonNull
        public Status check(@Nullable CacheControl cacheControl) {
            if (cacheControl == null) {
                return Status.GOOD;
            }
            long timestamp = getTimestamp();
            if (timestamp <= 0) {
                return Status.GOOD;
            }
            int ageSeconds = (int) ((currentTimeMillis() - timestamp) / 1000);
            if (ageSeconds <= cacheControl.maxAgeSeconds()) {
                return Status.GOOD;
            }
            if (ageSeconds <= cacheControl.maxStaleSeconds()) {
                return Status.STALE;
            }
            return Status.BAD;
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "value=" + value +
                    ", timestamp=" + getTimestamp() +
                    '}';
        }
    }
}
