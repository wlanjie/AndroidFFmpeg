//
// Created by CaoWu15 on 2017/7/11.
//

#include <string.h>
#include "voAAC.h"
#include "voType.h"
#include "audio.h"
#include "log.h"

void wlanjie::Audio::open(int sampleRate, int bitRate, int channels) {
    if (voGetAACEncAPI(&audioAPI) != VO_ERR_NONE) {
        LOGE("open vo aac encode failed.");
        return;
    }
    moper.Alloc = cmnMemAlloc;
    moper.Copy = cmnMemCopy;
    moper.Free = cmnMemFree;
    moper.Set = cmnMemSet;
    moper.Check = cmnMemCheck;
    VO_CODEC_INIT_USERDATA userData;
    memset(&userData, 0, sizeof(userData));
    if (audioAPI.Init(&hCodec, VO_AUDIO_CodingAAC, &userData) != VO_ERR_NONE) {
        LOGE("init vo aac failed.");
        return;
    }
    aacpara.sampleRate = sampleRate;
    aacpara.bitRate = bitRate;
    aacpara.nChannels = channels;
    aacpara.adtsUsed = true;
    if (audioAPI.SetParam(hCodec, VO_PID_AAC_ENCPARAM, &aacpara) != VO_ERR_NONE) {
        LOGE("vo aac set param failed.");
        return;
    }
}

void wlanjie::Audio::encoder(char *data, int data_size, int *aac_size, uint8_t **aac) {
    inData.Length = data_size;
    inData.Buffer = (VO_PBYTE) data;
    audioAPI.SetInputData(hCodec, &inData);
    outData.Length = 1024;
    outData.Buffer = (VO_PBYTE) malloc(outData.Length);
    audioAPI.GetOutputData(hCodec, &outData, &outInfo);
    *aac_size = outData.Length;
    *aac = outData.Buffer;
}

void wlanjie::Audio::close() {
    audioAPI.Uninit(hCodec);
}