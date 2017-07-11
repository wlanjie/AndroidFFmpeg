//
// Created by wlanjie on 2016/12/22.
//

#ifndef STREAMING_AUDIOENCODE_H
#define STREAMING_AUDIOENCODE_H

#include <cstdint>
#include <fstream>
#include <iostream>
#include "libAACenc/include/aacenc_lib.h"

namespace wlanjie {

    class AudioEncode {
    public:
        AudioEncode();

        virtual ~AudioEncode();

    private:
        int channel;
        int sample_rate;
        int bitrate;

    private:
        HANDLE_AACENCODER aac_handle;
        AACENC_InfoStruct info = {0};
        std::ofstream _outputStream;

    public:
        bool open(int channel, int sample_rate, int bitrate);

        void close();

        int encode(char *pcm, int pcm_length, int *aac_size, uint8_t **aac);
    };
}

#endif //STREAMING_AUDIOENCODE_H
