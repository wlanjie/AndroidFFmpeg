//
// Created by wlanjie on 2016/11/30.
//

#ifndef STREAMING_MUXER_H
#define STREAMING_MUXER_H

#include <sys/types.h>

extern int muxer_sps_pps(char* frame, int frame_size, u_int32_t dts, u_int32_t pts, char** sps_pps, int* sps_pps_size);
extern char* muxer_h264(char* frames, int frames_size, u_int32_t dts, u_int32_t pts, char** sps_pps, int* sps_pps_size, char** h264, int* h264_size);
extern int muxer_aac(char sound_format, char sound_rate, char sound_size, char sound_type,
                       char* frame, int frame_size, u_int32_t pts, char** aac, int* aac_size, int* aac_packet_type);


#endif //STREAMING_MUXER_H
