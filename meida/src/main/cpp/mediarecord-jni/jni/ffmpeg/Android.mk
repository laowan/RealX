LOCAL_PATH := $(call my-dir)

#include $(CLEAR_VARS)
#LOCAL_MODULE := mp3lame
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../prebuilt/ffmpeg/lib/libmp3lame.so
#include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := $(FFMPEG_MODULE_NAME)
LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../prebuilt/ffmpeg/lib/lib$(FFMPEG_MODULE_NAME).so
include $(PREBUILT_SHARED_LIBRARY)
