//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <queue>
#include <unistd.h>
#include "srs_librtmp.h"
#include "audioencode.h"
#include "h264encode.h"
#include "log.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define RTMP_CLASS_NAME "com/wlanjie/streaming/rtmp/Rtmp"
#define VIDEO_ENCODER_CLASS_NAME "com/wlanjie/streaming/video/OpenH264Encoder"
#define AUDIO_ENCODER_CLASS_NAME "com/wlanjie/streaming/audio/FdkAACEncoder"
#ifdef __cplusplus
extern "C" {
#endif

#define AUDIO_TYPE 0
#define VIDEO_TYPE 1

struct Frame {
    char *frame;
    char *data;
    int size = 0;
    int packet_type;
    int pts;
};

std::queue<Frame> q;

wlanjie::H264Encoder h264Encoder;
wlanjie::AudioEncode audioEncode;
srs_rtmp_t rtmp;
bool is_stop = false;

void Android_JNI_startPublish(JNIEnv *env, jobject object) {
    while (!is_stop) {
        while (!q.empty()) {
            Frame frame = q.front();
            q.pop();
            if (frame.size <= 0) {
                if (frame.data) {
                    free(frame.data);
                }
                continue;
            }
            if (frame.packet_type == AUDIO_TYPE) {
                int ret = srs_audio_write_raw_frame(rtmp, 10 , 3, 1, 1, frame.data, frame.size, frame.pts);
                if (ret != 0) {
                    LOGE("write audio ret = %d", ret);
                }
            } else {
                if (frame.size <= 0) {
                    LOGE("h264 size = %d", frame.size);
                    continue;
                }
                int ret = srs_h264_write_raw_frames(rtmp, frame.data, frame.size, frame.pts, frame.pts);
                if (ret != 0) {
                    LOGE("write h264 ret = %d.", ret);
                }
            }
            if (frame.data) {
                free(frame.data);
            }
        }
        usleep(1000 * 1000);
    }
}

void Android_JNI_setEncoderResolution(JNIEnv *env, jobject object, jint width, jint height) {
    h264Encoder.setFrameSize(width, height);
}

jboolean Android_JNI_openH264Encoder(JNIEnv* env, jobject object) {
    h264Encoder.openH264Encoder();
    return JNI_TRUE;
}

void Android_JNI_closeH264Encoder(JNIEnv* env, jobject object) {
    std::queue<Frame> empty;
    std::swap(q, empty);
    h264Encoder.closeH264Encoder();
}

jboolean Android_JNI_openAacEncode(JNIEnv *env, jobject object, jint channels, jint sample_rate, jint bitrate) {
    return (jboolean) audioEncode.open_aac_encode(channels, sample_rate, bitrate);
}

jint Android_JNI_encoderPcmToAac(JNIEnv *env, jobject object, jbyteArray pcm, int pts) {
    jbyte *pcm_frame = env->GetByteArrayElements(pcm, NULL);
    int pcm_length = env->GetArrayLength(pcm);
    int aac_size = audioEncode.encode_pcm_to_aac((char *) pcm_frame, pcm_length);
    env->ReleaseByteArrayElements(pcm, pcm_frame, NULL);
    if (aac_size > 0) {
        char *aac = (char *) malloc(aac_size);
        memcpy(aac, audioEncode.getAac(), aac_size);
        Frame f;
        f.data = aac;
        f.size = aac_size;
        f.pts = pts;
        f.packet_type = AUDIO_TYPE;
        q.push(f);
    }
    return 0;
}

void Android_JNI_closeAacEncoder() {
    audioEncode.close_aac_encode();
}

jint Android_JNI_connect(JNIEnv *env, jobject object, jstring url) {
    if (rtmp != NULL) {
        return -1;
    }
    const char *rtmp_url = env->GetStringUTFChars(url, 0);
    rtmp = srs_rtmp_create(rtmp_url);
    if (srs_rtmp_handshake(rtmp) != 0) {
        return -1;
    }
    if (srs_rtmp_connect_app(rtmp) != 0) {
        return -1;
    }
    if (srs_rtmp_publish_stream(rtmp) != 0) {
        return -1;
    }
    env->ReleaseStringUTFChars(url, rtmp_url);

    return 0;
}

int Android_JNI_write_video_sample(JNIEnv *env, jobject object, jbyteArray frame, jlong timestamp) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);

    if (data_size <= 0 || data == NULL) {
        return -1;
    }
    char *h264 = (char *) malloc((size_t) data_size);
    memcpy(h264, data, (size_t) data_size);
    Frame f;
    f.data = h264;
    f.size = data_size;
    f.pts = (int) timestamp;
    f.packet_type = VIDEO_TYPE;
    q.push(f);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return 0;
}

