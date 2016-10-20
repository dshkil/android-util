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

import android.annotation.TargetApi;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.shkil.android.util.common.BuildConfig;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ServiceConnector<T> {

    private static final String TAG = "ServiceConnector";

    public static boolean DEBUG = BuildConfig.DEBUG;

    protected static final long MAX_CONNECTION_WAIT_MILLIS = SECONDS.toMillis(60);

    public interface ILocalBinder {
        <I> I getInterface();
    }

    public static abstract class AbstractLocalBinder extends Binder implements ILocalBinder {
        @Override
        public <I> I getInterface() {
            return (I) this;
        }
    }

    private final Context context;
    private final Intent serviceIntent;
    private final Class<T> interfaceClass;
    private volatile T serviceInterface;

    private final Object connectionLock = new Object();
    private final AtomicInteger accessorCount = new AtomicInteger();

    public ServiceConnector(Context context, Intent serviceIntent, Class<T> interfaceClass) {
        this.context = context.getApplicationContext();
        this.serviceIntent = serviceIntent;
        this.interfaceClass = interfaceClass;
    }

    public void bindService(int flags) {
        bindService(flags, true);
    }

    void bindService(int flags, boolean manual) {
        boolean serviceReady = isServiceReady();
        if (DEBUG) {
            Log.v(TAG, "bindService(): service=" + serviceIntent + ", flags=" + flags
                    + ", isServiceReady=" + serviceReady + ", manual=" + manual
                    + ", thread=" + Thread.currentThread().getName());
        }
        if (serviceReady) {
            return;
        }
        if (context.bindService(serviceIntent, serviceConnection, flags)) {
            Log.v(TAG, "bindService(): service binding successful");
            return;
        }
        throw new RuntimeException("Target service not found. Intent: " + serviceIntent);
    }

    public void unbindService() {
        if (DEBUG) {
            Log.v(TAG, "unbindService(): service=" + serviceIntent);
        }
        if (isServiceReady()) {
            context.unbindService(serviceConnection);
        }
    }

    public ServiceAccessor<T> newServiceAccessor() {
        ServiceAccessor<T> accessor = new ServiceAccessor<T>(this) {
            @Override
            protected void onRelease(ServiceConnector<T> serviceConnector) {
                int count = accessorCount.decrementAndGet();
                if (DEBUG) {
                    Log.v(TAG, "onRelease(): count=" + count + ", service=" + serviceIntent);
                }
                if (count == 0) {
                    unbindService();
                }
            }
        };
        int count = accessorCount.incrementAndGet();
        Log.v(TAG, "newServiceAccessor(): count=" + count + ", service=" + serviceIntent);
        return accessor;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
            T serviceInterface = asInterface(serviceBinder);
            if (serviceInterface == null) {
                throw new NullPointerException("serviceInterface == null");
            }
            ServiceConnector.this.serviceInterface = serviceInterface;
            ServiceConnector.this.onServiceConnected(name);
            synchronized (connectionLock) {
                connectionLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ServiceConnector.this.serviceInterface = null;
            ServiceConnector.this.onServiceDisconnected(name);
            synchronized (connectionLock) {
                connectionLock.notifyAll();
            }
        }
    };

    @SuppressWarnings("unchecked")
    protected T asInterface(IBinder binder) {
        if (binder instanceof ILocalBinder) {
            return ((ILocalBinder) binder).getInterface();
        }
        try {
            Class<T> stubClass = (Class<T>) Class.forName(interfaceClass.getName() + "$Stub");
            Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);
            return (T) asInterfaceMethod.invoke(null, binder);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void onServiceConnected(ComponentName name) {
        if (DEBUG) {
            Log.v(TAG, "Connected to service " + name != null ? name.toShortString() : null);
        }
    }

    protected void onServiceDisconnected(ComponentName name) {
        if (DEBUG) {
            Log.v(TAG, "Disconnected from service " + name != null ? name.toShortString() : null);
        }
    }

    public boolean isServiceReady() {
        return serviceInterface != null;
    }

    @Nullable
    @TargetApi(ICE_CREAM_SANDWICH_MR1)
    public T getService(boolean waitConnection) throws RemoteException {
        T connectedServiceInterface = this.serviceInterface;
        if (connectedServiceInterface != null) {
            return connectedServiceInterface;
        }
        if (!waitConnection) {
            return null;
        }
        if (Utils.isRunningOnMainThread()) {
            throw new IllegalStateException("getService() with waitConnection == true called on main thread.");
        }
        bindService(Service.BIND_AUTO_CREATE, false);
        synchronized (connectionLock) {
            if (serviceInterface == null) {
                try {
                    connectionLock.wait(MAX_CONNECTION_WAIT_MILLIS);
                } catch (InterruptedException ex) {
                    Log.e(TAG, "Can't bind to service", ex);
                }
            }
        }
        T result = this.serviceInterface;
        if (result == null) {
            if (Build.VERSION.SDK_INT >= ICE_CREAM_SANDWICH_MR1) {
                throw new RemoteException("Can't get service interface: timed out");
            } else {
                throw new RemoteException();
            }
        }
        return result;
    }

    @Nullable
    public T getServiceIfReady() {
        try {
            return getService(false);
        } catch (RemoteException ex) {
            throw new RuntimeException(ex); // should never happen
        }
    }

}
