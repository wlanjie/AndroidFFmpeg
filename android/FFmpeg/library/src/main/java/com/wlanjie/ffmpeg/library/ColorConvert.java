package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/11.
 */
public class ColorConvert {

    static public native void NV21ToYUV420SP(byte[] src, byte[] dst, int ySize);

    static public native void NV21ToYUV420P(byte[] src, byte[] dst, int ySize);

    static public native void YUV420SPToYUV420P(byte[] src, byte[] dst, int ySize);

    static public native void NV21ToARGB(byte[] src, int[] dst, int width,int height);

    static public native void FIXGLPIXEL(int[] src,int[] dst, int width,int height);

    //slow
    static public native void NV21Transform(byte[] src, byte[] dst, int srcWidth,int srcHeight,int directionFlag);
}
