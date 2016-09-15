package com.wlanjie.ffmpeg.library;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 16/9/11.
 */
public class VideoSenderThread extends Thread {

    private static final long WAIT_TIME = 10000;
    private MediaCodec.BufferInfo mediaCodecInfo;
    private long startTime = 0;
    private MediaCodec videoEncoder;
    private FLVDataCollecter flvDataCollecter;
    private boolean shouldQuit = false;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public VideoSenderThread(String name, MediaCodec videoEncoder, FLVDataCollecter collecter) {
        super(name);
        mediaCodecInfo = new MediaCodec.BufferInfo();
        startTime = 0;
        this.videoEncoder = videoEncoder;
        flvDataCollecter = collecter;
    }

    public void quit() {
        shouldQuit = true;
        interrupt();
    }

    private void sendAVCDecoderConfigurationRecord(long tms, MediaFormat format) {
        byte[] avcDecoderConfigureationRecord = Packager.H264Packager.generateAVCDecoderConfigurationRecord(format);
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + avcDecoderConfigureationRecord.length;
        byte[] finalBuffer = new byte[packetLen];
        Packager.FLVPackager.fillFLVVideoTag(finalBuffer, 0, true, true, avcDecoderConfigureationRecord.length);
        System.arraycopy(avcDecoderConfigureationRecord, 0, finalBuffer, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH, avcDecoderConfigureationRecord.length);
        FLVData flvData = new FLVData();
        flvData.byteBuffer = finalBuffer;
        flvData.size = finalBuffer.length;
        flvData.dts = (int) tms;
        flvData.flvTagType = FLVData.FLV_RTMP_PACKET_TYPE_VIDEO;
        flvData.videoFrameType = FLVData.NALU_TYPE_IDR;
        flvDataCollecter.collect(flvData, RtmpSender.FROM_VIDEO);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int realDataLength = realData.remaining();
        int packetLen= Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + Packager.FLVPackager.NALU_HEADER_LENGTH + realDataLength;
        byte[] finalBuffer = new byte[packetLen];
        realData.get(finalBuffer, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + Packager.FLVPackager.NALU_HEADER_LENGTH, realDataLength);
        int frameType = finalBuffer[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + Packager.FLVPackager.NALU_HEADER_LENGTH] & 0x1F;
        Packager.FLVPackager.fillFLVVideoTag(finalBuffer, 0, false, frameType == 5, realDataLength);
        FLVData flvData = new FLVData();
        flvData.byteBuffer = finalBuffer;
        flvData.size = finalBuffer.length;
        flvData.dts = (int) tms;
        flvData.flvTagType = FLVData.FLV_RTMP_PACKET_TYPE_VIDEO;
        flvData.videoFrameType = frameType;
        flvDataCollecter.collect(flvData, RtmpSender.FROM_VIDEO);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void run() {
        super.run();
        while (!shouldQuit) {
            int eobIndex = videoEncoder.dequeueOutputBuffer(mediaCodecInfo, WAIT_TIME);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    sendAVCDecoderConfigurationRecord(0, videoEncoder.getOutputFormat());
                    break;
                default:
                    if (startTime == 0) {
                        startTime = mediaCodecInfo.presentationTimeUs / 1000;
                    }
                    if (mediaCodecInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && mediaCodecInfo.size != 0) {
                        ByteBuffer realData = videoEncoder.getOutputBuffers()[eobIndex];
                        realData.position(mediaCodecInfo.offset + 4);
                        realData.limit(mediaCodecInfo.offset + mediaCodecInfo.size);
                        sendRealData(mediaCodecInfo.presentationTimeUs / 1000, realData);
                    }
                    videoEncoder.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
        mediaCodecInfo = null;
    }
}
