package com.wlanjie.streaming.camera;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by wlanjie on 2016/12/12.
 */

class CameraRender extends Thread implements SurfaceTexture.OnFrameAvailableListener {

    private static final String VERTEX_SHADER = "" +
            "attribute vec4 position;\n" +
            "uniform mat4 camTextureTransform;\n" +
            "attribute vec4 camTexCoordinate;\n" +
            "varying vec2 v_CamTexCoordinate;\n" +
            "void main() {\n" +
            "v_CamTexCoordinate = (camTextureTransform * camTexCoordinate).xy;\n" +
            "gl_Position = position;\n" +
            "}";
    private static final String FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES camTexture;\n" +
            "varying vec2 v_CamTexCoordinate;" +
            "varying vec2 v_TexCoordinate;" +
            "void main() {\n" +
            "vec4 cameraColor = texture2D(camTexture, v_CamTexCoordinate);\n" +
            "gl_FragColor = cameraColor;" +
            "}";

    private static final float SQUARE_COORDS[] = {
            -1.0f, 1.0f, // top left
            1.0f, 1.0f, // top right
            -1.0f, -1.0f, // bottom left
            1.0f, -1.0f // bottom right

    };

    private static final float TEXTURE_COORDS[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };

    private static final short DRAW_ORDER[] = {
            0, 1, 2,
            1, 3, 2
    };

    /**
     * matrix for transforming our camera texture, available immediately after {@link #mPreviewTexture}s
     * {@code updateTexImage()} is called in our main {@link #draw()} loop.
     */
    private final float[] mCameraTransformMatrix = new float[16];

    /**
     * "arbitrary" maximum number of textures. seems that most phones dont like more than 16
     */
    private static final int MAX_TEXTURES = 16;

    /**
     * for storing all texture ids from genTextures, and used when binding
     * after genTextures, id[0] is reserved for camera texture
     */
    private int[] mTextureIds = new int[MAX_TEXTURES];

    private SurfaceTexture mPreviewTexture;

    private OnRendererReadyListener mOnRendererReadyListener;

    private int mCameraShaderProgram;

    private int mTextureCoordinateHandle;

    private int mPositionHandle;

    private EglCore mEglCore;

    private final CameraViewImpl mImpl;

    private final RenderHandler mHandler;

    private List<Texture> mTextureArray;

    private FloatBuffer vertexBuffer;

    private FloatBuffer textureBuffer;

    private ShortBuffer drawListBuffer;

    private int mPreviewWidth;

    private int mPreviewHeight;

    private final CameraCallback mCallback;

    CameraRender(CameraViewImpl impl, CameraCallback callback) {
        mImpl = impl;
        mCallback = callback;
        mHandler = new RenderHandler();
        initialize();
//        initGL(surfaceTexture);
    }

    private void initialize() {
        mTextureArray = new ArrayList<>();
    }

    @Override
    public synchronized void start() {
//        initialize();
        super.start();
    }

    void initGL(Object surfaceTexture) {
        mEglCore = new EglCore(null, 0);
        mEglCore.createWindowSurface(surfaceTexture);
        mEglCore.makeCurrent();

        vertexBuffer = ByteBuffer.allocateDirect(SQUARE_COORDS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(SQUARE_COORDS);
        vertexBuffer.position(0);

        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        textureBuffer.put(TEXTURE_COORDS);
        textureBuffer.position(0);

        drawListBuffer = ByteBuffer.allocateDirect(DRAW_ORDER.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        drawListBuffer.put(DRAW_ORDER);
        drawListBuffer.position(0);

        setupShader();
        onSetupComplete();
    }

    private void onSetupComplete() {
//        mOnRendererReadyListener.onRendererReady();
    }

    private void setupShader() {
        int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShaderHandle, VERTEX_SHADER);
        GLES20.glCompileShader(vertexShaderHandle);
        mEglCore.checkError();

        int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShaderHandle, FRAGMENT_SHADER);
        GLES20.glCompileShader(fragmentShaderHandle);
        mEglCore.checkError();

        mCameraShaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mCameraShaderProgram, vertexShaderHandle);
        GLES20.glAttachShader(mCameraShaderProgram, fragmentShaderHandle);
        GLES20.glLinkProgram(mCameraShaderProgram);
        mEglCore.checkError();

