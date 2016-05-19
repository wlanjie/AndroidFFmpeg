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

MediaSource *get_media_source(JNIEnv *env, jobject object) {
    jclass clazz = (*env)->GetObjectClass(env, object);
    jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    jfieldID media_source_ID = (*env)->GetFieldID(env, clazz, "mediaSource", "Lcom/wlanjie/ffmpeg/library/MediaSource;");
    if (media_source_ID == NULL) {
        throw_exception(env, illegal_argument_class, "Can't find FFmpeg.mediaSource field");
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    jobject media_source_object = (*env)->GetObjectField(env, object, media_source_ID);
    if (media_source_object == NULL) {
        throw_exception(env, illegal_argument_class, "Can't get FFmpeg.mediaSource Object");
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    jclass media_source_class = (*env)->GetObjectClass(env, media_source_object);
    if (media_source_class == NULL) {
        throw_exception(env, illegal_argument_class, "Can't get MediaSource Object");
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    jmethodID get_input_data_source_ID = (*env)->GetMethodID(env, media_source_class, "getInputDataSource", "()Ljava/lang/String;");
    if (get_input_data_source_ID == NULL) {
        throw_exception(env, illegal_argument_class, "Can't find MediaSource.getInputDataSource() method");
        (*env)->DeleteLocalRef(env, media_source_class);
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    jstring get_input_data_source = (*env)->CallObjectMethod(env, media_source_object, get_input_data_source_ID);
    if (get_input_data_source == NULL) {
        throw_exception(env, illegal_argument_class, "Can't call MediaSource getInputDataSource method value, please call FFmpeg.setInputDataSource() method");
        (*env)->DeleteLocalRef(env, media_source_class);
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    const char *input_data_source = (*env)->GetStringUTFChars(env, get_input_data_source, 0);
    if (input_data_source == NULL) {
        throw_exception(env, illegal_argument_class, "Can't get MediaSource getInputDataSource method value, please call FFmpeg.setInputDataSource() method");
        (*env)->DeleteLocalRef(env, media_source_class);
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    jmethodID get_output_data_source_ID = (*env)->GetMethodID(env, media_source_class, "getOutputDataSource", "()Ljava/lang/String;");
    if (get_output_data_source_ID == NULL) {
        throw_exception(env, illegal_argument_class, "Can't find MediaSource getOutputDataSource method");
        (*env)->DeleteLocalRef(env, media_source_class);
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    jstring get_output_data_source = (*env)->CallObjectMethod(env, media_source_object, get_output_data_source_ID);
    if (get_output_data_source == NULL) {
        throw_exception(env, illegal_argument_class, "Can't get MediaSource getInputDataSource method value, please call FFmpeg.setOutputDataSource() method");
        (*env)->DeleteLocalRef(env, media_source_class);
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
    }
    const char *output_data_source = (*env)->GetStringUTFChars(env, get_output_data_source, 0);
    if (output_data_source == NULL) {
        throw_exception(env, illegal_argument_class, "Can't get MediaSource getInputDataSource method value, please call FFmpeg.setOutputDataSource() method");
        (*env)->DeleteLocalRef(env, media_source_class);
        (*env)->DeleteLocalRef(env, media_source_object);
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        (*env)->DeleteLocalRef(env, clazz);
        return NULL;
    }
    MediaSource *mediaSource = av_mallocz(sizeof(*mediaSource));
    mediaSource->input_data_source = av_strdup(input_data_source);
    mediaSource->output_data_source = av_strdup(output_data_source);
    (*env)->ReleaseStringUTFChars(env, get_input_data_source, input_data_source);
    (*env)->DeleteLocalRef(env, media_source_class);
    (*env)->DeleteLocalRef(env, media_source_object);
    (*env)->DeleteLocalRef(env, illegal_argument_class);
    (*env)->DeleteLocalRef(env, clazz);
    return mediaSource;
}

int check_file_exist(JNIEnv *env, MediaSource *mediaSource) {
    if (strlen(mediaSource->input_data_source) == 0) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, illegal_argument_class, "must be before call FFmpeg.SetInputDataSource() method");
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        return -1;
    }
    if (strlen(mediaSource->output_data_source) == 0) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        jclass illegal_argument_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, illegal_argument_class, "must be before call FFmpeg.SetOutputDataSource() method");
        (*env)->DeleteLocalRef(env, illegal_argument_class);
        return -1;
    }
    if (access(mediaSource->input_data_source, 4) == -1) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        jclass file_not_found_class = (*env)->FindClass(env, "java/io/FileNotFoundException");
        char error[255];
        snprintf(error, sizeof(error), "%s not found\n", mediaSource->input_data_source);
        (*env)->ThrowNew(env, file_not_found_class, error);
        (*env)->DeleteLocalRef(env, file_not_found_class);
        return -1;
    }
    return 0;
}