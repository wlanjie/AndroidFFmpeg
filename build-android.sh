#! /usr/bin/env bash
FFMPEG_UPSTREAM=git://source.ffmpeg.org/ffmpeg.git
FFMPEG_LOCAL_REPO=extra/ffmpeg
LIBX264_UPSTREAM=git://git.videolan.org/x264.git
LIBX264_LOCAL_REPO=extra/x264
ROOT=`pwd`
CONTRIB_X264_PATH=$ROOT/android/contrib/x264
CONTRIB_FFMPEG_PATH=$ROOT/android/contrib/ffmpeg
set -e

git --version

if [ ! -d "${LIBX264_LOCAL_REPO}" ]; then
  git clone $LIBX264_UPSTREAM $LIBX264_LOCAL_REPO
fi

if [ ! -d "${FFMPEG_LOCAL_REPO}" ]; then
  git clone $FFMPEG_UPSTREAM $FFMPEG_LOCAL_REPO
fi

cd tools
#./compile-x264.sh armeabi
#./compile-x264.sh armeabi-v7a
./compile-x264.sh x86
#./compile-ffmpeg.sh armeabi
#./compile-ffmpeg.sh armeabi-v7a
./compile-ffmpeg.sh x86
cd -
