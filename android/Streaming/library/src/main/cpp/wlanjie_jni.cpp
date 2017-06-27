//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <queue>
#include "srs_librtmp.hpp"
#include "muxer.h"
#include "AudioEncode.h"
#include "h264encoder.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/streaming/Encoder"
#define SOFT_CLASS_NAME "com/wlanjie/streaming/SoftEncoder"
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
    int size;
    int packet_type;
    int pts;
};

std::queue<Frame> q;

wlanjie::H264encoder h264encoder;
AudioEncode audioEncode;
srs_rtmp_t rtmp;
bool is_stop = false;

void Android_JNI_startPublish(JNIEnv *env, jobject object) {
    while (!is_stop) {
        while (!q.empty()) {
            Frame frame = q.front();
            q.pop();
            srs_rtmp_write_packet(rtmp, (char) (frame.packet_type == AUDIO_TYPE ? SRS_RTMP_TYPE_AUDIO : SRS_RTMP_TYPE_VIDEO),
                                  (u_int32_t) frame.pts, frame.data, frame.size);
        }
        usleep(1000 * 500);
    }
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

void Android_JNI_setEncoderResolution(JNIEnv *env, jobject object, jint width, jint height) {
    h264encoder.setFrameSize(width, height);
}

void Android_JNI_setEncoderFps(JNIEnv *env, jobject object, jint fps) {
}

void Android_JNI_setEncoderGop(JNIEnv *env, jobject object, jint gop_size) {
}

void Android_JNI_setEncoderBitrate(JNIEnv *env, jobject object, jint bitrate) {
}

jboolean Android_JNI_openH264Encoder(JNIEnv* env, jobject object) {
    h264encoder.openH264Encoder();
    return JNI_TRUE;
}

void Android_JNI_closeH264Encoder(JNIEnv* env, jobject object) {
    h264encoder.closeH264Encoder();
}

jint Android_JNI_rgbaEncodeToH264(JNIEnv* env, jobject object, jbyteArray rgba_frame, jint src_width, jint src_height, jboolean need_flip, jint rotate_degree, jlong pts) {
    jbyte *rgba = env->GetByteArrayElements(rgba_frame, NULL);
    return 0;
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
        muxer_aac_success((char *) audioEncode.getAac(), aac_size, pts);
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

int Android_JNI_write_video_sample(JNIEnv *env, jobject object, jlong timestamp, jbyteArray frame) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);

    int ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_VIDEO, timestamp, (char *) data, data_size);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return ret;
}

jint Android_JNI_write_audio_sample(JNIEnv *env, jobject object, jlong timestamp, jbyteArray frame, jint sampleRate, jint channel) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
    int ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_AUDIO, timestamp, (char *) data, data_size);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return ret;
}

void Android_JNI_muxer_h264(JNIEnv *env, jobject object, jbyteArray frame, jint size, jint pts) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);

    muxer_h264_success((char *) data, size, pts);

    env->ReleaseByteArrayElements(frame, data, NULL);
}

void Android_JNI_muxer_aac(JNIEnv *env, jobject object, jbyteArray frame, jint size, jint pts) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);

    muxer_aac_success((char *) data, size, pts);

    env->ReleaseByteArrayElements(frame, data, NULL);
}

void Android_JNI_destroy(JNIEnv *env, jobject object) {
    is_stop = true;
    srs_rtmp_destroy(rtmp);
    rtmp = NULL;
}

void Android_JNI_encode_h264(JNIEnv *env, jobject object, jbyteArray data, jint width, jint height, jlong pts) {
    jbyte *frame = env->GetByteArrayElements(data, NULL);
    int h264_size;
    uint8_t *h264;
    h264encoder.encoder((char *) frame, width, height, (long) pts, &h264_size, &h264);
    env->ReleaseByteArrayElements(data, frame, NULL);
    if (h264_size > 0) {
        muxer_h264_success((char *) h264, h264_size, (int) pts);
    }
}

static JNINativeMethod encoder_methods[] = {
        { "setEncoderResolution",   "(II)V",                    (void *) Android_JNI_setEncoderResolution },
        { "muxerH264",              "([BII)V",                  (void *) Android_JNI_muxer_h264 },
        { "muxerAac",               "([BII)V",                  (void *) Android_JNI_muxer_aac },
};

static JNINativeMethod soft_encoder_methods[] = {
        { "setEncoderFps",          "(I)V",                     (void *) Android_JNI_setEncoderFps },
        { "setEncoderGop",          "(I)V",                     (void *) Android_JNI_setEncoderGop },
        { "setEncoderBitrate",      "(I)V",                     (void *) Android_JNI_setEncoderBitrate },
        { "openH264Encoder",        "()Z",                      (void *) Android_JNI_openH264Encoder },
        { "closeH264Encoder",       "()V",                      (void *) Android_JNI_closeH264Encoder },
        { "openAacEncoder",         "(III)Z",                   (void *) Android_JNI_openAacEncode },
        { "encoderPcmToAac",        "([BI)I",                   (void *) Android_JNI_encoderPcmToAac },
        { "closeAacEncoder",        "()V",                      (void *) Android_JNI_closeAacEncoder },
        { "rgbaEncodeToH264",       "([BIIZIJ)I",               (void *) Android_JNI_rgbaEncodeToH264 },
};

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
    jclass clazz = env->FindClass(CLASS_NAME);
    env->RegisterNatives(clazz, encoder_methods, NELEM(encoder_methods));
    env->DeleteLocalRef(clazz);
    jclass soft_clazz = env->FindClass(SOFT_CLASS_NAME);
    env->RegisterNatives(soft_clazz, soft_encoder_methods, NELEM(soft_encoder_methods));
    env->DeleteLocalRef(soft_clazz);
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
