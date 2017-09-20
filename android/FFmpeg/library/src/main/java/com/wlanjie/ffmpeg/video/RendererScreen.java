package com.wlanjie.ffmpeg.video;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.GLES20;

import com.wlanjie.ffmpeg.library.R;
import com.wlanjie.ffmpeg.util.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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
  private int mInputWidth;
  private int mInputHeight;
  private int mFboId;
  private IntBuffer mFboBuffer;
  private Resources mResources;
  private final FloatBuffer mReadPixelTextureBuffer;
  private final float[] TEXTURE_BUFFER = {
      1.0f, 0.0f,
      1.0f, 1.0f,
      0.0f, 0.0f,
      0.0f, 1.0f
  };

  public RendererScreen(Context context) {
    mResources = context.getResources();
    mCubeBuffer = ByteBuffer.allocateDirect(OpenGLUtils.CUBE.length * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer();
    mCubeBuffer.put(OpenGLUtils.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE.length * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer();
    mTextureBuffer.put(OpenGLUtils.TEXTURE).position(0);

    mReadPixelTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_BUFFER.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mReadPixelTextureBuffer.put(TEXTURE_BUFFER).position(0);
  }

  public void init() {
    mScreenProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_default), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_default));
    mScreenPosition = GLES20.glGetAttribLocation(mScreenProgramId, "position");
    mScreenUniformTexture = GLES20.glGetUniformLocation(mScreenProgramId, "inputImageTexture");
    mScreenTextureCoordinate = GLES20.glGetAttribLocation(mScreenProgramId, "inputTextureCoordinate");
  }

  void setDisplaySize(int width, int height) {
    mWidth = width;
    mHeight = height;
  }

  void setInputSize(int width, int height) {
    if (mFboId != 0 && width != mInputWidth && height != mInputHeight) {
        destroyFboTexture();
    }
    mFboBuffer = IntBuffer.allocate(width * height);
    mInputHeight = height;
    mInputWidth = width;
    int[] fbo = new int[1];
    GLES20.glGenFramebuffers(1, fbo, 0);
    mFboId = fbo[0];
  }

  private void destroyFboTexture() {
    if (mFboId != 0) {
      GLES20.glDeleteFramebuffers(1, new int[] { mFboId }, 0);
    }
  }

  public void draw(int textureId, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
    GLES20.glUseProgram(mScreenProgramId);
    GLES20.glViewport(0, 0, mWidth, mHeight);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
    GLES20.glUniform1i(mScreenUniformTexture, 0);

    GLES20.glEnableVertexAttribArray(mScreenPosition);
    GLES20.glVertexAttribPointer(mScreenPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);
    GLES20.glEnableVertexAttribArray(mScreenTextureCoordinate);
    GLES20.glVertexAttribPointer(mScreenTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GLES20.glDisableVertexAttribArray(mScreenPosition);
    GLES20.glDisableVertexAttribArray(mScreenTextureCoordinate);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
  }

  public void readPixel(int textureId) {
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);

    GLES20.glUseProgram(mScreenProgramId);
    GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glUniform1i(mScreenUniformTexture, 0);

    GLES20.glEnableVertexAttribArray(mScreenPosition);
    GLES20.glVertexAttribPointer(mScreenPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, mCubeBuffer);
    GLES20.glEnableVertexAttribArray(mScreenTextureCoordinate);
    GLES20.glVertexAttribPointer(mScreenTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, mTextureBuffer);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GLES20.glReadPixels(0, 0, mInputWidth, mInputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mFboBuffer);
    GLES20.glDisableVertexAttribArray(mScreenPosition);
    GLES20.glDisableVertexAttribArray(mScreenTextureCoordinate);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
  }

  public IntBuffer getFboBuffer() {
    return mFboBuffer;
  }

  public void draw(int textureId) {
    draw(textureId, mCubeBuffer, mTextureBuffer);
  }

  public void destroy() {
    GLES20.glDeleteProgram(mScreenProgramId);
  }

  public void updateTextureCoordinate(float[] textureCords) {
    mTextureBuffer.clear();
    mTextureBuffer.put(textureCords).position(0);
  }
}
