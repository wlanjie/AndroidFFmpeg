//
// Created by wlanjie on 16/5/3.
//

#ifndef FFMPEG_OPENFILE_H
#define FFMPEG_OPENFILE_H
#include "ffmpeg.h"
#include "log.h"
#include "filter.h"

int open_input_file(MediaSource *mediaSource);
int open_output_file(MediaSource *mediaSource, int new_width, int new_height);
void release();
#endif //FFMPEG_OPENFILE_H
