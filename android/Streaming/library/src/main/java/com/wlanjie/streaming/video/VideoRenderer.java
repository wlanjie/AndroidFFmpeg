package com.wlanjie.streaming.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.setting.StreamingSetting;
import com.wlanjie.streaming.util.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wlanjie on 2017/6/24.
 */
public class VideoRenderer implements GLSurfaceView.Renderer {
  private static final int SOFT_ENCODER_MESSAGE = 0;

  private Context mContext;
  private Effect mEffect;
  private ByteBuffer mFrameBuffer;
  private float[] mSurfaceMatrix = new float[16];
  private SurfaceTexture mSurfaceTexture;
  private int mSurfaceTextureId;
  private Handler mHandler;
  private HandlerThread mEncoderThread;
  private OnFrameListener mOnFrameListener;
  private StreamingSetting mStreamingSetting;
  private CameraSetting mCameraSetting;
  private final RendererScreen mRendererScreen;
  private FloatBuffer mCubeBuffer;
  private FloatBuffer mTextureBuffer;
  private FloatBuffer mRecordTextureBuffer;
  private VideoEncoder mVideoEncoder;
  private RendererVideoEncoder mRendererVideoEncoder;
  private SurfaceTextureCallback mSurfaceTextureCallback;

  public VideoRenderer(Context context) {
    mContext = context;
    mEffect = new Effect(context.getResources());
    mSurfaceTextureId = OpenGLUtils.getExternalOESTextureID();
    mSurfaceTexture = new SurfaceTexture(mSurfaceTextureId);
    mRendererScreen = new RendererScreen(context);

    mCubeBuffer = ByteBuffer.allocateDirect(OpenGLUtils.CUBE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mCubeBuffer.put(OpenGLUtils.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mTextureBuffer.put(OpenGLUtils.TEXTURE).position(0);

    mRecordTextureBuffer = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
  }

  public void setStreamingSetting(StreamingSetting streamingSetting) {
    mStreamingSetting = streamingSetting;
  }

  public void setCameraSetting(CameraSetting cameraSetting) {
    mCameraSetting = cameraSetting;
  }

  public void setSurfaceTextureCallback(SurfaceTextureCallback callback) {
    mSurfaceTextureCallback = callback;
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    if (mSurfaceTextureCallback != null) {
      mSurfaceTextureCallback.onSurfaceCreated();
    }
    GLES20.glDisable(GL10.GL_DITHER);
    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

    mRendererScreen.init();
    mEffect.init();

  }

  public void startEncoder() {
    mEffect.setVideoSize(mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight());
    initEncoder();
  }

  public void setOnMediaCodecEncoderListener(OnMediaCodecEncoderListener l) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      mVideoEncoder.setOnMediaCodecEncoderListener(l);
    }
  }

  public void stopEncoder() {
    if (mHandler != null) {
      mHandler.removeMessages(SOFT_ENCODER_MESSAGE);
    }
    if (mEncoderThread != null) {
      mEncoderThread.getLooper().quit();
      mEncoderThread.interrupt();
      mEncoderThread.quit();
      mEncoderThread = null;
    }
    if (mVideoEncoder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      mVideoEncoder.stopEncoder();
    }
  }

