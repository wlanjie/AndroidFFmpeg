//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include "encoder.h"
#include "srs_librtmp.hpp"
#include "muxer.h"
#include "log.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/streaming/Encoder"
#define SOFT_CLASS_NAME "com/wlanjie/streaming/SoftEncoder"
#ifdef __cplusplus
extern "C" {
#endif

void Android_JNI_setEncoderResolution(JNIEnv *env, jobject object, jint width, jint height) {
    setEncoderResolution(width, height);
}

void Android_JNI_setEncoderFps(JNIEnv *env, jobject object, jint fps) {
    setEncoderFps(fps);
}

void Android_JNI_setEncoderGop(JNIEnv *env, jobject object, jint gop_size) {
    setEncoderGop(gop_size);
}

void Android_JNI_setEncoderBitrate(JNIEnv *env, jobject object, jint bitrate) {
    setEncoderBitrate(bitrate);
}

void Android_JNI_setEncoderPreset(JNIEnv *env, jobject object, jstring preset) {
    const char *enc_preset = env->GetStringUTFChars(preset, NULL);
    setEncoderPreset(enc_preset);
    env->ReleaseStringUTFChars(preset, enc_preset);
}

jbyteArray Android_JNI_NV21ToI420(JNIEnv *env, jobject object, jbyteArray frame, jint src_width, jint src_height, jboolean need_flip, jint rotate_degree) {
    jbyte *nv21_frame = env->GetByteArrayElements(frame, NULL);
    jbyteArray i420Frame = encoderNV21ToI420(env, nv21_frame, src_width, src_height, need_flip, rotate_degree);
    env->ReleaseByteArrayElements(frame, nv21_frame, JNI_ABORT);
    return i420Frame;
}

jbyteArray Android_JNI_NV21ToNV12(JNIEnv* env, jobject object, jbyteArray frame, jint src_width,
                            jint src_height, jboolean need_flip, jint rotate_degree) {
    jbyte *nv21_frame = env->GetByteArrayElements(frame, NULL);
    jbyteArray nv12Frame = NV21ToNV12(env, nv21_frame, src_width, src_height, need_flip, rotate_degree);
    env->ReleaseByteArrayElements(frame, nv21_frame, NULL);
    return nv12Frame;
}

jboolean Android_JNI_openH264Encoder(JNIEnv* env, jobject object) {
    return openH264Encoder();
}

void Android_JNI_closeH264Encoder(JNIEnv* env, jobject object) {
    closeH264Encoder();
}

jint Android_JNI_NV21EncodeToH264(JNIEnv* env, jobject object, jbyteArray frame, jint src_width,
                           jint src_height, jboolean need_flip, jint rotate_degree, jlong pts) {
    return NV21EncodeToH264(env, object, frame, src_width, src_height, need_flip, rotate_degree, pts);
}

jboolean Android_JNI_openAacEncode(JNIEnv *env, jobject object, jint channels, jint sample_rate, jint bitrate) {
    return open_aac_encoder(channels, sample_rate, bitrate);
}

jint Android_JNI_encoderPcmToAac(JNIEnv *env, jobject object, jbyteArray pcm) {
    jbyte *pcm_frame = env->GetByteArrayElements(pcm, NULL);
    int pcm_length = env->GetArrayLength(pcm);
    int ret = encoder_pcm_to_aac(env, object, pcm_frame, pcm_length);
    env->ReleaseByteArrayElements(pcm, pcm_frame, NULL);
    return ret;
}

void Android_JNI_closeAacEncoder() {
    close_aac_encoder();
}

srs_rtmp_t rtmp;

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

//    int ret = srs_h264_write_raw_frames(rtmp, data, data_size, timestamp, timestamp);
    int ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_VIDEO, timestamp, (char *) data, data_size);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return ret;
}

jint Android_JNI_write_audio_sample(JNIEnv *env, jobject object, jlong timestamp, jbyteArray frame, jint sampleRate, jint channel) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
//    int ret = srs_audio_write_raw_frame(rtmp, 10, 3, 1, 1, data, data_size, timestamp);
    int ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_AUDIO, timestamp, (char *) data, data_size);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return ret;
}

void call_muxer_h264_success(JNIEnv* env, jobject object, char* data, int data_size, int pts, int is_sequence_header) {
    jbyteArray output = env->NewByteArray(data_size);
    env->SetByteArrayRegion(output, 0, data_size, (const jbyte *) data);

    jclass clazz = env->FindClass(CLASS_NAME);
    jmethodID mid = env->GetMethodID(clazz, "muxerH264Success", "([BII)V");
    env->CallVoidMethod(object, mid, output, pts, is_sequence_header);
}

