//
// Created by wlanjie on 2017/8/22.
//

#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <android/bitmap.h>
#include <system_error>

#include "log.h"
#include "core/av.h"
#include "core/avutils.h"
#include "core/formatcontext.h"
#include "core/codec.h"
#include "core/codeccontext.h"
#include "core/videorescaler.h"
#include "core/audioresampler.h"

#include "video.h"
#include "arguments.h"
#include "shortvideo.h"
#include "videorecorder.h"

#include "libyuv.h"
#include "composevideo.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/FFmpeg"

#define AUDIO_SETTING "com/wlanjie/ffmpeg/setting/AudioSetting"
#define VIDEO_SETTING "com/wlanjie/ffmpeg/setting/VideoSetting"

#ifdef __cplusplus
extern "C" {
#endif

#define OPEN_INPUT_ERROR 4000
#define FIND_STREAM_ERROR 4001

JavaVM *javaVM;

using namespace std;
using namespace av;

Video video;
ShortVideo shortVideo;

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
    int result = shortVideo.openOutput(uri);
//    VideoRecorder recorder;
//    recorder.recorder((char *) output);
    env->ReleaseStringUTFChars(path, output);
    return 0;
}

jobject Android_JNI_getVideoFrame(JNIEnv *env, jobject object) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListConstructMethodId = env->GetMethodID(arrayListClass, "<init>", "()V");
    jobject arrayListObject = env->NewObject(arrayListClass, arrayListConstructMethodId);
    jmethodID arrayListAddMethodId = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

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

    int size = video.getWidth() * video.getHeight() * 4;
    jbyteArray array = env->NewByteArray(size);
    uint8_t data[size];

    std::vector<VideoFrame> frames = video.getVideoFrame();
    for (int i = 0; i < frames.size(); i++) {
        VideoFrame videoFrame = frames.at(i);
        libyuv::I420ToABGR((const uint8_t*) videoFrame.raw()->data[0], videoFrame.raw()->linesize[0],
                           (const uint8_t*) videoFrame.raw()->data[1], videoFrame.raw()->linesize[1],
                           (const uint8_t*) videoFrame.raw()->data[2], videoFrame.raw()->linesize[2],
                           data, video.getWidth() * 4, video.getWidth(), video.getHeight());
        env->SetByteArrayRegion(array, 0, size, (const jbyte *) data);
        jobject bitmapObject = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId, video.getWidth(), video.getHeight(), bitmapConfigObject);
        jclass byteBufferClass = env->FindClass("java/nio/ByteBuffer");
        jmethodID byteBufferWrapMethodId = env->GetStaticMethodID(byteBufferClass, "wrap", "([B)Ljava/nio/ByteBuffer;");
        jobject byteBufferObject = env->CallStaticObjectMethod(byteBufferClass, byteBufferWrapMethodId, array);
        jmethodID copyPixelsFromBufferMethodId = env->GetMethodID(bitmapClass, "copyPixelsFromBuffer", "(Ljava/nio/Buffer;)V");
        env->CallVoidMethod(bitmapObject, copyPixelsFromBufferMethodId, byteBufferObject);
        env->CallBooleanMethod(arrayListObject, arrayListAddMethodId, bitmapObject);
    }
    free(str);
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

jint Android_JNI_encoderVideo(JNIEnv *env, jobject object, jbyteArray frame) {
    jbyte *videoFrame = env->GetByteArrayElements(frame, 0);
    jsize frameLength = env->GetArrayLength(frame);
    int result = shortVideo.encodeVideo((uint8_t *) videoFrame);
    env->ReleaseByteArrayElements(frame, videoFrame, 0);
    return result;
}

jint Android_JNI_encoderAudio(JNIEnv *env, jobject object, jbyteArray audio) {
    jbyte *audioFrame = env->GetByteArrayElements(audio, 0);
    jsize frameLength = env->GetArrayLength(audio);
    int result = shortVideo.encodeAudio((uint8_t *) audioFrame);
    env->ReleaseByteArrayElements(audio, audioFrame, 0);
    return result;
}

void Android_JNI_release(JNIEnv *env, jobject object) {
    video.close();
}

