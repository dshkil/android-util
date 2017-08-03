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
package com.shkil.android.util;

import android.app.Service;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.shkil.android.util.common.BuildConfig;
import com.shkil.android.util.concurrent.MainThread;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.concurrent.ResultFutures;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

public abstract class ServiceAccessor<T> implements Releasable {

    private static final String TAG = "ServiceAccessor";

    public static boolean DEBUG = true;

    private volatile ServiceConnector<T> serviceConnector;

    protected ServiceAccessor(ServiceConnector<T> serviceConnector) {
        if (serviceConnector == null) {
            throw new IllegalArgumentException("serviceConnector == null");
        }
        this.serviceConnector = serviceConnector;
    }

    public ServiceAccessor<T> bindService() {
        getServiceConnector().bindService(Service.BIND_AUTO_CREATE);
        return this;
    }

    @NonNull
    public T getService(boolean waitConnection) throws RemoteException {
        return getServiceConnector().getService(waitConnection);
    }

    @Nullable
    public T getServiceIfReady() {
        return getServiceConnector().getServiceIfReady();
    }

    /**
     * Returns service interface if service connection is ready or can be bound (means: when invoked on a worker thread).
     * Returns <code>null</code> if service connection is not ready and invoked on the main thread.
     */
    @Nullable
    public T getServiceIfPossible() {
        try {
            return getService(!Utils.isRunningOnMainThread());
        } catch (RemoteException ex) {
            Log.w(TAG, ex);
            return null;
        }
    }

    @NonNull
    public T getServiceOrThrowRuntime() {
        T service = getServiceIfPossible();
        if (service != null) {
            return service;
        }
        try {
            return getServiceConnector().getService(true);
        } catch (RemoteException ex) {
            throw new RuntimeException(ex);
        }
    }

    public interface OnServiceReadyCallback<T> {
        void onServiceReady(T service);
    }

    public void getServiceAsync(final OnServiceReadyCallback<T> callback) {
        getServiceAsync(callback, MainThread.EXECUTOR);
    }

    public void getServiceAsync(final OnServiceReadyCallback<T> callback, final Executor callbackExecutor) {
        final T service = getServiceIfPossible();
        if (service != null) {
            if (callbackExecutor == null) {
                callback.onServiceReady(service);
            } else {
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onServiceReady(service);
                    }
                });
            }
        } else {
            THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    final T service;
                    try {
                        service = getService(true);
                    } catch (RemoteException ex) {
                        throw new RuntimeException(ex);
                    }
                    if (callbackExecutor == null) {
                        callback.onServiceReady(service);
                    } else {
                        callbackExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onServiceReady(service);
                            }
                        });
                    }
                }
            });
        }
    }

    public ResultFuture<T> getServiceAsync() {
        T service = getServiceIfPossible();
        if (service != null) {
            return ResultFutures.success(service);
        }
        return ResultFutures.executeTask(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return getService(true);
            }
        }, THREAD_POOL_EXECUTOR).getResultFuture(MainThread.EXECUTOR, false);
    }

    public boolean isServiceReady() {
        return getServiceConnector().isServiceReady();
    }

    public synchronized final void release() {
        if (serviceConnector != null) {
            onRelease(serviceConnector);
            serviceConnector = null;
        }
    }

    protected abstract void onRelease(ServiceConnector<T> serviceConnector);

    private synchronized ServiceConnector<T> getServiceConnector() {
        if (serviceConnector == null) {
            throw new IllegalStateException("Service connection was released");
        }
        return serviceConnector;
    }

    @Override
    protected synchronized final void finalize() throws Throwable { //works even better than PhantomReference
        super.finalize();
        if (serviceConnector != null) {
            if (DEBUG) {
                Log.i(TAG, "Service accessor should be released by user");
            }
        }
        try {
            release();
        } catch (RuntimeException ex) {
            Log.w(TAG, ex);
        }
    }

}
