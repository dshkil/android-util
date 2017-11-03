package com.shkil.android.util.ble.sample.collar;

import android.bluetooth.BluetoothDevice;

import com.shkil.android.util.ble.BleDevice;
import com.shkil.android.util.ble.BleProfile.BatteryService;
import com.shkil.android.util.ble.BleProfile.BatteryService.Characteristics;
import com.shkil.android.util.ble.enums.ConnectionState;
import com.shkil.android.util.ble.exception.BleCallException;
import com.shkil.android.util.ble.sample.collar.model.BatteryStatus;


public class CollarDevice extends BleDevice {

    public CollarDevice(CollarService service, BluetoothDevice bluetoothDevice) {
        super(service, bluetoothDevice);
    }

    @Override
    protected void onConnectionEvent(ConnectionState state, int retry) {
    }

    @Override
    protected void onNotPairedEvent() {
    }

    public BatteryStatus getBatteryStatus() throws BleCallException {
        return read(BatteryService.UUID, Characteristics.BATTERY_LEVEL, BatteryStatus.READER);
    }
}
