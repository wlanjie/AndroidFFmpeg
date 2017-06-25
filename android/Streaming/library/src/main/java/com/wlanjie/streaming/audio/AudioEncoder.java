package com.wlanjie.streaming.audio;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.wlanjie.streaming.setting.AudioSetting;

import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 2017/6/24.
 */
public class AudioEncoder {

  private final static String MIME = "audio/mp4a-latm";
  private final static int AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
  private final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
  private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
  private OnAudioEncoderListener mOnAudioEncoderListener;

  private AudioSetting mAudioSetting;
  private MediaCodec mMediaCodec;

  public AudioEncoder() {
  }

  public void setOnAudioEncoderListener(OnAudioEncoderListener l) {
    mOnAudioEncoderListener = l;
  }

  public void start(AudioSetting audioSetting) {
    mAudioSetting = audioSetting;
    mMediaCodec = getAudioMediaCodec();
    mMediaCodec.start();
  }

  public synchronized void stop() {
    if (mMediaCodec != null) {
      mMediaCodec.stop();
      mMediaCodec.release();
      mMediaCodec = null;
    }
  }

  public synchronized void offerEncoder(byte[] input) {
    if (mMediaCodec == null) {
      return;
    }
    ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
    ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(12000);
    if (inputBufferIndex >= 0) {
      ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
      inputBuffer.clear();
      inputBuffer.put(input);
      mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
    }

    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 12000);
    while (outputBufferIndex >= 0) {
      ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
      if (mOnAudioEncoderListener != null) {
        mOnAudioEncoderListener.onAudioEncode(outputBuffer, mBufferInfo);
      }
      mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
      outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
    }
  }

  private MediaCodec getAudioMediaCodec() {
    MediaFormat format = MediaFormat.createAudioFormat(MIME, mAudioSetting.getSampleRate(), mAudioSetting.getChannelCount());
    format.setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE);
    format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioSetting.getMaxBps() * 1024);
    format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mAudioSetting.getSampleRate());
    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioUtils.getRecordBufferSize(mAudioSetting.getChannelCount(), mAudioSetting.getSampleRate()));
    format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mAudioSetting.getChannelCount());

    MediaCodec mediaCodec = null;
    try {
      mediaCodec = MediaCodec.createEncoderByType(MIME);
      mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    } catch (Exception e) {
      e.printStackTrace();
      if (mediaCodec != null) {
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
      }
    }
    return mediaCodec;
  }
}
