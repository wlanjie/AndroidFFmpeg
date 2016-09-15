package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/10.
 */
public class AudioFilter {

    private int size;
    private int sizeHalf;

    public void onInit(int size) {
        this.size = size;
        sizeHalf = size / 2;
    }

    public boolean onFrame(byte[] originBuffer, byte[] targetBuffer, long presentationTimeMs, int sequenceNum) {
        return false;
    }

    public void onDestroy() {

    }
}
