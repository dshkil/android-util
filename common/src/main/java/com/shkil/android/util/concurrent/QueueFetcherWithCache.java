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
import com.shkil.android.util.ValueFetcher;
import com.shkil.android.util.cache.Cache;
import com.shkil.android.util.cache.LruCache;
import com.shkil.android.util.cache.SingleValueCache;

import java.util.concurrent.Executor;

public abstract class QueueFetcherWithCache<K, V> extends QueueFetcher<K, V> implements FetcherWithCache<K,V> {

    protected final Cache<? super K, V> cache;

    public static <K,V> QueueFetcherWithCache<K,V> create(Executor executor, int cacheSize, boolean mayInterruptTask, final ValueFetcher<K,V> fetcher) {
        return new QueueFetcherWithCache<K,V>(executor, cacheSize, mayInterruptTask) {
            @Override
            protected V fetchValue(K key) throws Exception {
                return fetcher.fetchValue(key);
            }
        };
    }

    public static <K,V> QueueFetcherWithCache<K,V> create(Executor executor, Executor resultExecutor, int cacheSize, boolean mayInterruptTask, final ValueFetcher<K,V> fetcher) {
        return new QueueFetcherWithCache<K,V>(executor, resultExecutor, cacheSize, mayInterruptTask) {
            @Override
            protected V fetchValue(K key) throws Exception {
                return fetcher.fetchValue(key);
            }
        };
    }

    public static <K,V> QueueFetcherWithCache<K,V> create(Executor executor, Cache<? super K, V> cache, boolean mayInterruptTask, final ValueFetcher<K,V> fetcher) {
        return new QueueFetcherWithCache<K,V>(executor, cache, mayInterruptTask) {
            @Override
            protected V fetchValue(K key) throws Exception {
                return fetcher.fetchValue(key);
            }
        };
    }

    public QueueFetcherWithCache(Executor executor, int cacheSize, boolean mayInterruptTask) {
        this(executor, MainThread.EXECUTOR, cacheSize, mayInterruptTask);
    }

    public QueueFetcherWithCache(Executor executor, Executor resultExecutor, int cacheSize, boolean mayInterruptTask) {
        this(executor, resultExecutor, QueueFetcherWithCache.<K, V>newCache(cacheSize), mayInterruptTask);
    }

    public QueueFetcherWithCache(Executor executor, Cache<? super K, V> cache, boolean mayInterruptTask) {
        this(executor, MainThread.EXECUTOR, cache, mayInterruptTask);
    }

    public QueueFetcherWithCache(Executor executor, Executor resultExecutor, Cache<? super K, V> cache, boolean mayInterruptTask) {
        super(executor, resultExecutor, mayInterruptTask);
        this.cache = cache;
        super.addListener(new FetcherListener<K, V>() {
            @Override
            public void onResult(K key, Result<V> result) {
                Cache<? super K, V> cache = QueueFetcherWithCache.this.cache;
                V value = result.getValue();
                if (value != null) {
                    synchronized (cache.getSyncLock()) {
                        cache.put(key, value);
                    }
                }
            }
        });
    }

    protected static <K, V> Cache<K, V> newCache(int cacheSize) {
        return cacheSize == 1 ? new SingleValueCache<K, V>() : new LruCache<K, V>(cacheSize);
    }

    @Override
    public ResultFuture<V> fetch(K key, Priority priority) {
        V value;
        synchronized (cache.getSyncLock()) {
            value = cache.get(key);
        }
        if (value != null) {
            return ResultFutures.success(value);
        }
        return super.fetch(key, priority);
    }

    @Override
    public V put(K key, V value) {
        synchronized (cache.getSyncLock()) {
            return cache.put(key, value);
        }
    }

    @Override
    public <T extends K> V getCachedValue(T key) {
        synchronized (cache.getSyncLock()) {
            return cache.get(key);
        }
    }

    @Override
    public void clear() {
        synchronized (cache.getSyncLock()) {
            cache.clear();
        }
    }

    @Override
    public V evict(K key) {
        synchronized (cache.getSyncLock()) {
            return cache.remove(key);
        }
    }

}
