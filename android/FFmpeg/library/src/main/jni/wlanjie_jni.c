//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <stdio.h>
#include "utils.h"
#include "openfile.h"
#include "compress.h"
#include "ffmpeg.h"
#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/library/FFmpeg"
#ifdef __cplusplus
extern "C" {
#endif

static jint compress(JNIEnv *env, jclass clazz, jstring *input_path, jstring *output_path, jint new_width, jint new_height) {
    int ret = 0;
    const char *input = (*env)->GetStringUTFChars(env, input_path, NULL);
    const char *output = (*env)->GetStringUTFChars(env, output_path, NULL);
    //判断文件是否存在

    if (check_file_exist(env, input) < 0) {
        (*env)->ReleaseStringUTFChars(env, output_path, output);
        (*env)->ReleaseStringUTFChars(env, input_path, input);
        (*env)->DeleteLocalRef(env, clazz);
        release();
        return -1;
    }
    if (!input_file) {
        ret = open_input_file(input);
        if (ret < 0) {
            if ((*env)->ExceptionCheck) {
                jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
                (*env)->ExceptionClear(env);
                char error[255];
                snprintf(error, sizeof(error), "Cannot not open file %s\n", input);
                (*env)->ThrowNew(env, illegal_argument_class, error);
            }
            (*env)->ReleaseStringUTFChars(env, input_path, input);
            (*env)->ReleaseStringUTFChars(env, output_path, output);
            (*env)->DeleteLocalRef(env, clazz);
            release();
            return -2;
        }
    }
    ret = open_output_file(output, new_width, new_height);
    if (ret < 0) {
        return -3;
    }
    ret = transcode();
    if (ret < 0) {
        release();
    }
    (*env)->ReleaseStringUTFChars(env, input_path, input);
    (*env)->ReleaseStringUTFChars(env, output_path, output);
    (*env)->DeleteLocalRef(env, clazz);
    return ret;
}

static jint get_video_width(JNIEnv *env, jclass clazz, jstring input_path) {
    const char *input = (*env)->GetStringUTFChars(env, input_path, 0);
    if (check_file_exist(env, input) < 0) {
        (*env)->ReleaseStringUTFChars(env, input_path, input);
        release();
        return -1;
    }
    if (!input_file) {
        if (open_input_file(input) < 0) {
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
    (*env)->DeleteLocalRef(env, clazz);
    return 0;
}

static jint get_video_height(JNIEnv *env, jclass clazz, jstring input_path) {
    const char *input = (*env)->GetStringUTFChars(env, input_path, 0);
    if (check_file_exist(env, input) < 0) {
        (*env)->ReleaseStringUTFChars(env, input_path, input);
        release();
        return -1;
    }
    if (!input_file) {
        if (open_input_file(input) < 0) {
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
    (*env)->DeleteLocalRef(env, clazz);
    return 0;
}

static jdouble get_video_rotation(JNIEnv *env, jclass clazz, jstring input_path) {
    const char *input = (*env)->GetStringUTFChars(env, input_path, 0);
    if (check_file_exist(env, input) < 0) {
        release();
        return -1.0;
    }
    if (!input_file) {
        if (open_input_file(input) < 0) {
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
    (*env)->DeleteLocalRef(env, clazz);
    return 0;
}

static void release_ffmpeg(JNIEnv *env, jclass clazz) {
    release();
    (*env)->DeleteLocalRef(env, clazz);
}

static JNINativeMethod g_methods[] = {
        {"compress", "(Ljava/lang/String;Ljava/lang/String;II)I", compress},
        {"getVideoWidth", "(Ljava/lang/String;)I", get_video_width},
        {"getVideoHeight", "(Ljava/lang/String;)I", get_video_height},
        {"getRotation", "(Ljava/lang/String;)D", get_video_rotation},
        {"release", "()V", release_ffmpeg}
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    jclass clazz = (*env)->FindClass(env, CLASS_NAME);
    (*env)->RegisterNatives(env, clazz, g_methods, NELEM(g_methods));
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {

}

#ifdef __cplusplus
}
#endif
#endif
