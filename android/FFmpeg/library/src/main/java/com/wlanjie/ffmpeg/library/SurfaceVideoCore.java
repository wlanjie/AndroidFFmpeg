package com.wlanjie.ffmpeg.library;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by wlanjie on 16/9/11.
 */
public class SurfaceVideoCore implements VideoCore {

    private Parameters parameters;
    private Lock videoFilterLock;
    private final Object lock = new Object();
    private final Object videoEncoderLock = new Object();
    private final Object loopingLock = new Object();
    private final Object previewLock = new Object();
    private final Object screenShotLock = new Object();
    private SurfaceVideoFilter videoFilter;
    private VideoFilterHandler videoFilterHandler;
    private int currentCamera;
    private VideoBuffer[] originVideoBuffers;
    private int lastVideoQueueBufferIndex;
    private int loopingInterval;
    private MediaFormat videoFormat;
    private MediaCodec videoEncoder;
    private boolean isEncoderStarted;
    private VideoBuffer originNV21VideoBuffer;
    private VideoBuffer filteredNV21VideoBuffer;
    private VideoBuffer suitable4VideoEncoderBuffer;
    private VideoSenderThread videoSenderThread;
    private boolean isPreviewing = false;
    private boolean isStreaming = false;
    private Render previewRender;
    private ScreenShotListener screenShotListener;
    private FileOutputStream fileOutputStream;
    private byte[] h264Buffer;
    private byte[] yuv420pBuffer;

    public SurfaceVideoCore(Parameters parameters) {
        this.parameters = parameters;
        videoFilterLock = new ReentrantLock(false);
        videoFilter = null;
    }

