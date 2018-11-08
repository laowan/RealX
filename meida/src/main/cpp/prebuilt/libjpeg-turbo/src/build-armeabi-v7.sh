#!/bin/bash
#
# This file is the build script run on Linux used to build libjpeg-turbo for Android
# 
# Set these variables to suit your needs
NDK_PATH=/home/jtzhu/ndk/android-ndk-r13b
BUILD_PLATFORM=linux-x86_64
TOOLCHAIN_VERSION=4.9
ANDROID_VERSION=21
HOST=arm-linux-androideabi
SYSROOT=${NDK_PATH}/platforms/android-${ANDROID_VERSION}/arch-arm
export CFLAGS="-march=armv7-a -mfloat-abi=softfp -fprefetch-loop-arrays \
  -D__ANDROID_API__=${ANDROID_VERSION} --sysroot=${SYSROOT} \
  -isystem ${NDK_PATH}/sysroot/usr/include \
  -isystem ${NDK_PATH}/sysroot/usr/include/${HOST}"
export LDFLAGS=-pie
TOOLCHAIN=${NDK_PATH}/toolchains/${HOST}-${TOOLCHAIN_VERSION}/prebuilt/${BUILD_PLATFORM}

cat <<EOF >toolchain.cmake
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR arm)
set(CMAKE_C_COMPILER ${TOOLCHAIN}/bin/${HOST}-gcc)
set(CMAKE_FIND_ROOT_PATH ${TOOLCHAIN}/${HOST})
EOF

cmake -G"Unix Makefiles" -DCMAKE_TOOLCHAIN_FILE=toolchain.cmake \
  -DCMAKE_POSITION_INDEPENDENT_CODE=1 \
  -DCMAKE_INSTALL_PREFIX=./libjpeg-turbo \
  ./
make
make install