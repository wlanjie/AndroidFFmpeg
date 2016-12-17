package com.wlanjie.streaming.camera;

import android.content.res.Resources;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by wlanjie on 2016/12/14.
 */

final class OpenGLUtils {

    private static final String TAG = OpenGLUtils.class.getSimpleName();
    public static final int NO_TEXTURE = -1;

    private static int loadShader(final String source, final int type) {
        int[] compiled = new int[1];
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Load Shader failed: " + GLES20.glGetShaderInfoLog(shader));
            return 0;
        }
        return shader;
    }

    static int loadProgram(final String vertexSource, final String fragmentSource) {
        int vertexShader = loadShader(vertexSource, GLES20.GL_VERTEX_SHADER);
        if (vertexShader == 0) {
            Log.e(TAG, "Load Vertex Shader failed: " + GLUtils.getEGLErrorString(vertexShader));
            return 0;
        }
        int fragmentShader = loadShader(fragmentSource, GLES20.GL_FRAGMENT_SHADER);
        if (fragmentShader == 0) {
            Log.e(TAG, "Load Fragment Shader failed: " + GLUtils.getEGLErrorString(fragmentShader));
            return 0;
        }

        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);
        int[] link = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.e(TAG, "Link Program Failed");
            return 0;
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return programId;
    }

    static int getExternalOESTextureID() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    static String readSharedFromRawResource(Resources resources, int resourceId) {
        final InputStream inputStream = resources.openRawResource(resourceId);
        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        StringBuilder body = new StringBuilder();
        try {
            String nextLine;
            while ((nextLine = bufferedReader.readLine()) != null) {
                body.append(nextLine);
                body.append("\n");
            }
        } catch (IOException e) {
            return null;
        }
        return body.toString();
    }
}
