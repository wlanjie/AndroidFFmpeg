//
// Created by wlanjie on 16/5/5.
//

#ifndef FFMPEG_UTILS_H
#define FFMPEG_UTILS_H

#include <jni.h>
#include <unistd.h>
#include <stdio.h>
#import "ffmpeg.h"

MediaSource *get_media_source(JNIEnv *env, jobject instance);
int check_file_exist(JNIEnv *env, MediaSource *mediaSource);

#endif //FFMPEG_UTILS_H
