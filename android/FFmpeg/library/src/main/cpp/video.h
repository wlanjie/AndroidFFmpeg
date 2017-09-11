//
// Created by wlanjie on 2017/9/9.
//

#ifndef FFMPEG_VIDEO_H
#define FFMPEG_VIDEO_H

#include <system_error>

#include "formatcontext.h"

namespace av {

#define SUCCESS 0
#define OPEN_INPUT_ERROR 4000
#define FIND_STREAM_ERROR 4001
#define ADD_VIDEO_STREAM_ERROR 4002
#define ADD_AUDIO_STREAM_ERROR 4003
#define FIND_VIDEO_STREAM_ERROR 4004
#define FIND_AUDIO_STREAM_ERROR 4005
#define OPEN_OUTPUT_ERROR 4006
#define OPEN_VIDEO_DECODE_ERROR 4007
#define OPEN_AUDIO_DECODE_ERROR 4008
#define OPEN_VIDEO_ENCODER_ERROR 4009
#define OPEN_AUDIO_ENCODER_ERROR 4010
#define WRITE_HEADER_ERROR 4011
#define DECODING_VIDEO_ERROR 4012
#define DECODING_AUDIO_ERROR 4013
#define ENCODING_VIDEO_ERROR 4014
#define ENCODING_AUDIO_ERROR 4015
#define WRITE_PACKET_ERROR 4016
#define SCALE_ERROR 4017
#define WRITE_TRAILER_ERROR 4018
#define FLUSH_VIDEO_ERROR 4019
#define FLUSH_AUDIO_ERROR 4020

class Video {
private:
    FormatContext inputContext;
    FormatContext outputContext;
    OutputFormat outputFormat;
    std::error_code ec;

public:
    Video();
    ~Video();

    int openInput(std::string uri);

    int openOutput(std::string uri);

    int scale(int newWidth, int newHeight);

    int getWidth();

    int getHeight();

    int64_t getTotalFrame();

    double getDuration();

    double getFrameRate();

    void close();
};

}
#endif //FFMPEG_VIDEO_H
