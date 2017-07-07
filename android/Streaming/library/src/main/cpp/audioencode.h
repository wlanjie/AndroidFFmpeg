//
// Created by wlanjie on 2016/12/22.
//

#ifndef STREAMING_AUDIOENCODE_H
#define STREAMING_AUDIOENCODE_H

#include <cstdint>
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

        uint8_t aac_buf[1024 * 1024];
    private:
        HANDLE_AACENCODER aac_handle;
        AACENC_InfoStruct info = {0};

    public:
        uint8_t *getAac();

    public:
        bool open_aac_encode(int channel, int sample_rate, int bitrate);

        void close_aac_encode();

        int encode_pcm_to_aac(char *pcm, int pcm_length, int *aac_size, uint8_t **aac);
    };
}

#endif //STREAMING_AUDIOENCODE_H
