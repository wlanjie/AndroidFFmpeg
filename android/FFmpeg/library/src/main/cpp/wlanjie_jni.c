//
// Created by wlanjie on 16/5/3.
//


#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include "SDL_main.h"
#include <jni.h>
#include <librtmp/log.h>
#include "utils.h"
#include "openfile.h"
#include "SDL_android.h"
#include "ffplay.h"
#include "recorder.h"
#include "librtmp.h"
#include "color_convert.h"
#include "x264_encoder.h"

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

void Android_JNI_initRecorder(JNIEnv *env, jobject object, jstring path) {
    const char *url = (*env)->GetStringUTFChars(env, path, 0);
    int ret = recorder_init(url);
    if (ret < 0) {

    }
    (*env)->ReleaseStringUTFChars(env, path, url);
}

void Android_JNI_onRecordSamples(JNIEnv *env, jobject object, jshortArray buffer) {
    jshort *in = (*env)->GetShortArrayElements(env, buffer, JNI_FALSE);
    record_samples((const uint8_t **) &in);
}

void rtmp_log_callback(int logLevel, const char* msg, va_list args) {
    char log[1024];
    vsprintf(log, msg, args);
    LOGE("%s", log);
}

jlong Android_JNI_rtmp_open(JNIEnv *env, jclass clazz, jstring _url, jboolean isPublishMode) {
    if (DEBUG) {
        remove("/sdcard/rtmp_log.txt");
        RTMP_LogSetLevel(RTMP_LOGALL);
//        RTMP_LogSetCallback(rtmp_log_callback);
        FILE *fp = fopen("/sdcard/rtmp_log.txt", "a+");
        RTMP_LogSetOutput(fp);
    }

    const char *url = (*env)->GetStringUTFChars(env, _url, 0);
    RTMP *rtmp = rtmp_open(url, isPublishMode);
    (*env)->ReleaseStringUTFChars(env, _url, url);
    return rtmp;
}

int Android_JNI_rtmp_read(JNIEnv *env, jobject object, jlong pointer, jbyteArray data, int offset, int size) {

}

int Android_JNI_rtmp_write(JNIEnv *env, jobject object, jlong pointer, jbyteArray data, int size, int type, int ts) {
    jbyte *buffer = (*env)->GetByteArrayElements(env, data, 0);
    int res = rtmp_write(pointer, buffer, size, type, ts);
    (*env)->ReleaseByteArrayElements(env, data, buffer, 0);
    return res;
}

int Android_JNI_rtmp_close(JNIEnv *env, jobject object, jlong pointer) {
    return rtmp_close(pointer);
}

jstring Android_JNI_rtmp_getIpAddress(JNIEnv *env, jobject object, jlong pointer) {
    return NULL;
}

void Android_JNI_color_convert_NV21ToYUV420SP(JNIEnv *env, jobject object, jbyteArray srcArray, jbyteArray dstArray, jint ySize) {
    unsigned char *src = (unsigned char *)(*env)->GetByteArrayElements(env, srcArray, 0);
    unsigned char *dst = (unsigned char *)(*env)->GetByteArrayElements(env, dstArray, 0);
    NV21ToYUV420SP(src , dst, ySize);
    (*env)->ReleaseByteArrayElements(env, srcArray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstArray, dst, JNI_ABORT);
}

void Android_JNI_color_convert_NV21ToYUV420P(JNIEnv *env, jobject object, jbyteArray srcArray, jbyteArray dstArray, jint ySize) {
    unsigned char *src = (unsigned char *)(*env)->GetByteArrayElements(env, srcArray, 0);
    unsigned char *dst = (unsigned char *)(*env)->GetByteArrayElements(env, dstArray, 0);
    NV21ToYUV420P(src, dst, ySize);
    (*env)->ReleaseByteArrayElements(env, srcArray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstArray, dst, JNI_ABORT);
}

void Android_JNI_color_convert_YUV420SPToYUV420P(JNIEnv *env, jobject object, jbyteArray srcArray, jbyteArray dstArray, jint ySize) {
    unsigned char *src = (unsigned char *)(*env)->GetByteArrayElements(env, srcArray, 0);
    unsigned char *dst = (unsigned char *)(*env)->GetByteArrayElements(env, dstArray, 0);
    YUV420SPToYUV420P(src, dst, ySize);
    (*env)->ReleaseByteArrayElements(env, srcArray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstArray, dst, JNI_ABORT);
}

void Android_JNI_color_convert_NV21ToARGB(JNIEnv *env, jobject object, jbyteArray srcArray, jintArray dstArray, jint width, jint height) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcArray, 0);
    unsigned int *dst = (unsigned int *) (*env)->GetIntArrayElements(env, dstArray, 0);
    NV21ToARGB(src, dst, width, height);
    (*env)->ReleaseByteArrayElements(env, srcArray, src, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, dstArray, dst, JNI_ABORT);
}

void Android_JNI_color_convert_FIXGLPIXEL(JNIEnv *env, jobject object, jintArray srcArray, jintArray dstArray, jint width, jint height) {
    unsigned int *src = (unsigned int *) (*env)->GetIntArrayElements(env, srcArray, 0);
    unsigned int *dst = (unsigned int *) (*env)->GetIntArrayElements(env, dstArray, 0);
    FIXGLPIXEL(src, dst, width, height);
    (*env)->ReleaseIntArrayElements(env, srcArray, src, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, dstArray, dst, JNI_ABORT);
}

