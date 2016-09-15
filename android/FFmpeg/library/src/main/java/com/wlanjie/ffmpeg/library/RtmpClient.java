package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/11.
 */
public class RtmpClient {

    public static native long open(String url, boolean isPublishMode);

    public static native int read(long rtmpPointer, byte[] data, int offset, int size);

    public static native int write(long rtmpPointer, byte[] data, int size, int type, int ts);

    public static native int close(long rtmpPointer);

    public static native String getIpAddress(long rtmpPointer);
}
