#! /usr/bin/env bash
BUILD_ROOT=`pwd`
TARGET=$1
set -e
set +x

ACT_ARCHS_32="armeabi armeabi-v7a x86"
ACT_ARCHS_64="armeabi armeabi-v7a x86 armeabi-arm64 x86-64"
ACT_ARCHS_ALL=$ACT_ARCHS_64

case "$TARGET" in
    "")
        sh ./do-compile-sdl2.sh armeabi-v7a
        cp sdl2-build/armeabi-v7a/output/lib/libSDL2.so ../android/FFmpeg/library/src/main/jniLibs/armeabi-v7a
    ;;
    armeabi|armeabi-v7a|armeabi-arm64|x86|x86-64)
        sh ./do-compile-sdl2.sh $TARGET
        cp sdl2-build/$TARGET/output/lib/libSDL2.so ../android/FFmpeg/library/src/main/jniLibs/$TARGET
    ;;
    all32)
        for ARCH in $ACT_ARCHS_32
        do
            sh ./do-compile-sdl2.sh $ARCH
            cp sdl2-build/$ARCh/output/lib/libSDL2.so ../android/FFmpeg/library/src/main/jniLibs/$ARCh
        done
    ;;
    all|all64)
        for ARCH in ACT_ARCHS_64
        do
            sh ./compile-sdl2.sh $ARCH
        done
    ;;
    clean)
        for ARCH in $ACT_ARCHS_ALL
        do
            if [ -d sdl2-$ARCH ]; then
                cd sdl2-$ARCH && git clean -xdf && cd -
            fi
        done
        rm -rf ./build/sdl2-*
    ;;
    *)
        exit 1
    ;;

esac
