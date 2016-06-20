//
// Created by wlanjie on 16/5/3.
//

#ifndef FFMPEG_FILTER_H
#define FFMPEG_FILTER_H

#include "ffmpeg.h"
FilterGraph* init_filtergraph(InputStream *ist, OutputStream *ost);
int configure_filtergraph(FilterGraph *graph);
#endif //FFMPEG_FILTER_H
