package com.shkil.android.util.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.shkil.android.util.Result;
import com.shkil.android.util.concurrent.LatchResultFuture;
import com.shkil.android.util.concurrent.MainThread;

import java.io.IOException;

/**
 * Represents abstract operation in BleOperationQueue. Operation should start BLE request(s) inside execute() method,
 * handle callback(s) and invoke setResult() when result is ready. Operation completes when COMPLETED result was
 * set or after time out.
 *
 * @param <T> result type
 */
public abstract class BleOperation<T> {

    final LatchResultFuture<T> resultFuture = new LatchResultFuture<>(MainThread.EXECUTOR);

    protected void setResult(Result<T> result) {
        resultFuture.setResult(result);
    }

    public long getCompletionAwaitTimeoutMillis() {
        return BleOperationQueue.DEFAULT_COMPLETION_AWAIT_TIMEOUT_MILLIS;
    }

    /**
     * The method where BLE operations should be initiated
     *
     * @param gatt instance of BluetoothGatt, connected and ready to use.
     */
    protected abstract void execute(BluetoothGatt gatt) throws IOException, InterruptedException;

    /**
     * Callback that is invoked when onCharacteristicChanged on onCharacteristicRead is received
     *
     * @return true if characteristic change was handled. This will prevent following processing (usually just logging).
     */
    protected abstract boolean onCharacteristicReceive(BluetoothGattCharacteristic characteristic, boolean success);

    protected abstract boolean onCharacteristicWrite(BluetoothGattCharacteristic characteristic, boolean success);

    protected abstract void onDescriptorWrite(BluetoothGattDescriptor descriptor, boolean success);
}
