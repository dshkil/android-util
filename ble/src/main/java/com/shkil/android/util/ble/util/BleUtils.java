package com.shkil.android.util.ble.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.shkil.android.util.ble.BleProfile.Descriptors;
import com.shkil.android.util.concurrent.ValueLatch;
import com.shkil.android.util.logging.Logger;
import com.shkil.android.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static com.shkil.android.util.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BleUtils {

    private static final Logger log = LoggerFactory.getLogger(BleUtils.class);

    public static BluetoothGattService getBluetoothService(BluetoothGatt gatt, UUID serviceUuid) throws IOException {
        BluetoothGattService service = gatt.getService(serviceUuid);
        if (service == null) {
            throw new IOException("Service " + serviceUuid + " is not offered or service discovery has not been completed");
        }
        return service;
    }

    public static BluetoothGattCharacteristic getBluetoothCharacteristic(BluetoothGatt gatt, UUID serviceUuid, UUID characteristicUuid) throws IOException {
        BluetoothGattService service = getBluetoothService(gatt, serviceUuid);
        return getBluetoothCharacteristic(service, characteristicUuid);
    }

    public static BluetoothGattCharacteristic getBluetoothCharacteristic(BluetoothGattService service, UUID characteristicUuid) throws IOException {
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            throw new IOException("Characteristic " + characteristic + " is not found");
        }
        return characteristic;
    }

    public static void readBluetoothCharacteristic(BluetoothGatt gatt, UUID serviceUuid, UUID characteristicUuid) throws IOException {
        BluetoothGattCharacteristic characteristic = getBluetoothCharacteristic(gatt, serviceUuid, characteristicUuid);
        if (!gatt.readCharacteristic(characteristic)) {
            throw new IOException("Can't read bluetooth characteristic " + characteristicUuid);
        }
    }

    public static boolean writeBluetoothCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        for (int attempt = 1; attempt <= 10; attempt++) {
            if (gatt.writeCharacteristic(characteristic)) {
                return true;
            }
            log.warn("Can't write bluetooth characteristic on {} attempt.", attempt);
            sleep(150);
        }
        return false;
    }

    public static String getStringValue(BluetoothGattCharacteristic characteristic) {
        return characteristic.getStringValue(0);
    }

    public static void setNotificationEnabledOnClient(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                                      boolean enable, ValueLatch<Boolean> cccDescriptorWrittenLatch) throws IOException, InterruptedException {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            throw new IOException("Can't set characteristic notification status");
        }
        try {
            BluetoothGattDescriptor characteristicConfigDescriptor = characteristic.getDescriptor(Descriptors.CLIENT_CHARACTERISTIC_CONFIG);
            characteristicConfigDescriptor.setValue(enable ? ENABLE_NOTIFICATION_VALUE : DISABLE_NOTIFICATION_VALUE);
            for (int attempt = 1; attempt <= 20; attempt++) {
                if (gatt.writeDescriptor(characteristicConfigDescriptor)) {
                    break;
                }
                Thread.sleep(15);
            }
            boolean descriptorWritten = cccDescriptorWrittenLatch.awaitValue(30, SECONDS);
            if (!descriptorWritten) {
                throw new IOException("Can't write CCC descriptor");
            }
        } catch (TimeoutException ex) {
            throw new IOException(ex);
        }
    }

    public static boolean isNotificationEnabledOnClient(BluetoothGattCharacteristic characteristic) {
        BluetoothGattDescriptor cccDescriptor = characteristic.getDescriptor(Descriptors.CLIENT_CHARACTERISTIC_CONFIG);
        return Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, cccDescriptor.getValue());
    }

    public static String gattConnectionPriorityToString(int priority) {
        switch (priority) {
            case BluetoothGatt.CONNECTION_PRIORITY_BALANCED:
                return "BALANCED";
            case BluetoothGatt.CONNECTION_PRIORITY_HIGH:
                return "HIGH";
            case BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER:
                return "LOW_POWER";
        }
        return String.valueOf(priority);
    }
}
