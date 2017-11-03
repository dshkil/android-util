package com.shkil.android.util.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.shkil.android.util.Result;
import com.shkil.android.util.Utils;
import com.shkil.android.util.concurrent.LatchResultFuture;
import com.shkil.android.util.concurrent.ResultFuture;
import com.shkil.android.util.logging.Logger;
import com.shkil.android.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.GuardedBy;

import static com.shkil.android.util.Utils.newThreadFactory;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Android SDK doesn't contain any convenient management for concurrent BLE requests, so this class implements own
 * BLE operations queue. In the queue only one operation can be running at the same time, other operations wait
 * execution in FIFO queue
 */
class BleOperationQueue {

    private static final Logger log = LoggerFactory.getLogger(BleOperationQueue.class);

    protected static final long DEFAULT_COMPLETION_AWAIT_TIMEOUT_MILLIS = SECONDS.toMillis(30);

    @GuardedBy("this")
    private ExecutorService executor;
    private final AtomicReference<BleOperation> ongoingOperation = new AtomicReference<>();
    protected final BleDevice bleDevice;

    public BleOperationQueue(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
    }

    boolean onCharacteristicReceived(BluetoothGattCharacteristic characteristic, boolean success) {
        BleOperation ongoingOperation = this.ongoingOperation.get();
        if (ongoingOperation != null) {
            return ongoingOperation.onCharacteristicReceive(characteristic, success);
        }
        log.debug("There is no pending operation");
        return false;
    }

    boolean onCharacteristicWrite(BluetoothGattCharacteristic characteristic, boolean success) {
        BleOperation ongoingOperation = this.ongoingOperation.get();
        if (ongoingOperation != null) {
            return ongoingOperation.onCharacteristicWrite(characteristic, success);
        }
        log.debug("There is no pending operation");
        return false;
    }

    public void onDescriptorWrite(BluetoothGattDescriptor descriptor, boolean success) {
        BleOperation ongoingOperation = this.ongoingOperation.get();
        if (ongoingOperation != null) {
            ongoingOperation.onDescriptorWrite(descriptor, success);
        } else {
            log.debug("There is no pending operation");
        }
    }

    public synchronized <T> ResultFuture<T> enqueue(final BleOperation operation) {
        bleDevice.incrementPendingCount();
        if (executor == null) {
            executor = newSingleThreadExecutor(newThreadFactory("ble-operation-queue"));
        }
        executor.execute(new OperationRunnable<T>(operation));
        return operation.resultFuture;
    }

    private class OperationRunnable<T> implements Runnable {
        private final BleOperation operation;

        public OperationRunnable(BleOperation operation) {
            this.operation = operation;
        }

        @Override
        public void run() {
            //log.trace("Begin execution of {}", operation);
            LatchResultFuture resultFuture = operation.resultFuture;
            try {
                if (resultFuture.isCancelled()) {
                    return;
                }
                BluetoothGatt gatt = bleDevice.gatt();
                if (resultFuture.isCancelled()) {
                    return;
                }
                BleOperation wasOperation = ongoingOperation.getAndSet(operation);
                if (wasOperation != null) {
                    log.warn("Ongoing operation was not null: {}", wasOperation);
                }
                operation.execute(gatt);
                try {
                    resultFuture
                            .await(operation.getCompletionAwaitTimeoutMillis(), MILLISECONDS)
                            .getValueOrThrow();
                } catch (Exception ex) {
                    throw Utils.asIOException(ex);
                }
            } catch (IOException | InterruptedException ex) {
                resultFuture.setResult(Result.<T>failure(ex));
            } finally {
                ongoingOperation.compareAndSet(operation, null);
                bleDevice.decrementPendingCount();
            }
            //log.trace("End execution of {}", operation);
        }
    }

    /**
     * Clears queue, attempts terminate currently running operation
     */
    public synchronized void clear(long timeout, TimeUnit unit) {
        if (executor == null) {
            return;
        }
        List<Runnable> runnables = executor.shutdownNow();
        try {
            executor.awaitTermination(timeout, unit);
        } catch (InterruptedException ex) {
            log.warn(ex);
        }
        executor = null;
        int count = runnables.size();
        if (count > 0) {
            for (Runnable runnable : runnables) {
                if (runnable instanceof OperationRunnable<?>) {
                    BleOperation operation = ((OperationRunnable) runnable).operation;
                    operation.setResult(Result.interrupted(new InterruptedException("Queue cleared")));
                    bleDevice.decrementPendingCount();
                }
            }
            log.info("Cleared items count: {}", count);
        }
    }
}
