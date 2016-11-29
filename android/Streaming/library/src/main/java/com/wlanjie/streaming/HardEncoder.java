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

    HardEncoder(CameraView cameraView) {
        super(cameraView);
    }

    HardEncoder(Parameters parameters, CameraView cameraView) {
        super(parameters, cameraView);
    }

    @Override
    boolean openEncoder() {
        try {
            audioMediaCodec = MediaCodec.createEncoderByType(mParameters.audioCodec);
        } catch (IOException e) {
            Log.e(TAG, "create audioMediaCodec failed.");
            e.printStackTrace();
            return false;
        }

        MediaFormat audioFormat = MediaFormat.createAudioFormat(mParameters.audioCodec, mParameters.audioSampleRate, mParameters.channel);
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
        // COLOR_FormatYUV420Planar COLOR_FormatYUV420SemiPlanar
        // 二者的区别 http://blog.csdn.net/yuxiatongzhi/article/details/48708639
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
    void convertYuvToH264(byte[] data) {
        boolean isFront = mCameraView.getFacing() == CameraView.FACING_FRONT;
        byte[] processedData = NV21ToI420(data,
                mParameters.previewWidth,
                mParameters.previewHeight,
                isFront,
                mOrientation == Configuration.ORIENTATION_PORTRAIT ? isFront ? 270 : 90 : 0);

        if (processedData != null) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            onProcessedYuvFrame(processedData, pts);
        } else {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),
                    new IllegalArgumentException("libyuv failure"));
        }
    }

    @Override
    void convertPcmToAac(byte[] data, int size) {
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
}
