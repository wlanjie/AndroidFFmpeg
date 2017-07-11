//
// Created by wlanjie on 2016/12/22.
//

#include <libAACenc/include/aacenc_lib.h>
#include "audioencode.h"
#include "log.h"

wlanjie::AudioEncode::AudioEncode() {

}

wlanjie::AudioEncode::~AudioEncode() {

}

bool wlanjie::AudioEncode::open(int channel, int sample_rate, int bitrate) {
    _outputStream.open("/sdcard/wlanjie.aac", std::ios_base::binary | std::ios_base::out);
    this->channel = channel;
    this->sample_rate = sample_rate;
    this->bitrate = bitrate;
    if (aacEncOpen(&aac_handle, 0, channel) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_AOT, AOT_AAC_LC) != AACENC_OK) {
        return false;
    }
//    if (aacEncoder_SetParam(aac_handle, AACENC_SBR_MODE, 1) != AACENC_OK) {
//        return false;
//    }
    if (aacEncoder_SetParam(aac_handle, AACENC_SAMPLERATE, sample_rate) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_CHANNELMODE, channel == 1 ? MODE_1 : MODE_2) != AACENC_OK) {
        return false;
    }
//    if (aacEncoder_SetParam(aac_handle, AACENC_CHANNELORDER, 1)  != AACENC_OK) {
//        return false;
//    }
//    if (aacEncoder_SetParam(aac_handle, AACENC_BANDWIDTH, 1) != AACENC_OK) {
//        return false;
//    }
    if (aacEncoder_SetParam(aac_handle, AACENC_CHANNELORDER, 1) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_BITRATE, bitrate) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_TRANSMUX, 2) != AACENC_OK) {
        return false;
    }
    if (aacEncEncode(aac_handle, NULL, NULL, NULL, NULL) != AACENC_OK) {
        return false;
    }
    return aacEncInfo(aac_handle, &info) == AACENC_OK;
}

void wlanjie::AudioEncode::close() {
    if (aac_handle) {
        aacEncClose(&aac_handle);
    }
    _outputStream.close();
}

int wlanjie::AudioEncode::encode(char *pcm, int pcm_length, int *aac_size, uint8_t **aac) {
    LOGE("pcm_length = %d", pcm_length);
    AACENC_BufDesc in_buf = { 0 };
    AACENC_BufDesc out_buf = { 0 };
    AACENC_InArgs in_args = { 0 };
    AACENC_OutArgs out_args = { 0 };
    int in_buffer_identifier = IN_AUDIO_DATA;
    int in_buffer_size = 2 * channel * pcm_length;
    int in_buffer_element_size = 2;
    void *in_ptr = pcm;

    in_args.numInSamples = channel * pcm_length;

    in_buf.numBufs = 1;
    in_buf.bufs = &in_ptr;
    in_buf.bufferIdentifiers = &in_buffer_identifier;
    in_buf.bufSizes = &in_buffer_size;
    in_buf.bufElSizes = &in_buffer_element_size;

    uint8_t aac_buf[8192];
    void *out_ptr = aac_buf;
    int out_buffer_identifier = OUT_BITSTREAM_DATA;
    int out_buffer_size = sizeof(aac_buf);
    int out_buffer_element_size = 1;

    out_buf.bufs = &out_ptr;
    out_buf.numBufs = 1;
    out_buf.bufSizes = &out_buffer_size;
    out_buf.bufElSizes = &out_buffer_element_size;
    out_buf.bufferIdentifiers = &out_buffer_identifier;
    if (aacEncEncode(aac_handle, &in_buf, &out_buf, &in_args, &out_args) != AACENC_OK) {
        LOGE("Encode failed.\n");
        return -1;
    }
    if (out_args.numOutBytes == 0) {
        LOGE("Encode aac size is 0.\n");
        return -2;
    }
    _outputStream.write((const char *) aac_buf, out_args.numOutBytes);
    *aac_size = out_args.numOutBytes;
    *aac = aac_buf;
    return out_args.numOutBytes;
}