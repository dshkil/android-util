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

import android.annotation.TargetApi;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;


import com.shkil.android.util.Result;
import com.shkil.android.util.ResultListener;

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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

public abstract class QueueFetcher<K, V> implements Fetcher<K, V> {

    private static final String TAG = QueueFetcher.class.getSimpleName();

    public static final int RUNNING_TASKS_LIMIT = 1;

    private final Executor resultExecutor;
    private final boolean mayInterruptTask;

    private final Object lock = new Object();
    //@GuardedBy("lock")
    private final TreeMap<QueueKey<K>, FetcherTask> tasksQueue = new TreeMap<QueueKey<K>, FetcherTask>();
    //@GuardedBy("lock")
    private final Map<K, FetcherTask> runningTasks = new HashMap<K, FetcherTask>();
    //@GuardedBy("itself")
    private final List<FetcherListener<K, V>> listeners = new ArrayList<FetcherListener<K, V>>(5);

    private final Executor executor;

    private Priority defaultPriority = Priority.NORMAL;

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

    public QueueFetcher(Executor executor, /*@Nullable*/ Executor resultExecutor, boolean mayInterruptTask) {
        this.executor = executor;
        this.resultExecutor = resultExecutor;
        this.mayInterruptTask = mayInterruptTask;
    }

    public Priority getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(Priority defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    @Override
    public ResultFuture<V> fetch(K key) {
        return fetch(key, defaultPriority);
    }

    @Override
    public ResultFuture<V> fetch(K key, /*@Nullable*/ Priority priority) {
        if (priority == null) {
            priority = defaultPriority;
        }
        FetcherTask task;
        ResultFuture<V> future;
        boolean forceExecute = (priority == Priority.IMMEDIATE);
        synchronized (lock) {
            long priorityOrdinal = priority.toLong(SystemClock.uptimeMillis());
            task = runningTasks.get(key);
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
            DeferredFetchingFuture deferredFuture = new DeferredFetchingFuture(task, priorityOrdinal, mayInterruptTask);
            task.addListener(deferredFuture);
            future = deferredFuture;
        }
        if (forceExecute) {
            executor.execute(task);
        }
        return future;
    }

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

    private class FetcherTask extends FutureTask<Result<V>> {
        private final K key;
        private final List<DeferredFetchingFuture> listeners = new ArrayList<DeferredFetchingFuture>(4);
        private int useCount;

        public FetcherTask(final K key) {
            super(new Callable<Result<V>>() {
                @Override
                public Result<V> call() throws Exception {
                    Result<V> result = null;
                    try {
                        result = Result.success(fetchValue(key));
                    } catch (Exception ex) {
                        result = Result.<V>failure(ex);
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
                    result = Result.<V>failure(ex);
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
                try {
                    l.onResult(key, result);
                } catch (RuntimeException ex) {
                    Log.w(TAG, ex);
                }
            }
        }

        //@GuardedBy("lock")
        protected void addListener(DeferredFetchingFuture listener) {
            listeners.add(listener);
        }

        //@GuardedBy("lock")
        protected void removeListener(DeferredFetchingFuture listener) {
            listeners.remove(listener);
        }

        //@GuardedBy("lock")
        protected List<DeferredFetchingFuture> getListeners() {
            return listeners;
        }

        //@GuardedBy("lock")
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
    }

    private class DeferredFetchingFuture implements ResultFuture<V>, FetcherListener<K, V> {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile FetcherTask task;
        private volatile Result<V> result;
        private ResultListener<V> listener;
        private volatile Executor resultExecutor;
        private final long priority;
        private final boolean mayInterruptTask;

        public DeferredFetchingFuture(FetcherTask task, long priority, boolean mayInterruptTask) {
            this.task = task;
            this.priority = priority;
            this.mayInterruptTask = mayInterruptTask;
            this.resultExecutor = QueueFetcher.this.resultExecutor;
        }

        @Override
        public Result<V> await() {
            FetcherTask task = this.task;
            if (task != null) {
                try {
                    return result = task.get();
                }
                catch (ExecutionException ex) {
                    return Result.failure(ex);
                }
                catch (InterruptedException ex) {
                    this.cancel();
                    return Result.interrupted(ex);
                }
            }
            return result;
        }

        @Override
        public Result<V> await(long timeout, TimeUnit unit) throws TimeoutException {
            FetcherTask task = this.task;
            if (task != null) {
                try {
                    return result = task.get(timeout, unit);
                }
                catch (ExecutionException ex) {
                    return Result.failure(ex);
                }
                catch (InterruptedException ex) {
                    this.cancel();
                    return Result.interrupted(ex);
                }
            }
            return result;
        }

        @Override
        public boolean isResultReady() {
            FetcherTask task = this.task;
            return !isCancelled() && (task == null || (task.isDone() && !task.isCancelled()));
        }

        @Override
        public boolean cancel() {
            if (cancelled.getAndSet(true)) {
                return true;
            }
            FetcherTask task = this.task;
            if (task != null) {
                synchronized (lock) {
                    task.removeListener(this);
                    QueueKey key = new QueueKey(task.getKey());
                    if (tasksQueue.remove(key) != null) {
                        List<DeferredFetchingFuture> listeners = task.getListeners();
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
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void onResult(K key, Result<V> result) {
            this.result = result;
            synchronized (this) {
                if (listener != null) {
                    if (resultExecutor != null) {
                        resultExecutor.execute(new OnResultRunnable<V>(listener, result));
                    } else {
                        listener.onResult(result);
                    }
                }
            }
            this.task = null; // make eligible for gc
        }

        @Nullable
        @Override
        public Result<V> peekResult() {
            return result;
        }

        @Override
        public ResultFuture<V> setResultListener(ResultListener<V> listener) {
            synchronized (this) {
                if (this.listener != null) {
                    throw new IllegalStateException("Listener was already set");
                }
                if (isCancelled()) {
                    return this;
                }
                if (result != null) {
                    if (resultExecutor != null) {
                        resultExecutor.execute(new OnResultRunnable<V>(listener, result));
                    } else {
                        listener.onResult(result);
                    }
                } else {
                    this.listener = listener;
                }
            }
            return this;
        }

        @Override
        public ResultFuture<V> setResultListener(ResultListener<V> listener, Executor resultExecutor) {
            synchronized (this) {
                this.resultExecutor = resultExecutor;
                return setResultListener(listener);
            }
        }
/*
        @Override
        public ResultFuture<V> setResultListener(ResultListener<V> listener, long time, TimeUnit units) {
            throw new RuntimeException("Not implemented yet"); //TODO
        }

        @Override
        public ResultFuture<V> setResultListener(ResultListener<V> listener, Executor resultExecutor, long timeout, TimeUnit units) {
            synchronized (this) {
                this.resultExecutor = resultExecutor;
                return setResultListener(listener, timeout, units);
            }
        }
*/
        public long getPriority() {
            return priority;
        }
    }

    private static class OnResultRunnable<V> implements Runnable {
        private final ResultListener<V> listener;
        private final Result<V> result;

        public OnResultRunnable(ResultListener<V> listener, Result<V> result) {
            this.listener = listener;
            this.result = result;
        }

        @Override
        public void run() {
            listener.onResult(result);
        }
    }

    private long findMaxPriority(List<DeferredFetchingFuture> futures) {
        if (futures == null || futures.isEmpty()) {
            return 0;
        }
        long result = 0;
        for (DeferredFetchingFuture future : futures) {
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

}
