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
import android.os.IInterface;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.shkil.android.util.common.BuildConfig;

public abstract class ServiceAccessor<T> implements Releasable {

    private static final String TAG = "ServiceAccessor";

    public static boolean DEBUG = BuildConfig.DEBUG;

    private volatile ServiceConnector<T> serviceConnector;

    protected ServiceAccessor(ServiceConnector<T> serviceConnector) {
        if (serviceConnector == null) {
            throw new IllegalArgumentException("serviceConnector == null");
        }
        this.serviceConnector = serviceConnector;
    }

    public void bindToService() {
        checkConnectorAvailable();
        serviceConnector.bindService(Service.BIND_AUTO_CREATE);
    }

    @NonNull
    public synchronized T getService(boolean waitConnection) throws RemoteException {
        checkConnectorAvailable();
        return serviceConnector.getService(waitConnection);
    }

    @Nullable
    public synchronized T getServiceIfReady() {
        checkConnectorAvailable();
        return serviceConnector.getServiceIfReady();
    }

    /**
     * Returns service interface if service connection is ready or can be bound (means: when invoked on a worker thread).
     * Returns <code>null</code> if service connection is not ready and invoked on the main thread.
     */
    @Nullable
    public synchronized T getServiceIfPossible() {
        try {
            return getService(!Utils.isRunningOnMainThread());
        } catch (RemoteException ex) {
            Log.w(TAG, ex);
            return null;
        }
    }

    public synchronized boolean isServiceReady() {
        checkConnectorAvailable();
        return serviceConnector.isServiceReady();
    }

    public synchronized final void release() {
        if (serviceConnector != null) {
            onRelease(serviceConnector);
            serviceConnector = null;
        }
    }

    protected abstract void onRelease(ServiceConnector<T> serviceConnector);

    private void checkConnectorAvailable() {
        if (serviceConnector == null) {
            throw new IllegalStateException("Service connection was released");
        }
    }

    @Override
    protected synchronized final void finalize() throws Throwable { //works even better than PhantomReference
        super.finalize();
        if (serviceConnector != null) {
            if (DEBUG) {
                Log.w(TAG, "Service accessor should be released by user");
            }
        }
        try {
            release();
        } catch (RuntimeException ex) {
            Log.w(TAG, ex);
        }
    }

}
