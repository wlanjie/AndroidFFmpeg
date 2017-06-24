//
// Created by wlanjie on 2016/12/22.
//

#ifndef STREAMING_VIDEOENCODE_H
#define STREAMING_VIDEOENCODE_H
//
//#include <x264.h>
//
//#include "YuvConvert.h"
//
//class VideoEncode {
//
//private:
//    x264_param_t param;
//    x264_t *encoder;
//    x264_picture_t picture;
//    int width;
//    int height;
//    int fps;
//    int gop;
//    int bitrate;
//    char preset[16];
//    bool global_nal_header = false;
//    bool is_key_frame;
//    int pts;
//    int dts;
//    uint8_t h264_es[1024 * 1024];
//public:
//    VideoEncode();
//    virtual ~VideoEncode();
//
//public:
//    void setEncodeBitrate(int bitrate);
//    void setEncodeFps(int fps);
//    void setEncodeGop(int gop_size);
//    void setEncodePreset(const char *preset);
//    void setEncodeResolution(int width, int height);
//
//public:
//    int open_h264_encode();
//    void close_h264_encode();
//    int rgba_encode_to_h264(char *frame, int width, int height, bool need_flip, int rotate_degree, int pts);
//    YuvFrame *rgba_convert_i420(char *frame, int width, int height, bool need_flip, int rotate_degree);
//    YuvFrame *rgba_convert_nv12(char *frame, int width, int height, bool need_flip, int rotate_degree);
//    YuvFrame *nv21_convert_i420(char *frame, int width, int height, bool need_flip, int rotate_degree);
//    YuvFrame *nv21_convert_nv12(char *frame, int width, int height, bool need_flip, int rotate_degree);
//    int encode(uint8_t *data, int width, int height);
//public:
//    uint8_t *get_h264();
//
//private:
//    int encode_global_nal_header(YuvFrame *yuv);
//    int x264_encode(YuvFrame *yuv);
//    int encode_nal(const x264_nal_t *nal, int nal_size);
//
//private:
//    YuvConvert convert;
//};


#endif //STREAMING_VIDEOENCODE_H
