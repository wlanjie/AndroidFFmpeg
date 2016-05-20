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

static void release_media_source(MediaSource *mediaSource) {
    av_freep(&mediaSource->input_data_source);
    av_freep(&mediaSource->output_data_source);
    if (mediaSource->video_avfilter) {
        av_freep(&mediaSource->video_avfilter);
    }
    if (mediaSource->audio_avfilter) {
        av_freep(&mediaSource->audio_avfilter);
    }
    av_freep(&mediaSource);
}

static void throw_not_open_file_exception(JNIEnv *env, MediaSource *mediaSource) {
    if ((*env)->ExceptionCheck) {
        jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ExceptionClear(env);
        char error[255];
        snprintf(error, sizeof(error), "Cannot not open file %s\n", mediaSource->input_data_source);
        (*env)->ThrowNew(env, illegal_argument_class, error);
    }
    release_media_source(mediaSource);
    release();
}

static jint compress_jni(JNIEnv *env, jobject object, jint new_width, jint new_height) {
    int ret = 0;
    MediaSource *mediaSource = get_media_source(env, object);
    mediaSource->video_avfilter = av_strdup("null");
    mediaSource->audio_avfilter = av_strdup("anull");
    if (mediaSource == NULL) return -1;
    //判断文件是否存在
    if (check_file_exist(env, mediaSource) < 0) {
        release_media_source(mediaSource);
        (*env)->DeleteLocalRef(env, object);
        release();
        return -1;
    }
    if (!input_file) {
        ret = open_input_file(mediaSource);
        if (ret < 0) {
            throw_not_open_file_exception(env, mediaSource);
            (*env)->DeleteLocalRef(env, object);
            return ret;
        }
    }
    ret = open_output_file(mediaSource, new_width, new_height);
    if (ret < 0) {
        release_media_source(mediaSource);
        release();
        (*env)->DeleteLocalRef(env, object);
        return ret;
    }
    ret = transcode();
    release_media_source(mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return ret;
}

static jint get_video_width(JNIEnv *env, jobject object) {
    int ret = 0;
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    if (check_file_exist(env, mediaSource) < 0) {
        release_media_source(mediaSource);
        release();
        return -1;
    }
    if (!input_file) {
        if ((ret = open_input_file(mediaSource)) < 0) {
            throw_not_open_file_exception(env, mediaSource);
            (*env)->DeleteLocalRef(env, object);
            return ret;
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
    release_media_source(mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return ret;
}

static jint get_video_height(JNIEnv *env, jobject object) {
    int ret = 0;
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    if (check_file_exist(env, mediaSource) < 0) {
        release_media_source(mediaSource);
        release();
        return -1;
    }
    if (!input_file) {
        if ((ret = open_input_file(mediaSource)) < 0) {
            throw_not_open_file_exception(env, mediaSource);
            (*env)->DeleteLocalRef(env, object);
            return ret;
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
    release_media_source(mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return 0;
}

static jdouble get_video_rotation(JNIEnv *env, jobject object) {
    int ret = 0;
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    if (check_file_exist(env, mediaSource) < 0) {
        release_media_source(mediaSource);
        release();
        return -1.0;
    }
    if (!input_file) {
        if ((ret = open_input_file(mediaSource)) < 0) {
            throw_not_open_file_exception(env, mediaSource);
            (*env)->DeleteLocalRef(env, object);
            return ret;
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
    release_media_source(mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return 0;
}

static jint crop_jni(JNIEnv *env, jobject object, jint x, jint y, jint width, jint height) {
    int ret = 0;
    MediaSource *mediaSource = get_media_source(env, object);
    if (mediaSource == NULL) return -1;
    char crop_avfilter[128];
    snprintf(crop_avfilter, sizeof(crop_avfilter), "crop=%d:%d:%d:%d", width, height, x, y);
    if (check_file_exist(env, mediaSource) < 0) {
        release_media_source(mediaSource);
        (*env)->DeleteLocalRef(env, object);
        release();
        return -1;
    }
    mediaSource->video_avfilter = av_strdup(crop_avfilter);
    mediaSource->audio_avfilter = av_strdup("anull");
    if (!input_file) {
        ret = open_input_file(mediaSource);
        if (ret < 0) {
            throw_not_open_file_exception(env, mediaSource);
            (*env)->DeleteLocalRef(env, object);
            return ret;
        }
    }
    ret = open_output_file(mediaSource, 0, 0);
    if (ret < 0) {
        release_media_source(mediaSource);
        release();
        (*env)->DeleteLocalRef(env, object);
        return ret;
    }
    ret = transcode();
    release_media_source(mediaSource);
    (*env)->DeleteLocalRef(env, object);
    return ret;
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
        {"crop", "(IIII)I", crop_jni},
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
