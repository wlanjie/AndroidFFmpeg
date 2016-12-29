package com.wlanjie.streaming.camera;

import android.content.res.Resources;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.wlanjie.streaming.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by wlanjie on 2016/12/12.
 */

final class EglCore {

    private static final int NO_INIT = -1;
    private static final int NO_TEXTURE = -2;

    private static final String VERTEX_SHADER = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "varying vec2 textureCoordinate;\n" +
            "uniform mat4 textureTransform;\n" +
            "void main() {\n" +
            "   textureCoordinate = (textureTransform * inputTextureCoordinate).xy;\n" +
            "   gl_Position = position;\n" +
            "}";

    private static final String FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying mediump vec2 textureCoordinate;\n" +
            "uniform samplerExternalOES inputImageTexture\n" +
            "void main() {\n" +
            "   gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "}";

    private static final float TEXTURE_NO_ROTATION[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };

    private static final float TEXTURE_ROTATED_90[] = {
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f
    };

    private static final float TEXTURE_ROTATED_180[] = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f
    };

    private static final float TEXTURE_ROTATED_270[] = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
    };

    private static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    private int mInputWidth;
    private int mInputHeight;

    private final Queue<Runnable> mRunOnDraw;

    private int mDisplayWidth;
    private int mDisplayHeight;

    private int[] mFboId;
    private int[] mFboTextureId;
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

    // screen
    private int mScreenProgramId;
    private int mScreenPosition;
    private int mScreenUniformTexture;
    private int mScreenTextureCoordinate;
    private boolean mIsInitialized;

    private final Resources mResources;
    EglCore(Resources resources) {
        this.mResources = resources;
        mRunOnDraw = new ConcurrentLinkedQueue<>();
        mCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCubeBuffer.put(CUBE).position(0);

        mTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTextureBuffer.put(TEXTURE_NO_ROTATION).position(0);
    }

    void init() {
        onInit();
        onInitialized();
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

    private void onInit() {
        initVbo();

        mProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_oes), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_oes));
        mPosition = GLES20.glGetAttribLocation(mProgramId, "position");
        mUniformTexture = GLES20.glGetUniformLocation(mProgramId, "inputImageTexture");
        mTextureCoordinate = GLES20.glGetAttribLocation(mProgramId, "inputTextureCoordinate");
        mTextureTransform = GLES20.glGetUniformLocation(mProgramId, "textureTransform");

        mScreenProgramId = OpenGLUtils.loadProgram(OpenGLUtils.readSharedFromRawResource(mResources, R.raw.vertex_default), OpenGLUtils.readSharedFromRawResource(mResources, R.raw.fragment_default));
        mScreenPosition = GLES20.glGetAttribLocation(mScreenProgramId, "position");
        mScreenUniformTexture = GLES20.glGetUniformLocation(mScreenProgramId, "inputImageTexture");
        mScreenTextureCoordinate = GLES20.glGetAttribLocation(mScreenProgramId, "inputTextureCoordinate");
        mIsInitialized = true;
    }

    private void initVbo() {
        mCubeId = new int[1];
        mTextureCoordinatedId = new int[1];

        GLES20.glGenBuffers(1, mCubeId, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mCubeId[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mCubeBuffer.capacity() * 4, mCubeBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, mTextureCoordinatedId, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mTextureCoordinatedId[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mTextureBuffer.capacity() * 4, mTextureBuffer, GLES20.GL_STATIC_DRAW);;
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

    private void onInitialized() {

    }

    private void initFboTexture(int width, int height) {
        if (mFboId != null && width != mInputWidth && height != mInputHeight) {
            destroyFboTexture();
        }
        mFboId = new int[1];
        mFboTextureId = new int[1];
        mFboBuffer = IntBuffer.allocate(width * height);

        GLES20.glGenFramebuffers(1, mFboId, 0);
        GLES20.glGenTextures(1, mFboTextureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTextureId[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mFboTextureId[0], 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void destroyFboTexture() {
        if (mFboId != null) {
            GLES20.glDeleteFramebuffers(1, mFboId, 0);
        }
        if (mFboTextureId != null) {
            GLES20.glDeleteTextures(1, mFboTextureId, 0);
        }
    }

    int onDrawFrame(int cameraTextureId) {
        int fboTextureId = drawToFboTexture(cameraTextureId);
        return drawToScreen(fboTextureId);
    }

    private int drawToScreen(int textureId) {
        if (!mIsInitialized) {
            return NO_INIT;
        }
        if (mFboId == null) {
            return NO_TEXTURE;
        }

        GLES20.glUseProgram(mScreenProgramId);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mCubeId[0]);
        GLES20.glEnableVertexAttribArray(mScreenPosition);
        GLES20.glVertexAttribPointer(mScreenPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mTextureCoordinatedId[0]);
        GLES20.glEnableVertexAttribArray(mScreenTextureCoordinate);
        GLES20.glVertexAttribPointer(mScreenTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(mScreenUniformTexture, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisableVertexAttribArray(mScreenPosition);
        GLES20.glDisableVertexAttribArray(mScreenTextureCoordinate);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return 0;
    }

    IntBuffer getRgbaBuffer() {
        return mFboBuffer;
    }

    private int drawToFboTexture(int textureId) {
        if (!mIsInitialized) {
            return NO_INIT;
        }
        if (mFboId == null) {
            return NO_TEXTURE;
        }

        GLES20.glUseProgram(mProgramId);
        runPendingOnDrawTasks();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mCubeId[0]);
        GLES20.glEnableVertexAttribArray(mPosition);
        GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mTextureCoordinatedId[0]);
        GLES20.glEnableVertexAttribArray(mTextureCoordinate);
        GLES20.glVertexAttribPointer(mTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);

        GLES20.glUniformMatrix4fv(mTextureTransform, 1, false, mTextureTransformMatrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(mUniformTexture, 0);

        onDrawArrayPrepare();

        GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId[0]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glReadPixels(0, 0, mInputWidth, mInputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mFboBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mDisplayWidth, mDisplayHeight);

        onDrawArrayAfter();

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        GLES20.glDisableVertexAttribArray(mPosition);
        GLES20.glDisableVertexAttribArray(mTextureCoordinate);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return mFboTextureId[0];
    }

    void onDrawArrayPrepare() {}

    void onDrawArrayAfter() {};

    void setTextureTransformMatrix(float[] matrix) {
        mTextureTransformMatrix = matrix;
    }

    private void runPendingOnDrawTasks() {
        while (!mRunOnDraw.isEmpty()) {
            mRunOnDraw.poll().run();
        }
    }

    final void destroy() {
        mIsInitialized = false;
        destroyFboTexture();
        destroyVbo();
        GLES20.glDeleteProgram(mProgramId);
        GLES20.glDeleteProgram(mScreenProgramId);
        onDestroy();
    }

    void onDestroy() {

    }
}
