//
// Created by wlanjie on 16/9/16.
//

#ifndef FFMPEG_X264_ENCODER_H
#define FFMPEG_X264_ENCODER_H

int x264_encoder_init(int width, int height);
int x264_encoder_start(int type, unsigned char *input, unsigned char *output, int size);
void x264_encoder_finish();

#endif //FFMPEG_X264_ENCODER_H
