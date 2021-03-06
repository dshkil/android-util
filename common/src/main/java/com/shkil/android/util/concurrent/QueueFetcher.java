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

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.shkil.android.util.Result;
import com.shkil.android.util.ResultListener;
import com.shkil.android.util.ValueFetcher;
import com.shkil.android.util.cache.Cache;
import com.shkil.android.util.cache.CacheControl;
import com.shkil.android.util.exception.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.GuardedBy;

public abstract class QueueFetcher<K, V> implements Fetcher<K, V> {

    private static final String TAG = "QueueFetcher";

    public static final int RUNNING_TASKS_LIMIT = 1;

    private final Executor defaultResultExecutor;
    private final boolean mayInterruptTask;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private final TreeMap<QueueKey<K>, FetcherTask> tasksQueue = new TreeMap<QueueKey<K>, FetcherTask>();

    @GuardedBy("lock")
    private final Map<K, FetcherTask> runningTasks = new HashMap<K, FetcherTask>();

    @GuardedBy("itself")
    private final List<FetcherListener<K, V>> listeners = new ArrayList<>(5);

    @GuardedBy("lock")
    private volatile Cache<K, V> quickCache;

    @GuardedBy("lock")
    private volatile Cache<K, V> secondaryCache;

    private final Executor executor;

    private volatile RequestParams defaultRequestParams = RequestParams.DEFAULT;

    private static final Executor asyncTaskExecutor = AsyncTask.THREAD_POOL_EXECUTOR;

    private interface FetcherListenerWithPriority<K, V> extends FetcherListener<K, V> {
        long getPriority();
    }

    private static class QueueKey<K> implements Comparable<QueueKey> {
        private final K key;
        private final int hashCode;
        private long priority;

        private QueueKey(K key) {
            this.key = key;
            this.hashCode = key.hashCode();
        }

        private QueueKey(K key, long priority) {
            this.key = key;
            this.hashCode = key.hashCode();
            this.priority = priority;
        }

        public void setPriority(long priority) {
            this.priority = priority;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object another) {
            return another == this || ((QueueKey) another).key.equals(key);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public int compareTo(QueueKey another) {
            if (another == this) {
                return 0;
            }
            int diff = (int) (another.priority - this.priority);
            if (diff != 0) {
                return diff;
            }
            if (this.equals(another)) {
                return 0;
            }
            return System.identityHashCode(this) - System.identityHashCode(another);
        }

        @Override
        public String toString() {
            return "QueueKey[key=" + key + "]";
        }
    }

    public static <K, V> QueueFetcher<K, V> create(Executor executor, boolean mayInterruptTask, final ValueFetcher<K, V> fetcher) {
        return new QueueFetcher<K, V>(executor, mayInterruptTask) {
            @Override
            protected V fetchValue(K key) throws Exception {
                return fetcher.fetchValue(key);
            }
        };
    }

    public static <K, V> QueueFetcher<K, V> create(Executor executor, @Nullable Executor resultExecutor, boolean mayInterruptTask, final ValueFetcher<K, V> fetcher) {
        return new QueueFetcher<K, V>(executor, resultExecutor, mayInterruptTask) {
            @Override
            protected V fetchValue(K key) throws Exception {
                return fetcher.fetchValue(key);
            }
        };
    }

    public QueueFetcher(Executor executor, boolean mayInterruptTask) {
        this(executor, MainThread.EXECUTOR, mayInterruptTask);
    }

    public QueueFetcher(Executor executor, @Nullable Executor defaultResultExecutor, boolean mayInterruptTask) {
        this.executor = executor;
        this.defaultResultExecutor = defaultResultExecutor;
        this.mayInterruptTask = mayInterruptTask;
    }

    /**
     * Set a primary cache. A quick cache only is allowed here
     */
    public QueueFetcher<K, V> setCache(Cache<K, V> cache) {
        if (!cache.isQuick()) {
            throw new IllegalArgumentException("Attempt to set non-quick cache. Use setSecondaryCache() instead.");
        }
        synchronized (lock) {
            this.quickCache = cache;
        }
        return this;
    }

    public Cache<K, V> getCache() {
        return quickCache;
    }

    /**
     * Set a secondary cache
     */
    public QueueFetcher<K, V> setSecondaryCache(Cache<K, V> cache) {
        synchronized (lock) {
            this.secondaryCache = cache;
        }
        return this;
    }

