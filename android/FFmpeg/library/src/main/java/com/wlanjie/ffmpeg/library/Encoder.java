package com.wlanjie.ffmpeg.library;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Leo Ma on 4/1/2016.
 */
@SuppressWarnings("ALL")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class Encoder {
    private static final String TAG = "Encoder";

    public static int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;

    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    private MediaCodecInfo videoCodecInfo;
    private MediaCodec videoMediaCodec;
    private MediaCodec audioMediaCodec;
    private MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

    private boolean mCameraFaceFront = true;

    private long mPresentTimeUs;

    private int mVideoColorFormat;

    private MediaFormat audioFormat;

    private Parameters mParameters;

    private CameraView mCameraView;

    private AudioRecord mAudioRecord;

    private Thread audioRecordThread;

    private boolean audioRecordLoop;

    private long mLastTimeMillis;

    private int mVideoFrameCount;

    public static class Parameters {
        public String videoCodec = "video/avc";
        public String audioCodec = "audio/mp4a-latm";
        public String x264Preset = "veryfast";
        public int previewWidth = 1280;
        public int previewHeight = 720;
        public int portraitWidth = 480;
        public int portraitHeight = 854;
        public int landscapeWidth = 854;
        public int landscapeHeight = 480;
        public int outWidth = 480;
        public int outHeight = 854;  // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        public int videoBitRate = 500 * 1000; // 500 kbps
        public int fps = 24;
        public int gop = 48;
        public int audioSampleRate = 44100;
        public int audioBitRate = 32 * 1000; // 32kbps
        public boolean useSoftEncoder = true;
    }

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv

    public Encoder(CameraView cameraView) {
        this(new Parameters(), cameraView);
    }

    public Encoder(Parameters parameters, CameraView cameraView) {
        this.mParameters = parameters;
        this.mCameraView = cameraView;
        mVideoColorFormat = chooseVideoEncoder();
    }

    public boolean start() {

        // the referent PTS for video and audio encoder.
        mPresentTimeUs = System.nanoTime() / 1000;

        setEncoderResolution(mParameters.outWidth, mParameters.outHeight);
        setEncoderFps(mParameters.fps);
        setEncoderGop(mParameters.gop);
        // Unfortunately for some android phone, the output fps is less than 10 limited by the
        // capacity of poor cheap chips even with x264. So for the sake of quick appearance of
        // the first picture on the player, a spare lower GOP value is suggested. But note that
        // lower GOP will produce more I frames and therefore more streaming data flow.
        // setEncoderGop(15);
        setEncoderBitrate(mParameters.videoBitRate);
        setEncoderPreset(mParameters.x264Preset);

        if (mParameters.useSoftEncoder && !openSoftEncoder()) {
            return false;
        }

        // audioMediaCodec pcm to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            audioMediaCodec = MediaCodec.createEncoderByType(mParameters.audioCodec);
        } catch (IOException e) {
            Log.e(TAG, "create audioMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        // setup the audioMediaCodec.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        audioFormat = MediaFormat.createAudioFormat(mParameters.audioCodec, mParameters.audioSampleRate, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mParameters.audioBitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // videoMediaCodec yuv to 264 es stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            videoMediaCodec = MediaCodec.createByCodecName(videoCodecInfo.getName());
        } catch (IOException e) {
            Log.e(TAG, "create videoMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        // setup the videoMediaCodec.
        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat(mParameters.videoCodec, mParameters.outWidth, mParameters.outHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mParameters.videoBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mParameters.fps);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mParameters.gop / mParameters.fps);
        videoMediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // start device and encoder.
        videoMediaCodec.start();
        audioMediaCodec.start();

        startPreview();
        startAudioRecord();
        mCameraView.startCamera(mParameters.fps);

        return true;
    }

    private void startAudioRecord() {
        mAudioRecord = chooseAudioRecord();
        audioRecordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                mAudioRecord.startRecording();
                byte pcmBuffer[] = new byte[4096];
                while (audioRecordLoop && !Thread.interrupted()) {
                    int size = mAudioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                    if (size <= 0) {
                        continue;
                    }
                    onGetPcmFrame(pcmBuffer, size);
                }
            }
        });
        audioRecordLoop = true;
        audioRecordThread.start();
    }

    private void startPreview() {
        mCameraView.setPreviewResolution(mParameters.previewWidth, mParameters.previewHeight);
        mCameraView.setPreviewCallback(new CameraView.PreviewCallback() {
            @Override
            public void onGetYuvFrame(byte[] data) {
                if (mVideoFrameCount == 0) {
                    mLastTimeMillis = System.nanoTime() / 1000000;
                    mVideoFrameCount++;
                } else {
                    if (++mVideoFrameCount >= 48) {

                    }
                }
                Encoder.this.onGetYuvFrame(data);
            }
        });
    }

    private void stopAudioRecord() {
        audioRecordLoop = false;
        if (audioRecordThread != null) {
            audioRecordThread.interrupt();
            try {
                audioRecordThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                audioRecordThread.interrupt();
            }
            audioRecordThread = null;
        }
        if (mAudioRecord != null) {
            mAudioRecord.setRecordPositionUpdateListener(null);
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    public void stop() {
        mCameraView.stopCamera();
        stopAudioRecord();
        if (mParameters.useSoftEncoder) {
            closeSoftEncoder();
        }

        if (audioMediaCodec != null) {
            audioMediaCodec.stop();
            audioMediaCodec.release();
            audioMediaCodec = null;
        }

        if (videoMediaCodec != null) {
            videoMediaCodec.stop();
            videoMediaCodec.release();
            videoMediaCodec = null;
        }
    }

    public void setCameraFrontFace() {
        mCameraFaceFront = true;
    }

    public void setCameraBackFace() {
        mCameraFaceFront = false;
    }

    public void setPreviewResolution(int width, int height) {
        mParameters.previewWidth = width;
        mParameters.previewHeight = height;
    }

    public void setPortraitResolution(int width, int height) {
        mParameters.outWidth = width;
        mParameters.outHeight = height;
        mParameters.portraitWidth = width;
        mParameters.portraitHeight = height;
        mParameters.landscapeWidth = height;
        mParameters.landscapeHeight = width;
    }

    public void setLandscapeResolution(int width, int height) {
        mParameters.outWidth = width;
        mParameters.outHeight = height;
        mParameters.landscapeWidth = width;
        mParameters.landscapeHeight = height;
        mParameters.portraitWidth = height;
        mParameters.portraitHeight = width;
    }

    public void setVideoHDMode() {
        mParameters.videoBitRate = 1200 * 1000;  // 1200 kbps
        mParameters.x264Preset = "veryfast";
    }

    public void setVideoSmoothMode() {
        mParameters.videoBitRate = 500 * 1000;  // 500 kbps
        mParameters.x264Preset = "superfast";
    }

    public int getPreviewWidth() {
        return mParameters.previewWidth;
    }

    public int getPreviewHeight() {
        return mParameters.previewHeight;
    }

    public int getOutputWidth() {
        return mParameters.outWidth;
    }

    public int getOutputHeight() {
        return mParameters.outHeight;
    }

    public void setScreenOrientation(int orientation) {
        mOrientation = orientation;
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mParameters.outWidth = mParameters.portraitWidth;
            mParameters.outHeight = mParameters.portraitHeight;
        } else if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mParameters.outWidth = mParameters.landscapeWidth;
            mParameters.outHeight = mParameters.landscapeHeight;
        }

        setEncoderResolution(mParameters.outWidth, mParameters.outHeight);
    }

    private void onProcessedYuvFrame(byte[] yuvFrame, long pts) {
        ByteBuffer[] inBuffers = videoMediaCodec.getInputBuffers();
        ByteBuffer[] outBuffers = videoMediaCodec.getOutputBuffers();

        int inBufferIndex = videoMediaCodec.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(yuvFrame, 0, yuvFrame.length);
            videoMediaCodec.queueInputBuffer(inBufferIndex, 0, yuvFrame.length, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = videoMediaCodec.dequeueOutputBuffer(videoBufferInfo, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                byte[] data = new byte[bb.limit()];
                bb.get(data);
                writeVideo(videoBufferInfo.presentationTimeUs / 1000, data);
                videoMediaCodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    /**
     * this method call by jni
     * @param es h264 stream
     * @param pts pts
     * @param isKeyFrame is key frame
     */
    private void onSoftEncodedData(byte[] es, int pts, boolean isKeyFrame) {
        ByteBuffer bb = ByteBuffer.wrap(es);
        videoBufferInfo.offset = 0;
        videoBufferInfo.size = es.length;
        videoBufferInfo.presentationTimeUs = pts;
//        videoBufferInfo.flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
        writeVideo(pts / 1000, es);
    }

    // when got encoded aac raw stream.
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        try {
            int channel = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            byte[] data = new byte[es.limit()];
            es.get(data);
            writeAudio(bi.presentationTimeUs / 1000, data, sampleRate, channel);
            es.rewind();
        } catch (Exception e) {
            Log.e(TAG, "muxer write audio sample failed.");
            e.printStackTrace();
        }
    }

    public void onGetPcmFrame(byte[] data, int size) {
        ByteBuffer[] inBuffers = audioMediaCodec.getInputBuffers();
        ByteBuffer[] outBuffers = audioMediaCodec.getOutputBuffers();

        int inBufferIndex = audioMediaCodec.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(data, 0, size);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            audioMediaCodec.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = audioMediaCodec.dequeueOutputBuffer(audioBufferInfo, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                bb.position(audioBufferInfo.offset);
                bb.limit(audioBufferInfo.offset + audioBufferInfo.size);
                int packetLen = audioBufferInfo.size + 7;
                byte[] adtsData = new byte[packetLen];
                int profile = 1; // AAC LC
                // 39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
                int freqIdx = 4; // 44.1KHz
                int chanCfg = 2; // CPE

                // fill in ADTS data
                adtsData[0] = (byte) 0xFF;
                adtsData[1] = (byte) 0xF9;
                adtsData[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
                adtsData[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
                adtsData[4] = (byte) ((packetLen & 0x7FF) >> 3);
                adtsData[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
                adtsData[6] = (byte) 0xFC;
                bb.get(adtsData, 7, audioBufferInfo.size);
                bb.position(audioBufferInfo.offset);
                writeAudio(audioBufferInfo.presentationTimeUs / 1000, adtsData, 44100, 2);
                audioMediaCodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    public void onGetYuvFrame(byte[] data) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        if (mParameters.useSoftEncoder) {
            if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
                swPortraitYuvFrame(data, pts);
            } else {
                swLandscapeYuvFrame(data, pts);
            }
        } else {
            byte[] processedData = mOrientation == Configuration.ORIENTATION_PORTRAIT ?
                    hwPortraitYuvFrame(data) : hwLandscapeYuvFrame(data);
            if (processedData != null) {
                onProcessedYuvFrame(processedData, pts);
            } else {
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),
                        new IllegalArgumentException("libyuv failure"));
            }
        }
    }

    private byte[] hwPortraitYuvFrame(byte[] data) {
        if (mCameraFaceFront) {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    return NV21ToI420(data, mParameters.previewWidth, mParameters.previewHeight, true, 270);
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    return NV21ToNV12(data, mParameters.previewWidth, mParameters.previewHeight, true, 270);
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        } else {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    return NV21ToI420(data, mParameters.previewWidth, mParameters.previewHeight, false, 90);
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    return NV21ToNV12(data, mParameters.previewWidth, mParameters.previewHeight, false, 90);
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        }
    }

    private byte[] hwLandscapeYuvFrame(byte[] data) {
        if (mCameraFaceFront) {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    return NV21ToI420(data, mParameters.previewWidth, mParameters.previewHeight, true, 0);
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    return NV21ToNV12(data, mParameters.previewWidth, mParameters.previewHeight, true, 0);
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        } else {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    return NV21ToI420(data, mParameters.previewWidth, mParameters.previewHeight, false, 0);
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    return NV21ToNV12(data, mParameters.previewWidth, mParameters.previewHeight, false, 0);
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        }
    }

    private void swPortraitYuvFrame(byte[] data, long pts) {
        if (mCameraFaceFront) {
            NV21SoftEncode(data, mParameters.previewWidth, mParameters.previewHeight, true, 270, pts);
        } else {
            NV21SoftEncode(data, mParameters.previewWidth, mParameters.previewHeight, false, 90, pts);
        }
    }

    private void swLandscapeYuvFrame(byte[] data, long pts) {
        if (mCameraFaceFront) {
            NV21SoftEncode(data, mParameters.previewWidth, mParameters.previewHeight, true, 0, pts);
        } else {
            NV21SoftEncode(data, mParameters.previewWidth, mParameters.previewHeight, false, 0, pts);
        }
    }

    public AudioRecord chooseAudioRecord() {
        int minBufferSize = AudioRecord.getMinBufferSize(mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
            mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
                mic = null;
            } else {
                Encoder.aChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            }
        } else {
            Encoder.aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }

        return mic;
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int codecCount = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecCount; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mParameters.videoCodec)) {
                    Log.i(TAG, String.format("videoMediaCodec %s types: %s", mci.getName(), type));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }

    // choose the right supported color format. @see below:
    private int chooseVideoEncoder() {
        // choose the encoder "video/avc":
        //      1. select default one when type matched.
        //      2. google avc is unusable.
        //      3. choose qcom avc.
        videoCodecInfo = chooseVideoEncoder(null);
        //videoCodecInfo = chooseVideoEncoder("google");
        //videoCodecInfo = chooseVideoEncoder("qcom");

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = videoCodecInfo.getCapabilitiesForType(mParameters.videoCodec);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];
            Log.i(TAG, String.format("videoMediaCodec %s supports color fomart 0x%x(%d)", videoCodecInfo.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }

        for (int i = 0; i < cc.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("videoMediaCodec %s support profile %d, level %d", videoCodecInfo.getName(), pl.profile, pl.level));
        }

        Log.i(TAG, String.format("videoMediaCodec %s choose color format 0x%x(%d)", videoCodecInfo.getName(), matchedColorFormat, matchedColorFormat));
        return matchedColorFormat;
    }

    public native int connect(String url);
    public native int writeAudio(long timestamp, byte[] data, int sampleRate, int channel);
    public native int writeVideo(long timestamp, byte[] data);
    public native void destroy();
    private native void setEncoderResolution(int outWidth, int outHeight);
    private native void setEncoderFps(int fps);
    private native void setEncoderGop(int gop);
    private native void setEncoderBitrate(int bitrate);
    private native void setEncoderPreset(String preset);
    private native byte[] NV21ToI420(byte[] yuvFrame, int width, int height, boolean flip, int rotate);
    private native byte[] NV21ToNV12(byte[] yuvFrame, int width, int height, boolean flip, int rotate);
    private native int NV21SoftEncode(byte[] yuvFrame, int width, int height, boolean flip, int rotate, long pts);
    private native boolean openSoftEncoder();
    private native void closeSoftEncoder();
}
