//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include "audioencode.h"
#include "h264encode.h"
#include "muxer.h"
#include "log.h"
#include "rtmp/libs/srs_librtmp.hpp"
#include <queue>
#include <unistd.h>

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
pthread_t worker;
pthread_mutex_t mutex;
std::ofstream _outputStream;

void *publish(void *arg) {
    while (!is_stop) {
        while (!q.empty()) {
            pthread_mutex_lock(&mutex);
            Frame frame = q.front();
            q.pop();

            srs_rtmp_write_packet(rtmp, (char) (frame.packet_type == AUDIO_TYPE ? SRS_RTMP_TYPE_AUDIO : SRS_RTMP_TYPE_VIDEO),
                                  (u_int32_t) frame.pts, frame.data, frame.size);
            delete[] frame.data;
            pthread_mutex_unlock(&mutex);
        }
        usleep(1000 * 500);
    }
    return NULL;
}

void Android_JNI_startPublish(JNIEnv *env, jobject object) {
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_create(&worker, &attr, publish, NULL);
    pthread_mutex_init(&mutex, NULL);
}


void muxer_aac_success(char *data, int size, int pts) {
    char *aac = NULL;
    int aac_length = 0;
    int aac_packet_type = 0;
    muxer_aac(10, 3, 1, 1, data, size, pts, &aac, &aac_length, &aac_packet_type);

    if (aac_length > 0) {
        Frame frame;
        frame.data = aac;
        frame.size = aac_length;
        frame.pts = pts;
        frame.packet_type = AUDIO_TYPE;
        q.push(frame);
    }
}

void muxer_h264_success(char *data, int size, int pts) {
    char* sps_pps = NULL;
    int sps_pps_size = 0;
    char* h264 = NULL;
    int h264_size = 0;
    if (data == NULL) {
        return;
    }
    muxer_h264(data, size, pts, pts, &sps_pps, &sps_pps_size, &h264, &h264_size);
    if (sps_pps != NULL && sps_pps_size > 0) {
        Frame frame;
        frame.data = sps_pps;
        frame.size = sps_pps_size;
        frame.pts = pts;
        frame.packet_type = VIDEO_TYPE;
        q.push(frame);
    }
    if (h264 != NULL && h264_size > 0) {
        Frame frame;
        frame.data = h264;
        frame.size = h264_size;
        frame.pts = pts;
        frame.packet_type = VIDEO_TYPE;
        q.push(frame);
    }
}

void Android_JNI_setVideoParameter(JNIEnv *env, jobject object, jobject videoParameterObject) {
    jclass videoParameterClass = env->GetObjectClass(videoParameterObject);
    jmethodID getFrameWidthId = env->GetMethodID(videoParameterClass, "getFrameWidth", "()I");
    jint frameWidth = env->CallIntMethod(videoParameterObject, getFrameWidthId);

    jmethodID getFrameHeightId = env->GetMethodID(videoParameterClass, "getFrameHeight", "()I");
    jint frameHeight = env->CallIntMethod(videoParameterObject, getFrameHeightId);

    jmethodID getVideoWidthId = env->GetMethodID(videoParameterClass, "getVideoWidth", "()I");
    jint videoWidth = env->CallIntMethod(videoParameterObject, getVideoWidthId);

    jmethodID getVideoHeightId = env->GetMethodID(videoParameterClass, "getVideoHeight", "()I");
    jint videoHeight = env->CallIntMethod(videoParameterObject, getVideoHeightId);

    jmethodID getBitrateId = env->GetMethodID(videoParameterClass, "getBitrate", "()I");
    jint bitrate = env->CallIntMethod(videoParameterObject, getBitrateId);

    jmethodID getFrameRateId = env->GetMethodID(videoParameterClass, "getFrameRate", "()I");
    jint frameRate = env->CallIntMethod(videoParameterObject, getFrameRateId);

    wlanjie::VideoParameter parameter;
    parameter.frameWidth = frameWidth;
    parameter.frameHeight = frameHeight;
    parameter.videoWidth = videoWidth;
    parameter.videoHeight = videoHeight;
    parameter.frameRate = frameRate;
    parameter.bitrate = bitrate;
    h264Encoder.setVideoParameter(parameter);
}

jboolean Android_JNI_openH264Encoder(JNIEnv *env, jobject object) {
    h264Encoder.openH264Encoder();
    return JNI_TRUE;
}

void Android_JNI_closeH264Encoder(JNIEnv *env, jobject object) {
    std::queue<Frame> empty;
    std::swap(q, empty);
    h264Encoder.closeH264Encoder();
}

