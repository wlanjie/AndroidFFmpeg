package com.wlanjie.streaming.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.wlanjie.streaming.camera.CameraView;
import com.wlanjie.streaming.camera.EglCore;
import com.wlanjie.streaming.camera.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wlanjie on 2017/6/24.
 */
public class VideoRenderer implements GLSurfaceView.Renderer {

  private EglCore mEglCore;
  private int mSurfaceWidth;
  private int mSurfaceHeight;
  private ByteBuffer mFrameBuffer;
  private float[] mProjectionMatrix = new float[16];
  private float[] mSurfaceMatrix = new float[16];
  private float[] mTransformMatrix = new float[16];
  private SurfaceTexture mSurfaceTexture;
  private int mSurfaceTextureId;
  private Handler mHandler;
  private OnFrameListener mOnFrameListener;

  public VideoRenderer(Context context) {
    mEglCore = new EglCore(context.getResources());
    mSurfaceTextureId = OpenGLUtils.getExternalOESTextureID();
    mSurfaceTexture = new SurfaceTexture(mSurfaceTextureId);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glDisable(GL10.GL_DITHER);
    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

    mEglCore.init();

    HandlerThread thread = new HandlerThread("glDraw");
    thread.start();
    mHandler = new Handler(thread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        super.handleMessage(msg);
        IntBuffer buffer = mEglCore.getRgbaBuffer();
        mFrameBuffer.asIntBuffer().put(buffer.array());
        if (mOnFrameListener != null) {
          mOnFrameListener.onFrame(mFrameBuffer.array());
        }
      }
    };
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    mSurfaceWidth = width;
    mSurfaceHeight = height;
    mFrameBuffer = ByteBuffer.allocate(width * height * 4);
    mEglCore.onInputSizeChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
    mEglCore.onDisplaySizeChange(width, height);

    float outputAspectRatio = width > height ? (float) width / height : (float) height / width;
    float aspectRatio = outputAspectRatio / outputAspectRatio;
    if (width > height) {
      Matrix.orthoM(mProjectionMatrix, 0, -1.0f, 1.0f, -aspectRatio, aspectRatio, -1.0f, 1.0f);
    } else {
      Matrix.orthoM(mProjectionMatrix, 0, -aspectRatio, aspectRatio, -1.0f, 1.0f, -1.0f, 1.0f);
    }
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    mSurfaceTexture.updateTexImage();
    mSurfaceTexture.getTransformMatrix(mSurfaceMatrix);
    Matrix.multiplyMM(mTransformMatrix, 0, mSurfaceMatrix, 0, mProjectionMatrix, 0);
    mEglCore.setTextureTransformMatrix(mTransformMatrix);
    mEglCore.onDrawFrame(mSurfaceTextureId);
    mHandler.sendEmptyMessage(0);
  }

  public SurfaceTexture getSurfaceTexture() {
    return mSurfaceTexture;
  }

  public int getSurfaceWidth() {
    return mSurfaceWidth;
  }

  public int getSurfaceHeight() {
    return mSurfaceHeight;
  }

  public void destroy() {
    mEglCore.destroy();
  }

  public void setOnFrameListener(OnFrameListener l) {
    mOnFrameListener = l;
  }

  public interface OnFrameListener {
    void onFrame(byte[] rgba);
  }
}
