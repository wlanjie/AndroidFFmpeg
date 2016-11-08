#! /usr/bin/env bash
#
# Copyright (C) 2013-2014 Zhang Rui <bbcallen@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This script is based on projects below
# https://github.com/yixia/FFmpeg-Android
# http://git.videolan.org/?p=vlc-ports/android.git;a=summary

#--------------------
echo "===================="
echo "[*] check env $1"
echo "===================="
set -e


#--------------------
# common defines
FF_ARCH=$1
if [ -z "$FF_ARCH" ]; then
    echo "You must specific an architecture 'arm, armv7a, x86, ...'."
    echo ""
    exit 1
fi


FF_BUILD_ROOT=`pwd`
FF_ANDROID_PLATFORM=android-9

FF_BUILD_NAME=
FF_CROSS_PREFIX=
FF_DEP_OPENSSL_INC=
FF_DEP_OPENSSL_LIB=

FF_CFG_FLAGS=

FF_EXTRA_CFLAGS=
FF_EXTRA_LDFLAGS=
FF_DEP_LIBS=

FF_MODULE_DIRS="compat libavcodec libavfilter libavformat libavutil libswresample libswscale"
FF_ASSEMBLER_SUB_DIRS=


#--------------------
echo ""
echo "--------------------"
echo "[*] make NDK standalone toolchain"
echo "--------------------"
. ./do-detect-env.sh
FF_MAKE_TOOLCHAIN_FLAGS=$MAKE_TOOLCHAIN_FLAGS
FF_MAKE_FLAGS=$MAKE_FLAG
FF_GCC_VER=$GCC_VER
FF_GCC_64_VER=$GCC_64_VER


#----- armv7a begin -----
if [ "$FF_ARCH" = "armeabi-v7a" ]; then
    FF_BUILD_NAME=armeabi-v7a
    FF_BUILD_NAME_OPENSSL=armeabi-v7a
    FF_BUILD_NAME_X264=armeabi-v7a
    FF_CROSS_PREFIX=arm-linux-androideabi
    FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_VER}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=arm --cpu=cortex-a8"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-neon"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-thumb"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=armv7-a -mcpu=cortex-a8 -mfpu=vfpv3-d16 -mfloat-abi=softfp -mthumb"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS -Wl,--fix-cortex-a8"

    FF_ASSEMBLER_SUB_DIRS="arm"

elif [ "$FF_ARCH" = "armeabi" ]; then
    FF_BUILD_NAME=armeabi
    FF_BUILD_NAME_OPENSSL=armeabi
    FF_BUILD_NAME_X264=armeabi
    FF_CROSS_PREFIX=arm-linux-androideabi
    FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_VER}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=arm"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=armv5te -mtune=arm9tdmi -msoft-float"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS="arm"

elif [ "$FF_ARCH" = "x86" ]; then
    FF_BUILD_NAME=x86
    FF_BUILD_NAME_OPENSSL=x86
    FF_BUILD_NAME_X264=x86
    FF_CROSS_PREFIX=i686-linux-android
    FF_TOOLCHAIN_NAME=x86-${FF_GCC_VER}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=x86 --cpu=i686 --enable-yasm"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=atom -msse3 -ffast-math -mfpmath=sse"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS="x86"

elif [ "$FF_ARCH" = "x86-64" ]; then
    FF_ANDROID_PLATFORM=android-21
    FF_BUILD_NAME=x86-64
    FF_BUILD_NAME_OPENSSL=x86_-4
    FF_BUILD_NAME_X264=x86-64
    FF_CROSS_PREFIX=x86_64-linux-android
    FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_64_VER}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=x86_64 --enable-yasm"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS="x86"

elif [ "$FF_ARCH" = "armeabi-arm64" ]; then
    FF_ANDROID_PLATFORM=android-21

    FF_BUILD_NAME=armeabi-arm64
    FF_BUILD_NAME_OPENSSL=armeabi-arm64
    FF_BUILD_NAME_X264=armeabi-arm64
    FF_CROSS_PREFIX=aarch64-linux-android
    FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_64_VER}

    FF_CFG_FLAGS="$FF_CFG_FLAGS --arch=aarch64 --enable-yasm"

    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"

    FF_ASSEMBLER_SUB_DIRS="aarch64 neon"

else
    echo "unknown architecture $FF_ARCH";
    exit 1
fi

