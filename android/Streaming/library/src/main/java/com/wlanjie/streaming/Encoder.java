package com.wlanjie.streaming;

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

import com.wlanjie.streaming.camera.CameraView;

import java.io.IOException;
import java.nio.ByteBuffer;

@SuppressWarnings("deprecation")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class Encoder {
    private static final String TAG = "Encoder";

    private int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;

    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    private MediaCodec videoMediaCodec;
    private MediaCodec audioMediaCodec;
    private MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

    private long mPresentTimeUs;

    private Parameters mParameters;

    private CameraView mCameraView;

    private AudioRecord mAudioRecord;

    private Thread audioRecordThread;

    private boolean audioRecordLoop;

    public static class Parameters {
        private String videoCodec = "video/avc";
        private String audioCodec = "audio/mp4a-latm";
        private String x264Preset = "veryfast";
        private int previewWidth = 1280;
        private int previewHeight = 768;
        public int portraitWidth = 480;
        public int portraitHeight = 854;
        public int landscapeWidth = 854;
        public int landscapeHeight = 480;
        public int outWidth = 720;
        public int outHeight = 1280;  // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        private int videoBitRate = 500 * 1000; // 500 kbps
        private int fps = 24;
        private int gop = 48;
        private int audioSampleRate = 44100;
        private int audioBitRate = 32 * 1000; // 32kbps
        private int channel;
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
    // YUY2 -> YUV422SP  yuyv yuyvo
    private FlvMuxer flvMuxer;

    public Encoder(CameraView cameraView) {
        this(new Parameters(), cameraView);
    }

    public Encoder(Parameters parameters, CameraView cameraView) {
        this.mParameters = parameters;
        this.mCameraView = cameraView;
        flvMuxer = new FlvMuxer(this);
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
        setEncoderBitrate(mParameters.videoBitRate);
        setEncoderPreset(mParameters.x264Preset);

        if (mParameters.useSoftEncoder) {
            if (!openH264Encoder()) {
                return false;
            }
            mParameters.channel = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
            if (!openAacEncoder(mParameters.channel, mParameters.audioSampleRate, mParameters.audioBitRate)) {
                return false;
            }
        } else {
            if (!initHardEncoder()) {
                return false;
            }
        }

        startPreview();
        startAudioRecord();
        mCameraView.start();

        try {
            flvMuxer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private MediaCodecInfo chooseVideoEncoder() {
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) continue;
            for (String s : info.getSupportedTypes()) {
                if (mParameters.videoCodec.equalsIgnoreCase(s))  {
                    return info;
                }
            }
        }
        return null;
    }

    private boolean initHardEncoder() {
        try {
            audioMediaCodec = MediaCodec.createEncoderByType(mParameters.audioCodec);
        } catch (IOException e) {
            Log.e(TAG, "create audioMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(mParameters.audioCodec, mParameters.audioSampleRate, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mParameters.audioBitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        try {
            MediaCodecInfo info = chooseVideoEncoder();
            if (info == null) return false;
            videoMediaCodec = MediaCodec.createByCodecName(info.getName());
        } catch (Exception e) {
            Log.e(TAG, "create videoMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat(mParameters.videoCodec, mParameters.outWidth, mParameters.outHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mParameters.videoBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mParameters.fps);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mParameters.gop / mParameters.fps);
        videoMediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // start device and encoder.
        videoMediaCodec.start();
        audioMediaCodec.start();
        return true;
    }

    private void startAudioRecord() {
        mAudioRecord = chooseAudioRecord();
        if (mAudioRecord == null) {
            throw new IllegalStateException("Initialized Audio Record error");
        }
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
                    convertPcmToAac(pcmBuffer, size);
                }
            }
        });
        audioRecordLoop = true;
        audioRecordThread.start();
    }

    private void startPreview() {
        mCameraView.addCallback(new CameraView.Callback() {

            public void onCameraOpened(CameraView cameraView, int previewWidth, int previewHeight) {
                mParameters.previewWidth = previewWidth;
                mParameters.previewHeight = previewHeight;
            }

            /**
             * Called when camera is closed.
             *
             * @param cameraView The associated {@link CameraView}.
             */
            public void onCameraClosed(CameraView cameraView) {
            }

            /**
             * Called when a picture is taken.
             *
             * @param cameraView The associated {@link CameraView}.
             * @param data       JPEG data.
             */
            public void onPreviewFrame(CameraView cameraView, byte[] data) {
                convertYuvFrame(data);
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
        mCameraView.stop();
        stopAudioRecord();
        if (mParameters.useSoftEncoder) {
            closeH264Encoder();
            closeAacEncoder();
        } else {
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

        while (true) {
            int outBufferIndex = videoMediaCodec.dequeueOutputBuffer(videoBufferInfo, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
//                byte[] data = new byte[bb.limit()];
//                bb.get(data);
//                writeVideo(videoBufferInfo.presentationTimeUs / 1000, data);
                flvMuxer.writeVideo(bb, bb.limit(), (int) (videoBufferInfo.presentationTimeUs / 1000));
                videoMediaCodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    /**
     * this method call by jni
     * @param data h264 stream
     * @param pts pts
     * @param isKeyFrame is key frame
     */
    private void onSoftEncodedData(byte[] data, int pts, boolean isKeyFrame) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        flvMuxer.writeVideo(bb, data.length, pts);
    }

    /**
     * this method call by jni
     * @param data aac data
     */
    private void onAacSoftEncodeData(byte[] data) {
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        ByteBuffer bb = ByteBuffer.wrap(data);
        flvMuxer.writeAudio(bb, data.length, mParameters.audioSampleRate, mParameters.channel, (int) pts);
    }

    /**
     *  Add ADTS header at the beginning of each and every AAC packet.
     *  This is needed as MediaCodec encoder generates a packet of raw
     *  AAC data.
     *
     *  Note the packetLen must count in the ADTS header itself.
     **/
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }

    private void convertPcmToAac(byte[] data, int size) {
        if (mParameters.useSoftEncoder) {
            byte[] pcm = new byte[size];
            System.arraycopy(data, 0, pcm, 0, size);
            encoderPcmToAac(pcm);
        } else {
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

            while (true) {
                int outBufferIndex = audioMediaCodec.dequeueOutputBuffer(audioBufferInfo, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    flvMuxer.writeAudio(bb, audioBufferInfo.size, mParameters.audioSampleRate, mParameters.channel, (int) (audioBufferInfo.presentationTimeUs / 1000));
                    audioMediaCodec.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        }

    }

    private void convertYuvFrame(byte[] data) {
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        if (mParameters.useSoftEncoder) {
            if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
                portraitYuvFrameX264(data, pts);
            } else {
                landscapeYuvFrameX264(data, pts);
            }
        } else {
            byte[] processedData = mOrientation == Configuration.ORIENTATION_PORTRAIT ?
                    portraitYuvFrameMediaCodec(data) : landscapeYuvFrameMediaCodec(data);
            if (processedData != null) {
                onProcessedYuvFrame(processedData, pts);
            } else {
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),
                        new IllegalArgumentException("libyuv failure"));
            }
        }
    }

    // COLOR_FormatYUV420Planar COLOR_FormatYUV420SemiPlanar
    // 二者的区别 http://blog.csdn.net/yuxiatongzhi/article/details/48708639
    private byte[] portraitYuvFrameMediaCodec(byte[] data) {
        // NV12
        // return NV21ToNV12(data, mParameters.previewWidth, mParameters.previewHeight, true, 270);
        // NV21 Color format
        boolean isFront = mCameraView.getFacing() == CameraView.FACING_FRONT;
        return NV21ToI420(data, mParameters.previewWidth, mParameters.previewHeight, isFront, isFront ? 270 : 90);
    }

    private byte[] landscapeYuvFrameMediaCodec(byte[] data) {
        boolean isFront = mCameraView.getFacing() == CameraView.FACING_FRONT;
        return NV21ToI420(data, mParameters.previewWidth, mParameters.previewHeight, isFront, 0);
    }

    private void portraitYuvFrameX264(byte[] data, long pts) {
        boolean isFront = mCameraView.getFacing() == CameraView.FACING_FRONT;
        NV21EncodeToH264(data, mParameters.previewWidth, mParameters.previewHeight, isFront, isFront ? 270 : 90, pts);
    }

    private void landscapeYuvFrameX264(byte[] data, long pts) {
        boolean isFront = mCameraView.getFacing() == CameraView.FACING_FRONT;
        NV21EncodeToH264(data, mParameters.previewWidth, mParameters.previewHeight, isFront, 0, pts);
    }

    private AudioRecord chooseAudioRecord() {
        int minBufferSize = AudioRecord.getMinBufferSize(mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
            mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
                mic = null;
            } else {
                aChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            }
        } else {
            aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }

        return mic;
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
    private native int NV21EncodeToH264(byte[] yuvFrame, int width, int height, boolean flip, int rotate, long pts);
    private native boolean openH264Encoder();
    private native void closeH264Encoder();
    private native boolean openAacEncoder(int channels, int sampleRate, int bitrate);
    private native int encoderPcmToAac(byte[] pcm);
    private native void closeAacEncoder();

    static {
        System.loadLibrary("wlanjie");
    }
}
