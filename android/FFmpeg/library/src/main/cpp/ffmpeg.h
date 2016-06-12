//
// Created by wlanjie on 16/5/3.
//

#ifndef FFMPEG_FFMPEG_H
#define FFMPEG_FFMPEG_H

#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libavutil/parseutils.h"
#include "libavfilter/avfilter.h"
#include "libavutil/bprint.h"
#include "libavutil/pixdesc.h"
#include "libavutil/eval.h"
#include "libavutil/display.h"
#include "libavfilter/buffersrc.h"
#include "libavfilter/buffersink.h"

typedef struct InputFile {
    AVFormatContext *ic;
} InputFile;

typedef struct InputStream {
    AVStream *st;
    AVCodecContext *dec_ctx;
    struct AVCodec *dec;
    AVFilterContext *filter;
} InputStream;

typedef struct OutputFile {
    AVFormatContext *oc;
} OutputFile;

typedef struct OutputStream {
    AVStream *st;
    AVCodecContext *enc_ctx;
    struct AVCodec *enc;
    AVFilterContext *filter;
    char *avfilter;
    int new_width;
    int new_height;
} OutputStream;

typedef struct FilterGraph {
    InputStream *ist;
    OutputStream *ost;
    AVFilterGraph *graph;
} FilterGraph;

typedef struct MediaSource {
    const char *input_data_source;
    const char *output_data_source;
    char *video_avfilter;
    char *audio_avfilter;
} MediaSource;

extern InputFile *input_file;
extern InputStream **input_streams;
extern int nb_input_streams;
extern OutputFile *output_file;
extern OutputStream **output_streams;
extern int nb_output_streams;

double get_rotation(AVStream *st);
void *grow_array(void *array, int elem_size, int *size, int new_size);

#define GROW_ARRAY(array, nb_elems) \
    array = grow_array(array, sizeof(*array), &nb_elems, nb_elems + 1);

int transcode();
#endif //FFMPEG_FFMPEG_H
