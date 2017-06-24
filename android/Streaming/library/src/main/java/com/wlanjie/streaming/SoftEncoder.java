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
        setEncoderBitrate(mBuilder.videoBitRate);

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
    private native boolean openH264Encoder();
    private native void closeH264Encoder();
    private native boolean openAacEncoder(int channels, int sampleRate, int bitrate);
    private native int encoderPcmToAac(byte[] pcm, int pts);
    private native void closeAacEncoder();
    protected native int rgbaEncodeToH264(byte[] yuvFrame, int width, int height, boolean flip, int rotate, long pts);
}
