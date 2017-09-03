//
// Created by wlanjie on 2017/8/22.
//

#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <android/bitmap.h>
#include <system_error>

#include "log.h"
#include "av.h"
#include "avutils.h"
#include "formatcontext.h"
#include "codec.h"
#include "codeccontext.h"
#include "videorescaler.h"
#include "audioresampler.h"

#include "libyuv.h"

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

int scale(const char *input) {
    FormatContext inputContext;
    OutputFormat outputFormat;
    FormatContext outputContext;
    outputFormat.setFormat("mp4");
    outputContext.setFormat(outputFormat);

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
    Codec videoCodec;
    Stream videoOutputStream;
    Codec audioOutputCodec;
    Stream audioOutputStream;
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i;
            videoCodec = findEncodingCodec(AV_CODEC_ID_H264);
            videoOutputStream = outputContext.addStream(videoCodec);
        } else if (st.mediaType() == AVMEDIA_TYPE_AUDIO) {
            audioStreamIndex = i;
            audioOutputCodec = findEncodingCodec(AV_CODEC_ID_AAC);
            audioOutputStream = outputContext.addStream(audioOutputCodec);
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

    outputContext.openOutput("/sdcard/output.mp4", ec);

    videoOutputStream.setTimeBase(videoDecoderContext.timeBase());
    videoOutputStream.setFrameRate(videoStream.frameRate());
    VideoEncoderContext videoEncoderContext ( videoOutputStream );
    videoEncoderContext.setWidth(videoDecoderContext.width() / 2);
    videoEncoderContext.setHeight(videoDecoderContext.height() / 2);
    videoEncoderContext.setPixelFormat(videoDecoderContext.pixelFormat());
    videoEncoderContext.setTimeBase(videoDecoderContext.timeBase());
    videoEncoderContext.setBitRate(videoDecoderContext.bitRate());
    videoEncoderContext.addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
    videoEncoderContext.open(videoCodec, ec);
    if (ec) {
        LOGE("Can't open encoder\n");
        return -6;
    }

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

    VideoRescaler videoRescale;
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
                return -10;
            } else if (!videoPacket) {
                LOGE("Empty packet.\n");
                continue;
            }

            videoPacket.setStreamIndex(videoStreamIndex);
            outputContext.writePacket(videoPacket, ec);
        } else if (packet.streamIndex() == audioStreamIndex) {
            auto samples = audioDecoderContext.decode(packet, ec);
            if (ec) {
                return 1;
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
                    return 1;
                }
                if (!audioPacket) {
                    continue;
                }
                audioPacket.setStreamIndex(audioStreamIndex);
                outputContext.writePacket(audioPacket, ec);
                if (ec) {
                    return -1;
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
            return -1;
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
            return -1;
        }
    }

    outputContext.writeTrailer(ec);
    if (ec) {
        LOGE("Write Trailer error.\n");
        return -9;
    }
    outputContext.flush();
    videoDecoderContext.close();
    videoEncoderContext.close();
    audioDecoderContext.close();
    audioEncoderContext.close();
    outputContext.close();
    inputContext.close();
    return 0;
}

jint Android_JNI_openInput(JNIEnv *env, jobject object, jstring path) {
    const char *uri = env->GetStringUTFChars(path, 0);
    scale(uri);
    env->ReleaseStringUTFChars(path, uri);
    return 0;
}

jobject Android_JNI_getVideoFrame(JNIEnv *env, jobject object, jstring inputPath) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListConstructMethodId = env->GetMethodID(arrayListClass, "<init>", "()V");
    jobject arrayListObject = env->NewObject(arrayListClass, arrayListConstructMethodId);
    jmethodID arrayListAddMethodId = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    const char *input = env->GetStringUTFChars(inputPath, 0);
    FormatContext inputContext;
    string uri(input);
    error_code ec;
    inputContext.openInput(uri, ec);
    if (ec) {

    }
    inputContext.findStreamInfo(ec);
    if (ec) {

    }
    size_t videoStreamIndex = -1;
    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStreamIndex = i;
        }
    }
    if (videoStreamIndex == -1) {
        LOGE("Can't found video stream.");
    }
    VideoDecoderContext videoDecoderContext(inputContext.stream(videoStreamIndex));
    Codec videoCodec = findDecodingCodec(AV_CODEC_ID_H264);
    videoDecoderContext.open(videoCodec, ec);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodId = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    const wchar_t* configName = L"ARGB_8888";
    int len = wcslen(configName);
    jchar* str = (jchar*)malloc((len+1)*sizeof(jchar));
    for (int i = 0; i < len; ++i) {
        str[i] = (jchar)configName[i];
    }
    str[len] = 0;
    jstring jConfigName = env->NewString((const jchar*)str, len);
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOfMethodId = env->GetStaticMethodID(bitmapConfigClass, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jobject bitmapConfigObject = env->CallStaticObjectMethod(bitmapConfigClass, valueOfMethodId, jConfigName);

    int i = 0;
    while (true) {
        Packet packet = inputContext.readPacket(ec);
        if (ec) {
            LOGE(("Packet reading error: %s"), ec.message());
            break;
        }
        // EOF
        if (!packet) {
            break;
        }
        if (packet.streamIndex() == videoStreamIndex) {
            i++;
            if (i > 60) {
                break;
            }
            auto videoFrame = videoDecoderContext.decode(packet, ec);
            if (ec) {
                break;
            }
            if (!videoFrame) {
                continue;
            }
            AVPictureType pictureType = videoFrame.pictureType();
            LOGE("pictureType: %d", pictureType);
            uint8_t data[videoDecoderContext.width() * videoDecoderContext.height() * 4];
            libyuv::I420ToABGR((const uint8_t*) videoFrame.raw()->data[0], videoFrame.raw()->linesize[0],
                               (const uint8_t*) videoFrame.raw()->data[1], videoFrame.raw()->linesize[1],
                               (const uint8_t*) videoFrame.raw()->data[2], videoFrame.raw()->linesize[2],
                               data, videoDecoderContext.width() * 4, videoDecoderContext.width(), videoDecoderContext.height());

            jbyteArray array = env->NewByteArray(videoDecoderContext.width() * videoDecoderContext.height() * 4);
            env->SetByteArrayRegion(array, 0, videoDecoderContext.width() * videoDecoderContext.height() * 4,
                                    (const jbyte *) data);

            jobject bitmapObject = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId, videoDecoderContext.width(), videoDecoderContext.height(), bitmapConfigObject);
            jclass byteBufferClass = env->FindClass("java/nio/ByteBuffer");
            jmethodID byteBufferWrapMethodId = env->GetStaticMethodID(byteBufferClass, "wrap", "([B)Ljava/nio/ByteBuffer;");
            jobject byteBufferObject = env->CallStaticObjectMethod(byteBufferClass, byteBufferWrapMethodId, array);
            jmethodID copyPixelsFromBufferMethodId = env->GetMethodID(bitmapClass, "copyPixelsFromBuffer", "(Ljava/nio/Buffer;)V");
            env->CallVoidMethod(bitmapObject, copyPixelsFromBufferMethodId, byteBufferObject);

            env->CallBooleanMethod(arrayListObject, arrayListAddMethodId, bitmapObject);
        }
    }
    env->ReleaseStringUTFChars(inputPath, input);
    return arrayListObject;
}

static JNINativeMethod method[] = {
        { "openInput",              "(Ljava/lang/String;)I",                    (void *) Android_JNI_openInput },
        { "getVideoFrame",          "(Ljava/lang/String;)Ljava/util/List;",     (void *) Android_JNI_getVideoFrame }
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