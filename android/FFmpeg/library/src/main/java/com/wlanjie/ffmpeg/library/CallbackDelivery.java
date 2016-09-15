package com.wlanjie.ffmpeg.library;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

/**
 * Created by wlanjie on 16/9/11.
 */
public class CallbackDelivery {
    static private CallbackDelivery instance;
    private final Executor callbackPoster;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static CallbackDelivery i() {
        return instance == null ? instance = new CallbackDelivery() : instance;
    }

    private CallbackDelivery() {
        callbackPoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    public void post(Runnable runnable) {
        callbackPoster.execute(runnable);
    }
}
