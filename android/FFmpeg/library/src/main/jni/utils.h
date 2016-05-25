//
// Created by wlanjie on 16/5/5.
//

#ifndef FFMPEG_UTILS_H
#define FFMPEG_UTILS_H

#include <jni.h>
#include <unistd.h>
#include <stdio.h>
#import "ffmpeg.h"

typedef struct MediaSourceJNI {
    jclass ffmpeg_class;
    jclass illegal_argument_class;
    jobject media_source_object;
    jclass media_source_class;
} MediaSourceJNI;

int check_file_exist(JNIEnv *env, const char *input_data_source);
void set_video_width(JNIEnv *env, jobject object, int width);
void set_video_height(JNIEnv *env, jobject object, int height);
void set_video_rotation(JNIEnv *env, jobject object, double rotation);
#endif //FFMPEG_UTILS_H
