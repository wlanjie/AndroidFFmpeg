//
// Created by wlanjie on 16/9/11.
//

#include "librtmp.h"
#include "log.h"

RTMP* rtmp_open(char *url, int isPublishMode) {
    RTMP *rtmp = RTMP_Alloc();
    if (rtmp == NULL) {
        return NULL;
    }
    RTMP_Init(rtmp);
    int ret = RTMP_SetupURL(rtmp, url);
    if (!ret) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        return NULL;
    }
    if (isPublishMode) {
        RTMP_EnableWrite(rtmp);
    }
    LOGE("setup RTMP_Connect %s", url);
    ret = RTMP_Connect(rtmp, NULL);
    LOGE("RTMP_Connect = %d", ret);
    if (!ret) {
        RTMP_Free(rtmp);
        rtmp = NULL;
        return NULL;
    }
    ret = RTMP_ConnectStream(rtmp, 0);
    if (!ret) {
        ret = RTMP_ConnectStream(rtmp, 0);
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = NULL;
        return NULL;
    }
    return rtmp;
}

int rtmp_read(long rtmp, int size) {
    char *data = malloc(size * sizeof(char));
    int readCount = RTMP_Read((RTMP*) rtmp, data, size);

    free(data);
    return readCount;
}

int rtmp_write(long rtmp, signed char *buffer, int size, int type, int ts) {
    RTMPPacket *packet = (RTMPPacket*)malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, size);
    RTMPPacket_Reset(packet);
    if (type == RTMP_PACKET_TYPE_INFO) {
        packet->m_nChannel = 0x03;
    } else if (type == RTMP_PACKET_TYPE_VIDEO) {
        packet->m_nChannel = 0x04;
    } else if (type == RTMP_PACKET_TYPE_AUDIO) {
        packet->m_nChannel = 0x05;
    } else {
        packet->m_nChannel = -1;
    }
    packet->m_nInfoField2 = ((RTMP*)rtmp)->m_stream_id;
    memcpy(packet->m_body, buffer, size);
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nTimeStamp = ts;
    packet->m_packetType = type;
    packet->m_nBodySize = size;
    int ret = RTMP_SendPacket((RTMP*)rtmp, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    return 0;
}

int rtmp_close(long rtmp) {
    RTMP_Close((RTMP*)rtmp);
    RTMP_Free((RTMP*)rtmp);
    return 0;
}

char *get_ip_address(long rtmp) {
//    return ((RTMP*)rtmp)->ipaddr;
    return "";
}
