package com.shkil.android.util.concurrent;

/**
 * Provides the ability to cancel an operation in progress.
 */
public final class Cancellator implements Runnable {

    private boolean canceled;
    private OnCancelListener onCancelListener;
    private boolean cancelInProgress;

    /**
     * Returns true if the operation has been canceled.
     *
     * @return True if the operation has been canceled.
     */
    public boolean isCanceled() {
        synchronized (this) {
            return canceled;
        }
    }

    /**
     * Cancels the operation and signals the cancellation listener.
     * If the operation has not yet started, then it will be canceled as soon as it does.
     */
    public void cancel() {
        final OnCancelListener listener;
        synchronized (this) {
            if (canceled) {
                return;
            }
            canceled = true;
            cancelInProgress = true;
            listener = onCancelListener;
        }

        try {
            if (listener != null) {
                listener.onCancel();
            }
        } finally {
            synchronized (this) {
                cancelInProgress = false;
                notifyAll();
            }
        }
    }

    /**
     * Sets the cancellation listener to be called when canceled.
     *
     * This method is intended to be used by the recipient of a cancellation signal.
     *
     * If {@link Cancellator#cancel} has already been called, then the provided
     * listener is invoked immediately.
     *
     * This method is guaranteed that the listener will not be called after it
     * has been removed.
     *
     * @param listener The cancellation listener, or null to remove the current listener.
     */
    public void setOnCancelListener(OnCancelListener listener) {
        synchronized (this) {
            waitForCancelFinishedLocked();

            if (listener != null && onCancelListener != null) {
                throw new IllegalStateException("Only one cancel listener is supported");
            }
            onCancelListener = listener;
            if (!canceled || listener == null) {
                return;
            }
        }
        listener.onCancel();
    }

    private void waitForCancelFinishedLocked() {
        while (cancelInProgress) {
            try {
                wait();
            } catch (InterruptedException ex) {
            }
        }
    }

    @Override
    public void run() {
        cancel();
    }

    /**
     * Listens for cancellation.
     */
    public interface OnCancelListener {
        /**
         * Called when {@link Cancellator#cancel} is invoked.
         */
        void onCancel();
    }

}
