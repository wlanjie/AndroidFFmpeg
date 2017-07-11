package com.wlanjie.streaming.setting;

import android.media.AudioFormat;
import android.media.MediaCodecInfo;

/**
 * Created by wlanjie on 2017/6/14.
 */
public class AudioSetting {
  private int minBps = 32;
  private int maxBps = 64;
  private int sampleRate = 44100;
  private int channelCount = 2;
  private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
  private int aacProfile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;

  public boolean isAec() {
    return aec;
  }

  public void setAec(boolean aec) {
    this.aec = aec;
  }

  private boolean aec = false;

  public int getMinBps() {
    return minBps;
  }

  public void setMinBps(int minBps) {
    this.minBps = minBps;
  }

  public int getMaxBps() {
    return maxBps;
  }

  public void setMaxBps(int maxBps) {
    this.maxBps = maxBps;
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public int getChannelCount() {
    return channelCount;
  }

  public void setChannelCount(int channelCount) {
    this.channelCount = channelCount;
  }

  public int getAudioEncoding() {
    return audioEncoding;
  }

  public void setAudioEncoding(int audioEncoding) {
    this.audioEncoding = audioEncoding;
  }

  public int getAacProfile() {
    return aacProfile;
  }

  public void setAacProfile(int aacProfile) {
    this.aacProfile = aacProfile;
  }
}
