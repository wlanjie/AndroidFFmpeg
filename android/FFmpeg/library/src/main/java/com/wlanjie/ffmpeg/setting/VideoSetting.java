package com.wlanjie.ffmpeg.setting;
 
/** 
 * Created by wlanjie on 2017/6/14. 
 */ 
public class VideoSetting {
 
  private String rtmpUrl;
  private int fps = 15;
  private int videoWidth = 360;
  private int videoHeight = 640;
  private int maxBps = 500;
  private int minBps;
  private int ifi = 2;
  private EncoderType mEncoderType = EncoderType.SOFT;
 
  public String getRtmpUrl() {
    return rtmpUrl;
  } 
 
  public VideoSetting setRtmpUrl(String rtmpUrl) {
    this.rtmpUrl = rtmpUrl;
    return this;
  } 
 
  public int getFps() { 
    return fps;
  } 
 
  public VideoSetting setFps(int fps) {
    this.fps = fps;
    return this;
  } 
 
  public int getVideoWidth() { 
    return videoWidth;
  } 
 
  public VideoSetting setVideoWidth(int videoWidth) {
    this.videoWidth = videoWidth;
    return this;
  } 
 
  public int getVideoHeight() { 
    return videoHeight;
  } 
 
  public VideoSetting setVideoHeight(int videoHeight) {
    this.videoHeight = videoHeight;
    return this;
  } 
 
  public int getMaxBps() { 
    return maxBps;
  } 
 
  public VideoSetting setMaxBps(int maxBps) {
    this.maxBps = maxBps;
    return this;
  } 
 
  public int getMinBps() { 
    return minBps;
  } 
 
  public VideoSetting setMinBps(int minBps) {
    this.minBps = minBps;
    return this;
  } 
 
  public int getIfi() { 
    return ifi;
  } 
 
  public VideoSetting setIfi(int ifi) {
    this.ifi = ifi;
    return this;
  }

  public EncoderType getEncoderType() {
    return mEncoderType;
  }

  public void setEncoderType(EncoderType mEncoderType) {
    this.mEncoderType = mEncoderType;
  }
}