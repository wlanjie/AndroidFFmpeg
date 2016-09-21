//
// Created by wlanjie on 16/9/16.
//

#include "x264_encoder.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "x264.h"

typedef struct {
    x264_param_t *param;
    x264_t *handle;
    x264_picture_t *picture;
    x264_nal_t *nal;
} x264_encoder;

x264_encoder *encoder = NULL;
FILE *out_file = NULL;

int x264_encoder_init(int width, int height) {
    encoder = malloc(sizeof(x264_encoder));
    encoder->param = malloc(sizeof(x264_param_t));
    encoder->picture = malloc(sizeof(x264_picture_t));
    x264_param_default(encoder->param);
    x264_param_apply_profile(encoder->param, "baseline");

    //初始化x264_param_t
    //log
    encoder->param->i_log_level = X264_LOG_DEBUG;
    //set frame width
    encoder->param->i_width = width;
    //set frame height
    encoder->param->i_height = height;
    encoder->param->rc.i_lookahead = 0;
    //帧率
    encoder->param->i_fps_num = 20;
    encoder->param->i_fps_den = 1;
    //bps 单位k
//    encoder->param->rc.i_bitrate = 1000;
    //设置输入的视频采样格式
//    encoder->param->i_csp = X264_CSP_I420;
//    encoder->param->cpu = X264_cpu_detect();
//    encoder->param->i_threads = X264_THREADS_AUTO;
    if ((encoder->handle = x264_encoder_open(encoder->param)) == 0) {
        return -1;
    }
    //alloc a new picture
    x264_picture_alloc(encoder->picture, X264_CSP_I420, encoder->param->i_width, encoder->param->i_height);
    out_file = fopen("/sdcard/wlanjie_h264_c.h264", "wb");
    return 0;
}

int x264_encoder_start(int type, unsigned char *input, unsigned char *output, int size) {
    if (!encoder) {
        return -1;
    }
    int picSize = encoder->param->i_width * encoder->param->i_height;
    memcpy(encoder->picture->img.plane[0], input, picSize);
    memcpy(encoder->picture->img.plane[1], input + picSize, picSize >> 2);
    memcpy(encoder->picture->img.plane[2], input + picSize + (picSize >> 2), picSize >> 2);
    switch (type) {
        case 0:
            encoder->picture->i_type = X264_TYPE_P;
            break;
        case 1:
            encoder->picture->i_type = X264_TYPE_IDR;
            break;
        case 2:
            encoder->picture->i_type = X264_TYPE_I;
            break;
        default:
            encoder->picture->i_type = X264_TYPE_AUTO;
            break;
    }
    int pi_nal = -1;
    x264_picture_t pic_out;
    int i_frame_size;
    if ((i_frame_size = x264_encoder_encode(encoder->handle, &encoder->nal, &pi_nal, encoder->picture, &pic_out)) < 0) {
        return -2;
    }
    if (i_frame_size > 0) {
        memcpy(output, encoder->nal->p_payload, i_frame_size);
        if (out_file) {
            fwrite(encoder->nal->p_payload, i_frame_size, 1, out_file);
            fflush(out_file);
        }
    }
    return i_frame_size;
}

void x264_encoder_finish() {
    if (!encoder) {
        return;
    }
    if (encoder->picture) {
        x264_picture_clean(encoder->picture);
        free(encoder->picture);
        encoder->picture = 0;
    }
    if (encoder->param) {
        free(encoder->param);
        encoder->param = 0;
    }
    if (encoder->handle) {
        x264_encoder_close(encoder->handle);
    }
    free(encoder);
}