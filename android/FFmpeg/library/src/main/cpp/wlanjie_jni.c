//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include "SDL_main.h"
#include <jni.h>
#include "utils.h"
#include "openfile.h"
#include "SDL_android.h"
#include "ffplay.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/library/FFmpeg"
#ifdef __cplusplus
extern "C" {
#endif

MediaSource mediaSource = {NULL, NULL, NULL, NULL};

static void release_media_source(MediaSource *mediaSource) {
    av_freep(&mediaSource->input_data_source);
    av_freep(&mediaSource->output_data_source);
    if (mediaSource->video_avfilter) {
        av_freep(&mediaSource->video_avfilter);
    }
    if (mediaSource->audio_avfilter) {
        av_freep(&mediaSource->audio_avfilter);
    }
//    av_freep(&mediaSource);
}

static void throw_not_open_file_exception(JNIEnv *env, const char *input_data_source) {
    if ((*env)->ExceptionCheck) {
        (*env)->ExceptionClear(env);
    }
    jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
    char error[255];
    snprintf(error, sizeof(error), "Cannot not open file %s\n", input_data_source);
    (*env)->ThrowNew(env, illegal_argument_class, error);
    (*env)->DeleteLocalRef(env, illegal_argument_class);
    release();
}

static void call_set_input_data_source_exception(JNIEnv *env, MediaSource *mediaSource) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
    (*env)->ThrowNew(env, illegal_argument_class, "Can't get inputDataSource, please call FFmpeg.setInputDataSource method");
    (*env)->DeleteLocalRef(env, illegal_argument_class);
    release_media_source(mediaSource);
}

static jint Android_JNI_open_input(JNIEnv *env, jobject object, jstring input_path) {
    int ret = 0;
    const char *input_data_source = (*env)->GetStringUTFChars(env, input_path, NULL);
    //判断文件是否存在
    if (check_file_exist(env, input_data_source) < 0) {
        release_media_source(&mediaSource);
        (*env)->ReleaseStringUTFChars(env, input_path, input_data_source);
        (*env)->DeleteLocalRef(env, object);
        release();
        return -1;
    }
    ret = open_input_file(input_data_source);
    if (ret < 0 || input_file == NULL) {
        release_media_source(&mediaSource);
        throw_not_open_file_exception(env, input_data_source);
        (*env)->ReleaseStringUTFChars(env, input_path, input_data_source);
        (*env)->DeleteLocalRef(env, object);
        release();
        return ret;
    }
    for (int i = 0; i < input_file->ic->nb_streams; i++) {
        AVStream *st = input_file->ic->streams[i];
        if (st->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            set_video_width(env, object, st->codecpar->width);
            set_video_height(env, object, st->codecpar->height);
            set_video_rotation(env, object, get_rotation(st));
            break;
        }
    }
    (*env)->ReleaseStringUTFChars(env, input_path, input_data_source);
    (*env)->DeleteLocalRef(env, object);
    return ret;
}

static jint Android_JNI_compress(JNIEnv *env, jobject object, jstring output_path, jint new_width, jint new_height) {
    int ret = 0;
    const char *output_data_source = (*env)->GetStringUTFChars(env, output_path, 0);
    mediaSource.video_avfilter = av_strdup("null");
    mediaSource.audio_avfilter = av_strdup("anull");
    ret = open_output_file(output_data_source, &mediaSource, new_width, new_height);
    if (ret < 0) {
        release_media_source(&mediaSource);
        release();
        (*env)->ReleaseStringUTFChars(env, output_path, output_data_source);
        (*env)->DeleteLocalRef(env, object);
        return ret;
    }
    ret = transcode();
    if (ret < 0) {
        release();
    }
//    release_media_source(&mediaSource);
    (*env)->ReleaseStringUTFChars(env, output_path, output_data_source);
    (*env)->DeleteLocalRef(env, object);
    return ret;
}

