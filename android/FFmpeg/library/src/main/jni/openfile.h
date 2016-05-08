//
// Created by wlanjie on 16/5/3.
//

#ifndef FFMPEG_OPENFILE_H
#define FFMPEG_OPENFILE_H

int open_input_file(const char *input_path);
int open_output_file(const char *output_path, int new_width, int new_height);
void release();
#endif //FFMPEG_OPENFILE_H
