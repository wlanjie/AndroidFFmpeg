//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <stdio.h>
#include "utils.h"
#include "openfile.h"
#include "log.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/library/FFmpeg"
#ifdef __cplusplus
extern "C" {
#endif

static jint compress_jni(JNIEnv *env, jobject object, jint new_width, jint new_height) {
    int ret = 0;
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    //判断文件是否存在
    if (check_file_exist(env, mediaSource) < 0) {
        (*env)->DeleteLocalRef(env, object);
        release();
        return -1;
    }
    if (!input_file) {
        ret = open_input_file(mediaSource->input_path);
        if (ret < 0) {
            if ((*env)->ExceptionCheck) {
                jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
                (*env)->ExceptionClear(env);
                char error[255];
                snprintf(error, sizeof(error), "Cannot not open file %s\n", mediaSource->input_path);
                (*env)->ThrowNew(env, illegal_argument_class, error);
            }
            (*env)->DeleteLocalRef(env, object);
            release();
            return -2;
        }
    }
    ret = open_output_file(mediaSource->output_path, new_width, new_height);
    if (ret < 0) {
        return -3;
    }
    ret = transcode();
    if (ret < 0) {
        release();
    }
    (*env)->DeleteLocalRef(env, mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return ret;
}

static jint get_video_width(JNIEnv *env, jobject object) {
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return 0;
    if (check_file_exist(env, mediaSource) < 0) {
        release();
        return -1;
    }
    if (!input_file) {
        if (open_input_file(mediaSource->input_path) < 0) {
            release();
            return -1;
        }
    }
    for (int i = 0; i < input_file->ic->nb_streams; ++i) {
        InputStream *ist = input_streams[i];
        if (ist && ist->dec_ctx && ist->dec_ctx->codec_type == AVMEDIA_TYPE_VIDEO) {
            return ist->dec_ctx->width;
        } else {
            continue;
        }
    }
    (*env)->DeleteLocalRef(env, mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return 0;
}

static jint get_video_height(JNIEnv *env, jobject object) {
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    if (check_file_exist(env, mediaSource) < 0) {
        release();
        return -1;
    }
    if (!input_file) {
        if (open_input_file(mediaSource->input_path) < 0) {
            release();
            return -1;
        }
    }
    for (int i = 0; i < input_file->ic->nb_streams; ++i) {
        InputStream *ist = input_streams[i];
        if (ist && ist->dec_ctx && ist->dec_ctx->codec_type == AVMEDIA_TYPE_VIDEO) {
            return ist->dec_ctx->height;
        } else {
            continue;
        }
    }
    (*env)->DeleteLocalRef(env, mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return 0;
}

static jdouble get_video_rotation(JNIEnv *env, jobject object) {
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    if (check_file_exist(env, mediaSource) < 0) {
        release();
        return -1.0;
    }
    if (!input_file) {
        if (open_input_file(mediaSource->input_path) < 0) {
            release();
            return -1.0;
        }
    }
    for (int i = 0; i < input_file->ic->nb_streams; ++i) {
        InputStream *ist = input_streams[i];
        if (ist && ist->dec_ctx && ist->dec_ctx->codec_type == AVMEDIA_TYPE_VIDEO) {
            return get_rotation(ist->st);
        } else {
            continue;
        }
    }
    (*env)->DeleteLocalRef(env, mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return 0;
}

static void release_ffmpeg(JNIEnv *env, jclass clazz) {
    release();
    (*env)->DeleteLocalRef(env, clazz);
}

void log_callback(void *ptr, int level, const char *fmt, va_list vl) {
    FILE *fp = fopen("/sdcard/av_log.txt", "a+");
    if (fp) {
        vfprintf(fp, fmt, vl);
        fflush(fp);
        fclose(fp);
    }
}

static JNINativeMethod g_methods[] = {
        {"compress", "(II)I", compress_jni},
        {"getVideoWidth", "()I", get_video_width},
        {"getVideoHeight", "()I", get_video_height},
        {"getRotation", "()D", get_video_rotation},
        {"release", "()V", release_ffmpeg},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    av_register_all();
    avfilter_register_all();
    av_log_set_level(AV_LOG_ERROR);
    av_log_set_callback(log_callback);
    jclass clazz = (*env)->FindClass(env, CLASS_NAME);
    (*env)->RegisterNatives(env, clazz, g_methods, NELEM(g_methods));
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGE("JNI OnUnLoad");
}

#ifdef __cplusplus
}
#endif
#endif
