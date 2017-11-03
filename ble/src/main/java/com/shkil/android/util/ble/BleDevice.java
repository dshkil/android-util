package com.shkil.android.util.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.SparseArray;

import com.shkil.android.util.Utils;
import com.shkil.android.util.ble.BleProfile.DeviceInformationService;
import com.shkil.android.util.ble.BleProfile.DeviceInformationService.Characteristics;
import com.shkil.android.util.ble.enums.ConnectionState;
import com.shkil.android.util.ble.enums.PairingState;
import com.shkil.android.util.ble.exception.BleCallException;
import com.shkil.android.util.ble.exception.BleConnectionException;
import com.shkil.android.util.ble.exception.BleNotPairedException;
import com.shkil.android.util.ble.operation.ReadCharacteristicOperation;
import com.shkil.android.util.ble.operation.WriteCharacteristicOperation;
import com.shkil.android.util.ble.util.BleUtils;
import com.shkil.android.util.ble.util.BluetoothGattCallbackAdapter;
import com.shkil.android.util.ble.util.io.CharacteristicReader;
import com.shkil.android.util.ble.util.io.CharacteristicWriter;
import com.shkil.android.util.ble.util.io.IPacket;
import com.shkil.android.util.concurrent.MainThread;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.concurrent.ValueLatch;
import com.shkil.android.util.logging.Logger;
import com.shkil.android.util.logging.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;

