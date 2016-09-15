package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/11.
 */
public class SurfaceVideoFilter {
    protected int sizeWidth;
    protected int sizeHeight;
    protected int sizeY;
    protected int sizeTotal;
    protected int sizeU;
    protected int sizeUV;

    public void onInit(int width, int height) {
        sizeWidth = width;
        sizeHeight = height;
        sizeY = sizeHeight * sizeWidth;
        sizeUV = sizeHeight * sizeWidth / 2;
        sizeU = sizeUV / 2;
        sizeTotal = sizeY * 3 / 2;
    }

    public boolean onFrame(byte[] originBuffer, byte[] targetBuffer, long presentationTimeMs, int sequenceNumber) {
        return false;
    }

    public void onDestroy() {

    }
}
