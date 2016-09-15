//
// Created by wlanjie on 16/8/22.
//

#ifndef FFMPEG_RECORDER_H
#define FFMPEG_RECORDER_H

#include "libswresample/swresample.h"
#include "ffmpeg.h"

typedef struct Recorder {
    AVFormatContext *oc;
    AVCodec *video_codec;
    AVStream *video_st;
    AVCodecContext *avctx;
    AVCodec *audio_codec;
    AVStream *audio_st;
    int video_outbuf_size;
    uint8_t *video_outbuf;
    int audio_outbuf_size;
    uint8_t *audio_outbuf;
    SwrContext *swr_context;
    AVCodecContext *aactx;
    AVFrame *video_frame;
    AVFrame *audio_frame;
} Recorder;

int recorder_init(const char *url);
int record_samples(const uint8_t **in);

static Recorder *recorder;
#endif //FFMPEG_RECORDER_H
