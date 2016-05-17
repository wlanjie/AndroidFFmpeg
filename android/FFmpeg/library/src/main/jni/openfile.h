//
// Created by wlanjie on 16/5/3.
//

#ifndef FFMPEG_OPENFILE_H
#define FFMPEG_OPENFILE_H

int open_input_file(char *input_data_source);
int open_output_file(char *output_data_source, int new_width, int new_height);
void release();
#endif //FFMPEG_OPENFILE_H