        int[] status = new int[1];
        GLES20.glGetProgramiv(mCameraShaderProgram, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(mCameraShaderProgram);
        }
    }

    void setupCameraTexture(int previewWidth, int previewHeight) {
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureIds[0]);
        mEglCore.checkError();

        mPreviewTexture = new SurfaceTexture(mTextureIds[0]);
        mPreviewTexture.setOnFrameAvailableListener(this);

        mImpl.setPreviewSurface(mPreviewTexture);
    }

    void deInitGL() {
        GLES20.glDeleteTextures(MAX_TEXTURES, mTextureIds, 0);
        GLES20.glDeleteProgram(mCameraShaderProgram);

        mPreviewTexture.release();
        mPreviewTexture.setOnFrameAvailableListener(null);

        mEglCore.release();
    }

    @Override
    public void run() {
        super.run();
//        while (true) {
////            ByteBuffer buffer = mEglCore.getRgbaFrame();
//            try {
//                mEglCore.saveFrame(new File("/sdcard/" + System.nanoTime() + ".png"));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (CameraRender.class) {
            mPreviewTexture.updateTexImage();
            mPreviewTexture.getTransformMatrix(mCameraTransformMatrix);
            draw();
            mEglCore.makeCurrent();
            mEglCore.swapBuffers();
            ByteBuffer buffer = mEglCore.getRgbaFrame(mPreviewWidth, mPreviewHeight);
            if (buffer != null) {
                mCallback.onPreviewFrame(buffer.array());
            }
        }
    }

    private void draw() {
        GLES20.glViewport(0, 0, mPreviewWidth, mPreviewHeight);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // set shader
        GLES20.glUseProgram(mCameraShaderProgram);
        setUniformAndAttrib();
        setExtraTextures();
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, DRAW_ORDER.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordinateHandle);
//        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, );
    }

    public int addTexture(int textureId, Bitmap bitmap, String uniformName, boolean recycle) {
        int num = mTextureArray.size() + 1;
        GLES20.glActiveTexture(textureId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[num]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        if (recycle)
            bitmap.recycle();
        Texture texture = new Texture(num, textureId, uniformName);
        if (!mTextureArray.contains(texture)) {
            mTextureArray.add(texture);
        }
        return num;
    }

    private void setExtraTextures() {
        for (Texture texture : mTextureArray) {
            int imageParamHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, texture.uniformName);
            GLES20.glActiveTexture(texture.textureId);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[texture.textureNum]);
            GLES20.glUniform1i(imageParamHandle, texture.textureNum);
        }
    }

    private void setUniformAndAttrib() {
        int textureParamHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "camTexture");
        int textureTranformHandler = GLES20.glGetUniformLocation(mCameraShaderProgram, "camTextureTransform");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mCameraShaderProgram, "camTexCoordinate");
        mPositionHandle = GLES20.glGetAttribLocation(mCameraShaderProgram, "position");

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 4 * 2, vertexBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureIds[0]);
        GLES20.glUniform1i(textureParamHandle, 0);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);
        GLES20.glUniformMatrix4fv(textureTranformHandler, 1, false, mCameraTransformMatrix, 0);
    }

    private static class RenderHandler extends Handler {

    }

    private static class Texture {
        int textureNum;
        int textureId;
        String uniformName;

        public Texture(int textureNum, int textureId, String uniformName) {
            this.textureNum = textureNum;
            this.textureId = textureId;
            this.uniformName = uniformName;
        }
    }

    public void setOnRendererReadyListener(OnRendererReadyListener l) {
        mOnRendererReadyListener = l;
    }

    public interface OnRendererReadyListener {
        /**
         * Called when {@link #onSetupComplete()} is finished with its routine
         */
        void onRendererReady();

        /**
         * Called once the looper is killed and our {@link #run()} method completes
         */
        void onRendererFinished();
    }
}