void Android_JNI_color_convert_NV21Transform(JNIEnv *env, jobject object, jbyteArray srcArray, jbyteArray dstArray, jint width, jint height, jint dirctionFlag) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcArray, 0);
    unsigned char *dst = (unsigned char *) (*env)->GetByteArrayElements(env, dstArray, 0);
    NV21Transform(src, dst, width, height, dirctionFlag);
    (*env)->ReleaseByteArrayElements(env, srcArray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstArray, dst, JNI_ABORT);
}

#define COLOR_FORMAT_NV21 17

void Android_JNI_native_render(JNIEnv *env, jobject object, jobject surfaceObject, jbyteArray pixelsArray, jint width, jint height, jint size) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surfaceObject);
    if (window != NULL) {
        ANativeWindow_setBuffersGeometry(window, width, height, COLOR_FORMAT_NV21 );
        ANativeWindow_Buffer buffer;
        if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
            unsigned char *pixels = (*env)->GetByteArrayElements(env, pixelsArray, 0);
            if (buffer.width == buffer.stride) {
                memcpy(buffer.bits, pixels, size);
            } else {
                int h = height * 3 / 2;
                int w = width;
                for (int i = 0; i < h; ++i) {
                    memcpy(buffer.bits + buffer.stride * i, pixels + w * i, w);
                }
            }
            (*env)->ReleaseByteArrayElements(env, pixelsArray, pixels, JNI_ABORT);
            ANativeWindow_unlockAndPost(window);
        }
        ANativeWindow_release(window);
    }
}

jint Android_JNI_x264_encoder_init(JNIEnv *env, jobject object, jint width, jint height) {
    return x264_encoder_init(width, height);
}

jint Android_JNI_x264_encoder_start(JNIEnv *env, jobject object, int type, jbyteArray inputArray, jbyteArray outputArray) {
    unsigned char *input = (unsigned char *) (*env)->GetByteArrayElements(env, inputArray, 0);
    unsigned char *output = (unsigned char *) (*env)->GetByteArrayElements(env, outputArray, 0);
    int res = x264_encoder_start(type, input, output, 0);
    (*env)->ReleaseByteArrayElements(env, inputArray, input, 0);
    (*env)->ReleaseByteArrayElements(env, outputArray, output, 0);
    return res;
}

void Android_JNI_x264_encoder_close(JNIEnv *env, jobject object) {
    x264_encoder_finish();
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
        {"initRecorder", "(Ljava/lang/String;)V", Android_JNI_initRecorder},
        {"recordSamples", "([S)I", Android_JNI_onRecordSamples},
        {"release", "()V", release_ffmpeg},
};

static JNINativeMethod rtmp_methods[] = {
        {"open", "(Ljava/lang/String;Z)J", Android_JNI_rtmp_open},
        {"read", "(J[BII)I", Android_JNI_rtmp_read},
        {"write", "(J[BIII)I", Android_JNI_rtmp_write},
        {"close", "(J)I", Android_JNI_rtmp_close},
        {"getIpAddress", "(J)Ljava/lang/String;", Android_JNI_rtmp_getIpAddress},
};

static JNINativeMethod color_convert_methods[] = {
        {"NV21ToYUV420SP", "([B[BI)V", Android_JNI_color_convert_NV21ToYUV420SP},
        {"NV21ToYUV420P", "([B[BI)V", Android_JNI_color_convert_NV21ToYUV420P},
        {"YUV420SPToYUV420P", "([B[BI)V", Android_JNI_color_convert_YUV420SPToYUV420P},
        {"NV21ToARGB", "([B[III)V", Android_JNI_color_convert_NV21ToARGB},
        {"FIXGLPIXEL", "([I[III)V", Android_JNI_color_convert_FIXGLPIXEL},
        {"NV21Transform", "([B[BIII)V", Android_JNI_color_convert_NV21Transform}
};

static JNINativeMethod native_render_methods[] = {
        {"renderingSurface", "(Landroid/view/Surface;[BIII)V", Android_JNI_native_render}
};

static JNINativeMethod encoder_method[] = {
        {"x264EncoderInit", "(II)I", Android_JNI_x264_encoder_init},
        {"x264EncoderClose", "()V", Android_JNI_x264_encoder_close},
        {"x264EncoderStart", "(I[B[B)I", Android_JNI_x264_encoder_start}
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
    jclass rtmp_clazz = (*env)->FindClass(env, "com/wlanjie/ffmpeg/library/RtmpClient");
    (*env)->RegisterNatives(env, rtmp_clazz, rtmp_methods, NELEM(rtmp_methods));
    jclass color_convert_clazz = (*env)->FindClass(env, "com/wlanjie/ffmpeg/library/ColorConvert");
    (*env)->RegisterNatives(env, color_convert_clazz, color_convert_methods, NELEM(color_convert_methods));
    jclass render_class = (*env)->FindClass(env, "com/wlanjie/ffmpeg/library/NativeRender");
    (*env)->RegisterNatives(env, render_class, native_render_methods, NELEM(native_render_methods));
    jclass encoder = (*env)->FindClass(env, "com/wlanjie/ffmpeg/library/Encoder");
    (*env)->RegisterNatives(env, encoder, encoder_method, NELEM(encoder_method));
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
