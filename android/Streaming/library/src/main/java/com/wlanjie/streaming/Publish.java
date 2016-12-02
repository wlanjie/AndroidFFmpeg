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
        encoder = new Encoder.Builder()
                .setSoftEncoder(isSoftEncoder)
                .setCameraView(cameraView)
                .build();
    }

    public void start(final String url) throws IllegalStateException, IllegalArgumentException {
        if (encoder == null) return;
        encoder.start(url);
    }

    public void stop() {
        if (encoder != null) {
            encoder.stop();
            encoder.destroy();
        }
    }
}
