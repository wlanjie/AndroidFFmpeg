package com.wlanjie.streaming.camera;

/**
 * Created by wlanjie on 2016/12/13.
 */

interface CameraCallback {
    void onCameraOpened(int previewWidth, int previewHeight);

    void onCameraClosed();

    void onPreviewFrame(byte[] data);

    void onPreview(int previewWidth, int previewHeight);
}
