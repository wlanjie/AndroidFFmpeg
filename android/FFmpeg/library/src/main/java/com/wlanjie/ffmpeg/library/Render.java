package com.wlanjie.ffmpeg.library;

import android.graphics.SurfaceTexture;

/**
 * Created by wlanjie on 16/9/11.
 */
public interface Render {

    void create(SurfaceTexture surfaceTexture, int pixelFormat, int pixelWidth, int pixelHeight, int visualWidth, int visualHeight);

    void update(int visualWidth, int visualHeight);

    void rendering(byte[] pixel);

    void destroy();
}
