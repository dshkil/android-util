package com.shkil.android.util.ble.exception;

import java.io.IOException;

public class BleCallException extends IOException {

    public BleCallException(String message) {
        super(message);
    }

    public BleCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public BleCallException(Throwable cause) {
        super(cause);
    }
}
