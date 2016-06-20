//
// Created by wlanjie on 16/5/5.
//

#include "utils.h"

void throw_exception(JNIEnv *env, jclass *exception_class, char *message) {
    if (exception_class == NULL || message == NULL) return;
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    (*env)->ThrowNew(env, exception_class, message);
}

void release_media_source_jni(JNIEnv *env, MediaSourceJNI *mediaSourceJNI, char *error_message) {
    if (error_message) {
        if ((*env)->ExceptionCheck) {
            (*env)->ExceptionClear(env);
        }
        (*env)->ThrowNew(env, mediaSourceJNI->illegal_argument_class, error_message);
    }
    if (mediaSourceJNI->ffmpeg_class) {
        (*env)->DeleteLocalRef(env, mediaSourceJNI->ffmpeg_class);
    }
    if (mediaSourceJNI->illegal_argument_class) {
        (*env)->DeleteLocalRef(env, mediaSourceJNI->illegal_argument_class);
    }
    if (mediaSourceJNI->media_source_object) {
        (*env)->DeleteLocalRef(env, mediaSourceJNI->media_source_object);
    }
    if (mediaSourceJNI->media_source_class) {
        (*env)->DeleteLocalRef(env, mediaSourceJNI->media_source_class);
    }
    av_freep(&mediaSourceJNI);
}

MediaSourceJNI *get_media_source_jni(JNIEnv *env, jobject object) {
    MediaSourceJNI *mediaSourceJNI = av_mallocz(sizeof(MediaSourceJNI));
    mediaSourceJNI->ffmpeg_class = (*env)->GetObjectClass(env, object);
    mediaSourceJNI->illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    jfieldID media_source_field_ID = (*env)->GetFieldID(env, mediaSourceJNI->ffmpeg_class, "mediaSource", "Lcom/wlanjie/ffmpeg/library/MediaSource;");
    if (media_source_field_ID == NULL) {
        release_media_source_jni(env, mediaSourceJNI, "Can't find FFmpeg.mediaSource field");
        return NULL;
    }
    mediaSourceJNI->media_source_object = (*env)->GetObjectField(env, object, media_source_field_ID);
    if (mediaSourceJNI->media_source_object == NULL) {
        release_media_source_jni(env, mediaSourceJNI, "Can't get FFmpeg.mediaSource Object");
        return NULL;
    }
    mediaSourceJNI->media_source_class = (*env)->GetObjectClass(env, mediaSourceJNI->media_source_object);
    if (mediaSourceJNI->media_source_class == NULL) {
        release_media_source_jni(env, mediaSourceJNI, "Can't get MediaSource Object");
        return NULL;
    }
    return mediaSourceJNI;
}

void set_video_width(JNIEnv *env, jobject object, int width) {
    MediaSourceJNI *mediaSourceJNI = get_media_source_jni(env, object);
    jmethodID set_width_ID = (*env)->GetMethodID(env, mediaSourceJNI->media_source_class, "setWidth", "(I)V");
    (*env)->CallVoidMethod(env, mediaSourceJNI->media_source_object, set_width_ID, width);
    release_media_source_jni(env, mediaSourceJNI, NULL);
}

void set_video_height(JNIEnv *env, jobject object, int height) {
    MediaSourceJNI *mediaSourceJNI = get_media_source_jni(env, object);
    jmethodID set_width_ID = (*env)->GetMethodID(env, mediaSourceJNI->media_source_class, "setHeight", "(I)V");
    (*env)->CallVoidMethod(env, mediaSourceJNI->media_source_object, set_width_ID, height);
    release_media_source_jni(env, mediaSourceJNI, NULL);
}

void set_video_rotation(JNIEnv *env, jobject object, double rotation) {
    MediaSourceJNI *mediaSourceJNI = get_media_source_jni(env, object);
    jmethodID set_width_ID = (*env)->GetMethodID(env, mediaSourceJNI->media_source_class, "setRotation", "(D)V");
    (*env)->CallVoidMethod(env, mediaSourceJNI->media_source_object, set_width_ID, rotation);
    release_media_source_jni(env, mediaSourceJNI, NULL);
}

void set_video_duration(JNIEnv *env, jobject object, float duration) {

}

int check_file_exist(JNIEnv *env, const char *input_data_source) {
    if (input_data_source == NULL)
        return -1;
    if (strlen(input_data_source) == 0) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, illegal_argument_class, "must be before call FFmpeg.SetInputDataSource() method");
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        return -1;
    }
//    if (strlen(output_data_source) == 0) {
//        if ((*env)->ExceptionCheck(env)) {
//            (*env)->ExceptionClear(env);
//        }
//        jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
//        (*env)->ThrowNew(env, illegal_argument_class, "must be before call FFmpeg.SetOutputDataSource() method");
//        (*env)->DeleteLocalRef(env, illegal_argument_class);
//        return -1;
//    }
    if (access(input_data_source, 4) == -1) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        jclass file_not_found_class = (*env)->FindClass(env, "java/io/FileNotFoundException");
        char error[255];
        snprintf(error, sizeof(error), "%s not found\n", input_data_source);
        (*env)->ThrowNew(env, file_not_found_class, error);
        (*env)->DeleteLocalRef(env, file_not_found_class);
        return -1;
    }
    return 0;
}