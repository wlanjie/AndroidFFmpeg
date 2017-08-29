//
// Created by wlanjie on 2017/8/22.
//

#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <system_error>

#include "log.h"
#include "av.h"
#include "avutils.h"
#include "formatcontext.h"
#include "codec.h"
#include "codeccontext.h"
#include "videorescaler.h"
#include "audioresampler.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/FFmpeg"

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;
using namespace av;

size_t videoStreamIndex;
size_t audioStreamIndex;

void remux(const char *input, const char *output) {
    string uri { input };
    string out { output };
    error_code ec;
    InputFormat inputFormat("mp4");
    FormatContext inputContext;
    inputContext.openInput(uri, inputFormat, ec);
    inputContext.findStreamInfo(ec);


    OutputFormat outputFormat("mp4");
    FormatContext outputContext;
    outputContext.setFormat(outputFormat);
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
                return;
            }
        }
        outputStream.setTimeBase(stream.timeBase());
        auto outputCodecContext = GenericCodecContext(outputStream);
        outputCodecContext.copyContextFrom(codecContext, ec);
        if (ec) {

        }
    }
    outputContext.openOutput(out, ec);
    outputContext.writeHeader(ec);
    outputContext.dump();
    while (true) {
        auto packet = inputContext.readPacket(ec);
        if (!packet) {
            break;
        }
        outputContext.writePacket(packet);
    }
    outputContext.writePacket();
    outputContext.writeTrailer(ec);

    outputContext.close();
    inputContext.close();
}

int scale(const char *input, const char *output) {
    string uri { input };
    string out { output };
    error_code ec;
    InputFormat inputFormat("mp4");
    FormatContext inputContext;
    inputContext.openInput(uri, inputFormat, ec);
    inputContext.findStreamInfo(ec);

    OutputFormat outputFormat("mp4");
    FormatContext outputContext;
    outputContext.setFormat(outputFormat);
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        auto stream = inputContext.stream(i);
        if (stream.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i;
        } else if (stream.mediaType() == AVMEDIA_TYPE_AUDIO) {
            audioStreamIndex = i;
        }
    }

//    auto videoCodecContext = GenericCodecContext(inputContext.stream(videoStreamIndex));
//    auto audioCodecContext = GenericCodecContext(inputContext.stream(audioStreamIndex));
    auto videoCodecContext = findEncodingCodec(AV_CODEC_ID_H264);
    auto audioCodecContext = findEncodingCodec(AV_CODEC_ID_AAC);
    auto videoStream = outputContext.addStream(videoCodecContext, ec);
    videoStream.setFrameRate(inputContext.stream(videoStreamIndex).frameRate());
    videoStream.setTimeBase(Rational{ 1, 1000 });

    auto audioStream = outputContext.addStream(audioCodecContext, ec);

    VideoDecoderContext videoDecoderContext(inputContext.stream(videoStreamIndex));
    videoDecoderContext.open(ec);

    AudioDecoderContext audioDecoderContext(inputContext.stream(audioStreamIndex));
    audioDecoderContext.open(ec);

    VideoEncoderContext videoEncoderContext { videoStream };
    videoEncoderContext.setWidth(videoDecoderContext.width() / 2);
    videoEncoderContext.setHeight(videoDecoderContext.height() / 2);
    videoEncoderContext.setPixelFormat(videoDecoderContext.pixelFormat());
    videoDecoderContext.setTimeBase(Rational{ 1, 1000 });
    videoEncoderContext.setBitRate(videoDecoderContext.bitRate());
    videoEncoderContext.addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
    videoEncoderContext.open(ec);

//    AudioEncoderContext audioEncoderContext { audioStream };
//    audioEncoderContext.setSampleRate(audioDecoderContext.sampleRate());
//    audioEncoderContext.setSampleFormat(audioDecoderContext.sampleFormat());
//    audioDecoderContext.setChannelLayout(audioDecoderContext.channelLayout());
//    audioEncoderContext.setTimeBase(Rational{ 1, audioDecoderContext.sampleRate() });
//    audioEncoderContext.setBitRate(audioDecoderContext.bitRate());
//    audioEncoderContext.open(ec);

    Codec audioOutputCodec = findEncodingCodec("aac");
    Stream audioOutputStream = outputContext.addStream(audioOutputCodec);
    AudioEncoderContext audioEncoderContext (audioOutputStream);
    auto sampleFormats = audioOutputCodec.supportedSampleFormats();
    auto sampleRates = audioOutputCodec.supportedSamplerates();
    auto layouts = audioOutputCodec.supportedChannelLayouts();
    audioEncoderContext.setSampleRate(44100);
    audioEncoderContext.setSampleFormat(sampleFormats[0]);
    audioEncoderContext.setChannelLayout(AV_CH_LAYOUT_STEREO);
    audioEncoderContext.setTimeBase(Rational( 1, audioEncoderContext.sampleRate() ));
    audioEncoderContext.setBitRate(audioDecoderContext.bitRate());
    audioEncoderContext.open(ec);

    outputContext.openOutput(out, ec);
    outputContext.writeHeader(ec);
    outputContext.dump();

    while (true) {
        auto packet = inputContext.readPacket(ec);
        if (ec) {
            break;
        }
        // EOF
        if (!packet) {
            break;
        }
        if (packet.streamIndex() == videoStreamIndex) {
            auto frame = videoDecoderContext.decode(packet, ec);
            if (ec) {
                break;
            }
            if (!frame) {
                continue;
            }
            auto videoPacket = videoEncoderContext.encode(frame, ec);
            if (ec) {
                break;
            }
            outputContext.writePacket(videoPacket);
        } else if (packet.streamIndex() == audioStreamIndex) {
//            auto samples = audioDecoderContext.decode(packet, ec);
//            if (ec) {
//                break;
//            }
//            if (!samples) {
//                continue;
//            }
//            auto audioPacket = audioEncoderContext.encode(samples, ec);
//            if (ec) {
//                break;
//            }
//            outputContext.writePacket(audioPacket, ec);
//            if (ec) {
//
//            }
        }
    }
    outputContext.writeTrailer(ec);
    outputContext.close();
    inputContext.close();
    return 0;
}

