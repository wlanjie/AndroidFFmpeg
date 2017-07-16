package com.wlanjie.streaming.video;

/**
 * Created by wlanjie on 2017/7/16.
 * Call by Jni
 */
public class VideoParameter {
  private int frameWidth;
  private int frameHeight;
  private int videoWidth;
  private int videoHeight;
  private int bitrate;
  private int frameRate;

  public int getFrameWidth() {
    return frameWidth;
  }

  public void setFrameWidth(int frameWidth) {
    this.frameWidth = frameWidth;
  }

  public int getFrameHeight() {
    return frameHeight;
  }

  public void setFrameHeight(int frameHeight) {
    this.frameHeight = frameHeight;
  }

  public int getVideoWidth() {
    return videoWidth;
  }

  public void setVideoWidth(int videoWidth) {
    this.videoWidth = videoWidth;
  }

  public int getVideoHeight() {
    return videoHeight;
  }

  public void setVideoHeight(int videoHeight) {
    this.videoHeight = videoHeight;
  }

  public int getBitrate() {
    return bitrate;
  }

  public void setBitrate(int bitrate) {
    this.bitrate = bitrate;
  }

  public int getFrameRate() {
    return frameRate;
  }

  public void setFrameRate(int frameRate) {
    this.frameRate = frameRate;
  }
}
