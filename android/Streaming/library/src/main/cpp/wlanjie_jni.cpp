//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include "encoder.h"
#include "srs_librtmp.hpp"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/streaming/Encoder"
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

void Android_JNI_destroy(JNIEnv *env, jobject object) {
    srs_rtmp_destroy(rtmp);
    rtmp = NULL;
}

static JNINativeMethod enc_methods[] = {
        { "connect",                "(Ljava/lang/String;)I",    (void *) Android_JNI_connect },
        { "writeVideo",             "(J[B)I",                   (void *) Android_JNI_write_video_sample },
        { "writeAudio",             "(J[BII)I",                 (void *) Android_JNI_write_audio_sample },
        { "destroy",                "()V",                      (void *) Android_JNI_destroy },
        { "setEncoderResolution",   "(II)V",                    (void *) Android_JNI_setEncoderResolution },
        { "setEncoderFps",          "(I)V",                     (void *) Android_JNI_setEncoderFps },
        { "setEncoderGop",          "(I)V",                     (void *) Android_JNI_setEncoderGop },
        { "setEncoderBitrate",      "(I)V",                     (void *) Android_JNI_setEncoderBitrate },
        { "setEncoderPreset",       "(Ljava/lang/String;)V",    (void *) Android_JNI_setEncoderPreset },
        { "NV21ToI420",             "([BIIZI)[B",               (void *) Android_JNI_NV21ToI420 },
        { "NV21ToNV12",             "([BIIZI)[B",               (void *) Android_JNI_NV21ToNV12 },
        { "openH264Encoder",        "()Z",                      (void *) Android_JNI_openH264Encoder },
        { "closeH264Encoder",       "()V",                      (void *) Android_JNI_closeH264Encoder },
        { "NV21EncodeToH264",         "([BIIZIJ)I",             (void *) Android_JNI_NV21EncodeToH264 },
        { "openAacEncoder",         "(III)Z",                   (void *) Android_JNI_openAacEncode },
        { "encoderPcmToAac",        "([B)I",                    (void *) Android_JNI_encoderPcmToAac },
        { "closeAacEncoder",        "()V",                      (void *) Android_JNI_closeAacEncoder },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((vm)->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    jclass clazz = env->FindClass(CLASS_NAME);
    env->RegisterNatives(clazz, enc_methods, NELEM(enc_methods));
    env->DeleteLocalRef(clazz);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
}

#ifdef __cplusplus
}
#endif
#endif
