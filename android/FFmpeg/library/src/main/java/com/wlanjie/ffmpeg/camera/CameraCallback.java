package com.wlanjie.ffmpeg.camera;

/**
 * Created by wlanjie on 2016/12/13.
 */

public interface CameraCallback {
    void onCameraOpened(int previewWidth, int previewHeight);

    void onCameraClosed();

    void onPreviewFrame(byte[] data);
}
