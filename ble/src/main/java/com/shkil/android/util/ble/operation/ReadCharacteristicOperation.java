package com.shkil.android.util.ble.operation;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.support.annotation.Nullable;

import com.shkil.android.util.Result;
import com.shkil.android.util.ble.BleOperation;
import com.shkil.android.util.ble.util.io.CharacteristicReader;

import java.io.IOException;
import java.util.UUID;

import static com.shkil.android.util.Utils.notEqual;
import static com.shkil.android.util.ble.util.BleUtils.readBluetoothCharacteristic;

public class ReadCharacteristicOperation<T> extends BleOperation<T> {

    private final UUID serviceUuid;
    private final UUID characteristicUuid;
    private final CharacteristicReader<T> resultParser;

    public ReadCharacteristicOperation(UUID serviceUuid, UUID characteristicUuid, CharacteristicReader<T> resultParser) {
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
        this.resultParser = resultParser;
    }

    @Override
    protected void execute(BluetoothGatt gatt) throws IOException, InterruptedException {
        readBluetoothCharacteristic(gatt, serviceUuid, characteristicUuid);
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
        if (success) {
            Result<T> result;
            try {
                result = parseValue(characteristic);
            } catch (Exception ex) {
                result = Result.failure(ex);
            }
            if (result != null) {
                setResult(result);
            } else {
                // batch expected
            }
        } else {
            setResult(Result.<T>failure(new IOException("Bluetooth characteristic read operation was unsuccessful")));
        }
        return true;
    }

    @Override
    protected void onDescriptorWrite(BluetoothGattDescriptor descriptor, boolean success) {
    }

    @Nullable
    protected Result<T> parseValue(BluetoothGattCharacteristic characteristic) throws Exception {
        T value = resultParser.readValue(characteristic);
        if (value != null) {
            return Result.success(value);
        }
        return null;
    }
}
