package com.wlanjie.streaming;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.text.TextUtils;

import com.wlanjie.streaming.audio.AudioEncoder;
import com.wlanjie.streaming.audio.AudioProcessor;
import com.wlanjie.streaming.audio.AudioUtils;
import com.wlanjie.streaming.audio.OnAudioEncoderListener;
import com.wlanjie.streaming.audio.OnAudioRecordListener;
import com.wlanjie.streaming.camera.Camera1;
import com.wlanjie.streaming.camera.Camera2;
import com.wlanjie.streaming.camera.Camera2Api23;
import com.wlanjie.streaming.camera.CameraCallback;
import com.wlanjie.streaming.camera.CameraViewImpl;
import com.wlanjie.streaming.rtmp.Rtmp;
import com.wlanjie.streaming.setting.AudioSetting;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.StreamingSetting;
import com.wlanjie.streaming.video.OpenH264Encoder;
import com.wlanjie.streaming.video.VideoRenderer;

import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 2017/6/25.
 */
public class MediaStreamingManager {

  private final GLSurfaceView mGLSurfaceView;
  private final VideoRenderer mVideoRenderer;
  private AudioSetting mAudioSetting;
  private CameraSetting mCameraSetting;
  private StreamingSetting mStreamingSetting;
  private AudioProcessor mAudioProcessor;
  private AudioEncoder mAudioEncoder;
  private CameraViewImpl mCameraViewImpl;
  private CallbackBridge mCallbacks = new CallbackBridge();
  private Encoder mEncoder;
  private byte[] aac = new byte[1024 * 1024];

  private long mPresentTimeUs;

  public MediaStreamingManager(final GLSurfaceView glSurfaceView) {
    mGLSurfaceView = glSurfaceView;
    mEncoder = new SoftEncoder(new Encoder.Builder());
    mVideoRenderer = new VideoRenderer(glSurfaceView.getContext());
    mVideoRenderer.getSurfaceTexture().setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
      @Override
      public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        glSurfaceView.requestRender();
      }
    });
  }

  public void prepare(CameraSetting cameraSetting, StreamingSetting streamingSetting, AudioSetting audioSetting) {
    mCameraSetting = cameraSetting;
    mStreamingSetting = streamingSetting;
    mAudioSetting = audioSetting;
    mAudioProcessor = new AudioProcessor(AudioUtils.getAudioRecord(audioSetting), audioSetting);

    mGLSurfaceView.setEGLContextClientVersion(2);
    mGLSurfaceView.setRenderer(mVideoRenderer);
    mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    if (Build.VERSION.SDK_INT < 21) {
      mCameraViewImpl = new Camera1(mCallbacks);
    } else if (Build.VERSION.SDK_INT < 23) {
      mCameraViewImpl = new Camera2(mCallbacks, mGLSurfaceView.getContext());
    } else {
      mCameraViewImpl = new Camera2Api23(mCallbacks, mGLSurfaceView.getContext());
    }
    mCameraViewImpl.setPreviewSurface(mVideoRenderer.getSurfaceTexture());
  }

  /**
   * open camera
   */
  public void resume() {
    mCameraViewImpl.setSize(mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
    mCameraViewImpl.start();
    mCameraViewImpl.startPreview(mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight());
  }

  /**
   * stop camera
   */
  public void pause() {
    mCameraViewImpl.stop();
  }

  public void startStreaming() {
    if (mStreamingSetting == null) {
      throw new IllegalArgumentException("StreamingSetting is null.");
    }
    if (TextUtils.isEmpty(mStreamingSetting.getRtmpUrl()) || !mStreamingSetting.getRtmpUrl().startsWith("rtmp://")) {
      throw new IllegalArgumentException("url must be rtmp://");
    }
    mPresentTimeUs = System.nanoTime() / 1000;
    OpenH264Encoder.setFrameSize(mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight());
    OpenH264Encoder.openEncoder();

    mVideoRenderer.setOnFrameListener(new VideoRenderer.OnFrameListener() {
      @Override
      public void onFrame(byte[] rgba) {
        OpenH264Encoder.encode(rgba, mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight(), System.nanoTime() - mPresentTimeUs);
      }
    });

    mAudioProcessor.start();
    mAudioProcessor.setOnAudioRecordListener(new OnAudioRecordListener() {
      @Override
      public void onAudioRecord(byte[] buffer, int size) {
        if (true) {
          mEncoder.convertPcmToAac(buffer, size);
        } else {
          if (mAudioEncoder == null) {
            mAudioEncoder = new AudioEncoder();
            mAudioEncoder.setOnAudioEncoderListener(new OnAudioEncoderListener() {
              @Override
              public void onAudioEncode(ByteBuffer bb, MediaCodec.BufferInfo bi) {
                int outBitSize = bi.size;
                int outPacketSize = outBitSize + 7;
                bb.position(bi.offset);
                bb.limit(bi.offset + outBitSize);
                mEncoder.addADTStoPacket(aac, outPacketSize);
                bb.get(aac, 7, outBitSize);
                bb.position(bi.offset);
                mEncoder.muxerAac(aac, outPacketSize, (int) (bi.presentationTimeUs / 1000));
              }
            });
            mAudioEncoder.offerEncoder(buffer);
          }
        }
      }
    });

    int result = Rtmp.connect(mStreamingSetting.getRtmpUrl());
    if (result != 0) {
      throw new RuntimeException("connect rtmp server error.");
    }
    new Thread(){
      @Override
      public void run() {
        super.run();
        Rtmp.startPublish();
      }
    }.start();
  }

  class CallbackBridge implements CameraCallback {

    @Override
    public void onCameraOpened(int previewWidth, int previewHeight) {

    }

    @Override
    public void onCameraClosed() {

    }

    @Override
    public void onPreviewFrame(byte[] data) {

    }

    @Override
    public void onPreview(int previewWidth, int previewHeight) {

    }
  }
}
