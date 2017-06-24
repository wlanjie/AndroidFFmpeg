package com.wlanjie.streaming;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.wlanjie.streaming.camera.CameraView;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 2016/11/29.
 */

@SuppressWarnings("deprecation")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class HardEncoder extends Encoder {

    private final static String TAG = HardEncoder.class.getName();

    private MediaCodec audioMediaCodec;
    private MediaCodec videoMediaCodec;
    private MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
    private int videoColorFormat;
    private byte[] aac = new byte[1024 * 1024];
    private byte[] h264 = new byte[1024 * 1024];

    HardEncoder(Builder builder) {
        super(builder);
    }

    @Override
    boolean openEncoder() {
        try {
            audioMediaCodec = MediaCodec.createEncoderByType(mBuilder.audioCodec);
        } catch (IOException e) {
            Log.e(TAG, "create audioMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        MediaFormat audioFormat = MediaFormat.createAudioFormat(mBuilder.audioCodec, mBuilder.audioSampleRate, mAudioRecord.getChannelCount());
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBuilder.audioBitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        try {
            MediaCodecInfo info = chooseVideoEncoder();
            if (info == null) return false;
            videoMediaCodec = MediaCodec.createByCodecName(info.getName());

            // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
            MediaFormat videoFormat = MediaFormat.createVideoFormat(mBuilder.videoCodec, mBuilder.width, mBuilder.height);
            // COLOR_FormatYUV420Planar COLOR_FormatYUV420SemiPlanar
            // 二者的区别 http://blog.csdn.net/yuxiatongzhi/article/details/48708639
            videoColorFormat = chooseEncoderColorFormat(info);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, videoColorFormat);
            videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBuilder.videoBitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mBuilder.fps);
            // gop / fps
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mBuilder.fps * 2 / mBuilder.fps);
            videoMediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            Log.e(TAG, "create videoMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        // start device and encoder.
        videoMediaCodec.start();
        audioMediaCodec.start();
        return true;
    }

    @Override
    void closeEncoder() {
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

    @Override
    void rgbaEncoderToH264(byte[] data) {
        byte[] yuvData = null;
        switch (videoColorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                yuvData = rgbaToI420(data, mBuilder.previewWidth, mBuilder.previewHeight, false, 0);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                break;
        }

        if (yuvData != null) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            onProcessedYuvFrame(yuvData, pts);
        } else {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),
                    new IllegalArgumentException("libyuv failure"));
        }
    }

    @Override
    void convertPcmToAac(byte[] aacFrame, int size) {
        ByteBuffer[] inBuffers = audioMediaCodec.getInputBuffers();
        ByteBuffer[] outBuffers = audioMediaCodec.getOutputBuffers();

        int inBufferIndex = audioMediaCodec.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(aacFrame, 0, size);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            audioMediaCodec.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        while (true) {
            int outBufferIndex = audioMediaCodec.dequeueOutputBuffer(audioBufferInfo, 0);
            if (outBufferIndex >= 0) {
                int outBitSize = audioBufferInfo.size;
                int outPacketSize = outBitSize + 7;

                ByteBuffer bb = outBuffers[outBufferIndex];
                bb.position(audioBufferInfo.offset);
                bb.limit(audioBufferInfo.offset + outBitSize);

//                byte[] data = new byte[outPacketSize];
                addADTStoPacket(aac, outPacketSize);

                bb.get(aac, 7, outBitSize);
                bb.position(audioBufferInfo.offset);
                muxerAac(aac, outPacketSize, (int) (audioBufferInfo.presentationTimeUs / 1000));
                audioMediaCodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
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
//                byte[] data = new byte[videoBufferInfo.size];
                bb.get(h264, 0, videoBufferInfo.size);
                muxerH264(h264, videoBufferInfo.size, (int) (videoBufferInfo.presentationTimeUs / 1000));
                videoMediaCodec.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    private MediaCodecInfo chooseVideoEncoder() {
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) continue;
            for (String s : info.getSupportedTypes()) {
                if (mBuilder.videoCodec.equalsIgnoreCase(s))  {
                    return info;
                }
            }
        }
        return null;
    }

    private int chooseEncoderColorFormat(MediaCodecInfo info) {
        int colorFormat = 0;
        MediaCodecInfo.CodecCapabilities codecCapabilities = info.getCapabilitiesForType(mBuilder.videoCodec);
        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            if (codecCapabilities.colorFormats[i] >= MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar &&
                    codecCapabilities.colorFormats[i] <= MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                if (codecCapabilities.colorFormats[i] > colorFormat) {
                    colorFormat = codecCapabilities.colorFormats[i];
                }
            }
        }
        return colorFormat;
    }
}
