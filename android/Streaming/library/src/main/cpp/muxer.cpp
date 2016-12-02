//
// Created by wlanjie on 2016/11/30.
//

#include "muxer.h"
#include "log.h"


#include <srs_kernel_buffer.hpp>
#include <srs_kernel_error.hpp>
#include <srs_raw_avc.hpp>
#include <srs_kernel_utility.hpp>

// the remux raw codec.
SrsRawH264Stream avc_raw;
SrsRawAacStream aac_raw;

// for h264 raw stream,
// @see: https://github.com/ossrs/srs/issues/66#issuecomment-62240521
SrsBuffer h264_raw_stream;
// about SPS, @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
std::string h264_sps;
std::string h264_pps;
// whether the sps and pps sent,
// @see https://github.com/ossrs/srs/issues/203
bool h264_sps_pps_sent;
// only send the ssp and pps when both changed.
// @see https://github.com/ossrs/srs/issues/204
bool h264_sps_changed;
bool h264_pps_changed;
// for aac raw stream,
// @see: https://github.com/ossrs/srs/issues/212#issuecomment-64146250
SrsBuffer aac_raw_stream;
// the aac sequence header.
std::string aac_specific_config;

void muxer_sps_pps(u_int32_t dts, u_int32_t pts, char** sps_pps, int* sps_pps_size) {

    // send when sps or pps changed.
    if (!h264_sps_changed && !h264_pps_changed) {
        return;
    }

    // h264 raw to h264 packet.
    std::string sh;
    if (avc_raw.mux_sequence_header(h264_sps, h264_pps, dts, pts, sh) != ERROR_SUCCESS) {
        return;
    }

    // h264 packet to flv packet.
    int8_t frame_type = SrsCodecVideoAVCFrameKeyFrame;
    int8_t avc_packet_type = SrsCodecVideoAVCTypeSequenceHeader;
    char* flv = NULL;
    int nb_flv = 0;
    if (avc_raw.mux_avc2flv(sh, frame_type, avc_packet_type, dts, pts, &flv, &nb_flv) != ERROR_SUCCESS) {
        return;
    }

    // reset sps and pps.
    h264_sps_changed = false;
    h264_pps_changed = false;
    h264_sps_pps_sent = true;

    *sps_pps = flv;
    *sps_pps_size = nb_flv;
}


/**
* write h264 IPB-frame.
*/
int muxer_ibp( char* frame, int frame_size, u_int32_t dts, u_int32_t pts, char** h264, int* h264_size) {
    int ret = ERROR_SUCCESS;

    // when sps or pps not sent, ignore the packet.
    if (!h264_sps_pps_sent) {
        return ERROR_H264_DROP_BEFORE_SPS_PPS;
    }

    // 5bits, 7.3.1 NAL unit syntax,
    // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
    //  5: I Frame, 1: P/B Frame
    // @remark we already group sps/pps to sequence header frame;
    //      for I/P NALU, we send them in isolate frame, each NALU in a frame;
    //      for other NALU, for example, AUD/SEI, we just ignore them, because
    //      AUD used in annexb to split frame, while SEI generally we can ignore it.
    // TODO: maybe we should group all NALUs split by AUD to a frame.
    SrsAvcNaluType nut = (SrsAvcNaluType)(frame[0] & 0x1f);
    if (nut != SrsAvcNaluTypeIDR && nut != SrsAvcNaluTypeNonIDR) {
        return ret;
    }

    // for IDR frame, the frame is keyframe.
    SrsCodecVideoAVCFrame frame_type = SrsCodecVideoAVCFrameInterFrame;
    if (nut == SrsAvcNaluTypeIDR) {
        frame_type = SrsCodecVideoAVCFrameKeyFrame;
    }

    std::string ibp;
    if ((ret = avc_raw.mux_ipb_frame(frame, frame_size, ibp) != ERROR_SUCCESS)) {
        return ret;
    }

    int8_t avc_packet_type = SrsCodecVideoAVCTypeNALU;
    char* flv = NULL;
    int nb_flv = 0;
    if (avc_raw.mux_avc2flv(ibp, frame_type, avc_packet_type, dts, pts, &flv, &nb_flv) != ERROR_SUCCESS) {
        return ret;
    }

    *h264 = flv;
    *h264_size = nb_flv;
    return ret;
}

int muxer_sps_pps_frame(char* frame, int frame_size, u_int32_t dts, u_int32_t pts, char** sps_pps, int* sps_pps_size) {

    int ret = ERROR_SUCCESS;
    // empty frame.
    if (frame_size <= 0) {
        return ret;
    }

    // for sps
    if (avc_raw.is_sps(frame, frame_size)) {
        std::string sps;
        if ((ret = avc_raw.sps_demux(frame, frame_size, sps) != ERROR_SUCCESS)) {
            return ret;
        }

        if (h264_sps == sps) {
            return ERROR_H264_DUPLICATED_SPS;
        }
        h264_sps_changed = true;
        h264_sps = sps;
        return ret;
    }

    // for pps
    if (avc_raw.is_pps(frame, frame_size)) {
        std::string pps;
        if ((ret = avc_raw.pps_demux(frame, frame_size, pps) != ERROR_SUCCESS)) {
            return ret;
        }

        if (h264_pps == pps) {
            return ERROR_H264_DUPLICATED_PPS;
        }

        h264_pps_changed = true;
        h264_pps = pps;
        return ret;
    }

    // ignore others.
    // 5bits, 7.3.1 NAL unit syntax,
    // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
    //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame, 9: AUD
    SrsAvcNaluType nut = (SrsAvcNaluType)(frame[0] & 0x1f);
    if (nut != SrsAvcNaluTypeSPS && nut != SrsAvcNaluTypePPS
        && nut != SrsAvcNaluTypeIDR && nut != SrsAvcNaluTypeNonIDR
        && nut != SrsAvcNaluTypeAccessUnitDelimiter
            ) {
        return ret;
    }

    muxer_sps_pps(dts, pts, sps_pps, sps_pps_size);
    return ERROR_SUCCESS;
}

