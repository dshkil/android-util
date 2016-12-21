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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ResultFutureTask<V> extends FutureTask<Result<V>> {

    private final Object lock = new Object();
    private final List<TaskResultFuture<V>> listeners = new ArrayList<TaskResultFuture<V>>(4);
    private volatile Result<V> result;

    public static <V> ResultFutureTask<V> create(Callable<V> task) {
        return new ResultFutureTask<V>(task);
    }

    public static <V> ResultFutureTask<V> execute(Callable<V> task, Executor executor) {
        return new ResultFutureTask<V>(task).execute(executor);
    }

    protected ResultFutureTask(final Callable<V> task) {
        super(new Callable<Result<V>>() {
            @Override
            public Result<V> call() {
                try {
                    return Result.success(task.call());
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }
        });
    }

    @Override
    protected void done() {
        synchronized (lock) {
            if (isCancelled()) {
                result = Result.interrupted(new CancellationException());
            } else {
                try {
                    result = get();
                } catch (Exception ex) {
                    result = Result.failure(ex);
                }
            }
        }
        fireOnReady(result);
    }

    protected void fireOnReady(Result<V> result) {
        for (TaskResultFuture<V> listener : getListeners()) {
            listener.fireResult(result);
        }
    }

    protected final Object getLock() {
        return lock;
    }

    public ResultFuture<V> getResultFuture(Executor resultExecutor, boolean mayInterruptTask) {
        synchronized (lock) {
            if (result != null) {
                return ResultFutures.result(result, resultExecutor);
            }
            TaskResultFuture resultFuture = new TaskResultFuture(this,
                    resultExecutor, mayInterruptTask);
            addListener(resultFuture);
            return resultFuture;
        }
    }

    protected void addListener(TaskResultFuture<V> listener) {
        synchronized (lock) {
            listeners.add(listener);
        }
    }

    protected void removeListener(TaskResultFuture<V> listener) {
        synchronized (lock) {
            listeners.remove(listener);
        }
    }

    protected TaskResultFuture[] getListeners() {
        synchronized (lock) {
            return listeners.toArray(new TaskResultFuture[listeners.size()]);
        }
    }

    public ResultFutureTask<V> execute(Executor executor) {
        executor.execute(this);
        return this;
    }

    private static class TaskResultFuture<V> extends AbstractResultFuture<V> {
        private volatile ResultFutureTask<V> task;
        private final boolean mayInterruptTask;

        public TaskResultFuture(ResultFutureTask<V> task, Executor defaultResultExecutor,
                boolean mayInterruptTask) {
            super(defaultResultExecutor);
            this.task = task;
            this.mayInterruptTask = mayInterruptTask;
        }

        @Override
        public boolean isResultReady() {
            ResultFutureTask<V> task = this.task;
            return !isCancelled() && (task == null || (task.isDone() && !task.isCancelled()));
        }

        @Override
        protected Result<V> fetchResult() throws ExecutionException, InterruptedException {
            ResultFutureTask<V> task = this.task;
            if (task != null) {
                return task.get();
            }
            throw new IllegalStateException("Should never happen");
        }

        @Override
        protected Result<V> fetchResult(long time, TimeUnit units) throws TimeoutException,
                ExecutionException, InterruptedException {
            ResultFutureTask<V> task = this.task;
            if (task != null) {
                return task.get(time, units);
            }
            throw new IllegalStateException("Should never happen");
        }

        @Override
        protected boolean onCancel() {
            ResultFutureTask<V> task = this.task;
            if (task != null) {
                synchronized (task.getLock()) {
                    task.removeListener(this);
                    return task.cancel(mayInterruptTask);
                }
            }
            return false;
        }

        @Override
        protected void onDone(boolean cancelled) {
            this.task = null; // make eligible for gc
        }
    }
}
