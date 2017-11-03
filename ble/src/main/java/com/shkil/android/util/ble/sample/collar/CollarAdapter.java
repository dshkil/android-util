package com.shkil.android.util.ble.sample.collar;

import android.content.Context;

import com.shkil.android.util.ServiceConnector;
import com.shkil.android.util.ble.BleDeviceAdapter;
import com.shkil.android.util.ble.sample.collar.model.BatteryStatus;
import com.shkil.android.util.concurrent.ResultFuture;

import java.util.concurrent.Callable;

public class CollarAdapter extends BleDeviceAdapter<ICollarService> {

    public CollarAdapter(Context context, String deviceAddress, ServiceConnector<ICollarService> serviceConnector) {
        super(context, deviceAddress, serviceConnector);
    }

    public ResultFuture<BatteryStatus> getBatteryStatus() {
        return executeAsync(new Callable<BatteryStatus>() {
            @Override
            public BatteryStatus call() throws Exception {
                return getService().getBatteryStatus(address);
            }
        });
    }
}
