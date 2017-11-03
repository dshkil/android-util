package com.shkil.android.util.ble.sample.collar;

import com.shkil.android.util.ble.IBleService;
import com.shkil.android.util.ble.exception.BleCallException;
import com.shkil.android.util.ble.sample.collar.model.BatteryStatus;

public interface ICollarService extends IBleService {

    BatteryStatus getBatteryStatus(String deviceAddress) throws BleCallException;

}
