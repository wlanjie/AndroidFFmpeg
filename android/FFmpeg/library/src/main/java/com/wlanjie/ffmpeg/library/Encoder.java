package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/7.
 */

public class Encoder {

    public static native int x264EncoderInit(int width, int height);

    public static native void x264EncoderClose();

    public static native int x264EncoderStart(int type, byte[] input, byte[] output);
}
