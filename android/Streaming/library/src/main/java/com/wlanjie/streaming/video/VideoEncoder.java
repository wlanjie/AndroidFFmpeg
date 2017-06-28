package com.wlanjie.streaming.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;

import com.wlanjie.streaming.setting.StreamingSetting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by wlanjie on 2017/6/28.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoEncoder {
  private final static String MIME = "video/avc";
  private StreamingSetting mStreamingSetting;
  private InputSurface mInputSurface;
  private MediaCodec mMediaCodec;
  private HandlerThread mEncoderThread;
  private Handler mEncodeHandler;
  private MediaCodec.BufferInfo mBufferInfo;
  private boolean mIsStarted;
  private final ReentrantLock mEncoderLock = new ReentrantLock();
  private OnMediaCodecEncoderListener mEncoderListener;

  public void prepareEncoder(StreamingSetting streamingSetting) {
    mStreamingSetting = streamingSetting;
    if (mMediaCodec != null || mInputSurface != null) {
      throw new IllegalStateException("prepareEncoder already called.");
    }
    mMediaCodec = getMediaCodec();
    mEncoderThread = new HandlerThread("Encoder");
    mEncoderThread.start();
    mEncodeHandler = new Handler(mEncoderThread.getLooper());
    mBufferInfo = new MediaCodec.BufferInfo();
  }

  public void setOnMediaCodecEncoderListener(OnMediaCodecEncoderListener l) {
    mEncoderListener = l;
  }

  public boolean firstTimeSetup() {
    if (mMediaCodec == null || mInputSurface != null) {
      return false;
    }
    mInputSurface = new InputSurface(mMediaCodec.createInputSurface());
    mMediaCodec.start();
    return true;
  }

  public void makeCurrent() {
    mInputSurface.makeCurrent();
  }

  public void swapBuffers() {
    mInputSurface.swapBuffers();
    mInputSurface.setPresentationTime(System.nanoTime());
  }

  public void startEncoder() {
    mIsStarted = true;
    mEncodeHandler.post(mEncoderRunnable);
  }

  private final Runnable mEncoderRunnable = new Runnable() {
    @Override
    public void run() {
      encode();
    }
  };

  private void encode() {
    ByteBuffer[] outBuffers = mMediaCodec.getOutputBuffers();
    while (mIsStarted) {
      mEncoderLock.lock();
      int outBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 12000);
      if (outBufferIndex >= 0) {
        ByteBuffer buffer = outBuffers[outBufferIndex];
        if (mEncoderListener != null) {
          mEncoderListener.onEncode(buffer, mBufferInfo);
        }
      } else {
        SystemClock.sleep(10);
      }
      mEncoderLock.unlock();
    }
  }

  private MediaCodec getMediaCodec() {
    MediaFormat format = MediaFormat.createVideoFormat(MIME, mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight());
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    format.setInteger(MediaFormat.KEY_BIT_RATE, mStreamingSetting.getMaxBps() * 1024);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, mStreamingSetting.getFps());
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mStreamingSetting.getIfi());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
      format.setInteger(MediaFormat.KEY_COMPLEXITY, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
    }
    MediaCodec mediaCodec = null;
    try {
      mediaCodec = MediaCodec.createEncoderByType(MIME);
      mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    } catch (IOException e) {
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