void call_muxer_aac_success(JNIEnv* env, jobject object, char* data, int data_size, int pts, int is_sequence_header) {
    jbyteArray output = env->NewByteArray(data_size);
    env->SetByteArrayRegion(output, 0, data_size, (const jbyte *) data);

    jclass clazz = env->FindClass(CLASS_NAME);
    jmethodID mid = env->GetMethodID(clazz, "muxerAacSuccess", "([BII)V");
    env->CallVoidMethod(object, mid, output, pts, is_sequence_header);
}

void Android_JNI_muxer_h264(JNIEnv *env, jobject object, jbyteArray frame, jint pts) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
    char* sps_pps = NULL;
    int sps_pps_size = 0;
    char* h264 = NULL;
    int h264_size = 0;
    muxer_h264((char *) data, data_size, pts, pts, &sps_pps, &sps_pps_size, &h264, &h264_size);
    if (sps_pps != NULL && sps_pps_size > 0) {
        call_muxer_h264_success(env, object, sps_pps, sps_pps_size, pts, 0);
    }
    if (h264 != NULL && h264_size > 0) {
        call_muxer_h264_success(env, object, h264, h264_size, pts, 1);
    }
    env->ReleaseByteArrayElements(frame, data, NULL);

}

void Android_JNI_muxer_aac(JNIEnv *env, jobject object, jbyteArray frame, jint pts) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    jsize data_size = env->GetArrayLength(frame);
    char* aac = NULL;
    int aac_size = 0;
    int aac_packet_type = 0;
    int ret = muxer_aac(10, 3, 1, 1, (char *) data, data_size, pts, &aac, &aac_size, &aac_packet_type);
    if (aac != NULL && aac_size > 0) {
        call_muxer_aac_success(env, object, aac, aac_size, pts, aac_packet_type);
    }

    env->ReleaseByteArrayElements(frame, data, NULL);
}

void Android_JNI_destroy(JNIEnv *env, jobject object) {
    srs_rtmp_destroy(rtmp);
    rtmp = NULL;
}

static JNINativeMethod encoder_methods[] = {
        { "setEncoderResolution",   "(II)V",                    (void *) Android_JNI_setEncoderResolution },
        { "connect",                "(Ljava/lang/String;)I",    (void *) Android_JNI_connect },
        { "writeVideo",             "(J[B)I",                   (void *) Android_JNI_write_video_sample },
        { "writeAudio",             "(J[BII)I",                 (void *) Android_JNI_write_audio_sample },
        { "muxerH264",              "([BI)V",                   (void *) Android_JNI_muxer_h264 },
        { "muxerAac",               "([BI)V",                   (void *) Android_JNI_muxer_aac },
        { "destroy",                "()V",                      (void *) Android_JNI_destroy },
        { "NV21ToI420",             "([BIIZI)[B",               (void *) Android_JNI_NV21ToI420 },
        { "NV21ToNV12",             "([BIIZI)[B",               (void *) Android_JNI_NV21ToNV12 },
};

static JNINativeMethod soft_encoder_methods[] = {
        { "setEncoderFps",          "(I)V",                     (void *) Android_JNI_setEncoderFps },
        { "setEncoderGop",          "(I)V",                     (void *) Android_JNI_setEncoderGop },
        { "setEncoderBitrate",      "(I)V",                     (void *) Android_JNI_setEncoderBitrate },
        { "setEncoderPreset",       "(Ljava/lang/String;)V",    (void *) Android_JNI_setEncoderPreset },
        { "openH264Encoder",        "()Z",                      (void *) Android_JNI_openH264Encoder },
        { "closeH264Encoder",       "()V",                      (void *) Android_JNI_closeH264Encoder },
        { "openAacEncoder",         "(III)Z",                   (void *) Android_JNI_openAacEncode },
        { "encoderPcmToAac",        "([B)I",                    (void *) Android_JNI_encoderPcmToAac },
        { "closeAacEncoder",        "()V",                      (void *) Android_JNI_closeAacEncoder },
        { "NV21EncodeToH264",       "([BIIZIJ)I",               (void *) Android_JNI_NV21EncodeToH264 },
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
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
}

#ifdef __cplusplus
}
#endif
#endif
