package com.wlanjie.ffmpeg.setting;
 
/** 
 * Created by wlanjie on 2017/6/14. 
 */ 
public class VideoSetting {
 
  private int videoWidth = 360;
  private int videoHeight = 640;
  private int frameRate = 24;
  private int gopSize = 2;
  private int bitRate = 480 * 1000;

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

  public int getFrameRate() {
    return frameRate;
  }

  public void setFrameRate(int frameRate) {
    this.frameRate = frameRate;
  }

  public int getGopSize() {
    return gopSize;
  }

  public void setGopSize(int gopSize) {
    this.gopSize = gopSize;
  }

  public int getBitRate() {
    return bitRate;
  }

  public void setBitRate(int bitRate) {
    this.bitRate = bitRate;
  }
}