    public Cache<K, V> getSecondaryCache() {
        return secondaryCache;
    }

    public QueueFetcher<K, V> setDefaultRequestParams(@Nullable RequestParams params) {
        this.defaultRequestParams = params != null ? params : RequestParams.DEFAULT;
        return this;
    }

    public QueueFetcher<K, V> setDefaultRequestParams(@Nullable RequestParams.Builder params) {
        this.defaultRequestParams = params != null ? params.build() : RequestParams.DEFAULT;
        return this;
    }

    public RequestParams getDefaultRequestParams() {
        return defaultRequestParams;
    }

    @Override
    public ResultFuture<V> fetch(K key) {
        return fetch(key, (RequestParams) null);
    }

    @Override
    public ResultFuture<V> fetch(K key, @Nullable Priority priority) {
        RequestParams params = defaultRequestParams;
        if (priority != null) {
            if (params == RequestParams.DEFAULT) {
                params = RequestParams.priority(priority);
            } else {
                params = params.toBuilder().priority(priority).build();
            }
        }
        return fetch(key, params);
    }

    @Override
    public ResultFuture<V> fetch(final K key, @Nullable RequestParams params) {
        if (params == null) {
            params = defaultRequestParams;
        }
        CacheControl cacheControl = params.cacheControl();
        boolean cacheAllowed = cacheControl.isCacheAllowed();
        V staleResult = null;
        synchronized (lock) {
            if (quickCache != null && cacheAllowed) {
                if (quickCache.isCacheControlSupported() && cacheControl.isTimeLimited()) {
                    Cache.Entry<V> cacheEntry = quickCache.getEntry(key);
                    if (cacheEntry != null) {
                        switch (cacheEntry.check(cacheControl)) {
                            case GOOD:
                                return ResultFutures.success(cacheEntry.getValue());
                            case STALE:
                                staleResult = cacheEntry.getValue();
                                break;
                        }
                    }
                } else {
                    V value = quickCache.get(key);
                    if (value != null) {
                        return ResultFutures.success(value);
                    }
                }
            }
            if (staleResult != null && cacheControl.cacheOnly()) {
                return ResultFutures.success(staleResult);
            }
            if (staleResult == null && secondaryCache != null && cacheAllowed) {
                LatchFetchingFuture resultFuture = new LatchFetchingFuture();
                asyncTaskExecutor.execute(new SecondaryCacheRunnable(key, params, resultFuture));
                return resultFuture;
            } else {
                return getTaskResultFuture(key, params, deferredFetchingFutureFactory, staleResult);
            }
        }
    }

    private abstract class ResultFutureFactory<V> {
        abstract ResultFuture<V> createResultFuture(RequestParams params, @Nullable V staleResult, long priorityOrdinal, FetcherTask task);
    }

    @NonNull
    @GuardedBy("lock")
    private ResultFuture<V> getTaskResultFuture(K key, RequestParams params, ResultFutureFactory<V> resultFactory, @Nullable V staleResult) {
        CacheControl cacheControl = params.cacheControl();
        Priority priority = params.priority();
        boolean forceExecute = (priority == Priority.IMMEDIATE);
        long priorityOrdinal = priority.toLong(SystemClock.uptimeMillis());
        FetcherTask task = runningTasks.get(key);
        if (task == null) {
            if (forceExecute || (tasksQueue.isEmpty() && runningTasks.size() < RUNNING_TASKS_LIMIT)) {
                task = new FetcherTask(key);
                runningTasks.put(key, task);
                forceExecute = true;
            } else { // enqueue task
                QueueKey queueKey = new QueueKey(key);
                task = tasksQueue.remove(queueKey);
                if (task == null) {
                    task = new FetcherTask(key);
                    queueKey.setPriority(priorityOrdinal);
                } else {
                    long currentMaxPriority = findMaxPriority(task.getListeners());
                    queueKey.setPriority(Math.max(priorityOrdinal, currentMaxPriority));
                }
                tasksQueue.put(queueKey, task);
            }
        }
        task.incrementUseCount();
        if (!cacheControl.noStore()) {
            task.storeToCache(true);
        }
        ResultFuture<V> resultFuture = resultFactory.createResultFuture(params, staleResult, priorityOrdinal, task);
        if (forceExecute) {
            executor.execute(task);
        }
        return resultFuture;
    }

