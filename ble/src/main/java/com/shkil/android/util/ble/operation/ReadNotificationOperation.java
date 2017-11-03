package com.shkil.android.util.ble.operation;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.shkil.android.util.Result;
import com.shkil.android.util.ble.BleOperation;
import com.shkil.android.util.ble.BleProfile.Descriptors;
import com.shkil.android.util.ble.util.io.CharacteristicReader;
import com.shkil.android.util.concurrent.ValueLatch;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static com.shkil.android.util.Utils.notEqual;
import static com.shkil.android.util.ble.util.BleUtils.getBluetoothCharacteristic;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ReadNotificationOperation<T> extends BleOperation<T> {

    private final UUID serviceUuid;
    private final UUID characteristicUuid;
    private final CharacteristicReader<T> reader;
    private final ValueLatch<Boolean> cccDescriptorWrittenLatch = ValueLatch.create();

    public ReadNotificationOperation(UUID serviceUuid, UUID characteristicUuid, CharacteristicReader<T> reader) {
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
        this.reader = reader;
    }

    protected void writeClientConfigDescriptorValue(BluetoothGattDescriptor descriptor) {
        descriptor.setValue(ENABLE_NOTIFICATION_VALUE);
    }

    protected T parseResponse(BluetoothGattCharacteristic characteristic) throws Exception {
        return reader.readValue(characteristic);
    }

    @Override
    protected void execute(BluetoothGatt gatt) throws IOException, InterruptedException {
        BluetoothGattCharacteristic characteristic = getBluetoothCharacteristic(gatt, serviceUuid, characteristicUuid);
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            throw new IOException("Can't enable characteristic notification");
        }
        try {
            BluetoothGattDescriptor cccDescriptor = characteristic.getDescriptor(Descriptors.CLIENT_CHARACTERISTIC_CONFIG);
            writeClientConfigDescriptorValue(cccDescriptor);
            for (int attempt = 1; attempt <= 20; attempt++) {
                if (gatt.writeDescriptor(cccDescriptor)) {
                    break;
                }
                Thread.sleep(15);
            }
            boolean descriptorWritten = cccDescriptorWrittenLatch.awaitValue(30, SECONDS);
            if (!descriptorWritten) {
                throw new IOException("Can't write CCC descriptor");
            }
        } catch (TimeoutException ex) {
            throw new IOException("Can't write CCC descriptor", ex);
        }
    }

    @Override
    protected void onDescriptorWrite(BluetoothGattDescriptor descriptor, boolean success) {
        if (Descriptors.CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())
              && characteristicUuid.equals(descriptor.getCharacteristic().getUuid())) {
            cccDescriptorWrittenLatch.setValue(success);
        }
    }

    @Override
    protected boolean onCharacteristicWrite(BluetoothGattCharacteristic characteristic, boolean success) {
        return false;
    }

    @Override
    protected boolean onCharacteristicReceive(BluetoothGattCharacteristic characteristic, boolean success) {
        if (notEqual(characteristic.getUuid(), characteristicUuid)) {
            return false;
        }
        Result<T> result;
        if (!success) {
            result = Result.failure(new IOException("Bluetooth characteristic read operation was unsuccessful"));
        } else try {
            T response = parseResponse(characteristic);
            result = Result.success(response);
        } catch (Exception ex) {
            result = Result.failure(ex);
        }
        setResult(result);
        return true;
    }
}
