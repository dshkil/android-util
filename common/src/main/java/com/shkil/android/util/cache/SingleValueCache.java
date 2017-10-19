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

/**
 * Implementation of cache to keep one latest pair of key-value
 */
public class SingleValueCache<K, V> implements Cache<K, V> {

    private volatile K key;
    private volatile V value;
    private volatile long timestamp;

    public static <K, V> SingleValueCache<K, V> newCache() {
        return new SingleValueCache<>();
    }

    @Override
    public V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        synchronized (getSyncLock()) {
            return key.equals(this.key) ? this.value : null;
        }
    }

    @Override
    @Nullable
    public Entry<V> getEntry(K key) {
        V value = get(key);
        if (value != null) {
            return new ControllableCache.Entry<>(value, timestamp);
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        V oldValue;
        K removedKey;
        V removedValue;
        boolean evicted;
        synchronized (getSyncLock()) {
            this.timestamp = currentTimeMillis();
            if (key.equals(this.key)) {
                oldValue = this.value;
                if (equal(value, oldValue)) {
                    return value;
                }
                evicted = false;
                removedKey = key;
                removedValue = oldValue;
            } else {
                oldValue = null;
                evicted = true;
                removedKey = this.key;
                removedValue = this.value;
            }
            this.key = key;
            this.value = value;
        }
        if (removedKey != null) {
            entryRemoved(evicted, removedKey, removedValue, null);
        }
        return oldValue;
    }

    @Override
    public void clear() {
        K oldKey;
        V oldValue;
        synchronized (getSyncLock()) {
            oldKey = this.key;
            oldValue = this.value;
            this.key = null;
            this.value = null;
        }
        if (oldValue != null) {
            entryRemoved(true, oldKey, oldValue, null);
        }
    }

    @Override
    public Object getSyncLock() {
        return this;
    }

    @Override
    public boolean isQuick() {
        return true;
    }

    @Override
    public boolean isCacheControlSupported() {
        return true;
    }

    @Override
    public V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        V removedValue = null;
        synchronized (getSyncLock()) {
            if (key.equals(this.key)) {
                removedValue = this.value;
                this.value = null;
            }
        }
        if (removedValue != null) {
            entryRemoved(false, key, removedValue, null);
        }
        return removedValue;
    }

    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
    }

    private static boolean equal(Object o1, Object o2) {
        return o1 == o2 || (o1 != null && o1.equals(o2));
    }

    @Override
    public int size() {
        return key == null ? 0 : 1;
    }
}
