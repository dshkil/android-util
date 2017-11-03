package com.shkil.android.util.ble.util.io;

import android.bluetooth.BluetoothGattCharacteristic;

public interface CharacteristicWriter {

    void writeValue(BluetoothGattCharacteristic characteristic);

}
