//
// Created by wlanjie on 2016/12/22.
//

#ifndef STREAMING_YUVCONVERT_H
#define STREAMING_YUVCONVERT_H


#include <cstdint>

#include "libyuv.h"
#include "YuvFrame.h"

class YuvConvert {
public:
    YuvConvert();
    virtual ~YuvConvert();

private:
    int width;
    int height;

private:
    YuvFrame i420_rotated_frame;
    YuvFrame i420_scaled_frame;
    YuvFrame nv12;

public:
    void setEncodeResolution(int width, int height);

public:
    YuvFrame* rgba_convert_i420(char *rgba, int width, int height, bool need_flip, int rotate_degree);
    YuvFrame* nv21_convert_i420(char *nv21, int width, int height, bool need_flip, int rotate_degree);
    YuvFrame* nv21_convert_nv12(char *nv21, int width, int height, bool need_flip, int rotate_degree);
private:
    YuvFrame* convert_to_i420(char *frame, int width, int height, bool need_flip, int rotate_degree, libyuv::FourCC format);
};


#endif //STREAMING_YUVCONVERT_H
