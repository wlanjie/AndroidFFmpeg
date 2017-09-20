package com.wlanjie.ffmpeg.audio;

/**
 * Created by wlanjie on 2017/6/25.
 */
public interface OnAudioRecordListener {
  void onAudioRecord(byte[] buffer, int size);
}
