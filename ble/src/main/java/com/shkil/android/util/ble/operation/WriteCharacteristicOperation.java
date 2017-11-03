package com.shkil.android.util.ble.operation;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.shkil.android.util.Result;
import com.shkil.android.util.ble.BleOperation;
import com.shkil.android.util.ble.util.io.CharacteristicWriter;

import java.io.IOException;
import java.util.UUID;

import static com.shkil.android.util.Utils.notEqual;
import static com.shkil.android.util.ble.util.BleUtils.getBluetoothCharacteristic;
import static com.shkil.android.util.ble.util.BleUtils.writeBluetoothCharacteristic;

public class WriteCharacteristicOperation extends BleOperation<Boolean> {

    private final UUID serviceUuid;
    private final UUID characteristicUuid;
    private final CharacteristicWriter writer;

    public WriteCharacteristicOperation(UUID serviceUuid, UUID characteristicUuid, CharacteristicWriter writer) {
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
        this.writer = writer;
    }

    protected void writeToCharacteristic(BluetoothGattCharacteristic characteristic) {
        writer.writeValue(characteristic);
    }

    @Override
    protected final void execute(BluetoothGatt gatt) throws IOException, InterruptedException {
        BluetoothGattCharacteristic characteristic = getBluetoothCharacteristic(gatt, serviceUuid, characteristicUuid);
        writeToCharacteristic(characteristic);
        if (!writeBluetoothCharacteristic(gatt, characteristic)) {
            throw new IOException("Can't write characteristic " + characteristic);
        }
    }

    @Override
    protected boolean onCharacteristicWrite(BluetoothGattCharacteristic characteristic, boolean success) {
        if (notEqual(characteristicUuid, characteristic.getUuid())) {
            return false;
        }
        if (success) {
            setResult(Result.success(true));
        } else {
            setResult(Result.<Boolean>failure(new IOException("Bluetooth characteristic write operation was unsuccessful")));
        }
        return true;
    }

    @Override
    protected boolean onCharacteristicReceive(BluetoothGattCharacteristic characteristic, boolean success) {
        return false;
    }

    @Override
    protected void onDescriptorWrite(BluetoothGattDescriptor descriptor, boolean success) {
    }
}