    private boolean isArrayContain(int[] src, int target) {
        for (int color : src) {
            if (color == target) {
                return true;
            }
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private MediaCodec createVideoMediaCodec() {
        videoFormat.setString(MediaFormat.KEY_MIME, "video/avc");
        videoFormat.setInteger(MediaFormat.KEY_WIDTH, parameters.videoWidth);
        videoFormat.setInteger(MediaFormat.KEY_HEIGHT, parameters.videoHeight);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, parameters.mediacdoecAVCBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, parameters.mediacodecAVCFrameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, parameters.mediacodecAVCIFrameInterval);
        videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        MediaCodec result = null;
        try {
            result = MediaCodec.createEncoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
            int[] colorFormats = result.getCodecInfo().getCapabilitiesForType(videoFormat.getString(MediaFormat.KEY_MIME)).colorFormats;
            int videoColorFormat = -1;
            if (isArrayContain(colorFormats, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                videoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                parameters.mediacodecAVCColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            }
            if (videoColorFormat == -1 && isArrayContain(colorFormats, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                videoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                parameters.mediacodecAVCColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
            }
            if (videoColorFormat == -1) {
                return null;
            }
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, videoColorFormat);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private int calculator(int width, int height, int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return width * height * 3 / 2;
            default:
                return -1;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean prepare(Config config) {
        synchronized (lock) {
            parameters.renderingMode = config.getRenderingMode();
            parameters.mediacdoecAVCBitRate = config.getBitRate();
            parameters.videoBufferQueueNum = config.getVideoBufferQueueNum();
            parameters.mediacodecAVCIFrameInterval = 3;
            parameters.mediacodecAVCFrameRate = parameters.videoFPS;
            loopingInterval = 1000 / parameters.videoFPS;
            videoFormat = new MediaFormat();
            synchronized (videoEncoderLock) {
                videoEncoder = createVideoMediaCodec();
                isEncoderStarted = false;
                if (videoEncoder == null) {
                    return false;
                }
            }
            int videoWidth = parameters.videoWidth;
            int videoHeight = parameters.videoHeight;
            int videoQueueNumber = parameters.videoBufferQueueNum;
            h264Buffer = new byte[videoWidth * videoHeight];
            yuv420pBuffer = new byte[parameters.videoHeight * parameters.videoWidth * (ImageFormat.getBitsPerPixel(parameters.previewColorFormat)) / 8];
            Encoder.x264EncoderInit(videoWidth, videoHeight);
            try {
                File file = new File("/sdcard/wlanjie_h264.h264");
                fileOutputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            int originVideoBufferSize = calculator(videoWidth, videoHeight, parameters.previewColorFormat);
            originVideoBuffers = new VideoBuffer[videoQueueNumber];
            for (int i = 0; i < videoQueueNumber; i++) {
                originVideoBuffers[i] = new VideoBuffer(parameters.previewColorFormat, originVideoBufferSize);
            }
            for (VideoBuffer buffer : originVideoBuffers) {
                buffer.isReadyToFill = true;
            }
            lastVideoQueueBufferIndex = 0;
            parameters.previewBufferSize = originVideoBufferSize;
            originNV21VideoBuffer = new VideoBuffer(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                    calculator(videoWidth, videoHeight, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar));
            filteredNV21VideoBuffer = new VideoBuffer(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                    calculator(videoWidth, videoHeight, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar));
            suitable4VideoEncoderBuffer = new VideoBuffer(parameters.mediacodecAVCColorFormat,
                    calculator(videoWidth, videoHeight, parameters.mediacodecAVCColorFormat));
            HandlerThread videoFilterHandlerThread = new HandlerThread("videoFilterHandlerThread");
            videoFilterHandlerThread.start();
            videoFilterHandler = new VideoFilterHandler(videoFilterHandlerThread.getLooper());
            return true;
        }
    }

    @Override
    public void updateCameraTexture(SurfaceTexture cameraTexture) {

    }

    @Override
    public void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        synchronized (previewLock) {
            if (previewRender != null) {
                throw new RuntimeException("startPreView without destroy previous");
            }
            switch (parameters.renderingMode) {
                case Parameters.RENDERING_MODE_NATIVE_WINDOW:
                    previewRender = new NativeRender();
                    break;
                case Parameters.RENDERING_MODE_OPENGLES:
                    previewRender = new GLESRender();
                    break;
                default:
                    throw new RuntimeException("Unknow rendering mode");
            }
            previewRender.create(surfaceTexture, parameters.previewColorFormat,
                    parameters.videoWidth, parameters.videoHeight, visualWidth, visualHeight);
            synchronized (loopingLock) {
                if (!isPreviewing && !isStreaming) {
                    videoFilterHandler.removeMessages(VideoFilterHandler.WHAT_DRAW);
                    videoFilterHandler.sendMessageDelayed(videoFilterHandler.obtainMessage(VideoFilterHandler.WHAT_DRAW, SystemClock.uptimeMillis() + loopingInterval), loopingInterval);
                }
                isPreviewing = true;
            }
        }
    }

    @Override
    public void updatePreview(int visualWidth, int visualHeight) {
        synchronized (previewLock) {
            if (previewRender == null) {
                throw new RuntimeException("updatePreview without startPreview");
            }
            previewRender.update(visualWidth, visualHeight);
        }
    }

    @Override
    public void stopPreview() {
        synchronized (previewLock) {
            if (previewRender == null) {
                throw new RuntimeException("stopPreview without stopPreview");
            }
            previewRender.destroy();
            previewRender = null;
            synchronized (loopingLock) {
                isPreviewing = false;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean startStreaming(FLVDataCollecter flvDataCollecter) {
        synchronized (lock) {
            try {
                synchronized (videoEncoderLock) {
                    if (videoEncoder == null) {
                        videoEncoder = MediaCodec.createEncoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                    }
                    videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    videoEncoder.start();
                    isEncoderStarted = true;
                }
                videoSenderThread = new VideoSenderThread("VideoSenderThread", videoEncoder, flvDataCollecter);
                videoSenderThread.start();

                synchronized (loopingLock) {
                    if (!isPreviewing && !isStreaming) {
                        videoFilterHandler.removeMessages(VideoFilterHandler.WHAT_DRAW);
                        videoFilterHandler.sendMessageDelayed(videoFilterHandler.obtainMessage(VideoFilterHandler.WHAT_DRAW, SystemClock.uptimeMillis() + loopingInterval), loopingInterval);
                    }
                    isStreaming = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean stopStreaming() {
        synchronized (lock) {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Encoder.x264EncoderClose();
            }
            videoSenderThread.quit();
            synchronized (loopingLock) {
                isStreaming = false;
            }
            try {
                videoSenderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (videoEncoderLock) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
                isEncoderStarted = false;
            }
            videoSenderThread = null;
            return true;
        }
    }

    @Override
    public boolean destroy() {
        synchronized (lock) {
            videoFilterLock.lock();
            if (videoFilter != null) {
                videoFilter.onDestroy();
            }
            videoFilterLock.unlock();
            return true;
        }
    }

    @Override
    public void setCurrentCamera(int cameraIndex) {
        if (currentCamera != cameraIndex) {
            synchronized (lock) {
                if (videoFilterHandler != null) {
                    videoFilterHandler.removeMessages(VideoFilterHandler.WHAT_INCOMING_BUFF);
                }
                if (originVideoBuffers != null) {
                    for (VideoBuffer originVideoBuffer : originVideoBuffers) {
                        originVideoBuffer.isReadyToFill = true;
                    }
                    lastVideoQueueBufferIndex = 0;
                }
            }
        }
        currentCamera = cameraIndex;
    }

    @Override
    public void takeScreenShot(ScreenShotListener listener) {
        synchronized (screenShotLock) {
            screenShotListener = listener;
        }
    }

    @Override
    public float getDrawFrameRate() {
        synchronized (lock) {
            return videoFilterHandler == null ? 0 : videoFilterHandler.getDrawFrameRate();
        }
    }

    public void queueVideo(byte[] rawVideoFrame) {
        synchronized (lock) {
            int targetIndex = (lastVideoQueueBufferIndex + 1) % originVideoBuffers.length;
//            if (yuv420pBuffer == null) {
//                yuv420pBuffer = new byte[rawVideoFrame.length];
//            }
//            byte[] yuv420p = new byte[rawVideoFrame.length];
            ColorConvert.NV21ToYUV420P(rawVideoFrame, yuv420pBuffer, parameters.videoWidth * parameters.videoHeight);
            int result = Encoder.x264EncoderStart(-1, yuv420pBuffer, h264Buffer);
            if (result > 0) {
                try {
                    fileOutputStream.write(h264Buffer, 0, result);
                    fileOutputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (originVideoBuffers[targetIndex].isReadyToFill) {
                acceptVideo(rawVideoFrame, originVideoBuffers[targetIndex].buffer);
                originVideoBuffers[targetIndex].isReadyToFill = false;
                lastVideoQueueBufferIndex = targetIndex;
                videoFilterHandler.sendMessage(videoFilterHandler.obtainMessage(VideoFilterHandler.WHAT_INCOMING_BUFF, targetIndex, 0));
            }
        }
    }

    private void acceptVideo(byte[] src, byte[] dst) {
        int directionFlag = currentCamera == Camera.CameraInfo.CAMERA_FACING_BACK ? parameters.backCameraDirectionMode : parameters.frontCameraDirectionMode;
        ColorConvert.NV21Transform(src, dst, parameters.previewVideoWidth, parameters.previewVideoHeight, directionFlag);
    }

    public SurfaceVideoFilter acquireVideoFilter() {
        videoFilterLock.lock();
        return videoFilter;
    }

    public void releaseVideoFilter() {
        videoFilterLock.unlock();
    }

    public void setVideoFilter(SurfaceVideoFilter filter) {
        videoFilterLock.lock();
        if (videoFilter != null) {
            videoFilter.onDestroy();
        }
        videoFilter = filter;
        if (videoFilter != null) {
            videoFilter.onInit(parameters.videoWidth, parameters.videoHeight);
        }
        videoFilterLock.unlock();
    }

    private class VideoFilterHandler extends Handler {

        public static final int WHAT_INCOMING_BUFF = 1;
        public static final int WHAT_DRAW = 2;
        public static final int FILTER_LOCK_TOLERATION = 3; //3ms
        private FrameRateMeter frameRateMeter;
        private int sequenceNumber;

        public VideoFilterHandler(Looper looper) {
            super(looper);
            frameRateMeter = new FrameRateMeter();
            sequenceNumber = 0;
        }

        public float getDrawFrameRate() {
            return frameRateMeter.getFps();
        }

        private boolean lockVideoFilter() {
            try {
                boolean locked = videoFilterLock.tryLock(FILTER_LOCK_TOLERATION, TimeUnit.MILLISECONDS);
                if (locked) {
                    if (videoFilter != null) {
                        return true;
                    } else {
                        videoFilterLock.unlock();
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return false;
        }

        public void unlockVideoFilter() {
            videoFilterLock.unlock();
        }

        private void rendering(byte[] pixel) {
            synchronized (previewLock) {
                if (previewRender == null) {
                    return;
                }
                previewRender.rendering(pixel);
            }
        }

        private void checkScreenShot(byte[] pixel) {
            synchronized (screenShotLock) {
                if (screenShotListener != null) {
                    int[] argbPixel = new int[parameters.videoWidth * parameters.videoHeight];
                    ColorConvert.NV21ToARGB(pixel, argbPixel, parameters.videoWidth, parameters.videoHeight);
                    Bitmap result = Bitmap.createBitmap(argbPixel, parameters.videoWidth, parameters.videoHeight, Bitmap.Config.ARGB_8888);
                    CallbackDelivery.i().post(new ScreenShotListener.ScreenShotListenerRunnable(result, screenShotListener));
                    screenShotListener = null;
                }
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case WHAT_INCOMING_BUFF:
                    int targetIndex = msg.arg1;
                    /**
                     * orignVideoBuffs[targetIndex] is ready
                     * orignVideoBuffs[targetIndex]->orignNV21VideoBuff
                     */
                    System.arraycopy(originVideoBuffers[targetIndex].buffer, 0,
                            originNV21VideoBuffer.buffer, 0, originNV21VideoBuffer.buffer.length);
                    originVideoBuffers[targetIndex].isReadyToFill = true;
                    break;
                case WHAT_DRAW:
                    long time = (long) msg.obj;
                    long interval = time + loopingInterval - SystemClock.uptimeMillis();
                    synchronized (loopingLock) {
                        if (isPreviewing || isStreaming) {
                            if (interval > 0) {
                                videoFilterHandler.sendMessageDelayed(videoFilterHandler.obtainMessage(VideoFilterHandler.WHAT_DRAW,
                                        SystemClock.uptimeMillis() + interval), interval);
                            } else {
                                videoFilterHandler.sendMessage(videoFilterHandler.obtainMessage(VideoFilterHandler.WHAT_DRAW,
                                        SystemClock.uptimeMillis() + loopingInterval));
                            }
                        }
                    }
                    sequenceNumber++;
                    long nowTimeMs = SystemClock.uptimeMillis();
                    boolean isFilterLocked = lockVideoFilter();
                    if (isFilterLocked) {
                        boolean modified = videoFilter.onFrame(originNV21VideoBuffer.buffer, filteredNV21VideoBuffer.buffer, nowTimeMs, sequenceNumber);
                        unlockVideoFilter();
                        rendering(modified ? filteredNV21VideoBuffer.buffer : originNV21VideoBuffer.buffer);
                        checkScreenShot(modified ? filteredNV21VideoBuffer.buffer : originNV21VideoBuffer.buffer);
                        /**
                         * orignNV21VideoBuff is ready
                         * orignNV21VideoBuff->suitable4VideoEncoderBuff
                         */
                        if (parameters.mediacodecAVCColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            ColorConvert.NV21ToYUV420SP(modified ? filteredNV21VideoBuffer.buffer : originNV21VideoBuffer.buffer,
                                    suitable4VideoEncoderBuffer.buffer, parameters.videoWidth * parameters.videoHeight);
                        } else if (parameters.mediacodecAVCColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            ColorConvert.NV21ToYUV420P(modified ? filteredNV21VideoBuffer.buffer : originNV21VideoBuffer.buffer,
                                    suitable4VideoEncoderBuffer.buffer, parameters.videoWidth * parameters.videoHeight);
                        }
                    } else {
                        rendering(originNV21VideoBuffer.buffer);
                        checkScreenShot(originNV21VideoBuffer.buffer);
                        if (parameters.mediacodecAVCColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            ColorConvert.NV21ToYUV420SP(originNV21VideoBuffer.buffer,
                                    suitable4VideoEncoderBuffer.buffer, parameters.videoWidth * parameters.videoHeight);
                        } else if (parameters.mediacodecAVCColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            ColorConvert.NV21ToYUV420P(originNV21VideoBuffer.buffer,
                                    suitable4VideoEncoderBuffer.buffer, parameters.videoWidth * parameters.videoHeight);
                        }
                        originNV21VideoBuffer.isReadyToFill = true;
                    }
                    frameRateMeter.count();
                    //suitable4VideoEncoderBuff is ready
                    synchronized (videoEncoderLock) {
                        if (videoEncoder != null && isEncoderStarted) {
                            int eibIndex = videoEncoder.dequeueInputBuffer(-1);
                            if (eibIndex >= 0) {
                                ByteBuffer videoEncoderBuffer = videoEncoder.getInputBuffers()[eibIndex];
                                videoEncoderBuffer.position(0);
                                videoEncoderBuffer.put(suitable4VideoEncoderBuffer.buffer, 0, suitable4VideoEncoderBuffer.buffer.length);
                                videoEncoder.queueInputBuffer(eibIndex, 0, suitable4VideoEncoderBuffer.buffer.length, nowTimeMs * 1000, 0);
                            }
                        }
                    }
                    break;
            }
        }
    }

    private class VideoBuffer {

        public boolean isReadyToFill;
        public int colorFormat = -1;
        public byte[] buffer;

        public VideoBuffer(int colorFormat, int size) {
            this.isReadyToFill = true;
            this.colorFormat = colorFormat;
            this.buffer = new byte[size];
        }
    }
}
