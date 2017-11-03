package com.shkil.android.util.ble.util.io;

import android.bluetooth.BluetoothGattCharacteristic;

public interface CharacteristicReader<T> {

    T readValue(BluetoothGattCharacteristic characteristic) throws Exception;

}
