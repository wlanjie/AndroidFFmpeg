//
// Created by wlanjie on 2017/9/25.
//

#include <queue>

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
    LOGE("endSection");
    // flush
    while (true) {
        AudioSamples null(nullptr);
        Packet audioPacket = audioEncoderContext->encode(null, ec);
        if (ec || !audioPacket)
            break;

        outputContext.writePacket(audioPacket, ec);
        if (ec) {
            LOGE("flush video error: %s", ec.message().c_str());
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
        // TODO
        AVRational timeBase = videoEncoderContext->stream().timeBase();
        AVRational frameRate = { arguments.videoFrameRate, 1 };
        AVRational timeBaseQ = AV_TIME_BASE_Q;
        int64_t duration = (int64_t) ((AV_TIME_BASE) * (1 / av_q2d(frameRate)));
        int64_t nowTime = gettime() - start_time;
        videoPacket.raw()->pts = av_rescale_q(nowTime, timeBaseQ, timeBase);;
        videoPacket.raw()->dts = videoPacket.raw()->pts;
        videoPacket.raw()->duration = av_rescale_q(duration, timeBaseQ, timeBase);

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
    Codec videoCodec = findEncodingCodec("libx264");
    Stream videoStream = outputContext.addStream(videoCodec, ec);
    if (ec) {
        LOGE("add video stream error %s", ec.message().c_str());
        return ADD_VIDEO_STREAM_ERROR;
    }
    videoStream.setTimeBase(Rational(1, 180000));
    videoStream.setFrameRate(Rational(arguments.videoFrameRate, 1));

    char rotateStr[1024];
    sprintf(rotateStr, "%d", 180);
    av_dict_set(&videoStream.raw()->metadata, "rotate", rotateStr, 0);

    videoEncoderContext = new VideoEncoderContext(videoStream);
    videoEncoderContext->setWidth(arguments.videoWidth);
    videoEncoderContext->setHeight(arguments.videoHeight);
    videoEncoderContext->setMaxBFrames(1);
    videoEncoderContext->setGopSize(arguments.videoGopSize);
    videoEncoderContext->setPixelFormat(AV_PIX_FMT_YUV420P);
    videoEncoderContext->setTimeBase(Rational(1, arguments.videoFrameRate));
    videoEncoderContext->setFrameRate(Rational(arguments.videoFrameRate, 1));
//    videoEncoderContext->raw()->qmin = 10;
//    videoEncoderContext->raw()->qmax = 51;
    videoEncoderContext->setBitRate(arguments.videoBitRate);
    videoEncoderContext->addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
//    videoEncoderContext->raw()->thread_type = FF_THREAD_SLICE;
    videoEncoderContext->setOption("preset", "slow", ec);
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
    outputContext.dump();
    start_time = gettime();
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

int ShortVideo::composeVideo(std::vector<char *> inputVideoUri, char* composeUri) {
    FormatContext inputContext;
    char* inputUri = inputVideoUri.front();
    inputContext.openInput(inputUri, ec);
    if (ec) {
        LOGE("open input path: %s error: %s", inputUri, ec.message().c_str());
        return OPEN_INPUT_ERROR;
    }
    inputContext.findStreamInfo(ec);
    if (ec) {
        LOGE("find stream error: %s path: %s", ec.message().c_str(), inputUri);
        return FIND_STREAM_ERROR;
    }

    OutputFormat outputFormat("mp4");
    outputContext.setFormat(outputFormat);
    size_t videoStreamIndex;
    size_t audioStreamIndex;
    
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        auto stream = inputContext.stream(i);
        auto codecContext = GenericCodecContext(stream);
        if (!outputContext.outputFormat().codecSupported(codecContext.codec())) {
            continue;
        }
        auto outputStream = outputContext.addStream(codecContext.codec(), ec);
        if (ec) {
            if (ec == Errors::FormatCodecUnsupported) {
                continue;
            } else {
                LOGE("add stream error: %s", ec.message().c_str());
                return ADD_VIDEO_STREAM_ERROR;
            }
        }
        if (outputStream.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i;
            char rotateStr[1024];
            sprintf(rotateStr, "%d", 180);
            av_dict_set(&outputStream.raw()->metadata, "rotate", rotateStr, 0);
        } else {
            audioStreamIndex = i;
        }
        outputStream.setTimeBase(stream.timeBase());
        auto outputCodecContext = GenericCodecContext(outputStream);
        outputCodecContext.copyContextFrom(codecContext, ec);
        if (ec) {
            LOGE("copy context error: %s", ec.message().c_str());
            return ADD_VIDEO_STREAM_ERROR;
        }
    }
    inputVideoUri.erase(inputVideoUri.begin());
    outputContext.openOutput(composeUri, ec);
    if (ec) {
        LOGE("open output error path: %s error: %s", composeUri, ec.message().c_str());
        return OPEN_OUTPUT_ERROR;
    }
    outputContext.dump();
    outputContext.writeHeader(ec);
    if (ec) {
        LOGE("write header error: %s", ec.message().c_str());
        return WRITE_HEADER_ERROR;
    }
    int videoPts = 0;
    int audioPts = 0;
    int videoDts = 0;
    int audioDts = 0;
    while (1) {
        auto packet = inputContext.readPacket(ec);
        if (!packet) {
            if (inputVideoUri.empty()) {
                break;
            } else {
                float videoDuration = 0;
                float audioDuration = 0;
                size_t videoStreamIndex = 0;
                size_t audioStreamIndex = 0;
                for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
                    auto stream = inputContext.stream(i);
                    if (stream.mediaType() == AVMEDIA_TYPE_VIDEO) {
                        videoStreamIndex = i;
                    } else if (stream.mediaType() == AVMEDIA_TYPE_AUDIO) {
                        audioStreamIndex = i;
                    }
                }
                videoDuration = (float) (av_q2d(inputContext.stream(videoStreamIndex).timeBase()) * videoPts);
                audioDuration = (float) (av_q2d(inputContext.stream(audioStreamIndex).timeBase()) * audioPts);
                if (audioDuration > videoDuration) {
                    videoPts = (int) (audioDuration / av_q2d(inputContext.stream(videoStreamIndex).timeBase()));
                    videoDts = videoPts;
                    audioPts++;
                    audioDts++;
                } else {
                    audioPts = (int) (videoDuration / av_q2d(inputContext.stream(audioStreamIndex).timeBase()));
                    audioDts = videoPts;
                    videoPts++;
                    videoDts++;
                }

                inputContext.close();
                inputContext.openInput(inputVideoUri.front(), ec);
                if (ec) {
                    LOGE("open input path: %s error: %s", inputUri, ec.message().c_str());
                    return OPEN_INPUT_ERROR;
                }
                inputContext.findStreamInfo(ec);
                if (ec) {
                    LOGE("find stream error: %s path: %s", ec.message().c_str(), inputUri);
                    return FIND_STREAM_ERROR;
                }
                inputVideoUri.erase(inputVideoUri.begin());
                continue;
            }
        }
        if (packet.streamIndex() == videoStreamIndex) {
            if (strcmp(inputContext.raw()->filename, inputUri) != 0) {
                packet.setPts(packet.pts().timestamp() + videoPts);
                packet.setDts(packet.dts().timestamp() + videoDts);
            } else {
                videoPts = packet.pts().timestamp();
                videoDts = packet.dts().timestamp();
            }
        } else if (packet.streamIndex() == audioStreamIndex) {
            if (strcmp(inputContext.raw()->filename, inputUri) != 0) {
                packet.setPts(packet.pts().timestamp() + audioPts);
                packet.setDts(packet.dts().timestamp() + audioDts);
            } else {
                audioPts = packet.pts().timestamp();
                audioDts = packet.dts().timestamp();
            }
        }

        packet.raw()->pts = av_rescale_q_rnd(packet.pts().timestamp(), inputContext.stream(packet.streamIndex()).timeBase(), outputContext.stream(packet.streamIndex()).timeBase(), (AVRounding) (AV_ROUND_INF | AV_ROUND_PASS_MINMAX));
        packet.raw()->dts = av_rescale_q_rnd(packet.pts().timestamp(), inputContext.stream(packet.streamIndex()).timeBase(), outputContext.stream(packet.streamIndex()).timeBase(), (AVRounding) (AV_ROUND_INF | AV_ROUND_PASS_MINMAX));
        packet.raw()->pos = -1;
        outputContext.writePacket(packet, ec);
        if (ec) {
            LOGE("write packet error: %s", ec.message().c_str());
        }
    }
    outputContext.writeTrailer(ec);
    inputContext.close();
    outputContext.close();
    return SUCCESS;
}

void ShortVideo::close() {

}

}