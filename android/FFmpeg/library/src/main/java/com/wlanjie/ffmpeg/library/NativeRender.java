package com.wlanjie.ffmpeg.library;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.view.Surface;

/**
 * Created by wlanjie on 16/9/11.
 */
public class NativeRender implements Render {

    private Surface visualSurface;
    private int pixelWidth;
    private int pixelHeight;
    private int pixelSize;

    @Override
    public void create(SurfaceTexture surfaceTexture, int pixelFormat, int pixelWidth, int pixelHeight, int visualWidth, int visualHeight) {
        if (pixelFormat != ImageFormat.NV21) {
            throw new IllegalArgumentException("NativeRender pixelFormat only support NV21");
        }
        visualSurface = new Surface(surfaceTexture);
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.pixelSize = (3 * pixelWidth * pixelHeight) / 2;
    }

    @Override
    public void update(int visualWidth, int visualHeight) {

    }

    @Override
    public void rendering(byte[] pixel) {
        if (visualSurface != null && visualSurface.isValid()) {
            renderingSurface(visualSurface, pixel, pixelWidth, pixelHeight, pixelSize);
        }
    }

    @Override
    public void destroy() {
        visualSurface.release();
    }

    private native void renderingSurface(Surface surface, byte[] pixels, int w, int h, int s);
}