jint Android_JNI_write_audio_sample(JNIEnv *env, jobject object, jbyteArray frame, jlong timestamp, jint sampleRate, jint channel) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
    if (data_size <= 0 || data == NULL) {
        return -1;
    }
    char *aac = (char *) malloc((size_t) data_size);
    memcpy(aac, data, (size_t) data_size);
    Frame f;
    f.data = aac;
    f.size = data_size;
    f.pts = (int) timestamp;
    f.packet_type = AUDIO_TYPE;
    q.push(f);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return 0;
}

void Android_JNI_destroy(JNIEnv *env, jobject object) {
    is_stop = true;
    srs_rtmp_destroy(rtmp);
    rtmp = NULL;
}

void Android_JNI_encode_h264(JNIEnv *env, jobject object, jbyteArray data, jint width, jint height, jlong pts) {
    jbyte *frame = env->GetByteArrayElements(data, NULL);
    int h264_size;
    uint8_t *h264_encode;
    h264Encoder.encoder((char *) frame, width, height, (long) pts, &h264_size, &h264_encode);
    env->ReleaseByteArrayElements(data, frame, NULL);
    if (h264_size > 0) {
        char *h264 = (char *) malloc(h264_size);
        memcpy(h264, h264_encode, h264_size);
        Frame f;
        f.data = h264;
        f.size = h264_size;
        f.pts = (int) pts;
        f.packet_type = VIDEO_TYPE;
        q.push(f);
    }
}

static JNINativeMethod rtmp_methods[] = {
        { "startPublish",           "()V",                      (void *) Android_JNI_startPublish },
        { "connect",                "(Ljava/lang/String;)I",    (void *) Android_JNI_connect },
        { "writeVideo",             "([BJ)I",                   (void *) Android_JNI_write_video_sample },
        { "writeAudio",             "([BJII)I",                 (void *) Android_JNI_write_audio_sample },
        { "destroy",                "()V",                      (void *) Android_JNI_destroy },
};

static JNINativeMethod video_encoder_methods[] = {
        { "openEncoder",            "()Z",                      (void *) Android_JNI_openH264Encoder },
        { "closeEncoder",           "()V",                      (void *) Android_JNI_closeH264Encoder },
        { "setFrameSize",           "(II)V",                    (void *) Android_JNI_setEncoderResolution },
        { "encode",                 "([BIIJ)V",                 (void *) Android_JNI_encode_h264 },
};

static JNINativeMethod audio_encoder_methods[] = {
        { "openEncoder",            "(III)Z",                   (void *) Android_JNI_openAacEncode },
        { "encode",                 "([BI)I",                   (void *) Android_JNI_encoderPcmToAac },
        { "closeEncoder",           "()V",                      (void *) Android_JNI_closeAacEncoder },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((vm)->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_FALSE;
    }
    jclass rtmp_class = env->FindClass(RTMP_CLASS_NAME);
    env->RegisterNatives(rtmp_class, rtmp_methods, NELEM(rtmp_methods));
    jclass video_encoder_class = env->FindClass(VIDEO_ENCODER_CLASS_NAME);
    env->RegisterNatives(video_encoder_class, video_encoder_methods, NELEM(video_encoder_methods));
    jclass audio_encoder_class = env->FindClass(AUDIO_ENCODER_CLASS_NAME);
    env->RegisterNatives(audio_encoder_class, audio_encoder_methods, NELEM(audio_encoder_methods));
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
}

#ifdef __cplusplus
}
#endif
#endif