    private final ResultFutureFactory<V> deferredFetchingFutureFactory = new ResultFutureFactory<V>() {
        @Override
        ResultFuture<V> createResultFuture(RequestParams params, @Nullable V staleResult, long priority, FetcherTask task) {
            DeferredFetchingFuture resultFuture = new DeferredFetchingFuture(task, priority, mayInterruptTask);
            if (staleResult != null) {
                resultFuture.setStaleResult(staleResult, params);
            }
            task.addListener(resultFuture);
            return resultFuture;
        }
    };

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    protected void fireTaskQueueExecutor() {
        FetcherTask task = null;
        synchronized (lock) {
            if (runningTasks.size() < RUNNING_TASKS_LIMIT) {
                if (Build.VERSION.SDK_INT >= 9) {
                    for (Entry<QueueKey<K>, FetcherTask> entry = tasksQueue.pollFirstEntry(); entry != null; entry = tasksQueue.pollFirstEntry()) {
                        task = entry.getValue();
                        if (task.isDone()) {
                            continue;
                        }
                        QueueKey<K> next = entry.getKey();
                        runningTasks.put(next.key, task);
                        break;
                    }
                } else {
                    for (int size = tasksQueue.size(); size > 0; size--) {
                        QueueKey<K> next = tasksQueue.firstKey();
                        task = tasksQueue.remove(next);
                        if (task.isDone()) {
                            continue;
                        }
                        runningTasks.put(next.key, task);
                        break;
                    }
                }
            }
        }
        if (task != null) {
            executor.execute(task);
        }
    }

    protected abstract V fetchValue(K key) throws Exception;

