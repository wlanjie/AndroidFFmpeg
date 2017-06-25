package com.wlanjie.streaming.audio;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 2017/6/24.
 */
public interface OnAudioEncoderListener {
  void onAudioEncode(ByteBuffer bb, MediaCodec.BufferInfo bi);
}
