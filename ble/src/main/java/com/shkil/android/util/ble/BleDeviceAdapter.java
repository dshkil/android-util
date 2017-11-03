package com.shkil.android.util.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.shkil.android.util.ServiceAccessor;
import com.shkil.android.util.ServiceConnector;
import com.shkil.android.util.ble.enums.ConnectionState;
import com.shkil.android.util.concurrent.MainThread;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.concurrent.ResultFutures;
import com.shkil.android.util.logging.Logger;
import com.shkil.android.util.logging.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static android.bluetooth.BluetoothAdapter.checkBluetoothAddress;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
import static android.content.Context.BLUETOOTH_SERVICE;
import static com.shkil.android.util.Utils.newThreadFactory;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * This adapter is an appropriate method to communicate with a BLE device from UI, etc. It wraps communication with
 * underlying BleService/BleDevice.
 */
public class BleDeviceAdapter<S extends IBleService> {

    private static final Logger log = LoggerFactory.getLogger(BleDeviceAdapter.class);

    protected final BluetoothAdapter bluetoothAdapter;
    protected final String address;
    private final ServiceAccessor<S> serviceAccessor;
    private String deviceName;

    private static final Executor ASYNC_EXECUTOR = newCachedThreadPool(newThreadFactory("ble-adapter-{0}"));

    public BleDeviceAdapter(Context context, String deviceAddress, ServiceConnector<S> bleServiceConnector) {
        this(context, deviceAddress, bleServiceConnector.newServiceAccessor());
    }

    public BleDeviceAdapter(Context context, String deviceAddress, ServiceAccessor<S> bleServiceAccessor) {
        if (!checkBluetoothAddress(deviceAddress)) {
            throw new IllegalArgumentException("deviceAddress");
        }
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.address = deviceAddress;
        this.serviceAccessor = bleServiceAccessor;
    }

    public String getDeviceAddress() {
        return address;
    }

    @Nullable
    public String getDeviceName() {
        if (deviceName == null) {
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            if (bluetoothDevice != null) {
                deviceName = bluetoothDevice.getName();
            }
        }
        return deviceName;
    }

    @Override
    public String toString() {
        return "BleDeviceAdapter{" +
                "address=" + address +
                ", deviceName=" + getDeviceName() +
                '}';
    }

    @NonNull
    protected S getService() {
        try {
            return serviceAccessor.getService(true);
        } catch (RemoteException ex) {
            throw new RuntimeException(ex);
        }
    }

    @NonNull
    public ConnectionState getConnectionState() {
        IBleService service = serviceAccessor.getServiceIfPossible();
        if (service != null) {
            return service.getConnectionState(address);
        }
        return ConnectionState.SERVICE_NOT_READY;
    }

    public int getPendingOperationCount() {
        IBleService service = serviceAccessor.getServiceIfPossible();
        if (service != null) {
            return service.getPendingOperationCount(address);
        }
        return -1;
    }

    public ResultFuture<ConnectionState> getConnectionStateAsync() {
        return executeAsync(new Callable<ConnectionState>() {
            @Override
            public ConnectionState call() throws Exception {
                return getService().getConnectionState(address);
            }
        });
    }

    public ResultFuture<ConnectionState> connect(final boolean awaitConnected) {
        return executeAsync(new Callable<ConnectionState>() {
            @Override
            public ConnectionState call() throws Exception {
                return getService().connect(address, awaitConnected);
            }
        });
    }

    public void disconnect() {
        executeAsync(new Runnable() {
            @Override
            public void run() {
                getService().disconnect(address);
            }
        });
    }

    public ResultFuture<Integer> getRemoteRssi() {
        return executeAsync(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return getService().getRemoteRssi(address);
            }
        });
    }

    public ResultFuture<String> getFirmwareRevision() {
        return executeAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return getService().getFirmwareRevision(address);
            }
        });
    }

    public ResultFuture<String> getSoftwareRevision() {
        return executeAsync(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return getService().getSoftwareRevision(address);
            }
        });
    }

    protected final <T> ResultFuture<T> executeAsync(Callable<T> task) {
        return ResultFutures.executeTask(task, ASYNC_EXECUTOR)
                .getResultFuture(MainThread.EXECUTOR, true);
    }

    protected final void executeAsync(Runnable task) {
        ASYNC_EXECUTOR.execute(task);
    }

    public ConnectionLock newConnectionLock(String tag) {
        return new ConnectionLock(tag, CONNECTION_PRIORITY_BALANCED);
    }

    /**
     * @param connectionPriority See {@link BluetoothGatt#requestConnectionPriority(int)}
     */
    public ConnectionLock newConnectionLock(String tag, int connectionPriority) {
        return new ConnectionLock(tag, connectionPriority);
    }

    public final class ConnectionLock {
        private final String tag;
        private final int connectionPriority;
        private int useCount;
        private int lockInstanceId;

        ConnectionLock(String tag, int connectionPriority) {
            this.tag = tag;
            this.connectionPriority = connectionPriority;
        }

        public synchronized ConnectionLock acquire() {
            if (useCount++ == 0) {
                IBleService service = serviceAccessor.getServiceIfPossible();
                if (service != null) {
                    this.lockInstanceId = service.acquireConnectionLock(address, tag, connectionPriority);
                } else {
                    executeAsync(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ConnectionLock.this.lockInstanceId = getService().acquireConnectionLock(address, tag, connectionPriority);
                                }
                            }
                    );
                }
            } else {
                log.trace("Connection lock '{}' re-acquired. Count = {}.", tag, useCount);
            }
            return this;
        }

        public void release() {
            release(false);
        }

        private synchronized void release(boolean finalizing) {
            if (finalizing || --useCount == 0) {
                IBleService service = serviceAccessor.getServiceIfPossible();
                if (service != null) {
                    service.releaseConnectionLock(address, lockInstanceId);
                } else {
                    log.error("Can't release device connection: service unavailable");
                }
            } else if (useCount < 0) {
                throw new RuntimeException("ConnectionLock under-locked: " + tag);
            } else {
                log.trace("Connection lock '{}' release requested. Count = {}.", tag, useCount);
            }
        }

        @Override
        protected synchronized void finalize() throws Throwable {
            super.finalize();
            if (useCount > 0) {
                log.warn("Connection lock '{}' was not released by user: count = {}", tag, useCount);
                try {
                    release(true);
                } catch (Exception ex) {
                    log.error(ex);
                }
            }
        }
    }
}
