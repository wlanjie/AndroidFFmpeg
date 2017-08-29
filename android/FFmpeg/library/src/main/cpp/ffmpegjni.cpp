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
#include "api2-remux.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/FFmpeg"

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;
using namespace av;

size_t videoStream = -1;
size_t audioStreamIndex = -1;
Stream stream;
Stream audioStream;

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
}

jint Android_JNI_openInput(JNIEnv *env, jobject object, jstring path) {
    const char *uri = env->GetStringUTFChars(path, 0);
//    remux((char *) uri);
    remux(uri, "/sdcard/output.mp4");
//
//    FormatContext inputContext;
//    VideoDecoderContext videoDecoderContext;
//    AudioDecoderContext audioDecoderContext;
//    string uriString { uri };
//    error_code ec;
//    inputContext.openInput(uriString, ec);
//    if (ec) {
//        LOGE("Can't not open %s\n", uri);
//        return -1;
//    }
//    inputContext.findStreamInfo(ec);
//    if (ec) {
//        LOGE("Can't find stream: %s", ec.message());
//        return -2;
//    }
//
//    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
//        Stream st = inputContext.stream(i);
//        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
//            videoStream = i;
//            stream = st;
//        } else if (st.mediaType() == AVMEDIA_TYPE_AUDIO) {
//            audioStreamIndex = i;
//            audioStream = st;
//        }
//    }
//    if (stream.isNull()) {
//        LOGE("Video Stream not found \n");
//        return -3;
//    }
//    if (audioStream.isNull()) {
//        LOGE("Audio Stream not found \n");
//        return -300;
//    }
//    if (stream.isValid()) {
//        videoDecoderContext = VideoDecoderContext(stream);
//        videoDecoderContext.setRefCountedFrames(true);
//        LOGE("Video Codec ID: %d", videoDecoderContext.raw()->codec->id);
//        videoDecoderContext.open(Codec(), ec);
//        if (ec) {
//            LOGE("Can't open decoder\n");
//            return -4;
//        }
//    }
//    if (audioStream.isValid()) {
//        audioDecoderContext = AudioDecoderContext(audioStream);
//        audioDecoderContext.setRefCountedFrames(true);
//        audioDecoderContext.open(Codec(), ec);
//        if (ec) {
//            LOGE("Can't not open audio decoder\n");
//            return -200;
//        }
//    }
//
//    OutputFormat outputFormat;
//    FormatContext outputContext;
//    outputFormat.setFormat("mp4");
//    outputContext.setFormat(outputFormat);
//    Codec outputCodec = findEncodingCodec(outputFormat);
//    Stream outputStream = outputContext.addStream(outputCodec);
//    VideoEncoderContext encoder { outputStream };
//    encoder.setWidth(videoDecoderContext.width() / 2);
//    encoder.setHeight(videoDecoderContext.height() / 2);
//    encoder.setPixelFormat(videoDecoderContext.pixelFormat());
//    encoder.setTimeBase(Rational{ 1, 1000 });
//    encoder.setBitRate(videoDecoderContext.bitRate());
//    encoder.addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
//    outputStream.setFrameRate(stream.frameRate());
//    outputStream.setTimeBase(encoder.timeBase());
//    outputContext.openOutput("/sdcard/output.mp4", ec);
//    if (ec) {
//        LOGE("Can't open output \n");
//        return -5;
//    }
//    encoder.open(ec);
//    if (ec) {
//        LOGE("Can't open encoder\n");
//        return -6;
//    }
//    outputContext.dump();
//    outputContext.writeHeader(ec);
//    if (ec) {
//        LOGE("Can't not write header \n");
//        return -7;
//    }
//    outputContext.flush();
//
//    OutputFormat audioOutputFormat;
//    FormatContext audioOutputContext;
//    audioOutputFormat.setFormat("mp4");
//    audioOutputContext.setFormat(audioOutputFormat);
//    Codec audioOutputCodec = findEncodingCodec("aac");
//    Stream audioOutputStream = audioOutputContext.addStream(audioOutputCodec);
//    AudioEncoderContext audioEncoderContext (audioOutputStream);
//    auto sampleFormats = audioOutputCodec.supportedSampleFormats();
//    auto sampleRates = audioOutputCodec.supportedSamplerates();
//    auto layouts = audioOutputCodec.supportedChannelLayouts();
//    audioEncoderContext.setSampleRate(44100);
//    audioEncoderContext.setSampleFormat(sampleFormats[0]);
//    audioEncoderContext.setChannelLayout(AV_CH_LAYOUT_STEREO);
//    audioEncoderContext.setTimeBase(Rational( 1, audioEncoderContext.sampleRate() ));
//    audioEncoderContext.setBitRate(audioDecoderContext.bitRate());
//    audioEncoderContext.open(ec);
//    if (ec) {
//        LOGE("Can't open audio encoder.\n");
//        return -400;
//    }
//
//    VideoRescaler videoRescaler;
//    AudioResampler resampler(audioEncoderContext.channelLayout(), audioEncoderContext.sampleRate(), audioEncoderContext.sampleFormat(),
//                        audioDecoderContext.channelLayout(), audioDecoderContext.sampleRate(), audioDecoderContext.sampleFormat());
//    while (true) {
//        Packet packet = inputContext.readPacket(ec);
//        if (ec) {
//            LOGE("Packet reading error: %s \n", ec.message());
//            break;
//        }
//        // EOF
//        if (!packet) {
//            break;
//        }
//
//        if (packet.streamIndex() == videoStream) {
//            LOGI("Read packet: pts=%d dts=%d / seconds=%d timeBase=%d / st=%d", packet.pts, packet.dts, packet.pts().seconds(), packet.timeBase(), packet.streamIndex());
//            // DECODING
//            VideoFrame inpFrame = videoDecoderContext.decode(packet, ec);
//
//            if (ec) {
//                LOGE("Decoding error: %s\n", ec.message());
//                return -8;
//            } else if (!inpFrame) {
//                LOGE("Empty frame\n");
//                continue;
//            }
//            // Change timebase
//            inpFrame.setTimeBase(encoder.timeBase());
//            inpFrame.setStreamIndex(0);
//            inpFrame.setPictureType();
//            Packet outPacket = encoder.encode(inpFrame, ec);
//            outPacket.setStreamIndex(0);
//            outputContext.writePacket(outPacket, ec);
//            if (ec) {
//                return -1;
//            }
////            VideoFrame outFrame{ videoDecoderContext.pixelFormat(), videoDecoderContext.width() / 2, videoDecoderContext.height() / 2 };
////            // SCALE
////            videoRescaler.rescale(outFrame, inpFrame, ec);
////            if (ec) {
////                LOGE("scale video error %s.\n", ec.message());
////                break;
////            }
////            // ENCODE
////            Packet encoderPacket = encoder.encode(outFrame, ec);
////            if (ec) {
////                LOGE("Encoding error: %s", ec.message());
////                return -10;
////            } else if (!encoderPacket) {
////                LOGE("Empty packet.\n");
////                continue;
////            }
////            // Only one output stream
////            encoderPacket.setStreamIndex(0);
////            outputContext.writePacket(encoderPacket, ec);
//        } else {
//            auto samples = audioDecoderContext.decode(packet, ec);
//            if (ec) {
//                LOGE("decoder audio error %s.\n", ec.message());
//                return -500;
//            } else if (!samples) {
//                continue;
//            }
//            if (samples) {
//                resampler.push(samples, ec);
//                if (ec) {
//                    LOGE("Resampler push error %s.", ec.message());
//                    continue;
//                }
//            }
//            bool getAll = !samples;
//            while (true) {
//                AudioSamples outSamples(audioEncoderContext.sampleFormat(), audioEncoderContext.frameSize(), audioEncoderContext.channelLayout(), audioEncoderContext.sampleRate());
//                // Resample
//                bool hasFrame = resampler.pop(outSamples, getAll, ec);
//                if (ec) {
//                    LOGE("Resampling status %s", ec.message());
//                    break;
//                } else if (!hasFrame) {
//                    break;
//                }
//                outSamples.setStreamIndex(0);
//                outSamples.setTimeBase(audioEncoderContext.timeBase());
//                Packet audioPacket = audioEncoderContext.encode(outSamples, ec);
//                if (ec) {
//                    LOGE("Encoding audio error %s.\n", ec.message());
//                    return -600;
//                } else if (!audioPacket) {
//                    continue;
//                }
//                audioPacket.setStreamIndex(0);
//                audioOutputContext.writePacket(audioPacket, ec);
//                if (ec) {
//                    LOGE("Error write packet %s.\n", ec.message());
//                    return -700;
//                }
//            }
//            if (!packet && !samples) {
//                break;
//            }
//
//            while (true) {
//                AudioSamples null(nullptr);
//                Packet outPacket = audioEncoderContext.encode(null, ec);
//                if (ec || !outPacket) {
//                    break;
//                }
//                outPacket.setStreamIndex(0);
//                outputContext.writePacket(outPacket, ec);
//                if (ec) {
//                    return -1;
//                }
//            }
//        }
//
//        if (ec) {
//            LOGE("Error write packet: %s", ec.message());
//            return -11;
//        }
//    }
//    outputContext.writeTrailer(ec);
//    if (ec) {
//        LOGE("Write Trailer error.\n");
//        return -9;
//    }
//    outputContext.flush();
//    inputContext.close();
//    outputContext.close();
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