FF_TOOLCHAIN_PATH=$FF_BUILD_ROOT/ffmpeg-build/$FF_BUILD_NAME/toolchain
FF_MAKE_TOOLCHAIN_FLAGS="$FF_MAKE_TOOLCHAIN_FLAGS --install-dir=$FF_TOOLCHAIN_PATH"

FF_SYSROOT=$FF_TOOLCHAIN_PATH/sysroot
FF_PREFIX=$FF_BUILD_ROOT/ffmpeg-build/$FF_BUILD_NAME/output
FF_DEP_OPENSSL_INC=$FF_BUILD_ROOT/openssl-build/$FF_BUILD_NAME_OPENSSL/output/include
FF_DEP_OPENSSL_LIB=$FF_BUILD_ROOT/openssl-build/$FF_BUILD_NAME_OPENSSL/output/lib
FF_DEP_X264_INC=$FF_BUILD_ROOT/x264-build/$FF_BUILD_NAME_X264/output/include
FF_DEP_X264_LIB=$FF_BUILD_ROOT/x264-build/$FF_BUILD_NAME_X264/output/lib

case "$UNAME_S" in
    CYGWIN_NT-*)
        FF_SYSROOT="$(cygpath -am $FF_SYSROOT)"
        FF_PREFIX="$(cygpath -am $FF_PREFIX)"
    ;;
esac

mkdir -p $FF_PREFIX
mkdir -p $FF_SYSROOT

FF_TOOLCHAIN_TOUCH="$FF_TOOLCHAIN_PATH/touch"
if [ ! -f "$FF_TOOLCHAIN_TOUCH" ]; then
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh \
        $FF_MAKE_TOOLCHAIN_FLAGS \
        --platform=$FF_ANDROID_PLATFORM \
        --toolchain=$FF_TOOLCHAIN_NAME
    touch $FF_TOOLCHAIN_TOUCH;
fi

#--------------------
echo ""
echo "--------------------"
echo "[*] check ffmpeg env"
echo "--------------------"
export PATH=$FF_TOOLCHAIN_PATH/bin:$PATH
#export CC="ccache ${FF_CROSS_PREFIX}-gcc"
export CC="${FF_CROSS_PREFIX}-gcc"
export LD=${FF_CROSS_PREFIX}-ld
export AR=${FF_CROSS_PREFIX}-ar
export STRIP=${FF_CROSS_PREFIX}-strip

FF_CFLAGS="-O3 -Wall -pipe \
    -std=c99 \
    -ffast-math \
    -fstrict-aliasing -Werror=strict-aliasing \
    -Wno-psabi -Wa,--noexecstack \
    -DANDROID -DNDEBUG"

# cause av_strlcpy crash with gcc4.7, gcc4.8
# -fmodulo-sched -fmodulo-sched-allow-regmoves

# --enable-thumb is OK
#FF_CFLAGS="$FF_CFLAGS -mthumb"

# not necessary
#FF_CFLAGS="$FF_CFLAGS -finline-limit=300"

export COMMON_FF_CFG_FLAGS=
. $FF_BUILD_ROOT/module.sh

echo "COMMON_FF_CFG_FLAGS $COMMON_FF_CFG_FLAGS"

#--------------------
# with openssl
if [ -f "${FF_DEP_OPENSSL_LIB}/libssl.a" ]; then
    echo "OpenSSL detected"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-nonfree"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-openssl"

    FF_CFLAGS="$FF_CFLAGS -I${FF_DEP_OPENSSL_INC}"
    FF_DEP_LIBS="-L${FF_DEP_OPENSSL_LIB} -lssl -lcrypto"
fi

#with x264
if [ -f "${FF_DEP_X264_LIB}/libx264.a" ]; then
# FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-nonfree"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-libx264"
    FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-encoder=libx264"

    FF_CFLAGS="$FF_CFLAGS -I${FF_DEP_X264_INC}"
    FF_DEP_LIBS="-L${FF_DEP_X264_LIB} -lx264"
fi

FF_CFG_FLAGS="$FF_CFG_FLAGS $COMMON_FF_CFG_FLAGS"

#--------------------
# Standard options:
FF_CFG_FLAGS="$FF_CFG_FLAGS --prefix=$FF_PREFIX"

