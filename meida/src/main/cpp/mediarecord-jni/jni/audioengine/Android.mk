LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := $(AUDIOENGINE_MODULE_NAME)
LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../prebuilt/audioengine/lib/lib$(AUDIOENGINE_MODULE_NAME).so
include $(PREBUILT_SHARED_LIBRARY)
