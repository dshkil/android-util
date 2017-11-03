package com.shkil.android.util.ble.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;

import com.shkil.android.util.logging.Logger;

import java.util.Map;
import java.util.UUID;

import static com.shkil.android.util.Utils.toHex;

public class BluetoothGattCallbackAdapter extends BluetoothGattCallback {

    public static final boolean DEBUG = false;

    private final Logger log;
    private final Map<UUID, String> specifications;

    public BluetoothGattCallbackAdapter(Logger log) {
        this(log, null);
    }

    public BluetoothGattCallbackAdapter(Logger log, Map<UUID, String> specifications) {
        this.log = log;
        this.specifications = specifications;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        log.trace("onConnectionStateChange(): gatt={}, status={}, newState={}", toString(gatt), status(status), state(newState));
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        log.trace("onServicesDiscovered(): gatt={}, status={}", toString(gatt), status(status));
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        String value = toHex(characteristic.getValue());
        log.trace("onCharacteristicRead(): gatt={}, characteristic={}, status={}, value={}", toString(gatt), toString(characteristic), status(status), value);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        log.trace("onCharacteristicWrite(): gatt={}, characteristic={}, status={}, value=0x{}", toString(gatt), toString(characteristic), status(status), toHex(characteristic.getValue()));
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        String value = toHex(characteristic.getValue());
        log.trace("onCharacteristicChanged(): gatt={}, characteristic={}, value={}", toString(gatt), toString(characteristic), value);
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        log.trace("onDescriptorRead(): gatt={}, descriptor={}, status={}", toString(gatt), toString(descriptor), status(status));
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        log.trace("onDescriptorWrite(): gatt={}, descriptor={}, status={}", toString(gatt), toString(descriptor), status(status));
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        log.trace("onReliableWriteCompleted(): gatt={}, status={}", toString(gatt), status(status));
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        log.trace("onReadRemoteRssi(): gatt={}, rssi={}, status={}", toString(gatt), rssi, status(status));
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        log.trace("onMtuChanged(): gatt={}, mtu={}, status={}", toString(gatt), mtu, status(status));
    }

    protected String toString(BluetoothGatt gatt) {
        return gatt != null ? gatt + "(" + gatt.getDevice() + ")" : null;
    }

    protected String toString(BluetoothGattCharacteristic characteristic) {
        return characteristic != null ? toString(characteristic.getUuid()) : null;
    }

    protected String toString(BluetoothGattDescriptor descriptor) {
        return descriptor != null ? toString(descriptor.getUuid()) : null;
    }

    protected String toString(UUID specificationUuid) {
        if (DEBUG && specifications != null) {
            String specificationName = specifications.get(specificationUuid);
            if (specificationName != null) {
                return specificationName;
            }
        }
        return specificationUuid.toString();
    }

    private static String status(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            case BluetoothGatt.GATT_FAILURE:
                return "FAILURE";
        }
        return String.valueOf(status);
    }

    private static String state(int state) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return "DISCONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothProfile.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "DISCONNECTING";
        }
        return String.valueOf(state);
    }
}
