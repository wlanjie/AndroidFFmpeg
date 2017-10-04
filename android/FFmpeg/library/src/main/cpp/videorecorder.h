//
// Created by wlanjie on 2017/9/30.
//

#ifndef FFMPEG_VIDEORECORDER_H
#define FFMPEG_VIDEORECORDER_H

extern "C" {
#include <libavutil/opt.h>
#include <libavcodec/avcodec.h>
#include "libavformat/avformat.h"
};

namespace av {

class VideoRecorder {

public:
    void fill_yuv_image(uint8_t *data[4], int linesize[4],
                        int width, int height, int frame_index);

    void recorder(char *filename);

    void end();
};

}

#endif //FFMPEG_VIDEORECORDER_H
