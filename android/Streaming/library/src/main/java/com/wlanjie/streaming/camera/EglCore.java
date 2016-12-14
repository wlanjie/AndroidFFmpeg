package com.wlanjie.streaming.camera;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by wlanjie on 2016/12/12.
 */

final class EglCore {

    private EGLSurface mWindowSurface;
    private EGLDisplay mWindowDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig mEglConfig = null;
    private EGLContext mEglContext = null;
    private int mGlVersion;

    EglCore(EGLContext sharedContext, int flags) {
        if (mWindowDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up.");
        }
        if (sharedContext == null) {
            sharedContext = EGL14.EGL_NO_CONTEXT;
        }
        mWindowDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mWindowDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mWindowDisplay, version, 0, version, 1)) {
            mWindowDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        EGLConfig config = getConfig();
        if (config == null) {
            throw new RuntimeException("unable to find a suitable EGLConfig");
        }

        int[] contextAttrib = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(mWindowDisplay, config, sharedContext, contextAttrib, 0);
        checkError();
        mEglConfig = config;
        mEglContext = context;
        mGlVersion = 2;
    }

    int getGlVersion() {
        return mGlVersion;
    }

    private EGLConfig getConfig() {
        int[] attrib = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE, 0,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mWindowDisplay, attrib, 0, configs, 0, configs.length, numConfigs, 0)) {
            return null;
        }
        return configs[0];
    }

    void checkError() {
        if (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("egl error.");
        }
    }

    void createWindowSurface(Object surface) {
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
            throw new RuntimeException("invalid surface: " + surface);
        }
        int[] surfaceAttribs = { EGL14.EGL_NONE };
        mWindowSurface = EGL14.eglCreateWindowSurface(mWindowDisplay, mEglConfig, surface, surfaceAttribs, 0);
        checkError();
    }

    void makeCurrent() {
        if (mWindowDisplay != EGL14.EGL_NO_DISPLAY) {
            if (!EGL14.eglMakeCurrent(mWindowDisplay, mWindowSurface, mWindowSurface, mEglContext)) {
                throw new RuntimeException("eglMakeCurrent failed.");
            }
        }
    }

    boolean swapBuffers() {
        return EGL14.eglSwapBuffers(mWindowDisplay, mWindowSurface);
    }

    void release() {
        EGL14.eglDestroySurface(mWindowDisplay, mWindowSurface);
        mWindowSurface = EGL14.EGL_NO_SURFACE;

        if (mWindowDisplay != null) {
            EGL14.eglMakeCurrent(mWindowDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(mWindowDisplay, mEglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mWindowDisplay);
        }
        mWindowDisplay = EGL14.EGL_NO_DISPLAY;
        mEglContext = EGL14.EGL_NO_CONTEXT;
        mEglConfig = null;
    }

    /**
     * Returns true if our context and the specified surface are current.
     */
    private boolean isCurrent(EGLSurface eglSurface) {
        return mEglContext.equals(EGL14.eglGetCurrentContext()) &&
                eglSurface.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
    }

    /**
     * Performs a simple surface query.
     */
    private int querySurface(EGLSurface eglSurface, int what) {
        int[] value = new int[1];
        EGL14.eglQuerySurface(mWindowDisplay, eglSurface, what, value, 0);
        return value[0];
    }

    ByteBuffer getRgbaFrame(int width, int height) {
        if (!isCurrent(mWindowSurface)) {
            return null;
        }
        long start = System.currentTimeMillis();
        ByteBuffer buffer = ByteBuffer.allocate(width * height * 4);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        checkError();
        buffer.rewind();
        System.out.println("time = " + (System.currentTimeMillis() - start) + " " + buffer.limit());
        return buffer;
    }

    /**
     * Saves the EGL surface to a file.
     * <p>
     * Expects that this object's EGL surface is current.
     */
    void saveFrame() throws IOException {
        if (!isCurrent(mWindowSurface)) {
            throw new RuntimeException("Expected EGL context/surface is not current");
        }
        long start = System.currentTimeMillis();

        // glReadPixels fills in a "direct" ByteBuffer with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  While the Bitmap
        // constructor that takes an int[] wants little-endian ARGB (blue/red swapped), the
        // Bitmap "copy pixels" method wants the same format GL provides.
        //
        // Ideally we'd have some way to re-use the ByteBuffer, especially if we're calling
        // here often.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside down relative to what appears on screen if the
        // typical GL conventions are used.


        int width = querySurface(mWindowSurface, EGL14.EGL_WIDTH);
        int height = querySurface(mWindowSurface, EGL14.EGL_HEIGHT);
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        checkError();
        buffer.rewind();

        System.out.println("time = " + (System.currentTimeMillis() - start));
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream("/sdcard/" + System.nanoTime() + ".png"));
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.recycle();
        } finally {
            if (bos != null) bos.close();
        }
    }
}
