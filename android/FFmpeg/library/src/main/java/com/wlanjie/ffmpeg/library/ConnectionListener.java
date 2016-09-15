package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/11.
 */
public interface ConnectionListener {
    void onOpenConnectionResult(int result);
    void onWriteError(int error);
    void onCloseConnectionResult(int result);

    class WriteErrorRunnable implements Runnable {

        final ConnectionListener connectionListener;
        final int error;

        public WriteErrorRunnable(ConnectionListener connectionListener, int error) {
            this.connectionListener = connectionListener;
            this.error = error;
        }

        @Override
        public void run() {
            if (connectionListener != null) {
                connectionListener.onWriteError(error);
            }
        }
    }
}