  private void initEncoder() {
    if (mStreamingSetting.getEncoderType() == EncoderType.SOFT) {
      mEncoderThread = new HandlerThread("glDraw");
      mEncoderThread.start();
      mHandler = new Handler(mEncoderThread.getLooper()) {
        @Override
        public void handleMessage(Message msg) {
          super.handleMessage(msg);
          IntBuffer buffer = mEffect.getRgbaBuffer();
          mFrameBuffer.asIntBuffer().put(buffer.array());
          if (mOnFrameListener != null) {
            mOnFrameListener.onFrame(mFrameBuffer.array());
          }
        }
      };
    } else {
      mVideoEncoder = new VideoEncoder();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        mVideoEncoder.prepareEncoder(mStreamingSetting);
        mRendererVideoEncoder = new RendererVideoEncoder(mContext);
        mRendererVideoEncoder.init();
        mRendererVideoEncoder.setVideoSize(mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight());
      }
    }
  }

  private int getCameraWidth() {
    int previewWidth = mCameraSetting.getPreviewWidth();
    int previewHeight = mCameraSetting.getPreviewHeight();
    int cameraWidth;
    if(false) {
      // 横屏
      cameraWidth = Math.max(previewWidth, previewHeight);
    } else {
      cameraWidth = Math.min(previewWidth, previewHeight);
    }
    return cameraWidth;
  }

  private int getCameraHeight() {
    int previewWidth = mCameraSetting.getPreviewWidth();
    int previewHeight = mCameraSetting.getPreviewHeight();
    int cameraHeight;
    if(false) {
      // 横屏
      cameraHeight = Math.min(previewWidth, previewHeight);
    } else {
      cameraHeight = Math.max(previewWidth, previewHeight);
    }
    return cameraHeight;
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    if (mSurfaceTextureCallback != null) {
      mSurfaceTextureCallback.onSurfaceChanged(width, height);
    }
    mFrameBuffer = ByteBuffer.allocate(width * height * 4);

    int previewWidth = mCameraSetting.getPreviewWidth();
    int previewHeight = mCameraSetting.getPreviewHeight();
    int cameraWidth;
    int cameraHeight;
    if(false) {
      // 横屏
      cameraWidth = Math.max(previewWidth, previewHeight);
      cameraHeight = Math.min(previewWidth, previewHeight);
    } else {
      cameraWidth = Math.min(previewWidth, previewHeight);
      cameraHeight = Math.max(previewWidth, previewHeight);
    }

    mEffect.onInputSizeChanged(cameraWidth, cameraHeight);
    GLES20.glViewport(0, 0, width, height);

    mTextureBuffer.clear();
    mTextureBuffer.put(resetTextureCord(width, height, cameraWidth, cameraHeight)).position(0);

    mRecordTextureBuffer.clear();
    mRecordTextureBuffer.put(resetTextureCord(mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight(), cameraWidth, cameraHeight)).position(0);
    mRendererScreen.setDisplaySize(width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    mSurfaceTexture.updateTexImage();
    mSurfaceTexture.getTransformMatrix(mSurfaceMatrix);
    mEffect.setTextureTransformMatrix(mSurfaceMatrix);
    int textureId;
    if (mSurfaceTextureCallback != null) {
      textureId = mSurfaceTextureCallback.onDrawFrame(mSurfaceTextureId, getCameraWidth(), getCameraHeight(), mSurfaceMatrix);
      if (textureId <= 0) {
        textureId = mEffect.drawToFboTexture(mSurfaceTextureId);
      } else {
        mEffect.readPixel(textureId);
      }
    } else {
      textureId = mEffect.drawToFboTexture(mSurfaceTextureId);
      mRendererScreen.draw(textureId, mCubeBuffer, mTextureBuffer);
    }
    if (mHandler != null) {
      mHandler.sendEmptyMessage(SOFT_ENCODER_MESSAGE);
    }
    if (mRendererVideoEncoder != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        mRendererVideoEncoder.drawEncoder(textureId, mVideoEncoder, mCubeBuffer, mRecordTextureBuffer);
      }
    }
  }

  public SurfaceTexture getSurfaceTexture() {
    return mSurfaceTexture;
  }

  public void destroy() {
    mEffect.destroy();
    mRendererScreen.destroy();
    if (mRendererVideoEncoder != null) {
      mRendererVideoEncoder.destroy();
    }
  }

  public void setOnFrameListener(OnFrameListener l) {
    mOnFrameListener = l;
  }

  public interface OnFrameListener {
    void onFrame(byte[] rgba);
  }

  private float[] resetTextureCord(int width, int height, int inputWidth, int inputHeight) {
    float hRatio = width / ((float) inputWidth);
    float vRatio = height / ((float) inputHeight);
    float ratio;
    if (hRatio > vRatio) {
      ratio = height / (inputHeight * hRatio);
      return new float[]{
          0.0f, 0.5f + ratio / 2,
          0.0f, 0.5f - ratio / 2,
          1.0f, 0.5f + ratio / 2,
          1.0f, 0.5f - ratio / 2
      };
    } else {
      ratio = width / (inputWidth * vRatio);
      return new float[] {
          0.5f - ratio / 2, 1.0f,
          0.5f - ratio / 2, 0.0f,
          0.5f + ratio / 2, 1.0f,
          0.5f + ratio / 2, 0.0f
      };
    }
  }
}
