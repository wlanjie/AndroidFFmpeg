//
// Created by wlanjie on 16/9/11.
//

#ifndef FFMPEG_LIBRTMP_H
#define FFMPEG_LIBRTMP_H

#include "librtmp/rtmp.h"
#include <stddef.h>
#include <malloc.h>

RTMP* rtmp_open(char *url, int isPublishMode);
int rtmp_read(long rtmp, int size);
int rtmp_write(long rtmp, signed char *buffer, int size, int type, int ts);
int rtmp_close(long rtmp);
char *get_ip_address(long rtmp);

#endif //FFMPEG_LIBRTMP_H
