//
// Created by wlanjie on 2017/9/26.
//

#ifndef FFMPEG_ARGUMENTS_H
#define FFMPEG_ARGUMENTS_H

typedef struct {
    int videoWidth;
    int videoHeight;
    int videoFrameRate;
    int videoGopSize;
    int videoBitRate;
    int audioBitRate;
    int audioSampleRate;
    int audioChannelCount;
} Arguments;

#endif //FFMPEG_ARGUMENTS_H
