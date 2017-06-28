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
import com.wlanjie.streaming.util.TextureRotationUtil;
import com.wlanjie.streaming.util.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by wlanjie on 2017/5/25.
 */

public class RendererScreen {

  private final FloatBuffer mCubeBuffer;
  private final FloatBuffer mTextureBuffer;

  // screen
  private int mScreenProgramId;
  private int mScreenPosition;
  private int mScreenUniformTexture;
  private int mScreenTextureCoordinate;
  private int mWidth;
  private int mHeight;
  private int mVideoWidth;
  private int mVideoHeight;
  private Resources mResources;

  private EGLDisplay mSavedEglDisplay     = null;
  private EGLSurface mSavedEglDrawSurface = null;
  private EGLSurface mSavedEglReadSurface = null;
  private EGLContext mSavedEglContext     = null;

  public RendererScreen(Context context) {
    mResources = context.getResources();
    mCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer();
    mCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer();
    mTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
  }

  public void init() {
    mScreenProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_default), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_default));
    mScreenPosition = GLES20.glGetAttribLocation(mScreenProgramId, "position");
    mScreenUniformTexture = GLES20.glGetUniformLocation(mScreenProgramId, "inputImageTexture");
    mScreenTextureCoordinate = GLES20.glGetAttribLocation(mScreenProgramId, "inputTextureCoordinate");
  }

  public void setVideoSize(int width, int height) {
    mVideoWidth = width;
    mVideoHeight = height;
  }

  public void setDisplaySize(int width, int height) {
    mWidth = width;
    mHeight = height;
  }
//
//  public void drawEncoder(int textureId, Encoder encoder, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
//    if (encoder != null) {
//      saveRenderState();
//      if (encoder.firstTimeSetup()) {
//        encoder.startEncoder();
//        encoder.makeCurrent();
//        init();
//      } else {
//        encoder.makeCurrent();
//      }
//      GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
//      GLES20.glUseProgram(mScreenProgramId);
//
//      GLES20.glEnableVertexAttribArray(mScreenPosition);
//      GLES20.glVertexAttribPointer(mScreenPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);
//
//      GLES20.glEnableVertexAttribArray(mScreenTextureCoordinate);
//      GLES20.glVertexAttribPointer(mScreenTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);
//
//      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
//      GLES20.glUniform1i(mScreenUniformTexture, 0);
//
//      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//
//      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//
//      GLES20.glDisableVertexAttribArray(mScreenPosition);
//      GLES20.glDisableVertexAttribArray(mScreenTextureCoordinate);
//
//      encoder.swapBuffers();
//      restoreRenderState();
//    }
//  }

  public void draw(int textureId, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
    GLES20.glViewport(0, 0, mWidth, mHeight);
    GLES20.glUseProgram(mScreenProgramId);

    GLES20.glEnableVertexAttribArray(mScreenPosition);
    GLES20.glVertexAttribPointer(mScreenPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);

    GLES20.glEnableVertexAttribArray(mScreenTextureCoordinate);
    GLES20.glVertexAttribPointer(mScreenTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
    GLES20.glUniform1i(mScreenUniformTexture, 0);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    GLES20.glDisableVertexAttribArray(mScreenPosition);
    GLES20.glDisableVertexAttribArray(mScreenTextureCoordinate);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
  }

  public void draw(int textureId) {
    draw(textureId, mCubeBuffer, mTextureBuffer);
  }

  public void destroy() {
    GLES20.glDeleteProgram(mScreenProgramId);
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
  private void saveRenderState() {
    mSavedEglDisplay     = EGL14.eglGetCurrentDisplay();
    mSavedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
    mSavedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
    mSavedEglContext     = EGL14.eglGetCurrentContext();
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
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
