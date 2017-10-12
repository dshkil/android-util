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

public class ControllableCache<K, V> implements Cache<K, V> {

    private final Cache<K, CacheEntry<V>> cache;

    public ControllableCache(Cache<K, CacheEntry<V>> cache) {
        this.cache = cache;
    }

    @Override
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        return entry != null ? entry.getValue() : null;
    }

    public CacheEntry<V> getEntry(K key) {
        return cache.get(key);
    }

    @NonNull
    @Override
    public Result<V> get(K key, @Nullable CacheControl cacheControl) {
        if (cacheControl == null) {
            return Result.normalOrNone(get(key));
        }
        CacheEntry<V> entry = cache.get(key);
        if (entry != null) {
            int ageSeconds = (int) ((currentTimeMillis() - entry.getTimestamp()) / 1000);
            if (ageSeconds <= cacheControl.maxAgeSeconds()) {
                return Result.normal(entry.getValue());
            }
            if (ageSeconds <= cacheControl.maxStaleSeconds()) {
                return Result.stale(entry.getValue());
            }
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        CacheEntry<V> entry = cache.put(key, new CacheEntry<>(value, currentTimeMillis()));
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public V remove(K key) {
        CacheEntry<V> entry = cache.remove(key);
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public Object getSyncLock() {
        return cache.getSyncLock();
    }

    @Override
    public boolean isQuick() {
        return cache.isQuick();
    }

    @Override
    public boolean isCacheControlSupported() {
        return true;
    }

    @Override
    public String toString() {
        return "ControllableCache{" +
                "cache=" + cache +
                '}';
    }

    public static class CacheEntry<V> {
        private final V value;
        private final long timestamp;

        public CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public V getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "CacheEntry{" +
                    "value=" + value +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