    @GuardedBy("lock")
    protected void putResultToCache(final K key, final Result<V> result) {
        if (result.isSuccess()) {
            final V value = result.getValue();
            if (quickCache != null) {
                quickCache.put(key, value);
            }
            final Cache<K, V> secondaryCache = this.secondaryCache;
            if (secondaryCache != null) {
                AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            secondaryCache.put(key, value);
                        } catch (RuntimeException ex) {
                            Log.e(TAG, "Error putting value into secondary cache", ex);
                        }
                    }
                });
            }
        }
    }

    private class FetcherTask extends FutureTask<Result<V>> {
        private final K key;
        @GuardedBy("lock")
        private final List<FetcherListenerWithPriority> listeners = new ArrayList<>(4);
        @GuardedBy("lock")
        private int useCount;
        @GuardedBy("lock")
        private boolean storeToCache;

        public FetcherTask(final K key) {
            super(new Callable<Result<V>>() {
                @Override
                public Result<V> call() throws Exception {
                    Result<V> result;
                    try {
                        result = Result.success(fetchValue(key));
                    } catch (Exception ex) {
                        result = Result.failure(ex);
                    } finally {
                        synchronized (lock) {
                            runningTasks.remove(key);
                        }
                    }
                    return result;
                }
            });
            this.key = key;
        }

        @Override
        protected void done() {
            Result<V> result;
            if (isCancelled()) {
                result = Result.failure(new CancellationException());
            } else {
                try {
                    result = get();
                } catch (Exception ex) {
                    result = Result.failure(ex);
                }
            }
            fireOnReady(result);
            fireTaskQueueExecutor();
        }

        @SuppressWarnings("unchecked")
        private void fireOnReady(Result<V> result) {
            FetcherListener<K, V>[] listenersSnapshot;
            List<FetcherListener<K, V>> globalListeners = QueueFetcher.this.listeners;
            synchronized (lock) {
                if (storeToCache) {
                    putResultToCache(key, result);
                }
                synchronized (globalListeners) {
                    int listenersCount = listeners.size();
                    listenersSnapshot = listeners.toArray(new FetcherListener[listenersCount + globalListeners.size()]);
                    for (FetcherListener<K, V> l : globalListeners) {
                        listenersSnapshot[listenersCount++] = l;
                    }
                }
            }
            K key = this.key;
            for (FetcherListener<K, V> l : listenersSnapshot) {
                l.onResult(key, result);
            }
        }

        @GuardedBy("lock")
        protected void addListener(FetcherListenerWithPriority listener) {
            listeners.add(listener);
        }

        @GuardedBy("lock")
        protected void removeListener(FetcherListenerWithPriority listener) {
            listeners.remove(listener);
        }

        @GuardedBy("lock")
        protected List<FetcherListenerWithPriority> getListeners() {
            return listeners;
        }

        @GuardedBy("lock")
        protected void incrementUseCount() {
            useCount++;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (lock) {
                if (--useCount == 0) {
                    runningTasks.remove(key);
                    return super.cancel(mayInterruptIfRunning);
                }
            }
            return false;
        }

        public K getKey() {
            return key;
        }

        @GuardedBy("lock")
        public void storeToCache(boolean store) {
            this.storeToCache = store;
        }
    }

    private long findMaxPriority(List<FetcherListenerWithPriority> futures) {
        if (futures == null || futures.isEmpty()) {
            return 0;
        }
        long result = 0;
        for (FetcherListenerWithPriority future : futures) {
            long priority = future.getPriority();
            if (priority > result) {
                result = priority;
            }
        }
        return result;
    }

    public void addListener(FetcherListener<K, V> l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeListener(FetcherListener<K, V> l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    private class DeferredFetchingFuture extends AbstractResultFuture<V> implements FetcherListenerWithPriority<K, V> {
        private volatile FetcherTask task;
        private final long priority;
        private final boolean mayInterruptTask;
        @GuardedBy("this")
        private V staleResult;
        @GuardedBy("this")
        private boolean allowInterim;

        public DeferredFetchingFuture(FetcherTask task, long priority, boolean mayInterruptTask) {
            super(QueueFetcher.this.defaultResultExecutor);
            this.task = task;
            this.priority = priority;
            this.mayInterruptTask = mayInterruptTask;
        }

        @Override
        protected Result<V> fetchResult() throws ExecutionException, InterruptedException {
            FetcherTask task = this.task;
            if (task != null) {
                return processResult(task.get(), staleResult);
            }
            throw new IllegalStateException("Should never happen");
        }

        @Override
        protected Result<V> fetchResult(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
            FetcherTask task = this.task;
            if (task != null) {
                return processResult(task.get(timeout, unit), staleResult);
            }
            throw new IllegalStateException("Should never happen");
        }

        private Result<V> processResult(Result<V> result, V defaultValue) {
            if (result.getValue() == null && defaultValue != null) {
                return Result.success(defaultValue);
            }
            return result;
        }

        @Override
        public boolean isResultReady() {
            FetcherTask task = this.task;
            return !isCancelled() && (task == null || (task.isDone() && !task.isCancelled()));
        }

        @Override
        protected boolean onCancel() {
            FetcherTask task = this.task;
            if (task != null) {
                synchronized (lock) {
                    task.removeListener(this);
                    QueueKey key = new QueueKey(task.getKey());
                    if (tasksQueue.remove(key) != null) {
                        List<FetcherListenerWithPriority> listeners = task.getListeners();
                        if (listeners != null && listeners.size() > 0) {
                            long maxPriority = findMaxPriority(listeners);
                            key.setPriority(maxPriority);
                            tasksQueue.put(key, task);
                        }
                    }
                    return task.cancel(mayInterruptTask);
                }
            }
            return false;
        }

        @Override
        public synchronized void onResult(K key, Result<V> result) {
            if (task == null) {
                return;
            }
            if (allowInterim || staleResult == null || result.isSuccess()) {
                super.fireResult(result);
            } else {
                super.fireResult(Result.success(staleResult));
            }
            this.task = null; // make eligible for gc
        }

        @Override
        protected void onCompleted(boolean cancelled) {
            this.task = null; // make eligible for gc
        }

        public long getPriority() {
            return priority;
        }

        public synchronized void setStaleResult(V staleResult, RequestParams params) {
            this.allowInterim = params.allowInterim();
            if (allowInterim) {
                fireResult(Result.intermediate(staleResult));
            }
            this.staleResult = staleResult;
        }

        @Override
        Result<V> getResult() {
            Result<V> result = super.getResult();
            if (allowInterim || staleResult == null || (result != null && result.isSuccess())) {
                return result;
            }
            return Result.success(staleResult);
        }

        @Override
        protected synchronized ResultFuture<V> registerOnResult(ResultListener<V> listener, Executor resultExecutor) {
            Result<V> result = super.peekResult();
            if (allowInterim) {
                if (staleResult != null && (!isResultReady() || result.isNotSuccess())) {
                    executeOnResult(listener, Result.intermediate(staleResult), resultExecutor);
                }
            }
            return super.registerOnResult(listener, resultExecutor);
        }
    }


    private class LatchFetchingFuture extends LatchResultFuture<V> implements FetcherListenerWithPriority<K, V> {
        private volatile long priority;
        @GuardedBy("this")
        private boolean allowInterim;
        @GuardedBy("this")
        private V staleResult;
        private FetcherTask task;

        public LatchFetchingFuture() {
            super(QueueFetcher.this.defaultResultExecutor);
        }

        public void setTask(FetcherTask task, long priority) {
            this.task = task;
            this.priority = priority;
        }

        @Override
        public long getPriority() {
            return priority;
        }

        @Override
        protected void onCompleted(boolean cancelled) {
            super.onCompleted(cancelled);
            FetcherTask task = this.task;
            if (task != null) {
                task.cancel(mayInterruptTask);
            }
        }

        @Override
        public synchronized void onResult(K key, Result<V> result) {
            if (allowInterim || staleResult == null || result.isSuccess()) {
                super.fireResult(result);
            } else {
                super.fireResult(Result.success(staleResult));
            }
        }

        public synchronized void setStaleResult(V staleResult, RequestParams params) {
            this.allowInterim = params.allowInterim();
            this.staleResult = staleResult;
        }

        @Override
        Result<V> getResult() {
            Result<V> result = super.getResult();
            if (allowInterim || staleResult == null || (result != null && result.isSuccess())) {
                return result;
            }
            return Result.success(staleResult);
        }

        @Override
        protected synchronized ResultFuture<V> registerOnResult(ResultListener<V> listener, Executor resultExecutor) {
            Result<V> result = super.peekResult();
            if (allowInterim) {
                if (staleResult != null && (!isResultReady() || result.isNotSuccess())) {
                    executeOnResult(listener, Result.intermediate(staleResult), resultExecutor);
                }
            }
            return super.registerOnResult(listener, resultExecutor);
        }
    }

    private class SecondaryCacheRunnable implements Runnable {
        private final K key;
        private final RequestParams params;
        private final LatchFetchingFuture resultFuture;

        public SecondaryCacheRunnable(K key, RequestParams params, LatchFetchingFuture resultFuture) {
            this.key = key;
            this.params = params;
            this.resultFuture = resultFuture;
        }

        @Override
        public void run() {
            if (resultFuture.isCancelled()) {
                return;
            }
            Cache<K, V> secondaryCache = QueueFetcher.this.secondaryCache;
            Cache<K, V> quickCache = QueueFetcher.this.quickCache;
            CacheControl cacheControl = params.cacheControl();
            V staleResult = null;
            try {
                if (secondaryCache.isCacheControlSupported() && cacheControl.isTimeLimited()) {
                    Cache.Entry<V> cacheEntry = secondaryCache.getEntry(key);
                    if (cacheEntry != null) {
                        if (quickCache != null) {
                            quickCache.put(key, cacheEntry);
                        }
                        switch (cacheEntry.check(cacheControl)) {
                            case GOOD:
                                resultFuture.setSuccess(cacheEntry.getValue());
                                return;
                            case STALE:
                                staleResult = cacheEntry.getValue();
                                break;
                        }
                    }
                } else {
                    V value = secondaryCache.get(key);
                    if (value != null) {
                        if (quickCache != null) {
                            quickCache.put(key, value);
                        }
                        resultFuture.setSuccess(value);
                        return;
                    }
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Error getting value from secondary cache", ex);
            }
            if (cacheControl.cacheOnly()) {
                if (staleResult != null) {
                    resultFuture.setSuccess(staleResult);
                } else {
                    resultFuture.setFailure(new NotFoundException());
                }
                return;
            }
            if (staleResult != null && params.allowInterim()) {
                resultFuture.setIntermediate(staleResult);
            }
            if (resultFuture.isCancelled()) {
                return;
            }
            synchronized (lock) {
                getTaskResultFuture(key, params, new ResultFutureFactory<V>() {
                    @Override
                    ResultFuture<V> createResultFuture(RequestParams params, @Nullable V staleResult, long priority, FetcherTask task) {
                        resultFuture.setTask(task, priority);
                        if (staleResult != null) {
                            resultFuture.setStaleResult(staleResult, params);
                        }
                        task.addListener(resultFuture);
                        return resultFuture;
                    }
                }, staleResult);
            }
        }
    }
}
