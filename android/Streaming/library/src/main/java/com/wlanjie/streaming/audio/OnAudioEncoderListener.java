package com.wlanjie.streaming.audio;

/**
 * Created by wlanjie on 2017/6/24.
 */
public interface OnAudioEncoderListener {
  void onAudioEncode(byte[] data, int size, long timeUs);
}