# Advanced options (experts only):
FF_CFG_FLAGS="$FF_CFG_FLAGS --cross-prefix=${FF_CROSS_PREFIX}-"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-cross-compile"
FF_CFG_FLAGS="$FF_CFG_FLAGS --target-os=linux"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-pic"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-symver"

# Optimization options (experts only):
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-asm"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-inline-asm"

#--------------------
echo ""
echo "--------------------"
echo "[*] configurate ffmpeg"
echo "--------------------"
cd ../extra/ffmpeg

if [ -f "compat/strtod.o" ]; then
  rm -rf compat/strtod.o
fi

if [ -f "./config.h" ]; then
    make clean
    rm -rf config.h
    echo 'reuse configure'
fi

echo "ff_cflags  $FF_CFLAGS"
which $CC
./configure $FF_CFG_FLAGS \
    --extra-cflags="$FF_CFLAGS $FF_EXTRA_CFLAGS" \
    --extra-ldflags="$FF_DEP_LIBS"

#--------------------
echo ""
echo "--------------------"
echo "[*] compile ffmpeg"
echo "--------------------"
cp config.* $FF_PREFIX
make $FF_MAKE_FLAGS
make install
mkdir -p $FF_PREFIX/include/libffmpeg
cp -f config.h $FF_PREFIX/include/libffmpeg/config.h

#--------------------
echo ""
echo "--------------------"
echo "[*] link ffmpeg"
echo "--------------------"
echo $FF_EXTRA_LDFLAGS

FF_C_OBJ_FILES=
FF_ASM_OBJ_FILES=
for MODULE_DIR in $FF_MODULE_DIRS
do
    C_OBJ_FILES="$MODULE_DIR/*.o"
    if ls $C_OBJ_FILES 1> /dev/null 2>&1; then
        echo "link $MODULE_DIR/*.o"
        FF_C_OBJ_FILES="$FF_C_OBJ_FILES $C_OBJ_FILES"
    fi

    for ASM_SUB_DIR in $FF_ASSEMBLER_SUB_DIRS
    do
        ASM_OBJ_FILES="$MODULE_DIR/$ASM_SUB_DIR/*.o"
        if ls $ASM_OBJ_FILES 1> /dev/null 2>&1; then
            echo "link $MODULE_DIR/$ASM_SUB_DIR/*.o"
            FF_ASM_OBJ_FILES="$FF_ASM_OBJ_FILES $ASM_OBJ_FILES"
        fi
    done
done

$CC -lm -lz -shared --sysroot=$FF_SYSROOT -Wl,--no-undefined -Wl,-z,noexecstack $FF_EXTRA_LDFLAGS \
    -Wl,-soname,libffmpeg.so \
    $FF_C_OBJ_FILES \
    $FF_ASM_OBJ_FILES \
    $FF_DEP_LIBS \
    -o $FF_PREFIX/libffmpeg.so

mysedi() {
    f=$1
    exp=$2
    n=`basename $f`
    cp $f /tmp/$n
    sed $exp /tmp/$n > $f
    rm /tmp/$n
}

echo ""
echo "--------------------"
echo "[*] create files for shared ffmpeg"
echo "--------------------"
rm -rf $FF_PREFIX/shared
mkdir -p $FF_PREFIX/shared/lib/pkgconfig
ln -s $FF_PREFIX/include $FF_PREFIX/shared/include
ln -s $FF_PREFIX/libffmpeg.so $FF_PREFIX/shared/lib/libffmpeg.so
cp $FF_PREFIX/lib/pkgconfig/*.pc $FF_PREFIX/shared/lib/pkgconfig
for f in $FF_PREFIX/lib/pkgconfig/*.pc; do
    # in case empty dir
    if [ ! -f $f ]; then
        continue
    fi
    cp $f $FF_PREFIX/shared/lib/pkgconfig
    f=$FF_PREFIX/shared/lib/pkgconfig/`basename $f`
    # OSX sed doesn't have in-place(-i)
    mysedi $f 's/\/output/\/output\/shared/g'
    mysedi $f 's/-lavcodec/-lffmpeg/g'
    mysedi $f 's/-lavfilter/-lffmpeg/g'
    mysedi $f 's/-lavformat/-lffmpeg/g'
    mysedi $f 's/-lavutil/-lffmpeg/g'
    mysedi $f 's/-lswresample/-lffmpeg/g'
    mysedi $f 's/-lswscale/-lffmpeg/g'
done
