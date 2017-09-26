package com.wlanjie.ffmpeg.setting;

import android.media.AudioFormat;
import android.media.MediaCodecInfo;

/**
 * Created by wlanjie on 2017/6/14.
 */
public class AudioSetting {
  private int bitRate = 32 * 1000;
  private int sampleRate = 44100;
  private int channelCount = 1;
  private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
  private boolean aec = false;

  public boolean isAec() {
    return aec;
  }

  public void setAec(boolean aec) {
    this.aec = aec;
  }

  public int getBitRate() {
    return bitRate;
  }

  public void setBitRate(int bitRate) {
    this.bitRate = bitRate;
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
}
