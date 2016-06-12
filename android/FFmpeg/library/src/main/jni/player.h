//
// Created by wlanjie on 16/6/2.
//

#ifndef FFMPEG_PLAYER_H
#define FFMPEG_PLAYER_H

#include "ffmpeg.h"
#include "SDL.h"
#include "SDL_main.h"

int initPlayer();
int player(const char *file_path);

#endif //FFMPEG_PLAYER_H
