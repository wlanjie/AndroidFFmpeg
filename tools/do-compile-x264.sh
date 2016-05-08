set -e

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
    echo "You must define ANDROID_NDK, ANDROID_SDK before starting."
    echo "They must point to your NDK and SDK directories.\n"
    exit 1
fi

FF_ARCH=$1
if [ -z "$FF_ARCH" ]; then
    echo "You must specific an architecture 'arm, armv7a, x86, ...'.\n"
    exit 1
fi

FF_BUILD_ROOT=`pwd`
FF_ANDROID_PLATFORM=android-9
FF_HOST_NAME=

FF_BUILD_NAME=
FF_CROSS_PREFIX=

FF_CFG_FLAGS=
FF_PLATFORM_CFG_FLAGS=

FF_EXTRA_CFLAGS=
FF_EXTRA_LDFLAGS=

FF_CFLAGS=
. ./do-detect-env.sh
FF_MAKE_TOOLCHAIN_FLAGS=$MAKE_TOOLCHAIN_FLAGS
FF_MAKE_FLAGS=$MAKE_FLAG
FF_GCC_VER=$GCC_VER
FF_GCC_64_VER=$GCC_64_VER


#----- armv7a begin -----
if [ "$FF_ARCH" = "armv7a" ]; then
    FF_BUILD_NAME=x264-armv7a
    FF_CROSS_PREFIX=arm-linux-androideabi
	  FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_VER}
    FF_PLATFORM_CFG_FLAGS="android-armv7"
    FF_HOST_NAME="arm-linux"
    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -mcpu=cortex-a8 -mfpu=vfpv3-d16 -mthumb -march=armv7-a -mtune=cortex-a9 -mfloat-abi=softfp -mfpu=neon -D__ARM_ARCH_7__ -D__ARM_ARCH_7A__"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS -Wl,--fix-cortex-a8"
elif [ "$FF_ARCH" = "armv5" ]; then
    FF_BUILD_NAME=x264-armv5
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-asm"
    FF_CROSS_PREFIX=arm-linux-androideabi
	  FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_VER}
    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=armv5te -mtune=arm9tdmi -msoft-float"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"
    FF_PLATFORM_CFG_FLAGS="android"
    FF_HOST_NAME="arm-linux"
elif [ "$FF_ARCH" = "x86" ]; then
    FF_BUILD_NAME=x264-x86
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-asm"
    FF_CROSS_PREFIX=i686-linux-android
	  FF_TOOLCHAIN_NAME=x86-${FF_GCC_VER}
    FF_PLATFORM_CFG_FLAGS="android-x86"
    FF_HOST_NAME="x86-linux"
    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS -march=atom -msse3 -ffast-math -mfpmath=sse"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"
elif [ "$FF_ARCH" = "x86_64" ]; then
    FF_ANDROID_PLATFORM=android-21
    FF_BUILD_NAME=x264-x86_64
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-asm"
    FF_CROSS_PREFIX=x86_64-linux-android
    FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_64_VER}
    FF_PLATFORM_CFG_FLAGS="linux-x86_64"
    FF_HOST_NAME="x86-linux"
    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"
elif [ "$FF_ARCH" = "arm64" ]; then
    FF_ANDROID_PLATFORM=android-21
    FF_BUILD_NAME=x264-arm64
    FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-asm"
    FF_CROSS_PREFIX=aarch64-linux-android
    FF_TOOLCHAIN_NAME=${FF_CROSS_PREFIX}-${FF_GCC_64_VER}
    FF_PLATFORM_CFG_FLAGS="linux-aarch64"
    FF_HOST_NAME="arm-linux"
    FF_EXTRA_CFLAGS="$FF_EXTRA_CFLAGS"
    FF_EXTRA_LDFLAGS="$FF_EXTRA_LDFLAGS"
else
    echo "unknown architecture $FF_ARCH";
    exit 1
fi

FF_TOOLCHAIN_PATH=$FF_BUILD_ROOT/x264-build/$FF_BUILD_NAME/toolchain

FF_SYSROOT=$FF_TOOLCHAIN_PATH/sysroot
FF_PREFIX=$FF_BUILD_ROOT/x264-build/$FF_BUILD_NAME/output

mkdir -p $FF_PREFIX
mkdir -p $FF_SYSROOT

#--------------------
echo ""
echo "--------------------"
echo "[*] make NDK standalone toolchain"
echo "--------------------"
. ./do-detect-env.sh
FF_MAKE_TOOLCHAIN_FLAGS=$MAKE_TOOLCHAIN_FLAGS
FF_MAKE_FLAGS=$MAKE_FLAG

FF_MAKE_TOOLCHAIN_FLAGS="$FF_MAKE_TOOLCHAIN_FLAGS --install-dir=$FF_TOOLCHAIN_PATH"
FF_TOOLCHAIN_TOUCH="$FF_TOOLCHAIN_PATH/touch"
if [ ! -f "$FF_TOOLCHAIN_TOUCH" ]; then
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh \
        $FF_MAKE_TOOLCHAIN_FLAGS \
        --platform=$FF_ANDROID_PLATFORM \
        --toolchain=$FF_TOOLCHAIN_NAME
    touch $FF_TOOLCHAIN_TOUCH;
fi

echo ""
echo "--------------------"
echo "[*] check x264 env"
echo "--------------------"
export PATH=$FF_TOOLCHAIN_PATH/bin:$PATH

export COMMON_FF_CFG_FLAGS=

FF_CFG_FLAGS="$FF_CFG_FLAGS $COMMON_FF_CFG_FLAGS"
FF_CFLAGS="-O3 -Wall -pipe \
    -std=c99 \
    -ffast-math \
    -fstrict-aliasing -Werror=strict-aliasing \
    -Wno-psabi -Wa,--noexecstack \
    -DANDROID -DNDEBUG"
#--------------------
# Standard options:
FF_CFG_FLAGS="$FF_CFG_FLAGS --prefix=$FF_PREFIX"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-static"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-shared"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-pic"
FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-cli"
FF_CFG_FLAGS="$FF_CFG_FLAGS --host=$FF_HOST_NAME"
# FF_CFG_FLAGS="$FF_CFG_FLAGS --extra-cflags=$FF_EXTRA_CFLAGS"
FF_CFG_FLAGS="$FF_CFG_FLAGS --cross-prefix=${FF_CROSS_PREFIX}-"
FF_CFG_FLAGS="$FF_CFG_FLAGS $FF_PLATFORM_CFG_FLAGS"

echo ""
echo "--------------------"
echo "[*] configurate x264 "
echo "--------------------"
cd ../extra/x264
make clean
./configure $FF_CFG_FLAGS \
   --extra-cflags="$FF_CFLAGS $FF_EXTRA_CFLAGS" \
   --extra-ldflags="-landroid -llog $FF_EXTRA_LDFLAGS"

echo ""
echo "--------------------"
echo "[*] compile x264"
echo "--------------------"
make depend
make $FF_MAKE_FLAGS
make install
