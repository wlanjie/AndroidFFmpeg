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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
  private List<String> mVideos = new LinkedList<>();

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

    FFmpeg.getInstance().setSetting(audioSetting, videoSetting);

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
    mAudioProcessor.destroy();
    FFmpeg.getInstance().release();
  }

  public boolean isRecording() {
    return mIsRecording;
  }

  public void startRecorder(String filePath) {
    if (mIsRecording) {
      return;
    }
    int result = FFmpeg.getInstance().openOutput(filePath);
    if (result != 0) {
      return;
    }
    result = FFmpeg.getInstance().beginSection();
    if (result != 0) {
      return;
    }
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
    mVideos.add(filePath);
  }

  public int compositeVideos(String composePath) {
    List<String> videos = new ArrayList<>();
    videos.add("/sdcard/a.mp4");
    videos.add("/sdcard/b.mp4");
    return FFmpeg.getInstance().composeVideos(mVideos, composePath);
  }

  public void stopRecorder() {
    mIsRecording = false;
    FFmpeg.getInstance().endSection();
    mAudioProcessor.stopEncode();
    mVideoRenderer.stopEncoder();
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
