package com.wlanjie.streaming.video;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.wlanjie.streaming.R;
import com.wlanjie.streaming.util.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by wlanjie on 2017/5/25.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RendererVideoEncoder {

  private final FloatBuffer mCubeBuffer;
  private final FloatBuffer mTextureBuffer;

  // screen
  private int mProgramId;
  private int mPosition;
  private int mUniformTexture;
  private int mTextureCoordinate;
  private int mVideoWidth;
  private int mVideoHeight;
  private Resources mResources;

  private EGLDisplay mSavedEglDisplay     = null;
  private EGLSurface mSavedEglDrawSurface = null;
  private EGLSurface mSavedEglReadSurface = null;
  private EGLContext mSavedEglContext     = null;

  public RendererVideoEncoder(Context context) {
    mResources = context.getResources();
    mCubeBuffer = ByteBuffer.allocateDirect(OpenGLUtils.CUBE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mCubeBuffer.put(OpenGLUtils.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mTextureBuffer.put(OpenGLUtils.TEXTURE).position(0);
  }

  public void init() {
    mProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_default), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_default));
    mPosition = GLES20.glGetAttribLocation(mProgramId, "position");
    mUniformTexture = GLES20.glGetUniformLocation(mProgramId, "inputImageTexture");
    mTextureCoordinate = GLES20.glGetAttribLocation(mProgramId, "inputTextureCoordinate");
  }

  public void setVideoSize(int width, int height) {
    mVideoWidth = width;
    mVideoHeight = height;
  }

  public void drawEncoder(int textureId, VideoEncoder encoder, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
    if (encoder != null) {
      saveRenderState();
      if (encoder.firstTimeSetup()) {
        encoder.startEncoder();
        encoder.makeCurrent();
        init();
      } else {
        encoder.makeCurrent();
      }
      GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
      GLES20.glUseProgram(mProgramId);

      GLES20.glEnableVertexAttribArray(mPosition);
      GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);

      GLES20.glEnableVertexAttribArray(mTextureCoordinate);
      GLES20.glVertexAttribPointer(mTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
      GLES20.glUniform1i(mUniformTexture, 0);

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

      GLES20.glDisableVertexAttribArray(mPosition);
      GLES20.glDisableVertexAttribArray(mTextureCoordinate);

      encoder.swapBuffers();
      restoreRenderState();
    }
  }

  public void destroy() {
    GLES20.glDeleteProgram(mProgramId);
  }

  private void saveRenderState() {
    mSavedEglDisplay     = EGL14.eglGetCurrentDisplay();
    mSavedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
    mSavedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
    mSavedEglContext     = EGL14.eglGetCurrentContext();
  }

  private void restoreRenderState() {
    if (!EGL14.eglMakeCurrent(
        mSavedEglDisplay,
        mSavedEglDrawSurface,
        mSavedEglReadSurface,
        mSavedEglContext)) {
      throw new RuntimeException("eglMakeCurrent failed");
    }
  }

  public void updateTextureCoordinate(float[] textureCords) {
    mTextureBuffer.clear();
    mTextureBuffer.put(textureCords).position(0);
  }
}
