package com.wlanjie.ffmpeg.library;

import android.graphics.Bitmap;

/**
 * Created by wlanjie on 16/9/11.
 */
public interface ScreenShotListener {

    void onScreenShotResult(Bitmap bitmap);

    class ScreenShotListenerRunnable implements Runnable {

        Bitmap resultBitmap;
        ScreenShotListener listener;

        public ScreenShotListenerRunnable(Bitmap resultBitmap, ScreenShotListener listener) {
            this.resultBitmap = resultBitmap;
            this.listener = listener;
        }

        @Override
        public void run() {
            if (listener != null) {
                listener.onScreenShotResult(resultBitmap);
            }
        }
    }
}
