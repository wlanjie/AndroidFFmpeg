//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <queue>
#include "encoder.h"
#include "srs_librtmp.hpp"
#include "muxer.h"
#include "log.h"
#include "VideoEncode.h"
#include "AudioEncode.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/streaming/Encoder"
#define SOFT_CLASS_NAME "com/wlanjie/streaming/SoftEncoder"
#ifdef __cplusplus
extern "C" {
#endif

struct Frame {
    char *data;
    int size;
    int packet_type;
    int pts;
};

std::queue<Frame> q;

VideoEncode videoEncode;
AudioEncode audioEncode;
srs_rtmp_t rtmp;
bool is_stop = false;

void Android_JNI_startPublish(JNIEnv *env, jobject object) {
    while (!is_stop) {
        if (q.empty()) {
            continue;
        }
        Frame frame = q.front();
        q.pop();
        int ret = srs_rtmp_write_packet(rtmp, frame.packet_type == 0 ? SRS_RTMP_TYPE_AUDIO : SRS_RTMP_TYPE_VIDEO, frame.pts, frame.data, frame.size);
        LOGE("ret = %d size = %d", ret, q.size());
        usleep(500 * 10);
    }
}

void Android_JNI_setEncoderResolution(JNIEnv *env, jobject object, jint width, jint height) {
    videoEncode.setEncodeResolution(width, height);
}

void Android_JNI_setEncoderFps(JNIEnv *env, jobject object, jint fps) {
    videoEncode.setEncodeFps(fps);
}

void Android_JNI_setEncoderGop(JNIEnv *env, jobject object, jint gop_size) {
    videoEncode.setEncodeGop(gop_size);
}

void Android_JNI_setEncoderBitrate(JNIEnv *env, jobject object, jint bitrate) {
    videoEncode.setEncodeBitrate(bitrate);
}

void Android_JNI_setEncoderPreset(JNIEnv *env, jobject object, jstring preset) {
    const char *enc_preset = env->GetStringUTFChars(preset, NULL);
    videoEncode.setEncodePreset(enc_preset);
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
    return (jboolean) (videoEncode.open_h264_encode() >= 0 ? JNI_TRUE : JNI_FALSE);
}

void Android_JNI_closeH264Encoder(JNIEnv* env, jobject object) {
    videoEncode.close_h264_encode();
}

jint Android_JNI_NV21EncodeToH264(JNIEnv* env, jobject object, jbyteArray frame, jint src_width, jint src_height, jboolean need_flip, jint rotate_degree, jlong pts) {
    return NV21EncodeToH264(env, object, frame, src_width, src_height, need_flip, rotate_degree, pts);
}

jint Android_JNI_rgbaEncodeToH264(JNIEnv* env, jobject object, jbyteArray rgba_frame, jint src_width, jint src_height, jboolean need_flip, jint rotate_degree, jlong pts) {
    jbyte *rgba = env->GetByteArrayElements(rgba_frame, NULL);
    int h264_size = videoEncode.rgba_encode_to_h264((char *) rgba, src_width, src_height, need_flip, rotate_degree, pts);

    if (h264_size > 0) {
        char *sps_pps = NULL;
        int sps_pps_size = 0;
        char *h264 = NULL;
        int h264_length = 0;
        if (h264_size > 0) {
            muxer_h264((char *) videoEncode.get_h264(), h264_size, pts, pts, &sps_pps, &sps_pps_size,
                       &h264, &h264_length);
            if (sps_pps != NULL && sps_pps_size > 0) {
                Frame frame;
                frame.data = sps_pps;
                frame.size = sps_pps_size;
                frame.pts = pts;
                frame.packet_type = 1;
                q.push(frame);
            }
            if (h264 != NULL && h264_length > 0) {
                Frame frame;
                frame.data = h264;
                frame.size = h264_length;
                frame.pts = pts;
                frame.packet_type = 1;
                q.push(frame);
            }
        }

//        jbyteArray outputFrame = env->NewByteArray(h264_size);
//        env->SetByteArrayRegion(outputFrame, 0, h264_size, (jbyte *) videoEncode.get_h264());
//
//        jclass clz = env->GetObjectClass(object);
//        jmethodID mid = env->GetMethodID(clz, "onH264EncodedData", "([BIZ)V");
//        env->CallVoidMethod(object, mid, outputFrame, pts, false);
    }
    env->ReleaseByteArrayElements(rgba_frame, rgba, NULL);
    return 0;
}

jboolean Android_JNI_openAacEncode(JNIEnv *env, jobject object, jint channels, jint sample_rate, jint bitrate) {
    return (jboolean) audioEncode.open_aac_encode(channels, sample_rate, bitrate);
}

jint Android_JNI_encoderPcmToAac(JNIEnv *env, jobject object, jbyteArray pcm, int pts) {
    jbyte *pcm_frame = env->GetByteArrayElements(pcm, NULL);
    int pcm_length = env->GetArrayLength(pcm);
    int aac_size = audioEncode.encode_pcm_to_aac((char *) pcm_frame, pcm_length);
    if (aac_size > 0) {
        char *aac = NULL;
        int aac_length = 0;
        int aac_packet_type = 0;
        muxer_aac(10, 3, 1, 1, (char *) audioEncode.getAac(), aac_size, pts, &aac, &aac_length, &aac_packet_type);

        if (aac_length > 0) {
            Frame frame;
            frame.data = aac;
            frame.size = aac_length;
            frame.packet_type = 0;
            frame.pts = pts;
            q.push(frame);
        }

//        jbyteArray outputFrame = env->NewByteArray(aac_size);
//        env->SetByteArrayRegion(outputFrame, 0, aac_size, (const jbyte *) audioEncode.getAac());
//
//        jclass clz = env->GetObjectClass(object);
//        jmethodID mid = env->GetMethodID(clz, "onAacEncodeData", "([B)V");
//        env->CallVoidMethod(object, mid, outputFrame);
    }
    env->ReleaseByteArrayElements(pcm, pcm_frame, NULL);
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

jint Android_JNI_write_video_test(JNIEnv* env, jobject object, jbyteArray frame, jint src_width, jint src_height, jboolean need_flip, jint rotate_degree, jint pts) {
    jbyte *data = env->GetByteArrayElements(frame, NULL);
    int h264_size = videoEncode.rgba_encode_to_h264((char *) data, src_width, src_height, need_flip, rotate_degree, pts);
    int ret = -1;
    if (h264_size > 0) {
        ret = srs_h264_write_raw_frames(rtmp, (char *) videoEncode.get_h264(), h264_size, pts, pts);
    }
//    char *sps_pps = NULL;
//    int sps_pps_size = 0;
//    char *h264 = NULL;
//    int h264_length = 0;
//    if (h264_size > 0) {
//        muxer_h264((char *) videoEncode.get_h264(), h264_size, pts, pts, &sps_pps, &sps_pps_size,
//                   &h264, &h264_length);
//        if (sps_pps != NULL && sps_pps_size > 0) {
//            ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_VIDEO, pts, sps_pps, sps_pps_size);
//        }
//        if (h264 != NULL && h264_length > 0) {
//            ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_VIDEO, pts, h264, h264_length);
//        }
//    }
    LOGE("ret = %d", ret);
    env->ReleaseByteArrayElements(frame, data, NULL);
    return ret;
}

jint Android_JNI_write_audio_test(JNIEnv *env, jobject object, jbyteArray pcm, jint pts) {
    jbyte *data = env->GetByteArrayElements(pcm, NULL);
    jsize data_size = env->GetArrayLength(pcm);
    int aac_size = audioEncode.encode_pcm_to_aac((char *) data, data_size);
    int ret = -1;
    if (aac_size > 0) {
        ret = srs_audio_write_raw_frame(rtmp, 10, 3, 1, 1, (char *) audioEncode.getAac(), aac_size, pts);
    }
//    char *aac = NULL;
//    int aac_length = 0;
//    int aac_packet_type = 0;
//    int ret = muxer_aac(10, 3, 1, 1, (char *) audioEncode.getAac(), aac_size, pts, &aac, &aac_length, &aac_packet_type);
//    if (ret >= 0) {
//        ret = srs_rtmp_write_packet(rtmp, SRS_RTMP_TYPE_AUDIO, pts, aac, aac_length);
//    }
    env->ReleaseByteArrayElements(pcm, data, NULL);
    return ret;
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
    is_stop = true;
    srs_rtmp_destroy(rtmp);
    rtmp = NULL;
}

static JNINativeMethod encoder_methods[] = {
        {"startPublish",            "()V",                      (void *) Android_JNI_startPublish },
        { "setEncoderResolution",   "(II)V",                    (void *) Android_JNI_setEncoderResolution },
        { "connect",                "(Ljava/lang/String;)I",    (void *) Android_JNI_connect },
        { "writeVideo",             "(J[B)I",                   (void *) Android_JNI_write_video_sample },
        { "writeAudio",             "(J[BII)I",                 (void *) Android_JNI_write_audio_sample },
        { "muxerH264",              "([BI)V",                   (void *) Android_JNI_muxer_h264 },
        { "muxerAac",               "([BI)V",                   (void *) Android_JNI_muxer_aac },
        { "destroy",                "()V",                      (void *) Android_JNI_destroy },
        { "NV21ToI420",             "([BIIZI)[B",               (void *) Android_JNI_NV21ToI420 },
        { "NV21ToNV12",             "([BIIZI)[B",               (void *) Android_JNI_NV21ToNV12 },
        { "writeVideoTest",       "([BIIZII)I",               (void *) Android_JNI_write_video_test },
        { "writeAudioTest",       "([BI)I",               (void *) Android_JNI_write_audio_test },
};

static JNINativeMethod soft_encoder_methods[] = {
        { "setEncoderFps",          "(I)V",                     (void *) Android_JNI_setEncoderFps },
        { "setEncoderGop",          "(I)V",                     (void *) Android_JNI_setEncoderGop },
        { "setEncoderBitrate",      "(I)V",                     (void *) Android_JNI_setEncoderBitrate },
        { "setEncoderPreset",       "(Ljava/lang/String;)V",    (void *) Android_JNI_setEncoderPreset },
        { "openH264Encoder",        "()Z",                      (void *) Android_JNI_openH264Encoder },
        { "closeH264Encoder",       "()V",                      (void *) Android_JNI_closeH264Encoder },
        { "openAacEncoder",         "(III)Z",                   (void *) Android_JNI_openAacEncode },
        { "encoderPcmToAac",        "([BI)I",                    (void *) Android_JNI_encoderPcmToAac },
        { "closeAacEncoder",        "()V",                      (void *) Android_JNI_closeAacEncoder },
        { "NV21EncodeToH264",       "([BIIZIJ)I",               (void *) Android_JNI_NV21EncodeToH264 },
        { "rgbaEncodeToH264",       "([BIIZIJ)I",               (void *) Android_JNI_rgbaEncodeToH264 },
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
