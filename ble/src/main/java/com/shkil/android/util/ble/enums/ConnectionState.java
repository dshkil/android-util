package com.shkil.android.util.ble.enums;

import android.bluetooth.BluetoothProfile;

public enum ConnectionState {

    SERVICE_NOT_READY, DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING;

    public static ConnectionState fromBluetoothProfile(int state, ConnectionState defaultValue) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return DISCONNECTED;
            case BluetoothProfile.STATE_CONNECTING:
                return CONNECTING;
            case BluetoothProfile.STATE_CONNECTED:
                return CONNECTED;
            case BluetoothProfile.STATE_DISCONNECTING:
                return DISCONNECTING;
        }
        return defaultValue;
    }

}
