package com.wlanjie.streaming;

/**
 * Created by wlanjie on 2016/11/29.
 */

class SoftEncoder extends Encoder {

    SoftEncoder(Builder builder) {
        super(builder);
    }

    @Override
    boolean openEncoder() {

        setEncoderFps(mBuilder.fps);
        setEncoderGop(mBuilder.fps * 2);
        // Unfortunately for some android phone, the output fps is less than 10 limited by the
        // capacity of poor cheap chips even with x264. So for the sake of quick appearance of
        // the first picture on the player, a spare lower GOP value is suggested. But note that
        // lower GOP will produce more I frames and therefore more streaming data flow.
        setEncoderBitrate(mBuilder.videoBitRate);
        setEncoderPreset(mBuilder.x264Preset);

        return openH264Encoder() &&
                openAacEncoder(mAudioRecord.getChannelCount(), mBuilder.audioSampleRate, mBuilder.audioBitRate);
    }

    @Override
    void closeEncoder() {
        closeH264Encoder();
        closeAacEncoder();
    }

    @Override
    void rgbaEncoderToH264(byte[] data) {
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        rgbaEncodeToH264(data,
                mBuilder.previewWidth,
                mBuilder.previewHeight,
                false,
                0, pts);
    }

    @Override
    void convertPcmToAac(byte[] aacFrame, int size) {
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        encoderPcmToAac(aacFrame, (int) pts);
    }

    /**
     * set libx264 params fps
     * @param fps
     */
    private native void setEncoderFps(int fps);
    private native void setEncoderGop(int gop);
    private native void setEncoderBitrate(int bitrate);
    private native void setEncoderPreset(String preset);
    private native boolean openH264Encoder();
    private native void closeH264Encoder();
    private native boolean openAacEncoder(int channels, int sampleRate, int bitrate);
    private native int encoderPcmToAac(byte[] pcm, int pts);
    private native void closeAacEncoder();

    /**
     *
     * @param yuvFrame
     * @param width
     * @param height
     * @param flip
     * @param rotate
     * @param pts
     * @return
     */
    protected native int NV21EncodeToH264(byte[] yuvFrame, int width, int height, boolean flip, int rotate, long pts);

    protected native int rgbaEncodeToH264(byte[] yuvFrame, int width, int height, boolean flip, int rotate, long pts);
}
