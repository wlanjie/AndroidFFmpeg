//
// Created by wlanjie on 2017/9/9.
//

#include "video.h"
#include "log.h"
#include "codeccontext.h"
#include "videorescaler.h"
#include "audioresampler.h"

namespace av {

Video::Video() {

}

Video::~Video() {

}

int Video::openInput(std::string uri) {
    inputContext.openInput(uri, ec);
    if (ec) {
        LOGE("Can't open input path: %s error: %s", uri, ec.message());
        return OPEN_INPUT_ERROR;
    }
    inputContext.findStreamInfo(ec);
    if (ec) {
        LOGE("Can't find stream error: %s", ec.message());
        return FIND_STREAM_ERROR;
    }
    return SUCCESS;
}

int Video::openOutput(std::string uri) {
    outputFormat.setFormat("mp4");
    outputContext.setFormat(outputFormat);
    outputContext.openOutput(uri, ec);
    if (ec) {
        LOGE("Can't open output path: %s uri error: %s", ec.message());
        return OPEN_OUTPUT_ERROR;
    }
    return SUCCESS;
}

int Video::scale(int newWidth, int newHeight) {
    size_t videoStreamIndex = 4000;
    size_t audioStreamIndex = 4001;

    Codec videoCodec;
    Stream videoOutputStream;
    Codec audioCodec;
    Stream audioOutputStream;
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i;
            videoCodec = findEncodingCodec(AV_CODEC_ID_H264);
            videoOutputStream = outputContext.addStream(videoCodec, ec);
            if (ec) {
                LOGE("Can't add video stream %s", ec.message());
                return ADD_VIDEO_STREAM_ERROR;
            }
        } else if (st.mediaType() == AVMEDIA_TYPE_AUDIO) {
            audioStreamIndex = i;
            audioCodec = findEncodingCodec(AV_CODEC_ID_AAC);
            audioOutputStream = outputContext.addStream(audioCodec, ec);
            if (ec) {
                LOGE("Can't add audio stream %s", ec.message());
                return ADD_AUDIO_STREAM_ERROR;
            }
        }
    }
    if (videoStreamIndex == 4000) {
        LOGE("Can't find video stream");
        return FIND_VIDEO_STREAM_ERROR;
    }
    if (audioStreamIndex == 4001) {
        LOGE("Can't find audio stream");
        return FIND_AUDIO_STREAM_ERROR;
    }
    Stream videoStream = inputContext.stream(videoStreamIndex);
    Stream audioStream = inputContext.stream(audioStreamIndex);
    if (videoStream.isNull()) {
        LOGE("Can't find video stream");
        return FIND_VIDEO_STREAM_ERROR;
    }
    if (audioStream.isNull()) {
        LOGE("Can't find audio stream");
        return FIND_AUDIO_STREAM_ERROR;
    }
    VideoDecoderContext videoDecoderContext;
    AudioDecoderContext audioDecoderContext;

    if (videoStream.isValid()) {
        videoDecoderContext = VideoDecoderContext(videoStream);
        videoDecoderContext.setRefCountedFrames(true);
        Codec videoCodec = findDecodingCodec(AV_CODEC_ID_H264);
        videoDecoderContext.open(videoCodec, ec);
        if (ec) {
            LOGE("Can't open decoder error: %s", ec.message());
            return OPEN_VIDEO_DECODE_ERROR;
        }
    }
    if (audioStream.isValid()) {
        audioDecoderContext = AudioDecoderContext(audioStream);
        audioDecoderContext.setRefCountedFrames(true);
        Codec audioCodec = findDecodingCodec(AV_CODEC_ID_AAC);
        audioDecoderContext.open(audioCodec, ec);
        if (ec) {
            LOGE("Can't not open audio decoder error: %s", ec.message());
            return OPEN_AUDIO_DECODE_ERROR;
        }
    }

    videoOutputStream.setTimeBase(videoDecoderContext.timeBase());
    videoOutputStream.setFrameRate(videoStream.frameRate());
    VideoEncoderContext videoEncoderContext ( videoOutputStream );
    videoEncoderContext.setWidth(newWidth);
    videoEncoderContext.setHeight(newHeight);
    videoEncoderContext.setPixelFormat(videoDecoderContext.pixelFormat());
    videoEncoderContext.setTimeBase(videoDecoderContext.timeBase());
    videoEncoderContext.setBitRate(videoDecoderContext.bitRate());
    videoEncoderContext.addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
    videoEncoderContext.open(videoCodec, ec);
    if (ec) {
        LOGE("Can't open encoder error: %s", ec.message());
        return OPEN_VIDEO_ENCODER_ERROR;
    }

    AudioEncoderContext audioEncoderContext (audioOutputStream);
    auto sampleFormats = audioCodec.supportedSampleFormats();
    auto sampleRates = audioCodec.supportedSamplerates();
    auto layouts = audioCodec.supportedChannelLayouts();
    audioEncoderContext.setSampleRate(44100);
    audioEncoderContext.setSampleFormat(sampleFormats[0]);
    audioEncoderContext.setChannelLayout(AV_CH_LAYOUT_STEREO);
    audioEncoderContext.setTimeBase(Rational( 1, audioEncoderContext.sampleRate() ));
    audioEncoderContext.setBitRate(audioDecoderContext.bitRate());
    audioEncoderContext.open(ec);

    if (ec) {
        LOGE("Can't open audio encoder error: %s", ec.message());
        return OPEN_AUDIO_ENCODER_ERROR;
    }
    outputContext.dump();
    outputContext.writeHeader(ec);
    if (ec) {
        LOGE("Can't not write header error: %s", ec.message());
        return WRITE_HEADER_ERROR;
    }

    VideoRescaler videoRescale;
    AudioResampler resampler(audioEncoderContext.channelLayout(), audioEncoderContext.sampleRate(), audioEncoderContext.sampleFormat(),
                             audioDecoderContext.channelLayout(), audioDecoderContext.sampleRate(), audioDecoderContext.sampleFormat());
    while (true) {
        Packet packet = inputContext.readPacket(ec);
        if (ec) {
            LOGE("Packet reading error: %s", ec.message());
            break;
        }
        // EOF
        if (!packet) {
            break;
        }

        if (packet.streamIndex() == videoStreamIndex) {
            // DECODING
            VideoFrame inpFrame = videoDecoderContext.decode(packet, ec);

            if (ec) {
                LOGE("Decoding video error: %s", ec.message());
                return DECODING_VIDEO_ERROR;
            }
            if (!inpFrame) {
                LOGE("Empty frame");
                continue;
            }
            // Change timebase
            inpFrame.setTimeBase(videoEncoderContext.timeBase());
            inpFrame.setStreamIndex(videoStreamIndex);
            VideoFrame outFrame{ videoDecoderContext.pixelFormat(), videoDecoderContext.width() / 2, videoDecoderContext.height() / 2 };
            // SCALE
            videoRescale.rescale(outFrame, inpFrame, ec);
            if (ec) {
                LOGE("scale video error %s.\n", ec.message());
                break;
            }
            // ENCODE
            Packet videoPacket = videoEncoderContext.encode(outFrame, ec);
            if (ec) {
                LOGE("Encoding error: %s", ec.message());
                return ENCODING_VIDEO_ERROR;
            } else if (!videoPacket) {
                LOGE("Empty packet.");
                continue;
            }

            videoPacket.setStreamIndex(videoStreamIndex);
            outputContext.writePacket(videoPacket, ec);
            if (ec) {
                LOGE("Write video packet error: %s", ec.message());
                return WRITE_PACKET_ERROR;
            }
        } else if (packet.streamIndex() == audioStreamIndex) {
            auto samples = audioDecoderContext.decode(packet, ec);
            if (ec) {
                LOGE("Decoding audio error: %s", ec.message());
                return DECODING_AUDIO_ERROR;
            }
            // Empty samples set should not be pushed to the resampler, but it is valid case for the
            // end of reading: during samples empty, some cached data can be stored at the resampler
            // internal buffer, so we should consume it.
            if (samples) {
                resampler.push(samples, ec);
                if (ec) {
                    continue;
                }
            }

            // Pop resampler data
            bool getAll = !samples;
            while (true) {
                AudioSamples ouSamples(audioEncoderContext.sampleFormat(), audioEncoderContext.frameSize(), audioEncoderContext.channelLayout(), audioEncoderContext.sampleRate());

                // Resample:
                bool hasFrame = resampler.pop(ouSamples, getAll, ec);
                if (ec) {
                    break;
                }
                if (!hasFrame) {
                    break;
                }
                // ENCODE
                ouSamples.setStreamIndex(audioStreamIndex);
                ouSamples.setTimeBase(audioEncoderContext.timeBase());

                Packet audioPacket = audioEncoderContext.encode(ouSamples, ec);
                if (ec) {
                    return ENCODING_AUDIO_ERROR;
                }
                if (!audioPacket) {
                    continue;
                }
                audioPacket.setStreamIndex(audioStreamIndex);
                outputContext.writePacket(audioPacket, ec);
                if (ec) {
                    LOGE("Write audio packet error: %s", ec.message());
                    return WRITE_PACKET_ERROR;
                }
            }
        }
    }

    // flush audio
    while (true) {
        AudioSamples null(nullptr);
        Packet audioPacket = audioEncoderContext.encode(null, ec);
        if (ec || !audioPacket)
            break;

        audioPacket.setStreamIndex(audioStreamIndex);
        outputContext.writePacket(audioPacket, ec);
        if (ec) {
            LOGE("flush video error: %s", ec.message());
            return FLUSH_VIDEO_ERROR;
        }
    }

    while (true) {
        VideoFrame null(nullptr);
        Packet videoPacket = videoEncoderContext.encode(null, ec);
        if (ec || !videoPacket) {
            break;
        }
        videoPacket.setStreamIndex(videoStreamIndex);
        outputContext.writePacket(videoPacket, ec);
        if (ec) {
            LOGE("flush audio error: %s", ec.message());
            return FLUSH_AUDIO_ERROR;
        }
    }

    outputContext.writeTrailer(ec);
    if (ec) {
        LOGE("Write Trailer error: %s", ec.message());
        return WRITE_TRAILER_ERROR;
    }
    videoDecoderContext.close(ec);
    audioDecoderContext.close(ec);
    videoEncoderContext.close(ec);
    audioEncoderContext.close(ec);
    return SUCCESS;
}

int Video::getWidth() {
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            return st.raw()->codecpar->width;
        }
    }
    return 0;
}

int Video::getHeight() {
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            return st.raw()->codecpar->height;
        }
    }
    return 0;
}

int64_t Video::getTotalFrame() {
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            return st.raw()->nb_frames;
        }
    }
    return 0;
}

double Video::getDuration() {
    return inputContext.duration().seconds();
}

int Video::getFrameRate() {
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            Rational rational(st.raw()->avg_frame_rate);
            return (int) rational.operator()();
        }
    }
    return 0;
}

void Video::close() {
    inputContext.close();
    outputContext.close();
}

}