static jint Android_JNI_crop(JNIEnv *env, jobject object, jstring output_path, jint x, jint y, jint width, jint height) {
    int ret = 0;
    const char *output_data_source = (*env)->GetStringUTFChars(env, output_path, 0);
    char crop_avfilter[128];
    snprintf(crop_avfilter, sizeof(crop_avfilter), "crop=%d:%d:%d:%d", width, height, x, y);
    mediaSource.video_avfilter = av_strdup(crop_avfilter);
    mediaSource.audio_avfilter = av_strdup("anull");
    ret = open_output_file(output_data_source, &mediaSource, 0, 0);
    if (ret < 0) {
        release_media_source(&mediaSource);
        release();
        (*env)->ReleaseStringUTFChars(env, output_path, output_data_source);
        (*env)->DeleteLocalRef(env, object);
        return ret;
    }
    ret = transcode();
//    release_media_source(&mediaSource);
    (*env)->ReleaseStringUTFChars(env, output_path, output_data_source);
    (*env)->DeleteLocalRef(env, object);
    return ret;
}

/* Called before SDL_main() to initialize JNI bindings in SDL library */
extern void SDL_Android_Init(JNIEnv* env, jobject cls);

static jint Android_JNI_player(JNIEnv *env, jobject object, jstring input_path) {
    int ret = 0;

    SDL_Android_Init(env, object);
    SDL_SetMainReady();
    const char *input_data_source = (*env)->GetStringUTFChars(env, input_path, 0);
    ret = init_ffplay(input_data_source);
    if (ret < 0) {
        release();
    }
    (*env)->ReleaseStringUTFChars(env, input_path, input_data_source);
    return ret;
}

static void release_ffmpeg(JNIEnv *env, jobject object) {
    release();
    av_freep(&(mediaSource.input_data_source));
    av_freep(&(mediaSource.output_data_source));
    av_freep(&mediaSource);
    do_exit();
    Android_JNI_Release();
    (*env)->DeleteLocalRef(env, object);
}

void log_callback(void *ptr, int level, const char *fmt, va_list vl) {
    FILE *fp = fopen("/sdcard/av_log.txt", "a+");
    if (fp) {
        vfprintf(fp, fmt, vl);
        fflush(fp);
        fclose(fp);
    }
}

void Android_JNI_nativeResize(JNIEnv *env, jobject object, jint width, jint height, jint format, jfloat rate) {
    Android_JNI_onNativeResize(width, height, format, rate);
}

void rtmp_log_callback(int logLevel, const char* msg, va_list args) {
    char log[1024];
    vsprintf(log, msg, args);
    LOGE("%s", log);
}

static JNINativeMethod g_methods[] = {
        {"openInput", "(Ljava/lang/String;)I", Android_JNI_open_input},
        {"compress", "(Ljava/lang/String;II)I", Android_JNI_compress},
        {"crop", "(Ljava/lang/String;IIII)I", Android_JNI_crop},
        {"player", "(Ljava/lang/String;)I", Android_JNI_player},
        {"onNativePause", "()V", Android_JNI_onNativePause},
        {"onNativeResume", "()V", Android_JNI_onNativeResume},
        {"onNativeSurfaceChanged", "()V", Android_JNI_onNativeSurfaceChanged},
        {"onNativeSurfaceDestroyed", "()V", Android_JNI_onNativeSurfaceDestroyed},
        {"onNativeResize", "(IIIF)V", Android_JNI_nativeResize},
        {"release", "()V", release_ffmpeg},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    av_register_all();
    avfilter_register_all();
    avformat_network_init();
    av_log_set_level(AV_LOG_ERROR);
    av_log_set_callback(log_callback);
    jclass clazz = (*env)->FindClass(env, CLASS_NAME);
    (*env)->RegisterNatives(env, clazz, g_methods, NELEM(g_methods));
    SDL_JNI_Init(vm);

    if (DEBUG) {
        remove("/sdcard/av_log.txt");
        char *info = malloc(1000);
        memset(info, 0, 1000);
        AVCodec *temp = av_codec_next(NULL);
        while (temp != NULL) {
            if (temp->decode != NULL) {
                strcat(info, "[Decode]");
            } else {
                strcat(info, "[Encode]");
            }
            switch (temp->type) {
                case AVMEDIA_TYPE_VIDEO:
                    strcat(info, "[Video]");
                    break;
                case AVMEDIA_TYPE_AUDIO:
                    strcat(info, "[Audio]");
                    break;
                default:
                    break;
            }
            sprintf(info, "%s %10s\n", info, temp->name);
            av_log(NULL, AV_LOG_ERROR, "%s", info);
            temp = temp->next;
        }
        free(info);
    }
//    return (*env)->GetVersion(env);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    avformat_network_deinit();
}

#ifdef __cplusplus
}
#endif
#endif
