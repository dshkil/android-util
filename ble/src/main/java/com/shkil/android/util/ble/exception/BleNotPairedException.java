package com.shkil.android.util.ble.exception;

public class BleNotPairedException extends BleConnectionException {

    public BleNotPairedException(String message) {
        super(message);
    }

    public BleNotPairedException(String message, Throwable cause) {
        super(message, cause);
    }

    public BleNotPairedException(Throwable cause) {
        super(cause);
    }
}
