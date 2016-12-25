//
// Created by wlanjie on 2016/12/22.
//

#include <cstdlib>

#include "VideoEncode.h"

VideoEncode::VideoEncode() {
    global_nal_header = false;
}

VideoEncode::~VideoEncode() {

}

int VideoEncode::rgba_encode_to_h264(char *frame, int width, int height, bool need_flip,
                                     int rotate_degree, int pts) {
    YuvFrame *yuv = convert.rgba_convert_i420(frame, width, height, need_flip, rotate_degree);
    if (yuv == NULL) {
        return -1;
    }
    int es_len = global_nal_header ? encode_global_nal_header(yuv) : x264_encode(yuv);
    return es_len;
}

int VideoEncode::open_h264_encode() {
    // Presetting
    x264_param_default_preset(&param, "superfast", "zerolatency");

    // Resolution
    param.i_width = width;
    param.i_height = height;

    // Default setting i_rc_method as X264_RC_CRF which is better than X264_RC_ABR
    param.rc.i_bitrate = bitrate;  // kbps
    param.rc.i_rc_method = X264_RC_ABR; //参数i_rc_method表示码率控制，CQP(恒定质量)，CRF(恒定码率)，ABR(平均码率)

//    以下是2秒刷新一个I帧
    // fps
    param.i_fps_num = fps;
    param.i_fps_den = 1;
    param.i_keyint_max = fps * 2;

    // 允许部分B帧为参考帧
//    param.i_bframe_pyramid = 0;

    param.rc.b_mb_tree = 0;//这个不为0,将导致编码延时帧...在实时编码时,必须为0

//    该参数设置是让每个I帧都附带sps/pps。
//    param.b_repeat_headers = 1;  // 重复SPS/PPS 放到关键帧前面

    param.b_repeat_headers = 0;
    global_nal_header = true;
    // 如果为false，则一个slice只编码成一个NALU;
    // 否则有几个线程，在编码成几个NALU。缺省为true。
    param.b_sliced_threads = false;

    // gop
    param.i_keyint_max = gop;

    if (x264_param_apply_profile(&param, "baseline" ) < 0) {
        return -1;
    }

    encoder = x264_encoder_open(&param);
    if (encoder == NULL) {
        return -1;
    }
    return 0;
}

void VideoEncode::close_h264_encode() {
    int nnal;
    x264_nal_t *nal;
    x264_picture_t pic_out;

    if (encoder != NULL) {
        while(x264_encoder_delayed_frames(encoder)) {
            x264_encoder_encode(encoder, &nal, &nnal, NULL, &pic_out);
        }
        x264_encoder_close(encoder);
        encoder = NULL;
    }
}

int VideoEncode::encode_global_nal_header(YuvFrame *yuv) {
    x264_nal_t *nal;
    int nal_size;
    global_nal_header = false;

    x264_encoder_headers(encoder, &nal, &nal_size);
    return encode_nal(nal, nal_size);
}

int VideoEncode::x264_encode(YuvFrame *yuv) {
    int nal_size;
    x264_nal_t *nal;
    x264_picture_t pic_out;

    picture.img.i_csp = X264_CSP_I420;
    picture.img.i_plane = 3;
    picture.img.plane[0] = yuv->y;
    picture.img.i_stride[0] = yuv->width;
    picture.img.plane[1] = yuv->u;
    picture.img.i_stride[1] = yuv->width / 2;
    picture.img.plane[2] = yuv->v;
    picture.img.i_stride[2] = yuv->width / 2;
    picture.i_pts = pts;
    picture.i_type = X264_TYPE_AUTO;

    if (x264_encoder_encode(encoder, &nal, &nal_size, &picture, &pic_out) < 0) {
        return -1;
    }

    pts = pic_out.i_pts;
    dts = pic_out.i_dts;
    is_key_frame = pic_out.i_type == X264_TYPE_IDR;

    return encode_nal(nal, nal_size);
}

int VideoEncode::encode_nal(const x264_nal_t *nal, int nal_size) {
    uint8_t *p = h264_es;

    /* Write the SEI as part of the first frame. */
//    if (x264_ctx.sei_size > 0 && nnal > 0) {
//        memcpy(p, x264_ctx.sei, x264_ctx.sei_size);
//        p += x264_ctx.sei_size;
//        x264_ctx.sei_size = 0;
//    }

    for (int i = 0; i < nal_size; i++) {
        if (nal[i].i_type != NAL_SEI) {
            memcpy(p, nal[i].p_payload, nal[i].i_payload);
            p += nal[i].i_payload;
        }
    }

    return (int) (p - h264_es);
}

uint8_t *VideoEncode::get_h264() {
    return h264_es;
}

void VideoEncode::setEncodeBitrate(int bitrate) {
    this->bitrate = bitrate / 1000;
}

void VideoEncode::setEncodeFps(int fps) {
    this->fps = fps;
}

void VideoEncode::setEncodeGop(int gop_size) {
    this->gop = gop_size;
}

void VideoEncode::setEncodePreset(const char *preset) {
    strcpy(this->preset, preset);
}

void VideoEncode::setEncodeResolution(int width, int height) {
    convert.setEncodeResolution(width, height);
    this->width = width;
    this->height = height;
}