package com.wlanjie.streaming.rtmp;

/**
 * Created by wlanjie on 2017/6/24.
 */
public class Rtmp {

  public static native int connect(String rtmpUrl);

  public static native int writeVideo(byte[] data, long pts);

  public static native int writeAudio(byte[] data, long pts, int simpleRate, int channel);

  public static native void startPublish();

  public static native void destroy();
}
