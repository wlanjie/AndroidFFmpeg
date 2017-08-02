//
// Created by wlanjie on 2017/6/4.
//

#ifndef STREAMING_H264ENCODER_H
#define STREAMING_H264ENCODER_H

#include <iosfwd>
#include "wels/codec_api.h"
#include <fstream>
#include <iostream>

namespace wlanjie {

    struct VideoParameter {
        int frameWidth;
        int frameHeight;
        int videoWidth;
        int videoHeight;
        int bitrate;
        int frameRate;
    };

    class H264Encoder {
    public:
        H264Encoder();
        ~H264Encoder();

        void setVideoParameter(VideoParameter videoParameter);

        bool openH264Encoder();

        void closeH264Encoder();

        void encoder(char *rgba, long pts, int *h264_length, uint8_t **h264);
    private:
        SEncParamExt createEncoderParams() const;

    private:
        ISVCEncoder *encoder_;
        SFrameBSInfo info;
        Source_Picture_s _sourcePicture;
        Source_Picture_s _scaleSourcePicture;
        std::ofstream _outputStream;
        VideoParameter parameter;
    };

}

#endif //STREAMING_H264ENCODER_H
