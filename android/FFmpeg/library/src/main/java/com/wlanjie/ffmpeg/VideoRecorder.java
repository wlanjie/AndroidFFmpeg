package com.wlanjie.ffmpeg;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.Build;

import com.wlanjie.ffmpeg.audio.AudioProcessor;
import com.wlanjie.ffmpeg.audio.AudioUtils;
import com.wlanjie.ffmpeg.audio.OnAudioRecordListener;
import com.wlanjie.ffmpeg.camera.Camera1;
import com.wlanjie.ffmpeg.camera.Camera2;
import com.wlanjie.ffmpeg.camera.CameraCallback;
import com.wlanjie.ffmpeg.camera.LivingCamera;
import com.wlanjie.ffmpeg.setting.AudioSetting;
import com.wlanjie.ffmpeg.setting.CameraSetting;
import com.wlanjie.ffmpeg.setting.VideoSetting;
import com.wlanjie.ffmpeg.video.VideoRenderer;

/**
 * Created by wlanjie on 2017/9/13.
 */
public class VideoRecorder {

  public final static int CAMERA_FACING_BACK = 0;
  public final static int CAMERA_FACING_FRONT = 1;
  private volatile boolean mIsRecording = false;
  private GLSurfaceView mGLSurfaceView;
  private CameraSetting mCameraSetting;
  private AudioSetting mAudioSetting;
  private VideoRenderer mVideoRenderer;
  private CallbackBridge mCallbacks = new CallbackBridge();
  private LivingCamera mCamera;
  private CameraCallback mCameraCallback;
  private AudioProcessor mAudioProcessor;

  public VideoRecorder(Context context) {
    mVideoRenderer = new VideoRenderer(context);
  }

  public void setCameraCallback(CameraCallback callback) {
    mCameraCallback = callback;
  }

  public void prepare(GLSurfaceView glSurfaceView, CameraSetting cameraSetting, AudioSetting audioSetting, VideoSetting videoSetting) {
    mGLSurfaceView = glSurfaceView;
    mCameraSetting = cameraSetting;
    mAudioSetting = audioSetting;
    mVideoRenderer.prepare(cameraSetting, videoSetting);
    mVideoRenderer.getSurfaceTexture().setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
      @Override
      public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mGLSurfaceView.requestRender();
      }
    });

    mGLSurfaceView.setEGLContextClientVersion(2);
    mGLSurfaceView.setRenderer(mVideoRenderer);
    mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    mAudioProcessor = new AudioProcessor(AudioUtils.getAudioRecord(audioSetting), audioSetting);

    cameraSetting.setPreviewWidth(mCameraSetting.getPreviewWidth());
    cameraSetting.setPreviewHeight(mCameraSetting.getPreviewHeight());
    cameraSetting.setSurfaceTexture(mVideoRenderer.getSurfaceTexture());
    if (Build.VERSION.SDK_INT < 21) {
      mCamera = new Camera1(mCallbacks, cameraSetting);
    } else {
      mCamera = new Camera2(mCallbacks, cameraSetting, mGLSurfaceView.getContext());
    }
  }

  public void onResume() {
    if (mCamera == null) {
      throw new IllegalStateException("must be call prepare.");
    }
    mCamera.setFacing(CAMERA_FACING_BACK);
    mCamera.start();
  }

  public void onPause() {
    mCamera.stop();
  }

  public void onDestroy() {
    mVideoRenderer.destroy();
    FFmpeg.getInstance().release();
  }

  public boolean isRecording() {
    return mIsRecording;
  }

  public void startRecorder(String filePath) {
    if (mIsRecording) {
      return;
    }
    FFmpeg.getInstance().openOutput(filePath);
    FFmpeg.getInstance().beginSection();
    mVideoRenderer.startEncoder();
    mVideoRenderer.setOnFrameListener(new VideoRenderer.OnFrameListener() {
      @Override
      public void onFrame(byte[] rgba) {
        int result = FFmpeg.getInstance().encoderVideo(rgba);
      }
    });
    mAudioProcessor.start();
    mAudioProcessor.setOnAudioRecordListener(new OnAudioRecordListener() {
      @Override
      public void onAudioRecord(byte[] buffer, int size) {
        FFmpeg.getInstance().encoderAudio(buffer);
      }
    });
    mIsRecording = true;
  }

  public void stopRecorder() {
    FFmpeg.getInstance().endSection();
    mAudioProcessor.stopEncode();
    mVideoRenderer.stopEncoder();
    mIsRecording = false;
  }

  class CallbackBridge implements CameraCallback {

    @Override
    public void onCameraOpened(int previewWidth, int previewHeight) {
      if (mCameraCallback != null) {
        mCameraCallback.onCameraOpened(previewWidth, previewHeight);
      }
    }

    @Override
    public void onCameraClosed() {
      if (mCameraCallback != null) {
        mCameraCallback.onCameraClosed();
      }
    }

    @Override
    public void onPreviewFrame(byte[] data) {
      if (mCameraCallback != null) {
        mCameraCallback.onPreviewFrame(data);
      }
    }
  }
}
