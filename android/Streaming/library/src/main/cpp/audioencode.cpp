//
// Created by wlanjie on 2016/12/22.
//

#include <libAACenc/include/aacenc_lib.h>
#include "audioencode.h"

wlanjie::AudioEncode::AudioEncode() {

}

wlanjie::AudioEncode::~AudioEncode() {

}

bool wlanjie::AudioEncode::open_aac_encode(int channel, int sample_rate, int bitrate) {
    this->channel = channel;
    this->sample_rate = sample_rate;
    this->bitrate = bitrate;
    if (aacEncOpen(&aac_handle, 0, channel) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_AOT, 2) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_SAMPLERATE, sample_rate) != AACENC_OK) {
        return false;
    }
    if (aacEncoder_SetParam(aac_handle, AACENC_CHANNELMODE, channel == 1 ? MODE_1 : MODE_2) != AACENC_OK) {
        return false;
    }
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

void wlanjie::AudioEncode::close_aac_encode() {
    if (aac_handle) {
        aacEncClose(&aac_handle);
    }
}

int wlanjie::AudioEncode::encode_pcm_to_aac(char *pcm, int pcm_length) {
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

    int out_buffer_identifier = OUT_BITSTREAM_DATA;
    int out_buffer_size = pcm_length;
    int out_buffer_element_size = 1;
    void *out_ptr = aac_buf;

    out_buf.bufs = &out_ptr;
    out_buf.numBufs = 1;
    out_buf.bufSizes = &out_buffer_size;
    out_buf.bufElSizes = &out_buffer_element_size;
    out_buf.bufferIdentifiers = &out_buffer_identifier;
    if (aacEncEncode(aac_handle, &in_buf, &out_buf, &in_args, &out_args) != AACENC_OK) {
        return -1;
    }
    if (out_args.numOutBytes == 0) {
        return -2;
    }
    return out_args.numOutBytes;
}

uint8_t *wlanjie::AudioEncode::getAac() {
    return aac_buf;
}











