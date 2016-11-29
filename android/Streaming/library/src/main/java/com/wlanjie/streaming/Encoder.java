package com.wlanjie.streaming;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;

import com.wlanjie.streaming.camera.CameraView;

import java.io.IOException;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
abstract class Encoder {

    int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    /**
     * first frame time
     */
    long mPresentTimeUs;

    Parameters mParameters;

    CameraView mCameraView;

    private AudioRecord mAudioRecord;

    private Thread audioRecordThread;

    private boolean audioRecordLoop;

    public static class Parameters {
        String videoCodec = "video/avc";
        String audioCodec = "audio/mp4a-latm";
        String x264Preset = "veryfast";
        int previewWidth = 1280;
        int previewHeight = 768;
        public int portraitWidth = 480;
        public int portraitHeight = 854;
        public int landscapeWidth = 854;
        public int landscapeHeight = 480;
        public int outWidth = 720;
        public int outHeight = 1280;  // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        int videoBitRate = 500 * 1000; // 500 kbps
        int fps = 24;
        int gop = 48;
        int audioSampleRate = 44100;
        int audioBitRate = 32 * 1000; // 32kbps
        protected int channel;
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
    FlvMuxer flvMuxer;

    public Encoder(CameraView cameraView) {
        this(new Parameters(), cameraView);
    }

    public Encoder(Parameters parameters, CameraView cameraView) {
        this.mParameters = parameters;
        this.mCameraView = cameraView;
        flvMuxer = new FlvMuxer(this);
    }

    boolean start() {

        // the referent PTS for video and audio encoder.
        mPresentTimeUs = System.nanoTime() / 1000;

        mAudioRecord = chooseAudioRecord();
        if (mAudioRecord == null) {
            return false;
        }
        mParameters.channel = mAudioRecord.getChannelCount();
        setEncoderResolution(mParameters.outWidth, mParameters.outHeight);
        openEncoder();

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

    /**
     * open h264 aac encoder
     * @return true success, false failed
     */
    abstract boolean openEncoder();

    /**
     * close h264 aac encoder
     */
    abstract void closeEncoder();

    /**
     * covert pcm to aac
     * @param data pcm data
     * @param size pcm data size
     */
    abstract void convertPcmToAac(byte[] data, int size);

    /**
     * convert yuv to h264
     * @param data yuv data
     */
    abstract void convertYuvToH264(byte[] data);

    /**
     * start audio record
     */
    private void startAudioRecord() {
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

    /**
     * start camera preview
     */
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
                convertYuvToH264(data);
            }
        });
    }

    /**
     * stop audio record
     */
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

    /**
     * stop camera preview, audio record and close encoder
     */
    public void stop() {
        mCameraView.stop();
        stopAudioRecord();
        closeEncoder();
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

    /**
     * init audio record
     * @return audio record
     */
    private AudioRecord chooseAudioRecord() {
        int minBufferSize = AudioRecord.getMinBufferSize(mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
            mic = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mParameters.audioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) {
                mic = null;
            }
        }
        return mic;
    }

    /**
     * set output width and height
     * @param outWidth output width
     * @param outHeight output height
     */
    private native void setEncoderResolution(int outWidth, int outHeight);

    /**
     * connect rtmp server
     * @param url rtmp url
     * @return 0 is success, other failed.
     */
    protected native int connect(String url);

    /**
     * publish audio to rtmp server
     * @param timestamp pts
     * @param data aac data
     * @param sampleRate aac sample rate
     * @param channel aac channel
     * @return 0 is success, other failed.
     */
    public native int writeAudio(long timestamp, byte[] data, int sampleRate, int channel);

    /**
     * publish video to rtmp server
     * @param timestamp pts
     * @param data h264 data
     * @return 0 is success, other failed.
     */
    public native int writeVideo(long timestamp, byte[] data);

    /**
     * destroy rtmp resources {@link #connect(String url)}
     */
    protected native void destroy();

    /**
     * convert NV21 to I420
     * @param yuvFrame NV21 data
     * @param width preview width
     * @param height preview height
     * @param flip
     * @param rotate
     * @return I420 data
     */
    protected native byte[] NV21ToI420(byte[] yuvFrame, int width, int height, boolean flip, int rotate);

    /**
     * convert NV21 to NV12
     * @param yuvFrame NV21 data
     * @param width preview width
     * @param height preview height
     * @param flip
     * @param rotate
     * @return NV12 data
     */
    protected native byte[] NV21ToNV12(byte[] yuvFrame, int width, int height, boolean flip, int rotate);

    static {
        System.loadLibrary("wlanjie");
    }
}
