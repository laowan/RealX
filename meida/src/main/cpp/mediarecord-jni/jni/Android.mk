LOCAL_PATH := $(call my-dir)

MY_ARCH_FLAG=neon

ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
    ifeq ($(MY_ARCH_FLAG), neon)
        FFMPEG_MODULE_NAME := ffmpeg-neon
    else
        FFMPEG_MODULE_NAME := ffmpeg
    endif
endif

AUDIOENGINE_MODULE_NAME := audioengine
LIBTURBOJPEG_MODULE_NAME := jpeg_jni

#MY_APP_FFMPEG_INCLUDE_PATH := $(realpath $(LOCAL_PATH)/../../prebuilt/ffmpeg/include)
#MY_APP_FFMPEG_LIBRARY_PATH := $(realpath $(LOCAL_PATH)/../../prebuilt/ffmpeg/lib)

MY_APP_FFMPEG_INCLUDE_PATH := $(LOCAL_PATH)/../../prebuilt/ffmpeg/include
MY_APP_FFMPEG_LIBRARY_PATH := $(LOCAL_PATH)/../../prebuilt/ffmpeg/lib


MY_APP_LIBYUV_INCLUDE_PATH := $(LOCAL_PATH)/../../prebuilt/libyuv/include
MY_APP_LIBYUV_LIBRARY_PATH := $(LOCAL_PATH)/../../prebuilt/libyuv/lib


MY_APP_AUDIOENGINE_INCLUDE_PATH := $(LOCAL_PATH)/../../prebuilt/audioengine/include
MY_APP_AUDIOENGINE_LIBRARY_PATH := $(LOCAL_PATH)/../../prebuilt/audioengine/lib

MY_APP_LIBTURBOJPEG_INCLUDE_PATH := $(LOCAL_PATH)/../../prebuilt/libjpeg-turbo/include
MY_APP_LIBTURBOJPEG_LIBRARY_PATH := $(LOCAL_PATH)/../../prebuilt/libjpeg-turbo/lib32

MY_APP_PREBUILD_INCLUDE_PATH := $(LOCAL_PATH)/../../prebuilt


include $(call all-subdir-makefiles)
