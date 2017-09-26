//
// Created by wlanjie on 2017/9/25.
//

#include "shortvideo.h"
#include "log.h"
#include "errorcode.h"
#include "arguments.h"

#include "libyuv.h"

namespace av {

ShortVideo::ShortVideo() {

}

ShortVideo::~ShortVideo() {

}

int ShortVideo::openOutput(std::string &uri) {
    OutputFormat outputFormat;
    outputFormat.setFormat("mp4");
    outputContext.openOutput(uri, ec);
    if (ec) {
        LOGE("open output error path %s error %s", uri.c_str(), ec.message().c_str());
        return OPEN_OUTPUT_ERROR;
    }
    return SUCCESS;
}

int ShortVideo::beginSection() {
    audioNextPts = 0;
    videoNextPts = 0;
    initVideoEncoderContext();
    initAudioEncoderContext();
    outputContext.writeHeader(ec);
    if (ec) {
        LOGE("write header error %s", ec.message().c_str());
        return WRITE_HEADER_ERROR;
    }
    return SUCCESS;
}

int ShortVideo::endSection() {
    // flush
    while (true) {
        AudioSamples null(nullptr);
        Packet audioPacket = audioEncoderContext->encode(null, ec);
        if (ec || !audioPacket)
            break;

        outputContext.writePacket(audioPacket, ec);
        if (ec) {
            LOGE("flush video error: %s", ec.message().c_str());
            return FLUSH_VIDEO_ERROR;
        }
    }

    while (true) {
        VideoFrame null(nullptr);
        Packet videoPacket = videoEncoderContext->encode(null, ec);
        if (ec || !videoPacket) {
            break;
        }
        outputContext.writePacket(videoPacket, ec);
        if (ec) {
            LOGE("flush audio error: %s", ec.message().c_str());
            return FLUSH_AUDIO_ERROR;
        }
    }

    outputContext.writeTrailer(ec);
    if (ec) {
        LOGE("write trailer error %s", ec.message().c_str());
        return WRITE_TRAILER_ERROR;
    }
    videoEncoderContext->close();
    audioEncoderContext->close();
    outputContext.close();
    delete videoEncoderContext;
    delete audioEncoderContext;
    return SUCCESS;
}

int ShortVideo::encodeAudio(uint8_t *audioFrame) {
    AudioSamples ouSamples(audioEncoderContext->sampleFormat(), audioEncoderContext->frameSize(), audioEncoderContext->channelLayout(), audioEncoderContext->sampleRate());
    ouSamples.raw()->data[0] = audioFrame;
    ouSamples.raw()->pts = audioNextPts;
    audioNextPts += audioEncoderContext->frameSize();
    Packet audioPacket = audioEncoderContext->encode(ouSamples, ec);
    if (ec) {
        LOGE("encode audio error: %s.", ec.message().c_str());
        return ENCODING_AUDIO_ERROR;
    }
    if (!audioPacket) {
        return ENCODING_AUDIO_ERROR;
    }
    if (audioPacket.isComplete()) {
        outputContext.writePacket(audioPacket, ec);
        if (ec) {
            LOGE("write audio packet error: %s", ec.message().c_str());
            return WRITE_PACKET_ERROR;
        }
    }
    return SUCCESS;
}

int ShortVideo::encodeVideo(uint8_t *videoFrame) {
    int width = arguments.videoWidth;
    int height = arguments.videoHeight;
    int ySize = width * height;
    uint8_t *data = static_cast<uint8_t *> (av_malloc(width * height * 3 / 2));
    uint8_t *y = data;
    uint8_t *u = y + ySize;
    uint8_t *v = u + (ySize >> 2);
    int yStride = width;
    int uStride = width >> 1;
    int vStride = width >> 1;
    int result = libyuv::RGBAToI420((const uint8 *) videoFrame, width * 4,
                                    y, yStride,
                                    u, uStride,
                                    v, vStride,
                                    width, height);
    if (result != 0) {
        LOGE("rgba to I420 error.");
        return RGBA_TO_I420_ERROR;
    }
    result = libyuv::I420Rotate(
            y, yStride,
            u, uStride,
            v, vStride,
            y, yStride,
            u, uStride,
            v, vStride,
            width, height,
            libyuv::RotationMode::kRotate180
    );
    if (result != 0) {
        LOGE("yuv rotate error.");
        return RGBA_TO_I420_ERROR;
    }
    VideoFrame outFrame(AV_PIX_FMT_YUV420P, width, height);
    outFrame.raw()->linesize[0] = yStride;
    outFrame.raw()->linesize[1] = uStride;
    outFrame.raw()->linesize[2] = vStride;
    outFrame.raw()->data[0] = y;
    outFrame.raw()->data[1] = u;
    outFrame.raw()->data[2] = v;
    outFrame.raw()->pts = videoNextPts++;
    Packet videoPacket = videoEncoderContext->encode(outFrame, ec);
    av_free(data);
    if (ec || !videoPacket) {
        LOGE("encode video error: %s.", ec.message().c_str());
        return ENCODING_VIDEO_ERROR;
    }
    if (videoPacket.isComplete()) {
        outputContext.writePacket(videoPacket, ec);
        if (ec) {
            LOGE("write video packet error: %s.", ec.message().c_str());
            return WRITE_PACKET_ERROR;
        }
    }
    return SUCCESS;
}

void ShortVideo::setArguments(Arguments &arg) {
    arguments = arg;
}

int ShortVideo::initVideoEncoderContext() {
    Codec videoCodec = findEncodingCodec(AV_CODEC_ID_H264);
    Stream videoStream = outputContext.addStream(videoCodec, ec);
    if (ec) {
        LOGE("add video stream error %s", ec.message().c_str());
        return ADD_VIDEO_STREAM_ERROR;
    }
    videoStream.setTimeBase(Rational( 1, arguments.videoFrameRate ));
    // TODO
    videoStream.setFrameRate(arguments.videoFrameRate);
    videoEncoderContext = new VideoEncoderContext(videoStream);
    videoEncoderContext->setWidth(arguments.videoWidth);
    videoEncoderContext->setHeight(arguments.videoHeight);
    videoEncoderContext->setMaxBFrames(3);
    videoEncoderContext->setGopSize(arguments.videoGopSize);
    videoEncoderContext->setPixelFormat(AV_PIX_FMT_YUV420P);
    videoEncoderContext->setTimeBase(videoStream.timeBase());
    videoEncoderContext->setBitRate(arguments.videoBitRate);
    videoEncoderContext->addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
    videoEncoderContext->setOption("preset", "ultrafast", ec);
    if (ec) {
        LOGE("video encoder setOption error %s", ec.message().c_str());
        return SET_OPTION_ERROR;
    }
    Dictionary dictionary;
    dictionary.set("tune", "zerolatency");
    dictionary.set("profile", "baseline");
    videoEncoderContext->open(dictionary, videoCodec, ec);
    if (ec) {
        LOGE("video encoder open error: %s.", ec.message().c_str());
        return OPEN_VIDEO_ENCODER_ERROR;
    }
    return SUCCESS;
}

int ShortVideo::initAudioEncoderContext() {
    Codec audioCodec = findEncodingCodec(AV_CODEC_ID_AAC);
    Stream audioStream = outputContext.addStream(audioCodec, ec);
    if (ec) {
        LOGE("add audio stream error %s", ec.message().c_str());
        return ADD_AUDIO_STREAM_ERROR;
    }
    audioStream.setTimeBase(Rational(1, arguments.audioSampleRate));
    audioEncoderContext = new AudioEncoderContext(audioStream);
    audioEncoderContext->setSampleFormat(AV_SAMPLE_FMT_S16);
    audioEncoderContext->setSampleRate(arguments.audioSampleRate);
    audioEncoderContext->setChannelLayout(AV_CH_LAYOUT_MONO);
    audioEncoderContext->setChannels(1);
    audioEncoderContext->setTimeBase(audioStream.timeBase());
    audioEncoderContext->setBitRate(arguments.audioBitRate);
    audioEncoderContext->open(audioCodec, ec);
    if (ec) {
        LOGE("audio encoder open error: %s.", ec.message().c_str());
        return OPEN_AUDIO_ENCODER_ERROR;
    }
    return SUCCESS;
}

void ShortVideo::close() {

}

}