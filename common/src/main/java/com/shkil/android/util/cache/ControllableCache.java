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

import android.support.annotation.Nullable;

import static java.lang.System.currentTimeMillis;

public class ControllableCache<K, V> implements Cache<K, V> {

    private final Cache<K, Entry<V>> cache;

    public ControllableCache(Cache<K, Entry<V>> cache) {
        this.cache = cache;
    }

    @Override
    public V get(K key) {
        Entry<V> entry = cache.get(key);
        return entry != null ? entry.getValue() : null;
    }

    @Nullable
    @Override
    public Entry<V> getEntry(K key) {
        return cache.get(key);
    }

    @Override
    public V put(K key, V value) {
        Entry<V> entry = cache.put(key, new Entry<>(value, currentTimeMillis()));
        return entry != null ? entry.getValue() : null;
    }

    @Override
    public V remove(K key) {
        Entry<V> entry = cache.remove(key);
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

    protected static class Entry<V> extends Cache.Entry<V> {
        private final long timestamp;

        public Entry(V value, long timestamp) {
            super(value);
            this.timestamp = timestamp;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
    }
}
