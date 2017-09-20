package com.wlanjie.ffmpeg.video;

/**
 * Created by wlanjie on 2017/6/25.
 */
public class OpenH264Encoder {

  public native static boolean openEncoder();
  public native static void closeEncoder();
  public native static void setVideoParameter(VideoParameter parameter);
  public native static void encode(byte[] data, long pts);
}
