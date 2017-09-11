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
#include "video.h"

#include "libyuv.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/FFmpeg"

#ifdef __cplusplus
extern "C" {
#endif

#define OPEN_INPUT_ERROR 4000
#define FIND_STREAM_ERROR 4001

JavaVM *javaVM;

using namespace std;
using namespace av;

Video video;

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

jint Android_JNI_openInput(JNIEnv *env, jobject object, jstring path) {
    const char *input = env->GetStringUTFChars(path, 0);
    string uri(input);
    int result = video.openInput(input);
    env->ReleaseStringUTFChars(path, input);
    return result;
}

jint Android_JNI_openOutput(JNIEnv *env, jobject object, jstring path) {
    const char *output = env->GetStringUTFChars(path, 0);
    string uri(output);
    int result = video.openOutput(uri);
    env->ReleaseStringUTFChars(path, output);
    return result;
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

    int size = videoDecoderContext.width() * videoDecoderContext.height() * 4;
    jbyteArray array = env->NewByteArray(size);
    uint8_t data[size];

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
            auto videoFrame = videoDecoderContext.decode(packet, ec);
            if (ec) {
                break;
            }
            if (!videoFrame) {
                continue;
            }

            libyuv::I420ToABGR((const uint8_t*) videoFrame.raw()->data[0], videoFrame.raw()->linesize[0],
                               (const uint8_t*) videoFrame.raw()->data[1], videoFrame.raw()->linesize[1],
                               (const uint8_t*) videoFrame.raw()->data[2], videoFrame.raw()->linesize[2],
                               data, videoDecoderContext.width() * 4, videoDecoderContext.width(), videoDecoderContext.height());

            env->SetByteArrayRegion(array, 0, size, (const jbyte *) data);
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

jint Android_JNI_scale(JNIEnv *env, jobject object, jint newWidth, jint newHeight) {
    return video.scale(newWidth, newHeight);
}

jobject Android_JNI_getVideoInfo(JNIEnv *env, jobject object) {
    jclass videoClass = env->FindClass("com/wlanjie/ffmpeg/Video");
    jmethodID videoInitMethodID = env->GetMethodID(videoClass, "<init>", "()V");
    jobject videoObject = env->NewObject(videoClass, videoInitMethodID);
    jfieldID videoWidthFieldID = env->GetFieldID(videoClass, "width", "I");
    env->SetIntField(videoObject, videoWidthFieldID, video.getWidth());
    jfieldID videoHeightFieldID = env->GetFieldID(videoClass, "height", "I");
    env->SetIntField(videoObject, videoHeightFieldID, video.getHeight());
    jfieldID videoTotalFrameFieldID = env->GetFieldID(videoClass, "totalFrame", "I");
    env->SetIntField(videoObject, videoTotalFrameFieldID, video.getTotalFrame());
    jfieldID videoDurationFieldID = env->GetFieldID(videoClass, "duration", "D");
    env->SetDoubleField(videoObject, videoDurationFieldID, video.getDuration());
    jfieldID videoFrameRateFieldID = env->GetFieldID(videoClass, "frameRate", "D");
    env->SetDoubleField(videoObject, videoFrameRateFieldID, video.getFrameRate());
    return videoObject;
}

void Android_JNI_release(JNIEnv *env, jobject object) {
    video.close();
}

static JNINativeMethod method[] = {
        { "openInput",              "(Ljava/lang/String;)I",                    (void *) Android_JNI_openInput },
        { "openOutput",             "(Ljava/lang/String;)I",                    (void *) Android_JNI_openOutput },
        { "getVideoFrame",          "(Ljava/lang/String;)Ljava/util/List;",     (void *) Android_JNI_getVideoFrame },
        { "scale",                  "(II)I",                                    (void *) Android_JNI_scale },
        { "getVideoInfo",           "()Lcom/wlanjie/ffmpeg/Video;",             (void *) Android_JNI_getVideoInfo },
        { "release",                "()V",                                      (void *) Android_JNI_release }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_VERSION_1_6;
    }
    javaVM = vm;
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