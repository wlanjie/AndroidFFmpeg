package com.wlanjie.streaming;

import com.wlanjie.streaming.camera.CameraView;

/**
 * Created by wlanjie on 2016/11/29.
 */

public class Publish {

    private final Encoder encoder;

    public Publish(CameraView cameraView) {
        this(cameraView, true);
    }

    public Publish(CameraView cameraView, boolean isSoftEncoder) {
        encoder = isSoftEncoder ?
                new SoftEncoder(cameraView) :
                new HardEncoder(cameraView);
    }

    public boolean start(final String url) {
        if (encoder == null) return false;
        int ret = encoder.connect(url);
        return ret == 0 && encoder.start();
    }

    public void stop() {
        if (encoder != null) {
            encoder.stop();
            encoder.destroy();
        }
    }
}
