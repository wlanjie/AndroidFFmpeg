//
// Created by wlanjie on 16/4/26.
//

#ifndef STREAMING_LOG_H
#define STREAMING_LOG_H

#include <android/log.h>

#define TAG "streaming"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG,  __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG ,__VA_ARGS__)

#endif //STREAMING_LOG_H