int scale2(const char *input) {
    FormatContext inputContext;
    VideoDecoderContext videoDecoderContext;
    AudioDecoderContext audioDecoderContext;
    string uriString { input };
    error_code ec;
    inputContext.openInput(uriString, ec);
    if (ec) {
        LOGE("Can't not open %s\n", uri);
        return -1;
    }
    inputContext.findStreamInfo(ec);
    if (ec) {
        LOGE("Can't find stream: %s", ec.message());
        return -2;
    }

    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i;
        } else if (st.mediaType() == AVMEDIA_TYPE_AUDIO) {
            audioStreamIndex = i;
        }
    }
    Stream videoStream = inputContext.stream(videoStreamIndex);
    Stream audioStream = inputContext.stream(audioStreamIndex);
    if (videoStream.isNull()) {
        LOGE("Video Stream not found \n");
        return -3;
    }
    if (audioStream.isNull()) {
        LOGE("Audio Stream not found \n");
        return -300;
    }
    if (videoStream.isValid()) {
        videoDecoderContext = VideoDecoderContext(videoStream);
        videoDecoderContext.setRefCountedFrames(true);
        Codec videoCodec = findDecodingCodec(AV_CODEC_ID_H264);
        videoDecoderContext.open(videoCodec, ec);
        if (ec) {
            LOGE("Can't open decoder\n");
            return -4;
        }
    }
    if (audioStream.isValid()) {
        audioDecoderContext = AudioDecoderContext(audioStream);
        audioDecoderContext.setRefCountedFrames(true);
        Codec audioCodec = findDecodingCodec(AV_CODEC_ID_AAC);
        audioDecoderContext.open(audioCodec, ec);
        if (ec) {
            LOGE("Can't not open audio decoder\n");
            return -200;
        }
    }

    OutputFormat outputFormat;
    FormatContext outputContext;
    outputFormat.setFormat("mp4");
    outputContext.setFormat(outputFormat);
    outputContext.openOutput("/sdcard/output.mp4", ec);

    Codec videoCodec = findEncodingCodec(AV_CODEC_ID_H264);
    Stream videoOutputStream = outputContext.addStream(videoCodec);
    videoOutputStream.setTimeBase(videoDecoderContext.timeBase());
    videoOutputStream.setFrameRate(videoStream.frameRate());

    VideoEncoderContext videoEncoderContext ( videoOutputStream );
    videoEncoderContext.setWidth(videoDecoderContext.width() / 2);
    videoEncoderContext.setHeight(videoDecoderContext.height() / 2);
    videoEncoderContext.setPixelFormat(videoDecoderContext.pixelFormat());
