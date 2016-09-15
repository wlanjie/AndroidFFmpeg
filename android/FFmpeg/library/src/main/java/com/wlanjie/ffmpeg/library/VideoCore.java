package com.wlanjie.ffmpeg.library;

import android.graphics.SurfaceTexture;

/**
 * Created by wlanjie on 16/9/11.
 */
public interface VideoCore {

    int OVERWATCH_TEXTURE_ID = 10;

    boolean prepare(Config config);

    void updateCameraTexture(SurfaceTexture cameraTexture);

    void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight);

    void updatePreview(int visualWidth, int visualHeight);

    void stopPreview();

    boolean startStreaming(FLVDataCollecter flvDataCollecter);

    boolean stopStreaming();

    boolean destroy();

    void setCurrentCamera(int cameraIndex);

    void takeScreenShot(ScreenShotListener listener);

    float getDrawFrameRate();
}
