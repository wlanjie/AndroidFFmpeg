package com.wlanjie.streaming.video;

import android.content.res.Resources;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.wlanjie.streaming.R;
import com.wlanjie.streaming.util.OpenGLUtils;
import com.wlanjie.streaming.util.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Created by wlanjie on 2016/12/12.
 */

public final class Effect {

  private int mInputWidth;
  private int mInputHeight;

  private int mDisplayWidth;
  private int mDisplayHeight;

  private int mFboId;
  private int mFboTextureId;
  private IntBuffer mFboBuffer;

  private int[] mCubeId;
  private final FloatBuffer mCubeBuffer;
  private int[] mTextureCoordinatedId;
  private final FloatBuffer mTextureBuffer;

  private int mProgramId;
  private int mPosition;
  private int mUniformTexture;
  private int mTextureCoordinate;
  private int mTextureTransform;
  private float[] mTextureTransformMatrix;

  private final Resources mResources;

  Effect(Resources resources) {
    this.mResources = resources;
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
    initVbo();

    mProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_oes), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_oes));
    mPosition = GLES20.glGetAttribLocation(mProgramId, "position");
    mUniformTexture = GLES20.glGetUniformLocation(mProgramId, "inputImageTexture");
    mTextureCoordinate = GLES20.glGetAttribLocation(mProgramId, "inputTextureCoordinate");
    mTextureTransform = GLES20.glGetUniformLocation(mProgramId, "textureTransform");
  }

  void onInputSizeChanged(int width, int height) {
    this.mInputWidth = width;
    this.mInputHeight = height;
    initFboTexture(width, height);
  }

  void onDisplaySizeChange(int width, int height) {
    mDisplayWidth = width;
    mDisplayHeight = height;
  }

  private void initVbo() {
    mCubeId = new int[1];
    mTextureCoordinatedId = new int[1];

    GLES20.glGenBuffers(1, mCubeId, 0);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mCubeId[0]);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mCubeBuffer.capacity() * 4, mCubeBuffer, GLES20.GL_STATIC_DRAW);

    GLES20.glGenBuffers(1, mTextureCoordinatedId, 0);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mTextureCoordinatedId[0]);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mTextureBuffer.capacity() * 4, mTextureBuffer, GLES20.GL_STATIC_DRAW);
    ;
  }

  private void destroyVbo() {
    if (mCubeId != null) {
      GLES20.glDeleteBuffers(1, mCubeId, 0);
      mCubeId = null;
    }
    if (mTextureCoordinatedId != null) {
      GLES20.glDeleteBuffers(1, mTextureCoordinatedId, 0);
      mTextureCoordinatedId = null;
    }
  }

  private void initFboTexture(int width, int height) {
    if (mFboId != 0 && width != mInputWidth && height != mInputHeight) {
      destroyFboTexture();
    }
    int[] fbo = new int[1];
    int[] texture = new int[1];
    mFboBuffer = IntBuffer.allocate(width * height);

    GLES20.glGenFramebuffers(1, fbo, 0);
    GLES20.glGenTextures(1, texture, 0);

    mFboId = fbo[0];
    mFboTextureId = texture[0];

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTextureId);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mFboTextureId, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
  }

  private void destroyFboTexture() {
    if (mFboId != 0) {
      GLES20.glDeleteFramebuffers(1, new int[] { mFboId }, 0);
    }
    if (mFboTextureId != 0) {
      GLES20.glDeleteTextures(1, new int[] { mFboTextureId }, 0);
    }
  }

  IntBuffer getRgbaBuffer() {
    return mFboBuffer;
  }

  int drawToFboTexture(int textureId, FloatBuffer cubeBuffer, FloatBuffer textureBuffer) {
    GLES20.glUseProgram(mProgramId);

    GLES20.glEnableVertexAttribArray(mPosition);
    GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);

    GLES20.glEnableVertexAttribArray(mTextureCoordinate);
    GLES20.glVertexAttribPointer(mTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);

    GLES20.glUniformMatrix4fv(mTextureTransform, 1, false, mTextureTransformMatrix, 0);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glUniform1i(mUniformTexture, 0);

    GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GLES20.glReadPixels(0, 0, mInputWidth, mInputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mFboBuffer);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GLES20.glViewport(0, 0, mDisplayWidth, mDisplayHeight);

    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

    GLES20.glDisableVertexAttribArray(mPosition);
    GLES20.glDisableVertexAttribArray(mTextureCoordinate);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    return mFboTextureId;
  }

  int drawToFboTexture(int textureId) {
    return drawToFboTexture(textureId, mCubeBuffer, mTextureBuffer);
  }

  void setTextureTransformMatrix(float[] matrix) {
    mTextureTransformMatrix = matrix;
  }

  public final void destroy() {
    destroyFboTexture();
    destroyVbo();
    GLES20.glDeleteProgram(mProgramId);
  }
}
