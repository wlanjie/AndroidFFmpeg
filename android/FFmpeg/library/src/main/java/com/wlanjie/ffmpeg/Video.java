package com.wlanjie.ffmpeg;

/**
 * Created by wlanjie on 2017/9/10.
 */
public class Video {
  private int width;
  private int height;
  private int totalFrame;
  private double duration;
  private double frameRate;

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getTotalFrame() {
    return totalFrame;
  }

  public double getDuration() {
    return duration;
  }

  public double getFrameRate() {
    return frameRate;
  }
}
