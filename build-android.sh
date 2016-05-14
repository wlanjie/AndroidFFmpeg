#! /usr/bin/env bash
FFMPEG_UPSTREAM=git://source.ffmpeg.org/ffmpeg.git
FFMPEG_LOCAL_REPO=extra/ffmpeg
LIBX264_UPSTREAM=git://git.videolan.org/x264.git
LIBX264_LOCAL_REPO=extra/x264
SDL_LOCAL_REPO=extra/SDL2
ROOT=`pwd`
CONTRIB_X264_PATH=$ROOT/android/contrib/x264
CONTRIB_FFMPEG_PATH=$ROOT/android/contrib/ffmpeg
SDL_JNI_PATH=$ROOT/android/FFmpeg/library/src/main/jni/SDL2
YUV_UPSTREAM=http://git.chromium.org/external/libyuv.git
YUV_LOCAL_REPO=extra/libyuv
YUV_JNI_PATH=$ROOT/android/FFmpeg/library/src/main/jni/libyuv
set -e

git --version

if [ ! -d "${LIBX264_LOCAL_REPO}" ]; then
  git clone $LIBX264_UPSTREAM $LIBX264_LOCAL_REPO
fi
# if [ ! -d "${CONTRIB_X264_PATH}" ]; then
#   ln -s $ROOT/$LIBX264_LOCAL_REPO $CONTRIB_X264_PATH
# fi

if [ ! -d "${FFMPEG_LOCAL_REPO}" ]; then
  git clone $FFMPEG_UPSTREAM $FFMPEG_LOCAL_REPO
fi
# if [ ! -d "${CONTRIB_FFMPEG_PATH}" ]; then
#   ln -s $ROOT/$FFMPEG_LOCAL_REPO $CONTRIB_FFMPEG_PATH
# fi

# if [ ! -d "${SDL_LOCAL_REPO}" ]; then
#   git clone https://github.com/spurious/SDL-mirror.git
#   rename SDL-mirror SDL2
# fi
#
# if [ ! -d "${SDL_JNI_PATH}" ]; then
#   ln -s $ROOT/$SDL_LOCAL_REPO $SDL_JNI_PATH
# fi

if [ ! -d "${YUV_LOCAL_REPO}" ]; then
  git clone $YUV_UPSTREAM $YUV_LOCAL_REPO
fi

# if [ ! -d "${YUV_JNI_PATH}" ]; then
#   ln -s $ROOT/$YUV_LOCAL_REPO $YUV_JNI_PATH
# fi
cd tools
#./compile-x264.sh armeabi
#./compile-x264.sh armeabi-v7a
./compile-x264.sh x86
#./compile-ffmpeg.sh armeabi
#./compile-ffmpeg.sh armeabi-v7a
#./compile-ffmpeg.sh x86
cd -