import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
import static android.content.Context.BLUETOOTH_SERVICE;
import static android.os.SystemClock.uptimeMillis;
import static com.shkil.android.util.Utils.isEmpty;
import static com.shkil.android.util.ble.util.BleUtils.gattConnectionPriorityToString;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class BleDevice {

    private static final Logger log = LoggerFactory.getLogger("BleDevice");
    private static final Logger gattCallbackLog = LoggerFactory.getLogger("BleDeviceGattCallback");

    private static final boolean TRACE = false;
    private static final boolean TRACE_ALL_GATT_CALLBACKS = TRACE && false;

    private static final long DEFAULT_CONNECTION_WAIT_MILLIS = MINUTES.toMillis(1);
    private static final int DEFAULT_CONNECTION_PRIORITY = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;

    private static final long OPERATION_RESULT_WAIT_TIMEOUT_MILLIS = MINUTES.toMillis(5);

    private static final CharacteristicReader<String> STRING_CHARACTERISTIC_READER = new CharacteristicReader<String>() {
        @Override
        public String readValue(BluetoothGattCharacteristic characteristic) throws Exception {
            return BleUtils.getStringValue(characteristic);
        }
    };

    private final BleService service;
    private final BluetoothDevice device;
    private final BluetoothAdapter bluetoothAdapter;

    private final Object connectionLock = new Object();

    @GuardedBy("connectionLock")
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    @GuardedBy("connectionLock")
    private CountDownLatch connectionLatch;

    @GuardedBy("connectionLock")
    private CountDownLatch disconnectionLatch;

    @GuardedBy("connectionLock")
    private volatile BluetoothGatt gatt;

    @GuardedBy("connectionLock")
    private final SparseArray<ConnectionLock> connectionLocks = new SparseArray<>();

    @GuardedBy("connectionLock")
    private int connectionLockIdCounter;

    @GuardedBy("connectionLock")
    private int requestedConnectionPriority = Integer.MIN_VALUE;

    private final BleOperationQueue operations;
    private final AtomicInteger pendingCount = new AtomicInteger();
    private volatile long inactivityUptimeMillis;
    private final AtomicInteger connectionRetry = new AtomicInteger();

    @GuardedBy("connectionLock")
    private long connectingSinceUptime;

    private final Object rssiReadLock = new Object();

    @GuardedBy("rssiReadLock")
    private ValueLatch<Integer> rssiReadLatch;

    private final Handler handler = new Handler(MainThread.LOOPER);

    public BleDevice(BleService service, BluetoothDevice bluetoothDevice) {
        this.service = service;
        this.device = bluetoothDevice;
        BluetoothManager bluetoothManager = (BluetoothManager) service.getSystemService(BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.operations = new BleOperationQueue(this);
    }

    public ConnectionState connect(boolean awaitConnected) {
        return connect(awaitConnected, false);
    }

    protected ConnectionState connect(boolean awaitConnected, boolean forceConnectionAttempt) {
        CountDownLatch disconnectionLatch;
        synchronized (connectionLock) {
            disconnectionLatch = this.disconnectionLatch;
        }
        if (disconnectionLatch != null) {
            try {
                disconnectionLatch.await();
            } catch (InterruptedException ex) {
                log.info("Connection interrupted while waiting disconnection latch");
                return getConnectionState();
            }
        }
        CountDownLatch awaitConnectionLatch = null;
        synchronized (connectionLock) {
            if (gatt == null) {
                gatt = device.connectGatt(service, false, gattCallback);
            }
            if (gatt == null) {
                if (bluetoothAdapter.isEnabled()) {
                    log.error("Can't get GATT instance");
                } else {
                    log.warn("Can't get GATT instance. Bluetooth is off");
                }
                setConnectionState(ConnectionState.DISCONNECTED);
                return ConnectionState.DISCONNECTED;
            }
            switch (connectionState) {
                case CONNECTED:
                    log.trace("Already {}: device={}", connectionState, this);
                    return ConnectionState.CONNECTED;
                case DISCONNECTED: {
                    requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
                    boolean started = gatt.connect();
                    if (started) {
                        this.connectionLatch = new CountDownLatch(1);
                        setConnectionState(ConnectionState.CONNECTING);
                    } else {
                        if (bluetoothAdapter.isEnabled()) {
                            log.error("Can't initiate GATT connection");
                        } else {
                            log.warn("Can't initiate GATT connection. Bluetooth is off");
                        }
                        return ConnectionState.DISCONNECTED;
                    }
                    break;
                }
                case CONNECTING: {
                    if (forceConnectionAttempt) {
                        requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
                        boolean started = gatt.connect();
                        if (!started) {
                            if (bluetoothAdapter.isEnabled()) {
                                log.error("Can't initiate GATT connection");
                            } else {
                                log.warn("Can't initiate GATT connection. Bluetooth is off");
                            }
                            return ConnectionState.DISCONNECTED;
                        }
                    }
                    break;
                }
                case DISCONNECTING:
                    log.warn("Unexpected connection state: " + connectionState);
                    return connectionState;
                default:
                    throw new RuntimeException("Unexpected connection state: " + connectionState);
            }
            if (awaitConnected) {
                awaitConnectionLatch = this.connectionLatch;
            }
        }
        if (awaitConnectionLatch != null) {
            try {
                if (!awaitConnectionLatch.await(DEFAULT_CONNECTION_WAIT_MILLIS, MILLISECONDS)) {
                    log.warn("Connection await timed out");
                }
            } catch (InterruptedException ex) {
                log.warn("Connection await failed", ex);
            }
        }
        return getConnectionState();
    }

    public final void disconnect() {
        disconnect(false);
    }

    protected void disconnect(boolean auto) {
        boolean connecting = false;
        synchronized (connectionLock) {
            switch (connectionState) {
                case CONNECTING:
                    connecting = true;
                    if (connectionLatch != null) {
                        connectionLatch.countDown();
                    }
                    // non-breaking
                case CONNECTED: {
                    disconnectionLatch = new CountDownLatch(1);
                    setConnectionState(ConnectionState.DISCONNECTING);
                    operations.clear(0, SECONDS);
                    gatt.disconnect();
                    log.trace("GATT disconnect invoked");
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Sometimes GATT callback DISCONNECTED hasn't come
                            synchronized (connectionLock) {
                                if (BleDevice.this.getConnectionState() == ConnectionState.DISCONNECTING) {
                                    BleDevice.this.setConnectionState(ConnectionState.DISCONNECTED);
                                }
                            }
                        }
                    }, connecting ? 0 : 5000);
                    break;
                }
                default:
                    log.warn("Nothing to disconnect");
            }
        }
    }

    public String getAddress() {
        return device.getAddress();
    }

    public String getDeviceName() {
        return device.getName();
    }

    /**
     * @return true if connection state was really changed; false otherwise
     */
    boolean setConnectionState(ConnectionState newState) {
        synchronized (connectionLock) {
            if (newState != null && this.connectionState != newState) {
                this.connectionState = newState;
                onConnectionStateChanged(newState);
                return true;
            }
        }
        return false;
    }

    void updateInactivityTime() {
        inactivityUptimeMillis = uptimeMillis();
        service.checkInactiveDeviceConnections();
    }

    private void setConnectingSinceUptime(long connectingSinceUptime) {
        synchronized (connectionLock) {
            if (this.connectingSinceUptime != connectingSinceUptime) {
                this.connectingSinceUptime = connectingSinceUptime;
                checkConnectionAttemptTimeoutRunnable.run();
            }
        }
    }

    final Runnable checkConnectionAttemptTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(checkConnectionAttemptTimeoutRunnable);
            synchronized (connectionLock) {
                if (getConnectionState() == ConnectionState.CONNECTING) {
                    if (shouldKeepConnectionAlive()) {
                        handler.postDelayed(checkConnectionAttemptTimeoutRunnable, 5000);
                    } else {
                        log.info("Terminating connection attempts to {} due to inactivity", getAddress());
                        disconnect(true);
                    }
                }
            }
        }
    };

    void onConnectionStateChanged(ConnectionState newState) {
        log.debug("onConnectionStateChanged(): device={}, newState={}", getAddress(), newState);
        synchronized (connectionLock) {
            if (newState == ConnectionState.CONNECTING) {
                setConnectingSinceUptime(uptimeMillis());
            } else {
                setConnectingSinceUptime(0);
                connectionRetry.set(0);
            }
            switch (newState) {
                case CONNECTED: {
                    disconnectionLatch = null;
                    onConnectedHook();
                    if (connectionLatch != null) {
                        connectionLatch.countDown();
                    }
                    updateInactivityTime();
                    break;
                }
                case DISCONNECTED: {
                    if (connectionLatch != null) {
                        connectionLatch.countDown();
                    }
                    connectionLatch = null;
                    if (gatt != null) {
                        gatt.disconnect();
                        if (shouldKeepConnectionAlive()) {
                            // It is not optimization. It is workaround for Samsung with Android 4.4.4
                            log.debug("GATT is not closed because we need to keep connection alive");
                        } else {
                            log.debug("Closing GATT...");
                            gatt.close();
                            gatt = null;
                        }
                    }
                    requestedConnectionPriority = Integer.MIN_VALUE;
                    if (disconnectionLatch != null) {
                        disconnectionLatch.countDown();
                    }
                    break;
                }
            }
        }
        onConnectionEvent(newState, 0);
    }

    protected void onConnectedHook() {
        adjustConnectionPriority();
    }

    protected abstract void onNotPairedEvent();

    protected abstract void onConnectionEvent(ConnectionState state, int retry);

    public final ConnectionState getConnectionState() {
        synchronized (connectionLock) {
            return connectionState;
        }
    }

    @Override
    public String toString() {
        return "BleDevice{name=" + device.getName() + ", address=" + device.getAddress() + ", state=" + connectionState + '}';
    }

    protected Map<UUID, String> getSpecifications() {
        return BleProfile.NAMES;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallbackAdapter(gattCallbackLog, getSpecifications()) {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                boolean paired = requestPairingIfNeeded() == PairingState.PAIRED;
                if (isEmpty(gatt.getServices())) {
                    setConnectionState(ConnectionState.CONNECTING);
                    discoverServices(gatt);
                } else {
                    setConnectionState(paired ? ConnectionState.CONNECTED : ConnectionState.CONNECTING);
                }
            } else {
                ConnectionState newConnectionState = ConnectionState.fromBluetoothProfile(newState, null);
                if (newConnectionState != null) {
                    if (newConnectionState == ConnectionState.DISCONNECTED) {
                        boolean keepConnectionAlive = shouldKeepConnectionAlive();
                        String willRetryMessage = keepConnectionAlive ? ". Will try reconnect in a moment..." : "";
                        if (getConnectionState() == ConnectionState.CONNECTING) {
                            log.warn("Connection attempt to {} was unsuccessful (status {}){}", getAddress(), status, willRetryMessage);
                        } else {
                            log.warn("Connection with {} has been lost (status {}){}", getAddress(), status, willRetryMessage);
                        }
                        if (keepConnectionAlive) {
                            if (status != 133) {
                                setConnectionState(ConnectionState.DISCONNECTED);
                            }
                            int retry = connectionRetry.incrementAndGet();
                            onConnectionEvent(ConnectionState.CONNECTING, retry);
                            Utils.sleep(250);
                            connect(false, true);
                        } else {
                            setConnectionState(ConnectionState.DISCONNECTED);
                        }
                    } else {
                        setConnectionState(newConnectionState);
                    }
                } else {
                    log.error("Unknown newState value {}", newState);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (requestPairingIfNeeded() == PairingState.PAIRED) {
                    setConnectionState(ConnectionState.CONNECTED);
                } else {
                    setConnectionState(ConnectionState.CONNECTING);
                }
            } else if (getConnectionState() == ConnectionState.CONNECTING) {
                // Retry
                discoverServices(gatt); //TODO limit attempts
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            operations.onDescriptorWrite(descriptor, status == BluetoothGatt.GATT_SUCCESS);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (TRACE_ALL_GATT_CALLBACKS) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }
            boolean handled = operations.onCharacteristicWrite(characteristic, status == BluetoothGatt.GATT_SUCCESS);
            if (!handled && !TRACE_ALL_GATT_CALLBACKS) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (TRACE_ALL_GATT_CALLBACKS) {
                super.onCharacteristicRead(gatt, characteristic, status);
            }
            boolean handled = operations.onCharacteristicReceived(characteristic, status == BluetoothGatt.GATT_SUCCESS);
            if (!handled && !TRACE_ALL_GATT_CALLBACKS) {
                super.onCharacteristicRead(gatt, characteristic, status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (TRACE_ALL_GATT_CALLBACKS) {
                super.onCharacteristicChanged(gatt, characteristic);
            }
            boolean handled = operations.onCharacteristicReceived(characteristic, true);
            if (!handled && !TRACE_ALL_GATT_CALLBACKS) {
                super.onCharacteristicChanged(gatt, characteristic);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            synchronized (rssiReadLock) {
                if (rssiReadLatch != null) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        rssiReadLatch.setValue(rssi);
                    } else {
                        log.warn("Error reading RSSI");
                        rssiReadLatch.setValue(0);
                    }
                    rssiReadLatch = null;
                }
            }
        }
    };

    static void discoverServices(BluetoothGatt gatt) {
        if (!gatt.discoverServices()) {
            log.error("Can't discover services");
        }
    }

    @SuppressLint("NewApi")
    protected PairingState requestPairingIfNeeded() {
        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            return PairingState.PAIRED;
        }
        if (bondState == BluetoothDevice.BOND_BONDING) {
            return PairingState.PAIRING;
        }
        bluetoothAdapter.startDiscovery(); // Workaround: http://stackoverflow.com/questions/26143680/bring-bluetooth-pairing-dialogue-to-the-front
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Utils.sleep(500);
                bluetoothAdapter.cancelDiscovery();
                if (!device.createBond()) {
                    log.warn("BluetoothDevice.createBond() returned false");
                }
            }
        });
        return PairingState.PAIRING;
    }

    void onBluetoothAdapterStateChanged(int newState, long previousState) {
        if (newState == BluetoothAdapter.STATE_ON) {
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                if (hasConnectionLocks()) {
                    connect(false);
                }
            } else {
                log.trace("onBluetoothAdapterStateChanged(): Device {} is not paired", this);
            }
        }
    }

    void onBondStateChanged(int newState, int previousState) {
        if (connectionState == ConnectionState.CONNECTING) {
            if (newState == BluetoothDevice.BOND_BONDED) {
                setConnectionState(ConnectionState.CONNECTED);
            } else if (newState == BluetoothDevice.BOND_NONE && previousState == BluetoothDevice.BOND_BONDING) {
                disconnect(true);
            }
        }
        if (newState == BluetoothDevice.BOND_NONE && (previousState == BluetoothDevice.BOND_BONDING || previousState == BluetoothDevice.BOND_BONDED)) {
            onNotPairedEvent();
        }
    }

    BluetoothGatt gatt() throws BleConnectionException {
        return gatt(DEFAULT_CONNECTION_WAIT_MILLIS);
    }

    BluetoothGatt gatt(long connectionAwaitTimeoutMillis) throws BleConnectionException {
        CountDownLatch connectionLatch = null;
        boolean shouldConnect;
        synchronized (connectionLock) {
            shouldConnect = gatt == null || connectionState != ConnectionState.CONNECTED;
        }
        if (shouldConnect) {
            ConnectionState resultState = connect(false);
            if (resultState == ConnectionState.CONNECTING) {
                synchronized (connectionLock) {
                    connectionLatch = this.connectionLatch;
                }
            }
        }
        if (connectionAwaitTimeoutMillis > 0) {
            try {
                if (connectionLatch != null) {
                    boolean connected = connectionLatch.await(connectionAwaitTimeoutMillis, MILLISECONDS);
                    if (!connected) {
                        log.warn("Connection timed out");
                        throw new BleConnectionException("Connection await timed out");
                    }
                }
                if (gatt != null && gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDED) {
                    throw new BleNotPairedException("Can't connect to GATT server - device is not paired");
                }
                ConnectionState connectionState = getConnectionState();
                if (connectionState != ConnectionState.CONNECTED) {
                    throw new BleConnectionException("Can't connect to GATT server - device is " + connectionState);
                }
            } catch (InterruptedException ex) {
                throw new BleConnectionException("Can't connect to GATT server", ex);
            }
        }
        if (!bluetoothAdapter.isEnabled()) {
            throw new BleConnectionException("Can't connect to GATT server. Bluetooth is off");
        } else if (gatt == null) {
            throw new BleConnectionException("Can't connect to GATT server");
        }
        return gatt;
    }

    protected <T> ResultFuture<T> enqueue(BleOperation<T> operation) {
        return operations.enqueue(operation);
    }

    protected static <T> T awaitOperationResult(ResultFuture<T> future) throws BleCallException {
        try {
            return future.await(OPERATION_RESULT_WAIT_TIMEOUT_MILLIS, MILLISECONDS).getValueOrThrow();
        } catch (Exception ex) {
            if (ex instanceof TimeoutException) {
                future.cancel();
            }
            throw new BleCallException(ex);
        }
    }

    protected <T> T execute(BleOperation<T> operation) throws BleCallException {
        return awaitOperationResult(operations.<T>enqueue(operation));
    }

    protected <T> ResultFuture<T> enqueueRead(UUID serviceUuid, UUID characteristicUuid, CharacteristicReader<T> resultParser) {
        return enqueue(new ReadCharacteristicOperation<>(serviceUuid, characteristicUuid, resultParser));
    }

    protected <T> T read(UUID serviceUuid, UUID characteristicUuid, CharacteristicReader<T> resultParser) throws BleCallException {
        return awaitOperationResult(enqueueRead(serviceUuid, characteristicUuid, resultParser));
    }

    protected ResultFuture<Boolean> enqueueWrite(UUID serviceUuid, UUID characteristicUuid, CharacteristicWriter writer) {
        return enqueue(new WriteCharacteristicOperation(serviceUuid, characteristicUuid, writer));
    }

    protected void write(UUID serviceUuid, UUID characteristicUuid, CharacteristicWriter writer) throws BleCallException {
        Boolean value = awaitOperationResult(enqueueWrite(serviceUuid, characteristicUuid, writer));
        if (value != Boolean.TRUE) {
            throw new BleCallException("Characteristic write operation was unsuccessful");
        }
    }

    protected void write(UUID serviceUuid, UUID characteristicUuid, final IPacket packet) throws BleCallException {
        write(serviceUuid, characteristicUuid, new CharacteristicWriter() {
            @Override
            public void writeValue(BluetoothGattCharacteristic characteristic) {
                characteristic.setValue(packet.getPacketBytes());
            }
        });
    }

    protected void incrementPendingCount() {
        int count = pendingCount.getAndIncrement();
        if (count == 0) {
            updateInactivityTime();
        }
        //log.trace("incrementPendingCount(): count={}, inactivityTimestamp={}", (count + 1), inactivityUptimeMillis);
    }

    protected void decrementPendingCount() {
        int count = pendingCount.decrementAndGet();
        if (count == 0) {
            updateInactivityTime();
        }
        //log.trace("decrementPendingCount(): count={}, inactivityTimestamp={}", count, inactivityUptimeMillis);
    }

    public boolean shouldKeepConnectionAlive() {
        if (getConnectionState() == ConnectionState.CONNECTING
                && uptimeMillis() - connectingSinceUptime < DEFAULT_CONNECTION_WAIT_MILLIS) {
            return true;
        }
        return hasPendingOperations() || hasConnectionLocks();
    }

    public long getInactivityUptimeMillis() {
        return inactivityUptimeMillis;
    }

    private static class ConnectionLock {
        final String tag;
        final int connectionPriority;

        public ConnectionLock(String tag, int connectionPriority) {
            this.tag = tag;
            this.connectionPriority = connectionPriority;
        }
    }

    protected void requestConnectionPriority(int priority) {
        requestConnectionPriority(priority, null);
    }

    protected void requestConnectionPriority(int priority, String tag) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        synchronized (connectionLock) {
            if (priority != requestedConnectionPriority && gatt != null && gatt.requestConnectionPriority(priority)) {
                requestedConnectionPriority = priority;
                if (tag != null) {
                    log.trace("Connection priority updated: {} / tag='{}'", gattConnectionPriorityToString(priority), tag);
                } else {
                    log.trace("Connection priority updated: {}", gattConnectionPriorityToString(priority));
                }
            }
        }
    }

    @GuardedBy("connectionLock")
    protected void adjustConnectionPriority() {
        if (getConnectionState() == ConnectionState.CONNECTING) {
            requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
            return;
        }
        ConnectionLock maxPriorityConnectionLock = null;
        for (int i = connectionLocks.size() - 1; i >= 0; i--) {
            ConnectionLock item = connectionLocks.valueAt(i);
            if (maxPriorityConnectionLock == null
                    || comparePriority(item.connectionPriority, maxPriorityConnectionLock.connectionPriority)) {
                maxPriorityConnectionLock = item;
            }
        }
        if (maxPriorityConnectionLock != null) {
            requestConnectionPriority(maxPriorityConnectionLock.connectionPriority, maxPriorityConnectionLock.tag);
        } else {
            requestConnectionPriority(DEFAULT_CONNECTION_PRIORITY);
        }
    }

    private static boolean comparePriority(int itemPriority, int maxPriority) {
        if (maxPriority < CONNECTION_PRIORITY_BALANCED || maxPriority > CONNECTION_PRIORITY_LOW_POWER
                || itemPriority < CONNECTION_PRIORITY_BALANCED || itemPriority > CONNECTION_PRIORITY_LOW_POWER) {
            log.error("Unsupported connection priority value");
            return false;
        }
        switch (maxPriority) {
            case CONNECTION_PRIORITY_LOW_POWER:
                return itemPriority != CONNECTION_PRIORITY_LOW_POWER;
            case CONNECTION_PRIORITY_BALANCED:
                return itemPriority == CONNECTION_PRIORITY_HIGH;
            case CONNECTION_PRIORITY_HIGH:
                return false;
        }
        return true;
    }

    /**
     * @param connectionPriority See {@link BluetoothGatt#requestConnectionPriority(int)}
     */
    public int acquireConnectionLock(String tag, int connectionPriority) {
        int lockInstanceId;
        synchronized (connectionLock) {
            lockInstanceId = ++connectionLockIdCounter;
            connectionLocks.put(lockInstanceId, new ConnectionLock(tag, connectionPriority));
            adjustConnectionPriority();
        }
        log.trace("Connection lock '{}' acquired. Desired priority={}", tag, gattConnectionPriorityToString(connectionPriority));
        return lockInstanceId;
    }

    public void releaseConnectionLock(int lockInstanceId) {
        synchronized (connectionLock) {
            int index = connectionLocks.indexOfKey(lockInstanceId);
            if (index >= 0) {
                ConnectionLock connectionLock = connectionLocks.valueAt(index);
                connectionLocks.removeAt(index);
                log.trace("Connection lock '{}' released", connectionLock.tag);
                adjustConnectionPriority();
            }
            updateInactivityTime();
        }
    }

    public boolean hasConnectionLocks() {
        synchronized (connectionLock) {
            return connectionLocks.size() > 0;
        }
    }

    public boolean hasPendingOperations() {
        return pendingCount.get() > 0;
    }

    public int getPendingOperationCount() {
        return pendingCount.get();
    }

    public int getRemoteRssi() throws BleCallException {
        if (gatt == null) {
            return 0;
        }
        ValueLatch<Integer> rssiReadLatch;
        synchronized (rssiReadLock) {
            if (this.rssiReadLatch == null) {
                if (gatt.readRemoteRssi()) {
                    this.rssiReadLatch = ValueLatch.create();
                } else {
                    throw new BleCallException("Can't read remote RSSI");
                }
            }
            rssiReadLatch = this.rssiReadLatch;
        }
        try {
            return rssiReadLatch.awaitValue(10, SECONDS);
        } catch (Exception ex) {
            throw new BleCallException(ex);
        }
    }

    public String getFirmwareRevision() throws BleCallException {
        return read(DeviceInformationService.UUID, Characteristics.FIRMWARE_REVISION, STRING_CHARACTERISTIC_READER);
    }

    public String getSoftwareRevision() throws BleCallException {
        return read(DeviceInformationService.UUID, Characteristics.SOFTWARE_REVISION, STRING_CHARACTERISTIC_READER);
    }
}
