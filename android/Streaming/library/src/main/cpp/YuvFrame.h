//
// Created by wlanjie on 2016/12/22.
//

#ifndef STREAMING_YUVFRAME_H
#define STREAMING_YUVFRAME_H


#include <cstdint>

class YuvFrame {
public:
    int width;
    int height;
    uint8_t *data;
    uint8_t *y;
    uint8_t *u;
    uint8_t *v;
};


#endif //STREAMING_YUVFRAME_H
