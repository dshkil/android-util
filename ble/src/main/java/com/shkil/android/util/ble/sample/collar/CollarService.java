package com.shkil.android.util.ble.sample.collar;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.IBinder;

import com.shkil.android.util.ble.BleService;
import com.shkil.android.util.ble.exception.BleCallException;
import com.shkil.android.util.ble.sample.collar.model.BatteryStatus;


public class CollarService extends BleService<CollarDevice> {

    private final IBinder binder = new CollarServiceBinder();

    @Override
    protected CollarDevice createDevice(BluetoothDevice device) {
        return new CollarDevice(this, device);
    }

    @Override
    protected IBinder getBinder(Intent intent) {
        return binder;
    }

    class CollarServiceBinder extends BleServiceBinder implements ICollarService {
        @Override
        public BatteryStatus getBatteryStatus(String address) throws BleCallException {
            return getDevice(address).getBatteryStatus();
        }
    }
}
