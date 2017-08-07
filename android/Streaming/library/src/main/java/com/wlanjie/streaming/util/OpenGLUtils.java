package com.wlanjie.streaming.util;

import android.content.res.Resources;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wlanjie on 2017/5/26.
 */

public class OpenGLUtils {

  public static final float TEXTURE[] = {
      0.0f, 1.0f,
      0.0f, 0.0f,
      1.0f, 1.0f,
      1.0f, 0.0f
  };

  public static final float CUBE[] = {
      -1.0f, 1.0f,
      -1.0f, -1.0f,
      1.0f, 1.0f,
      1.0f, -1.0f
  };

  public static String readSharedFromRawResource(Resources resources, int resourceId) {
    final InputStream inputStream = resources.openRawResource(resourceId);
    final InputStreamReader reader = new InputStreamReader(inputStream);
    final BufferedReader bufferedReader = new BufferedReader(reader);
    StringBuilder body = new StringBuilder();
    try {
      String nextLine;
      while ((nextLine = bufferedReader.readLine()) != null) {
        body.append(nextLine);
        body.append("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return body.toString();
  }

  public static int loadProgram(final String vertexSource, final String fragmentSource) {
    int vertexShader = loadShader(vertexSource, GLES20.GL_VERTEX_SHADER);
    int fragmentShader = loadShader(fragmentSource, GLES20.GL_FRAGMENT_SHADER);
    int programId = GLES20.glCreateProgram();
    GLES20.glAttachShader(programId, vertexShader);
    GLES20.glAttachShader(programId, fragmentShader);
    GLES20.glLinkProgram(programId);
    GLES20.glDeleteShader(vertexShader);
    GLES20.glDeleteShader(fragmentShader);
    return programId;
  }

  public static int loadShader(final String source, final int type) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);
    return shader;
  }

  public static int getExternalOESTextureID() {
    int[] texture = new int[1];
    GLES20.glGenTextures(1, texture, 0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
    return texture[0];
  }
}
