package com.shkil.android.util.ble;

import com.shkil.android.util.ble.enums.ConnectionState;
import com.shkil.android.util.ble.exception.BleCallException;

public interface IBleService {

    ConnectionState getConnectionState(String deviceAddress);

    ConnectionState connect(String deviceAddress, boolean awaitConnected);

    void disconnect(String deviceAddress);

    int acquireConnectionLock(String deviceAddress, String tag, int connectionPriority);

    void releaseConnectionLock(String deviceAddress, int lockInstanceId);

    int getPendingOperationCount(String deviceAddress);

    int getRemoteRssi(String address) throws BleCallException;

    String getFirmwareRevision(String deviceAddress) throws BleCallException;

    String getSoftwareRevision(String address) throws BleCallException;
}
