//
// Created by CaoWu15 on 2017/7/11.
//

#ifndef STREAMING_AUDIO_H
#define STREAMING_AUDIO_H

#include <cstdint>
#include "cmnMemory.h"
#include "voAAC.h"

namespace wlanjie {

    class Audio {
    private:
        VO_AUDIO_CODECAPI           audioAPI;
        VO_MEM_OPERATOR             moper;
        VO_HANDLE                   hCodec;
        AACENC_PARAM                aacpara;
        VO_CODECBUFFER              inData;
        VO_CODECBUFFER              outData;
        VO_AUDIO_OUTPUTINFO         outInfo;

    public:
        void open(int sampleRate, int bitRate, int channels);
        void encoder(char *data, int data_size, int *aac_size, uint8_t **aac);
        void close();
    };

}
#endif //STREAMING_AUDIO_H
