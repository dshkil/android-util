package com.shkil.android.util.ble.exception;

import java.io.IOException;

public class BleConnectionException extends IOException {

    public BleConnectionException(String message) {
        super(message);
    }

    public BleConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public BleConnectionException(Throwable cause) {
        super(cause);
    }
}