void Android_JNI_setSetting(JNIEnv *env, jobject object, jobject audioSetting, jobject videoSetting) {
    Arguments arguments;
    jclass audioSettingClass = env->GetObjectClass(audioSetting);
    arguments.audioBitRate = env->CallIntMethod(audioSetting, env->GetMethodID(audioSettingClass, "getBitRate", "()I"));
    arguments.audioSampleRate = env->CallIntMethod(audioSetting, env->GetMethodID(audioSettingClass, "getSampleRate", "()I"));
    arguments.audioChannelCount = env->CallIntMethod(audioSetting, env->GetMethodID(audioSettingClass, "getChannelCount", "()I"));
    jclass videoSettingClass = env->GetObjectClass(videoSetting);
    arguments.videoWidth = env->CallIntMethod(videoSetting, env->GetMethodID(videoSettingClass, "getVideoWidth", "()I")) ;
    arguments.videoHeight = env->CallIntMethod(videoSetting, env->GetMethodID(videoSettingClass, "getVideoHeight", "()I")) ;
    arguments.videoFrameRate = env->CallIntMethod(videoSetting, env->GetMethodID(videoSettingClass, "getFrameRate", "()I")) ;
    arguments.videoGopSize = env->CallIntMethod(videoSetting, env->GetMethodID(videoSettingClass, "getGopSize", "()I")) ;
    arguments.videoBitRate = env->CallIntMethod(videoSetting, env->GetMethodID(videoSettingClass, "getBitRate", "()I")) ;
    shortVideo.setArguments(arguments);
    env->DeleteLocalRef(videoSettingClass);
    env->DeleteLocalRef(audioSettingClass);
}

int Android_JNI_beginSection(JNIEnv *env, jobject object) {
    return shortVideo.beginSection();
}

int Android_JNI_endSection(JNIEnv *env, jobject object) {
    return shortVideo.endSection();
}

int Android_JNI_composeVideos(JNIEnv *env, jobject object, jobject listObject, jstring composePath) {
    ComposeVideo composeVideo;
    const char* composeUri = env->GetStringUTFChars(composePath, NULL);
    jclass listClass = env->GetObjectClass(listObject);
    int videoSize = env->CallIntMethod(listObject, env->GetMethodID(listClass, "size", "()I"));

    std::vector<char*> inputVideoUri;

    for (int i = 0; i < videoSize; ++i) {
        jstring videoPath = (jstring) env->CallObjectMethod(listObject, env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;"), i);
        const char* videoUri = env->GetStringUTFChars(videoPath, NULL);

        char* uri = (char *) malloc(strlen(videoUri) + 1);
        strcpy(uri, videoUri);
        inputVideoUri.push_back(uri);

        env->ReleaseStringUTFChars(videoPath, videoUri);
        env->DeleteLocalRef(videoPath);
    }

//    composeVideo.composeVideo(inputVideoUri[0], inputVideoUri[1], (char *) composeUri);
    shortVideo.composeVideo(inputVideoUri, (char *) composeUri);

    env->ReleaseStringUTFChars(composePath, composeUri);
    env->DeleteLocalRef(listClass);
    LOGE("compose video done");
    return 0;
}

static JNINativeMethod method[] = {
        { "openInput",              "(Ljava/lang/String;)I",                    (void *) Android_JNI_openInput },
        { "openOutput",             "(Ljava/lang/String;)I",                    (void *) Android_JNI_openOutput },
        { "getVideoFrame",          "()Ljava/util/List;",                       (void *) Android_JNI_getVideoFrame },
        { "scale",                  "(II)I",                                    (void *) Android_JNI_scale },
        { "getVideoInfo",           "()Lcom/wlanjie/ffmpeg/Video;",             (void *) Android_JNI_getVideoInfo },
        { "encoderVideo",           "([B)I",                                    (void *) Android_JNI_encoderVideo },
        { "encoderAudio",           "([B)I",                                    (void *) Android_JNI_encoderAudio },
        { "setSetting",             "(L" AUDIO_SETTING ";L" VIDEO_SETTING ";" ")V",       (void *) Android_JNI_setSetting },
        { "beginSection",           "()I",                                      (void *) Android_JNI_beginSection },
        { "endSection",             "()I",                                      (void *) Android_JNI_endSection },
        { "composeVideos",          "(Ljava/util/List;Ljava/lang/String;)I",    (void *) Android_JNI_composeVideos },
        { "release",                "()V",                                      (void *) Android_JNI_release }
};

void log_callback(void *ptr, int level, const char *fmt, va_list vl) {
//    switch(level) {
//        case AV_LOG_DEBUG:
//            LOGE(fmt, vl);
//            break;
//        case AV_LOG_VERBOSE:
//            LOGE(fmt, vl);
//            break;
//        case AV_LOG_INFO:
//            LOGE(fmt, vl);
//            break;
//        case AV_LOG_WARNING:
//            LOGE(fmt, vl);
//            break;
//        case AV_LOG_ERROR:
//            LOGE(fmt, vl);
//            break;
//    }
    int print_prefix = 1;
    char prev[1024];
    char line[1024];
    av_log_format_line(ptr, level, fmt, vl, line, sizeof(line), &print_prefix);
    strcpy(prev, line);
    LOGE("%s", line);
}

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
    av_log_set_callback(log_callback);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {

}

#ifdef __cplusplus
}
#endif
#endif