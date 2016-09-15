package com.wlanjie.ffmpeg.library;

import android.graphics.SurfaceTexture;

/**
 * Created by wlanjie on 16/9/11.
 */
public class HardVideoCore implements VideoCore {

    public HardVideoCore(Parameters parameters) {
    }

    @Override
    public boolean prepare(Config config) {
        return false;
    }

    @Override
    public void updateCameraTexture(SurfaceTexture cameraTexture) {

    }

    @Override
    public void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {

    }

    @Override
    public void updatePreview(int visualWidth, int visualHeight) {

    }

    @Override
    public void stopPreview() {

    }

    @Override
    public boolean startStreaming(FLVDataCollecter flvDataCollecter) {
        return false;
    }

    @Override
    public boolean stopStreaming() {
        return false;
    }

    @Override
    public boolean destroy() {
        return false;
    }

    @Override
    public void setCurrentCamera(int cameraIndex) {

    }

    @Override
    public void takeScreenShot(ScreenShotListener listener) {

    }

    @Override
    public float getDrawFrameRate() {
        return 0;
    }

    public void onFrameAvailable() {

    }

    public HardVideoFilter acquireVideoFilter() {
        return null;
    }

    public void releaseVideoFilter() {

    }

    public void setVideoFilter(HardVideoFilter hardVideoFilter) {

    }
}
