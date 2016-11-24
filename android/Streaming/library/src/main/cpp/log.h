//
// Created by wlanjie on 16/4/26.
//

#ifndef FFMPEG_LOG_H
#define FFMPEG_LOG_H

#include <android/log.h>

#define TAG "ffmpeg"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG,  __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG ,__VA_ARGS__)

#endif //FFMPEG_LOG_H