//    videoEncoderContext.setTimeBase(Rational{ 1, 1000 });
    videoEncoderContext.setTimeBase(videoDecoderContext.timeBase());
    videoEncoderContext.setBitRate(videoDecoderContext.bitRate());
    videoEncoderContext.addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
    videoEncoderContext.open(videoCodec, ec);
    if (ec) {
        LOGE("Can't open encoder\n");
        return -6;
    }

    Codec audioOutputCodec = findEncodingCodec(AV_CODEC_ID_AAC);
    Stream audioOutputStream = outputContext.addStream(audioOutputCodec);
    AudioEncoderContext audioEncoderContext (audioOutputStream);
    auto sampleFormats = audioOutputCodec.supportedSampleFormats();
    auto sampleRates = audioOutputCodec.supportedSamplerates();
    auto layouts = audioOutputCodec.supportedChannelLayouts();
    audioEncoderContext.setSampleRate(44100);
    audioEncoderContext.setSampleFormat(sampleFormats[0]);
    audioEncoderContext.setChannelLayout(AV_CH_LAYOUT_STEREO);
    audioEncoderContext.setTimeBase(Rational( 1, audioEncoderContext.sampleRate() ));
    audioEncoderContext.setBitRate(audioDecoderContext.bitRate());
    audioEncoderContext.open(ec);
    if (ec) {
        LOGE("Can't open audio encoder.\n");
        return -400;
    }

    if (ec) {
        LOGE("Can't open output \n");
        return -5;
    }
    outputContext.dump();
    outputContext.writeHeader(ec);
    if (ec) {
        LOGE("Can't not write header \n");
        return -7;
    }
    outputContext.flush();

    VideoRescaler videoRescaler;
    AudioResampler resampler(audioEncoderContext.channelLayout(), audioEncoderContext.sampleRate(), audioEncoderContext.sampleFormat(),
                             audioDecoderContext.channelLayout(), audioDecoderContext.sampleRate(), audioDecoderContext.sampleFormat());
    while (true) {
        Packet packet = inputContext.readPacket(ec);
        if (ec) {
            LOGE("Packet reading error: %s \n", ec.message());
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
                LOGE("Decoding error: %s\n", ec.message());
                return -8;
            }
            if (!inpFrame) {
                LOGE("Empty frame\n");
                continue;
            }
            // Change timebase
            inpFrame.setTimeBase(videoEncoderContext.timeBase());
            inpFrame.setStreamIndex(videoStreamIndex);
            inpFrame.setPictureType();
            VideoFrame outFrame{ videoDecoderContext.pixelFormat(), videoDecoderContext.width() / 2, videoDecoderContext.height() / 2 };
            // SCALE
            videoRescaler.rescale(outFrame, inpFrame, ec);
            if (ec) {
                LOGE("scale video error %s.\n", ec.message());
                break;
            }
            // ENCODE
            Packet encoderPacket = videoEncoderContext.encode(outFrame, ec);
            if (ec) {
                LOGE("Encoding error: %s", ec.message());
                return -10;
            } else if (!encoderPacket) {
                LOGE("Empty packet.\n");
                continue;
            }
            // Only one output stream
            encoderPacket.setStreamIndex(0);
            outputContext.writePacket(encoderPacket, ec);
        } else if (packet.streamIndex() == audioStreamIndex) {
//            auto samples = audioDecoderContext.decode(packet, ec);
//            if (ec) {
//                return 1;
//            }
//            // Empty samples set should not be pushed to the resampler, but it is valid case for the
//            // end of reading: during samples empty, some cached data can be stored at the resampler
//            // internal buffer, so we should consume it.
//            if (samples) {
//                resampler.push(samples, ec);
//                if (ec) {
//                    continue;
//                }
//            }
//
//            // Pop resampler data
//            bool getAll = !samples;
//            while (true) {
//                AudioSamples ouSamples(audioEncoderContext.sampleFormat(), audioEncoderContext.frameSize(), audioEncoderContext.channelLayout(), audioEncoderContext.sampleRate());
//
//                // Resample:
//                bool hasFrame = resampler.pop(ouSamples, getAll, ec);
//                if (ec) {
//                    break;
//                }
//                if (!hasFrame) {
//                    break;
//                }
//                // ENCODE
//                ouSamples.setStreamIndex(audioStreamIndex);
//                ouSamples.setTimeBase(audioEncoderContext.timeBase());
//
//                Packet audioPacket = audioEncoderContext.encode(ouSamples, ec);
//                if (ec) {
//                    return 1;
//                }
//                if (!audioPacket) {
//                    continue;
//                }
//                outputContext.writePacket(audioPacket, ec);
//                if (ec) {
//                    return -1;
//                }
//            }
//
//            // For the first packets samples can be empty: decoder caching
//            if (!packet && !samples) {
//                break;
//            }
        }

        if (ec) {
            LOGE("Error write packet: %s", ec.message());
            return -11;
        }
    }
    outputContext.writeTrailer(ec);
    if (ec) {
        LOGE("Write Trailer error.\n");
        return -9;
    }
    outputContext.flush();
    inputContext.close();
//    outputContext.close();
}

jint Android_JNI_openInput(JNIEnv *env, jobject object, jstring path) {
    const char *uri = env->GetStringUTFChars(path, 0);
//    scale(uri, "/sdcard/output.mp4");
    scale2(uri);
    env->ReleaseStringUTFChars(path, uri);
    return 0;
}

static JNINativeMethod method[] = {
        { "openInput", "(Ljava/lang/String;)I", (void *) Android_JNI_openInput }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_VERSION_1_6;
    }
    jclass clazz = env->FindClass(CLASS_NAME);
    env->RegisterNatives(clazz, method, NELEM(method));
    init();
    setFFmpegLoggingLevel(AV_LOG_DEBUG);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {

}

#ifdef __cplusplus
}
#endif
#endif