jboolean Android_JNI_openAacEncode(JNIEnv *env, jobject object, jint channels, jint sample_rate,
                                   jint bitrate) {
    return (jboolean) audioEncode.open(channels, sample_rate, bitrate);
}

jint Android_JNI_encode_audio(JNIEnv *env, jobject object, jbyteArray pcm, jint pts) {
    if (is_stop) {
        return 0;
    }
    jbyte *pcm_frame = env->GetByteArrayElements(pcm, NULL);
    int pcm_length = env->GetArrayLength(pcm);
    int aac_size;
    uint8_t *aac;
    audioEncode.encode((char *) pcm_frame, pcm_length, &aac_size, &aac);
    env->ReleaseByteArrayElements(pcm, pcm_frame, NULL);
    if (aac_size > 0) {
        muxer_aac_success((char *) aac, aac_size, pts);
    }
    return 0;
}

void Android_JNI_closeAacEncoder() {
    audioEncode.close();
}

void Android_JNI_encode_video(JNIEnv *env, jobject object, jbyteArray data, jlong pts) {
    if (is_stop) {
        return;
    }
    jbyte *frame = env->GetByteArrayElements(data, NULL);
    int h264_size;
    uint8_t *h264;
    h264Encoder.encoder((char *) frame, (long) pts, &h264_size, &h264);
    env->ReleaseByteArrayElements(data, frame, NULL);
    if (h264_size > 0) {
        muxer_h264_success((char *) h264, h264_size, (int) pts);
        delete[] h264;
    }
}

jint Android_JNI_connect(JNIEnv *env, jobject object, jstring url) {
    if (rtmp != NULL) {
        LOGE("rtmp not NULL");
        return -1;
    }
    is_stop = false;
    const char *rtmp_url = env->GetStringUTFChars(url, 0);
    rtmp = srs_rtmp_create(rtmp_url);
    int result = 0;
    if ((result = srs_rtmp_handshake(rtmp)) != 0) {
        LOGE("srs_rtmp_handshake error = %d", result);
        return -1;
    }
    if ((result = srs_rtmp_connect_app(rtmp)) != 0) {
        LOGE("srs_rtmp_connect_app error =%d", result);
        return -1;
    }
    if ((result = srs_rtmp_publish_stream(rtmp)) != 0) {
        LOGE("srs_rtmp_publish_stream = %d", result);
        return -1;
    }

    _outputStream.open("/sdcard/streaming.h264", std::ios_base::binary | std::ios_base::out);
    env->ReleaseStringUTFChars(url, rtmp_url);

    return 0;
}

int Android_JNI_write_video(JNIEnv *env, jobject object, jbyteArray frame, jlong timestamp) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
    muxer_h264_success((char *) data, data_size, timestamp);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return 0;
}

jint Android_JNI_write_audio(JNIEnv *env, jobject object, jbyteArray frame, jlong timestamp,
                             jint sampleRate, jint channel) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
    muxer_aac_success((char *) data, data_size, timestamp);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return 0;
}

void Android_JNI_destroy(JNIEnv *env, jobject object) {
    _outputStream.close();
    is_stop = true;
    srs_rtmp_destroy(rtmp);
    void *retval;
    pthread_join(worker, &retval);
    pthread_mutex_destroy(&mutex);
    rtmp = NULL;
}

static JNINativeMethod rtmp_methods[] = {
        {"startPublish", "()V",                   (void *) Android_JNI_startPublish},
        {"connect",      "(Ljava/lang/String;)I", (void *) Android_JNI_connect},
        {"writeVideo",   "([BJ)I",                (void *) Android_JNI_write_video},
        {"writeAudio",   "([BJII)I",              (void *) Android_JNI_write_audio},
        {"destroy",      "()V",                   (void *) Android_JNI_destroy},
};

static JNINativeMethod video_encoder_methods[] = {
        {"openEncoder",         "()Z",      (void *) Android_JNI_openH264Encoder},
        {"closeEncoder",        "()V",      (void *) Android_JNI_closeH264Encoder},
        {"setVideoParameter",   "(Lcom/wlanjie/streaming/video/VideoParameter;)V", (void *) Android_JNI_setVideoParameter },
        {"encode",              "([BJ)V", (void *) Android_JNI_encode_video},
};

static JNINativeMethod audio_encoder_methods[] = {
        {"openEncoder",  "(III)Z", (void *) Android_JNI_openAacEncode},
        {"encode",       "([BI)I", (void *) Android_JNI_encode_audio},
        {"closeEncoder", "()V",    (void *) Android_JNI_closeAacEncoder},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((vm)->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
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
