//
// Created by wlanjie on 16/5/3.
//

#ifndef FFMPEG_OPENFILE_H
#define FFMPEG_OPENFILE_H
#include "ffmpeg.h"
#include "log.h"
#include "filter.h"

int open_input_file(const char *input_data_source);
int open_output_file(const char *output_data_source, MediaSource *mediaSource, int new_width, int new_height);
void release();
#endif //FFMPEG_OPENFILE_H
