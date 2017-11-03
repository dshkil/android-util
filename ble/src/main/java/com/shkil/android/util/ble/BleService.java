package com.shkil.android.util.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

import com.shkil.android.util.ServiceConnector.AbstractLocalBinder;
import com.shkil.android.util.Utils;
import com.shkil.android.util.ble.enums.ConnectionState;
import com.shkil.android.util.ble.exception.BleCallException;
import com.shkil.android.util.concurrent.MainThread;
import com.shkil.android.util.logging.Logger;
import com.shkil.android.util.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Service that hosts BLE device instances (BleDevice) and encapsulates some common low-level Bluetooth logic.
 * This service also responsible for closing inactive BLE connections.
 * BleService and BleDevice can identify device only by their Bluetooth MAC address.
 * Normally, the app components shouldn't work with this service directly, BleDeviceAdapter
 * should be used instead.
 */
public abstract class BleService<D extends BleDevice> extends Service {

    private static final Logger log = LoggerFactory.getLogger(BleService.class);

    private static final long CONNECTION_INACTIVITY_MILLIS = SECONDS.toMillis(15);

    private BluetoothAdapter bluetoothAdapter;

    @GuardedBy("self")
    private final Map<String, D> devices = new HashMap<>();

    private final Handler handler = new Handler(MainThread.LOOPER);

    @Override
    public void onCreate() {
        super.onCreate();
        log.trace("Service onCreate()");
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, intentFilter);
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return getBinder(intent);
    }

    protected abstract IBinder getBinder(Intent intent);

    @Override
    public void onDestroy() {
        super.onDestroy();
        log.trace("Service onDestroy()");
        unregisterReceiver(bluetoothBroadcastReceiver);
        handler.removeCallbacks(deviceInactivityCheckRunnable);
        synchronized (devices) {
            for (BleDevice device : devices.values()) {
                device.disconnect(true);
            }
            devices.clear();
        }
    }

    protected final D getDevice(String address) {
        return getDevice(address, true);
    }

    protected final D getDevice(String address, boolean canInstantiate) {
        synchronized (devices) {
            D device = devices.get(address);
            if (device == null && canInstantiate) {
                device = createDevice(bluetoothAdapter.getRemoteDevice(address));
                devices.put(address, device);
            }
            return device;
        }
    }

    protected abstract D createDevice(BluetoothDevice device);

    protected void onBluetoothAdapterStateChanged(int newState, int previousState) {
        if (newState == BluetoothAdapter.STATE_ON) {
            log.info("Bluetooth adapter has been enabled");
        }
        List<D> deviceList;
        synchronized (devices) {
            deviceList = new ArrayList<>(devices.values());
        }
        for (BleDevice device : deviceList) {
            device.onBluetoothAdapterStateChanged(newState, previousState);
        }
    }

    protected void onDeviceBondStateChanged(BluetoothDevice device, int newState, int previousState) {
        BleDevice bleDevice = getDevice(device.getAddress(), false);
        if (bleDevice != null) {
            bleDevice.onBondStateChanged(newState, previousState);
        }
    }

    protected class BleServiceBinder extends AbstractLocalBinder implements IBleService {
        @Override
        public ConnectionState getConnectionState(String address) {
            return getDevice(address).getConnectionState();
        }

        @Override
        public ConnectionState connect(String address, boolean awaitConnected) {
            return getDevice(address).connect(awaitConnected);
        }

        @Override
        public void disconnect(String deviceAddress) {
            getDevice(deviceAddress).disconnect();
        }

        @Override
        public int acquireConnectionLock(String address, String tag, int connectionPriority) {
            return getDevice(address, true).acquireConnectionLock(tag, connectionPriority);
        }

        @Override
        public void releaseConnectionLock(String address, int lockInstanceId) {
            getDevice(address, true).releaseConnectionLock(lockInstanceId);
        }

        @Override
        public int getPendingOperationCount(String address) {
            return getDevice(address).getPendingOperationCount();
        }

        @Override
        public int getRemoteRssi(String address) throws BleCallException {
            return getDevice(address).getRemoteRssi();
        }

        @Override
        public String getFirmwareRevision(String address) throws BleCallException {
            return getDevice(address).getFirmwareRevision();
        }

        @Override
        public String getSoftwareRevision(String address) throws BleCallException {
            return getDevice(address).getSoftwareRevision();
        }
    }

    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (log.isTraceEnabled()) {
                log.trace("Bluetooth broadcast received: {}, extras={}", action, Utils.toString(intent.getExtras()));
            }
            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    int newAdapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    int previousAdapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, 0);
                    onBluetoothAdapterStateChanged(newAdapterState, previousAdapterState);
                    break;
                }
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int newBondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);
                    int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, 0);
                    onDeviceBondStateChanged(device, newBondState, previousBondState);
                    break;
                }
            }
        }
    };

    protected void checkInactiveDeviceConnections() {
        handler.removeCallbacks(deviceInactivityCheckRunnable);
        long minInactivityUptimeMillis = 0;
        long now = SystemClock.uptimeMillis();
        int aliveConnectionCount = 0;
        List<D> devicesSnapshot;
        synchronized (devices) {
            devicesSnapshot = new ArrayList<>(devices.values());
        }
        for (BleDevice device : devicesSnapshot) {
            switch (device.getConnectionState()) {
                case CONNECTING:
                case DISCONNECTING:
                    aliveConnectionCount++;
                    continue;
                case CONNECTED:
                    // only this one will proceed with real check
                    break;
                default:
                    continue;
            }
            long inactivityUptime = device.getInactivityUptimeMillis();
            if (device.shouldKeepConnectionAlive()) {
                aliveConnectionCount++;
            } else if (now - inactivityUptime > CONNECTION_INACTIVITY_MILLIS) {
                log.info("Disconnecting {} due to inactivity", device);
                device.disconnect();
            } else {
                aliveConnectionCount++;
                if (minInactivityUptimeMillis == 0 || inactivityUptime < minInactivityUptimeMillis) {
                    minInactivityUptimeMillis = inactivityUptime;
                }
            }
        }
        if (minInactivityUptimeMillis > 0) {
            long nextCheckTimestamp = minInactivityUptimeMillis + CONNECTION_INACTIVITY_MILLIS;
            handler.postAtTime(deviceInactivityCheckRunnable, nextCheckTimestamp);
        } else if (aliveConnectionCount == 0) {
            //log.trace("There is no active bluetooth devices.");
        }
    }

    private final Runnable deviceInactivityCheckRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (devices) {
                BleService.this.checkInactiveDeviceConnections();
            }
        }
    };
}
