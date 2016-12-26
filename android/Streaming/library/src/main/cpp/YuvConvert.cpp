//
// Created by wlanjie on 2016/12/22.
//

#include <cstdlib>

#include "YuvConvert.h"

YuvConvert::YuvConvert() {

}

YuvConvert::~YuvConvert() {

}

YuvFrame *YuvConvert::rgba_convert_i420(char *rgba, int width, int height, bool need_flip,
                                   int rotate_degree) {
    return convert_to_i420(rgba, width, height, need_flip, rotate_degree, libyuv::FOURCC_RGBA);
}

YuvFrame *YuvConvert::nv21_convert_i420(char *nv21, int width, int height, bool need_flip,
                                    int rotate_degree) {
    return convert_to_i420(nv21, width, height, need_flip, rotate_degree, libyuv::FOURCC_NV21);
}

YuvFrame *YuvConvert::nv21_convert_nv12(char *nv21, int width, int height, bool need_flip,
                                    int rotate_degree) {
    YuvFrame *frame = convert_to_i420(nv21, width, height, need_flip, rotate_degree, libyuv::FOURCC_NV21);
    if (frame == NULL) {
        return NULL;
    }
    int ret = libyuv::ConvertFromI420(i420_scaled_frame.y, i420_scaled_frame.width,
                                    i420_scaled_frame.u, i420_scaled_frame.width / 2,
                                    i420_scaled_frame.v, i420_scaled_frame.width / 2,
                                    nv12.data, nv12.width,
                                    nv12.width, nv12.height,
                                    libyuv::FOURCC_NV12);
    if (ret < 0) {
        return NULL;
    }
    return &nv12;
}

YuvFrame *YuvConvert::convert_to_i420(char *frame, int width, int height, bool need_flip,
                                 int rotate_degree, libyuv::FourCC format) {
    int y_size = width * height;
    if (rotate_degree % 180 == 0) {
        if (i420_rotated_frame.width != width || i420_rotated_frame.height != height) {
            free(i420_rotated_frame.data);
            i420_rotated_frame.width = width;
            i420_rotated_frame.height = height;
            i420_rotated_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
            i420_rotated_frame.y = i420_rotated_frame.data;
            i420_rotated_frame.u = i420_rotated_frame.y + y_size;
            i420_rotated_frame.v = i420_rotated_frame.u + y_size / 4;
        }
    } else {
        if (i420_rotated_frame.width != height || i420_rotated_frame.height != width) {
            free(i420_rotated_frame.data);
            i420_rotated_frame.width = height;
            i420_rotated_frame.height = width;
            i420_rotated_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
            i420_rotated_frame.y = i420_rotated_frame.data;
            i420_rotated_frame.u = i420_rotated_frame.y + y_size;
            i420_rotated_frame.v = i420_rotated_frame.u + y_size / 4;
        }
    }

    int ret = libyuv::ConvertToI420((uint8_t *) frame, y_size,
                             i420_rotated_frame.y, i420_rotated_frame.width,
                             i420_rotated_frame.u, i420_rotated_frame.width / 2,
                             i420_rotated_frame.v, i420_rotated_frame.width / 2,
                             0, 0,
                             width, height,
                             width, height,
                             (libyuv::RotationMode) rotate_degree, format);
    if (ret < 0) {
        return NULL;
    }

    ret = libyuv::I420Scale(i420_rotated_frame.y, i420_rotated_frame.width,
                    i420_rotated_frame.u, i420_rotated_frame.width / 2,
                    i420_rotated_frame.v, i420_rotated_frame.width / 2,
                    need_flip ? -i420_rotated_frame.width : i420_rotated_frame.width, i420_rotated_frame.height,
                    i420_scaled_frame.y, i420_scaled_frame.width,
                    i420_scaled_frame.u, i420_scaled_frame.width / 2,
                    i420_scaled_frame.v, i420_scaled_frame.width / 2,
                    i420_scaled_frame.width, i420_scaled_frame.height,
                    libyuv::kFilterNone);
    return ret < 0 ? NULL : &i420_scaled_frame;
}

void YuvConvert::setEncodeResolution(int width, int height) {

    this->width = width;
    this->height = height;
    int y_size = width * height;
    if (i420_scaled_frame.width != width || i420_scaled_frame.height != height) {
        free(i420_scaled_frame.data);
        i420_scaled_frame.width = width;
        i420_scaled_frame.height = height;
        i420_scaled_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
        i420_scaled_frame.y = i420_scaled_frame.data;
        i420_scaled_frame.u = i420_scaled_frame.y + y_size;
        i420_scaled_frame.v = i420_scaled_frame.u + y_size / 4;
    }

    if (nv12.width != width || nv12.height != height) {
        free(nv12.data);
        nv12.width = width;
        nv12.height = height;
        nv12.data = (uint8_t *) malloc(y_size * 3 / 2);
        nv12.y = nv12.data;
        nv12.u = nv12.y + y_size;
        nv12.v = nv12.u + y_size / 4;
    }
}