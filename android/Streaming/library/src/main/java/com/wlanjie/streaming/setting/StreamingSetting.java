package com.wlanjie.streaming.setting; 
 
/** 
 * Created by wlanjie on 2017/6/14. 
 */ 
public class StreamingSetting { 
 
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
 
  public StreamingSetting setRtmpUrl(String rtmpUrl) {
    this.rtmpUrl = rtmpUrl;
    return this;
  } 
 
  public int getFps() { 
    return fps;
  } 
 
  public StreamingSetting setFps(int fps) {
    this.fps = fps;
    return this;
  } 
 
  public int getVideoWidth() { 
    return videoWidth;
  } 
 
  public StreamingSetting setVideoWidth(int videoWidth) {
    this.videoWidth = videoWidth;
    return this;
  } 
 
  public int getVideoHeight() { 
    return videoHeight;
  } 
 
  public StreamingSetting setVideoHeight(int videoHeight) {
    this.videoHeight = videoHeight;
    return this;
  } 
 
  public int getMaxBps() { 
    return maxBps;
  } 
 
  public StreamingSetting setMaxBps(int maxBps) {
    this.maxBps = maxBps;
    return this;
  } 
 
  public int getMinBps() { 
    return minBps;
  } 
 
  public StreamingSetting setMinBps(int minBps) {
    this.minBps = minBps;
    return this;
  } 
 
  public int getIfi() { 
    return ifi;
  } 
 
  public StreamingSetting setIfi(int ifi) {
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