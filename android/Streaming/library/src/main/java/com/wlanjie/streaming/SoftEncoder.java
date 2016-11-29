package com.wlanjie.streaming;

import android.content.res.Configuration;

import com.wlanjie.streaming.camera.CameraView;

import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 2016/11/29.
 */

class SoftEncoder extends Encoder {

    SoftEncoder(CameraView cameraView) {
        super(cameraView);
    }

    SoftEncoder(Parameters parameters, CameraView cameraView) {
        super(parameters, cameraView);
    }

    @Override
    boolean openEncoder() {

        setEncoderFps(mParameters.fps);
        setEncoderGop(mParameters.gop);
        // Unfortunately for some android phone, the output fps is less than 10 limited by the
        // capacity of poor cheap chips even with x264. So for the sake of quick appearance of
        // the first picture on the player, a spare lower GOP value is suggested. But note that
        // lower GOP will produce more I frames and therefore more streaming data flow.
        setEncoderBitrate(mParameters.videoBitRate);
        setEncoderPreset(mParameters.x264Preset);

        return openH264Encoder() &&
                openAacEncoder(mParameters.channel, mParameters.audioSampleRate, mParameters.audioBitRate);
    }

    @Override
    void closeEncoder() {
        closeH264Encoder();
        closeAacEncoder();
    }

    @Override
    void convertYuvToH264(byte[] data) {
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        boolean isFront = mCameraView.getFacing() == CameraView.FACING_FRONT;
        NV21EncodeToH264(data,
                mParameters.previewWidth,
                mParameters.previewHeight,
                isFront,
                mOrientation == Configuration.ORIENTATION_PORTRAIT ? isFront ? 270 : 90 : 0, pts);
    }

    @Override
    void convertPcmToAac(byte[] data, int size) {
        byte[] pcm = new byte[size];
        System.arraycopy(data, 0, pcm, 0, size);
        encoderPcmToAac(pcm);
    }

    /**
     * this method call by jni
     * @param data h264 stream
     * @param pts pts
     * @param isKeyFrame is key frame
     */
    private void onH264EncodedData(byte[] data, int pts, boolean isKeyFrame) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        flvMuxer.writeVideo(bb, data.length, pts);
    }

    /**
     * this method call by jni
     * @param data aac data
     */
    private void onAacEncodeData(byte[] data) {
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        ByteBuffer bb = ByteBuffer.wrap(data);
        flvMuxer.writeAudio(bb, data.length, mParameters.audioSampleRate, mParameters.channel, (int) pts);
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
    private native int encoderPcmToAac(byte[] pcm);
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
}
