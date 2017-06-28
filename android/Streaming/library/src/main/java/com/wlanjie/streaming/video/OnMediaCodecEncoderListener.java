package com.wlanjie.streaming.video;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by caowu15 on 2017/6/28.
 */

public interface OnMediaCodecEncoderListener {
  void onEncode(ByteBuffer buffer, MediaCodec.BufferInfo info);
}
