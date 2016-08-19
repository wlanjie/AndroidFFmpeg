package com.wlanjie.ffmpeg.library;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import android.view.InputDevice;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

/**
 * Created by wlanjie on 16/4/26.
 */
public class FFmpeg {

    private static final String TAG = "FFmpeg";

    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("wlanjie");
    }

    private Surface surface;
    private volatile static FFmpeg instance;
    private AudioTrack mAudioTrack;

    protected FFmpeg() {}

    public static FFmpeg getInstance() {
        if (instance == null) {
            synchronized (FFmpeg.class) {
                if (instance == null) {
                    instance = new FFmpeg();
                }
            }
        }
        return instance;
    }

    private MediaSource mediaSource = new MediaSource();

    /**
     * 设置视频输入和输出文件
     * @param inputFile 输入视频文件
     * @throws FileNotFoundException 如果输入视频文件不存在抛出此异常
     * @throws IllegalArgumentException 如果输入文件和输出文件为null,抛出此异常
     */
    public void openInput(File inputFile) throws FileNotFoundException, IllegalArgumentException {
        if (inputFile == null) {
            throw new IllegalArgumentException("input file must be not null");
        }
        if (!inputFile.exists()) {
            throw new FileNotFoundException("input file not found");
        }
        mediaSource.setInputDataSource(inputFile.getAbsolutePath());
    }

    /**
     * 打开输入文件
     * @return >= 0 success, <0 error
     * @throws IllegalStateException 不能打开时,或者打开的路径为空时抛出此异常
     */
    public native int openInput(String inputPath) throws IllegalStateException, IllegalArgumentException;

    /**
     * 获取视频的宽
     * @return 视频的宽
     * @throws FileNotFoundException 如果视频文件不存在,则抛出此异常
     */
    public int getVideoWidth() {
        return mediaSource.getWidth();
    }
//    public native int getVideoWidth() throws FileNotFoundException, IllegalStateException;

    /**
     * 获取视频的高
     * @return 视频的高
     * @throws FileNotFoundException 如果视频文件不存在,则抛出此异常
     */
    public int getVideoHeight() {
        return mediaSource.getHeight();
    }
//    public native int getVideoHeight() throws FileNotFoundException, IllegalStateException;

    /**
     * 压缩视频,视频采用h264编码,音频采用aac编码,此方法是阻塞式操作,如果在ui线程操作,会产生anr异常
     * 如果需要缩放,指定高度的值,宽度-1,则宽度根据指定的高度自动计算而得
     * 如果指定宽度的值,高度为-1,则高度根据指定的宽度自动计算而得
     * 如果宽和高同时为-1,则不缩放
     * @param width 压缩的视频宽,如果需要保持原来的宽,则是0,如果指定高度为原视频的一半,宽度传入-1,则根据高度自动计算而得
     * @param height 压缩的视频高,如果需要保持原来的高,则是0, 如果指定宽度为原视频的一半,高度传入-1,则根据宽度自动计算而得
     * @throws FileNotFoundException 如果文件不存在抛出此异常
     * @throws IllegalStateException
     */

    public native int compress(String outputPath, int width, int height) throws FileNotFoundException;

    /**
     * 获取视频的角度
     * @return 视频的角度
     */
    public double getRotation() {
        return mediaSource.getRotation();
    }
//    public native double getRotation();

    /**
     * 裁剪视频
     * @param x 视频x坐标
     * @param y 视频y坐标
     * @param width 裁剪视频之后的宽度
     * @param height 裁剪视频之后的高度
     */
    public native int crop(String outputPath, int x, int y, int width, int height);

    public native int player(String url);

    public void setSurface(Surface surface) {
        if (surface == null) {
            throw new IllegalArgumentException("surface can't be null");
        }
        this.surface = surface;
    }

    public native void onNativePause();

    public native void onNativeResume();

    public native void onNativeResize(int width, int height, int format, float rate);

    public native void onNativeSurfaceChanged();

    public native void onNativeSurfaceDestroyed();

    public native void onNativeLowMemory();

    public native void pollInputDevices();

    public void setSurfaceView(SurfaceView surfaceView) {
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                setSurface(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        player("rtmp://live.hkstv.hk.lxdns.com/live/hks");
                    }
                });
                thread.start();
                onNativeResize(width, height, format, 0);
                onNativeResume();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                onNativeSurfaceDestroyed();
            }
        });
    }

    /**
     * This method is called by SDL using JNI.
     * @return an array which may be empty but is never null.
     */
    public static int[] inputGetInputDeviceIds(int sources) {
        int[] ids = InputDevice.getDeviceIds();
        int[] filtered = new int[ids.length];
        int used = 0;
        for (int i = 0; i < ids.length; ++i) {
            InputDevice device = InputDevice.getDevice(ids[i]);
            if ((device != null) && ((device.getSources() & sources) != 0)) {
                filtered[used++] = device.getId();
            }
        }
        return Arrays.copyOf(filtered, used);
    }


    public void setSurfaceHolder(SurfaceHolder holder) {
        setSurface(holder.getSurface());
    }

    /**
     * native call method
     * @return
     */
    public Surface getNativeSurface() {
        if (surface == null) {
            throw new IllegalArgumentException("surface can't be null, please use setSurface or setSurfaceHolder");
        }
        return surface;
    }


    /**
     * This method is called by SDL using JNI.
     */
    public int audioInit(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
        int channelConfig = isStereo ? AudioFormat.CHANNEL_CONFIGURATION_STEREO : AudioFormat.CHANNEL_CONFIGURATION_MONO;
        int audioFormat = is16Bit ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        int frameSize = (isStereo ? 2 : 1) * (is16Bit ? 2 : 1);

        Log.v(TAG, "SDL audio: wanted " + (isStereo ? "stereo" : "mono") + " " + (is16Bit ? "16-bit" : "8-bit") + " " + (sampleRate / 1000f) + "kHz, " + desiredFrames + " frames buffer");

        // Let the user pick a larger buffer if they really want -- but ye
        // gods they probably shouldn't, the minimums are horrifyingly high
        // latency already
        desiredFrames = Math.max(desiredFrames, (AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize);

        if (mAudioTrack == null) {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    channelConfig, audioFormat, desiredFrames * frameSize, AudioTrack.MODE_STREAM);

            // Instantiating AudioTrack can "succeed" without an exception and the track may still be invalid
            // Ref: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/media/java/android/media/AudioTrack.java
            // Ref: http://developer.android.com/reference/android/media/AudioTrack.html#getState()

            if (mAudioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed during initialization of Audio Track");
                mAudioTrack = null;
                return -1;
            }

            mAudioTrack.play();
        }

        Log.v(TAG, "SDL audio: got " + ((mAudioTrack.getChannelCount() >= 2) ? "stereo" : "mono") + " " + ((mAudioTrack.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) ? "16-bit" : "8-bit") + " " + (mAudioTrack.getSampleRate() / 1000f) + "kHz, " + desiredFrames + " frames buffer");

        return 0;
    }


    /**
     * This method is called by SDL using JNI.
     */
    public void audioWriteShortBuffer(short[] buffer) {
        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {
                    // Nom nom
                }
            } else {
                return;
            }
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    public void audioWriteByteBuffer(byte[] buffer) {
        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {
                    // Nom nom
                }
            } else {
                return;
            }
        }
    }

    /**
     * This method is called by SDL using JNI.
     */
    public void audioQuit() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack = null;
        }
    }


    /**
     * 释放资源
     */
    public native void release();

}
