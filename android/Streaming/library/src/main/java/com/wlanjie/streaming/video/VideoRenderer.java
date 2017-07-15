package com.wlanjie.streaming.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.wlanjie.streaming.camera.CameraFacingId;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.setting.StreamingSetting;
import com.wlanjie.streaming.util.OpenGLUtils;
import com.wlanjie.streaming.util.Rotation;
import com.wlanjie.streaming.util.TextureRotationUtil;

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
  public static final int CENTER_INSIDE = 0, CENTER_CROP = 1, FIT_XY = 2;
  private int mScaleType = CENTER_CROP;
  private final float CUBE[] = {
      -1.0f, 1.0f,
      -1.0f, -1.0f,
      1.0f, 1.0f,
      1.0f, -1.0f
  };

  private Context mContext;
  private Effect mEffect;
  private int mSurfaceWidth;
  private int mSurfaceHeight;
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
  FloatBuffer mRecordCubeBuffer;
  FloatBuffer mRecordTextureBuffer;
  private VideoEncoder mVideoEncoder;
  private RendererVideoEncoder mRendererVideoEncoder;

  public VideoRenderer(Context context) {
    mContext = context;
    mEffect = new Effect(context.getResources());
    mSurfaceTextureId = OpenGLUtils.getExternalOESTextureID();
    mSurfaceTexture = new SurfaceTexture(mSurfaceTextureId);
    mRendererScreen = new RendererScreen(context);

    mCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

    mRecordCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();

    mRecordTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
  }

  public void setStreamingSetting(StreamingSetting streamingSetting) {
    mStreamingSetting = streamingSetting;
  }

  public void setCameraSetting(CameraSetting cameraSetting) {
    mCameraSetting = cameraSetting;
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
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

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    mSurfaceWidth = width;
    mSurfaceHeight = height;
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

//    adjustSize(width, height, cameraWidth, cameraHeight,
//        mCameraSetting.getDisplayOrientation(),
//        mCameraSetting.getFacing() == CameraFacingId.CAMERA_FACING_FRONT,
//        mCameraSetting.getFacing() == CameraFacingId.CAMERA_FACING_BACK, mCubeBuffer, mTextureBuffer);
//
//    adjustSize(width, height, mStreamingSetting.getVideoWidth(), mStreamingSetting.getVideoHeight(),
//        mCameraSetting.getDisplayOrientation(),
//        mCameraSetting.getFacing() == CameraFacingId.CAMERA_FACING_FRONT,
//        mCameraSetting.getFacing() == CameraFacingId.CAMERA_FACING_BACK, mRecordCubeBuffer, mRecordTextureBuffer);
    mTextureBuffer.clear();
    mTextureBuffer.put(resetTextureCord(width, height, cameraWidth, cameraHeight)).position(0);
    mEffect.onInputSizeChanged(cameraWidth, cameraHeight);
    GLES20.glViewport(0, 0, width, height);

    mRendererScreen.setDisplaySize(width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    mSurfaceTexture.updateTexImage();
    mSurfaceTexture.getTransformMatrix(mSurfaceMatrix);
    mEffect.setTextureTransformMatrix(mSurfaceMatrix);
    int textureId = mEffect.drawToFboTexture(mSurfaceTextureId);
    mRendererScreen.draw(textureId, mCubeBuffer, mTextureBuffer);
    if (mHandler != null) {
      mHandler.sendEmptyMessage(SOFT_ENCODER_MESSAGE);
    }
    if (mRendererVideoEncoder != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        mRendererVideoEncoder.drawEncoder(textureId, mVideoEncoder, mCubeBuffer, mTextureBuffer);
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

  private void adjustSize(int width, int height, int inputWidth, int inputHeight,
                               int rotation, boolean flipHorizontal, boolean flipVertical,
                               FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
    float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
        flipHorizontal, flipVertical);
    float[] cube = TextureRotationUtil.CUBE;
    float ratio1 = (float) width / inputWidth;
    float ratio2 = (float) height / inputHeight;
    float ratioMax = Math.max(ratio1, ratio2);
    int imageWidthNew = Math.round(inputWidth * ratioMax);
    int imageHeightNew = Math.round(inputHeight * ratioMax);

    float ratioWidth = imageWidthNew / (float) width;
    float ratioHeight = imageHeightNew / (float) height;

    switch (mScaleType) {
      case CENTER_INSIDE:
        cube = new float[]{
            TextureRotationUtil.CUBE[0] / ratioHeight, TextureRotationUtil.CUBE[1] / ratioWidth,
            TextureRotationUtil.CUBE[2] / ratioHeight, TextureRotationUtil.CUBE[3] / ratioWidth,
            TextureRotationUtil.CUBE[4] / ratioHeight, TextureRotationUtil.CUBE[5] / ratioWidth,
            TextureRotationUtil.CUBE[6] / ratioHeight, TextureRotationUtil.CUBE[7] / ratioWidth,
        };
        break;
      case CENTER_CROP:
        float distHorizontal = (1 - 1 / ratioWidth) / 2;
        float distVertical = (1 - 1 / ratioHeight) / 2;
        textureCords = new float[]{
            addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
            addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
            addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
            addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
        };
        break;
    }
    cubeBuffer.clear();
    cubeBuffer.put(cube).position(0);
    textureBuffer.clear();
    textureBuffer.put(textureCords).position(0);
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

  private float addDistance(float coordinate, float distance) {
    return coordinate == 0.0f ? distance : 1 - distance;
  }
}