char* muxer_h264(char* frames, int frames_size, u_int32_t dts, u_int32_t pts, char** sps_pps, int* sps_pps_size, char** h264, int* h264_size) {

    srs_assert(frames != NULL);
    srs_assert(frames_size > 0);

    int ret = ERROR_SUCCESS;

    if (h264_raw_stream.initialize(frames, frames_size) != ERROR_SUCCESS) {
        return NULL;
    }

    while (!h264_raw_stream.empty()) {
        char* frame = NULL;
        int frame_size = 0;
        if (avc_raw.annexb_demux(&h264_raw_stream, &frame, &frame_size) != ERROR_SUCCESS) {
            return NULL;
        }
        if (frame_size <= 0) {
            continue;
        }

        if ((ret = muxer_sps_pps_frame(frame, frame_size, dts, pts, sps_pps, sps_pps_size)) != ERROR_SUCCESS) {
            if (ret == ERROR_H264_DUPLICATED_PPS || ret == ERROR_H264_DUPLICATED_SPS ) {
                continue;
            }
        }
        if ((ret = muxer_ibp(frame, frame_size, dts, pts, h264, h264_size)) != ERROR_SUCCESS) {
            if (ret == ERROR_H264_DROP_BEFORE_SPS_PPS) {
                continue;
            }
            return NULL;
        }
    }
    return NULL;
}

bool aac_is_adts(char* aac_raw_data, int aac_raw_size) {
    SrsBuffer stream;
    if (stream.initialize(aac_raw_data, aac_raw_size) != ERROR_SUCCESS) {
        return false;
    }

    return srs_aac_startswith_adts(&stream);
}

int muxer_audio_frame(char* frame, int frame_size, SrsRawAacStreamCodec* codec, u_int32_t timestamp, char** aac, int* aac_size) {
    int ret = ERROR_SUCCESS;

    char* data = NULL;
    int size = 0;
    if ((ret = aac_raw.mux_aac2flv(frame, frame_size, codec, timestamp, &data, &size)) != ERROR_SUCCESS) {
        return ret;
    }

    *aac = data;
    *aac_size = size;
    return ret;
}

int muxer_aac_frame(SrsRawAacStreamCodec* codec, char* frame, int frame_size, u_int32_t timestamp, char** aac, int* aac_size) {
    int ret = ERROR_SUCCESS;

    // send out aac sequence header if not sent.
    if (aac_specific_config.empty()) {
        std::string sh;
        if ((ret = aac_raw.mux_sequence_header(codec, sh) != ERROR_SUCCESS)) {
            return ret;
        }
        aac_specific_config = sh;

        codec->aac_packet_type = 0;

        if ((ret = muxer_audio_frame((char *) sh.data(), sh.length(), codec, timestamp, aac, aac_size)) != ERROR_SUCCESS) {
            return ret;
        }
    } else {

        codec->aac_packet_type = 1;
        if ((ret = muxer_audio_frame(frame, frame_size, codec, timestamp, aac, aac_size)) != ERROR_SUCCESS) {
            return ret;
        }
    }


    return ret;
}

int muxer_aac(char sound_format, char sound_rate, char sound_size, char sound_type,
                char* frames, int frames_size, u_int32_t pts, char** aac, int* aac_size, int* aac_packet_type) {

    int ret = ERROR_SUCCESS;
    if (sound_format == SrsCodecAudioAAC) {
        if (!aac_is_adts(frames, frames_size)) {
            return NULL;
        }

        SrsBuffer* stream = &aac_raw_stream;
        if ((ret = stream->initialize(frames, frames_size)) != ERROR_SUCCESS) {
            return ret;
        }

        while (!stream->empty()) {
            char* frame = NULL;
            int frame_size = 0;
            SrsRawAacStreamCodec codec;
            if ((ret = aac_raw.adts_demux(stream, &frame, &frame_size, codec)) != ERROR_SUCCESS) {
                return ret;
            }

            // override by user specified.
            codec.sound_format = sound_format;
            codec.sound_rate = sound_rate;
            codec.sound_size = sound_size;
            codec.sound_type = sound_type;

            if ((ret = muxer_aac_frame(&codec, frame, frame_size, pts, aac, aac_size)) != ERROR_SUCCESS) {
                return ret;
            }
            *aac_packet_type = codec.aac_packet_type;
        }

    }
    return NULL;
}