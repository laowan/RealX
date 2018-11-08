LOCAL_PATH := $(call my-dir)

# clear environment
include $(CLEAR_VARS)

# indicate source files
LOCAL_SRC_FILES := turbojpeg-jni.c

# indicate include path
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../../../../prebuilt/libjpeg-turbo/include

# indicate c flags
LOCAL_CFLAGS := -g
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -march=armv7-a -mfloat-abi=softfp -fprefetch-loop-arrays
endif

# indicate ld flags
LOCAL_LDFLAGS := -L$(LOCAL_PATH)/../../../../prebuilt/libjpeg-turbo/lib32

# indicate ld libraries
LOCAL_LDLIBS += -llog -landroid -lz -lturbojpeg

# indicate local static libraries that this module depends on
#LOCAL_STATIC_LIBRARIES := turbojpeg

# indicate module name
LOCAL_MODULE := jpeg_jni

# build shared library
include $(BUILD_SHARED_LIBRARY)
