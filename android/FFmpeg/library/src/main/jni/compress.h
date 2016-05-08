//
// Created by wlanjie on 16/4/26.
//

#ifndef FFMPEG_COMPRESS_H
#define FFMPEG_COMPRESS_H

int open_files(const char *input_file, const char *output_file, int new_width, int new_height);
int transcode();
#endif //FFMPEG_COMPRESS_H
