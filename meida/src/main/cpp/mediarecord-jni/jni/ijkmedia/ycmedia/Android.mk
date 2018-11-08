LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
# -mfloat-abi=soft is a workaround for FP register corruption on Exynos 4210
# http://www.spinics.net/lists/arm-kernel/msg368417.html
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mfloat-abi=soft
endif
LOCAL_CFLAGS += -std=c99
LOCAL_CFLAGS += -DCC_ANDROID
LOCAL_LDLIBS += -llog -landroid -lz -lGLESv2 -lEGL

LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_C_INCLUDES += $(MY_APP_FFMPEG_INCLUDE_PATH)
LOCAL_C_INCLUDES += $(MY_APP_LIBYUV_INCLUDE_PATH)
LOCAL_C_INCLUDES += $(MY_APP_PREBUILD_INCLUDE_PATH)
LOCAL_C_INCLUDES += $(MY_APP_AUDIOENGINE_INCLUDE_PATH)
LOCAL_C_INCLUDES += $(MY_APP_AUDIOENGINE_INCLUDE_PATH)/SoundTouch

LOCAL_C_INCLUDES += $(LOCAL_PATH)/android
LOCAL_C_INCLUDES += $(LOCAL_PATH)/speexdsp
LOCAL_C_INCLUDES += $(LOCAL_PATH)/speexdsp/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/audio_agc
LOCAL_C_INCLUDES += $(LOCAL_PATH)/audio_agc/AGC/include
LOCAL_C_INCLUDES += $(realpath $(LOCAL_PATH)/../ijkyuv/include)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/android/record
LOCAL_C_INCLUDES += $(LOCAL_PATH)/android/x264
LOCAL_C_INCLUDES += $(LOCAL_PATH)/android/kissfft

LOCAL_SRC_FILES += android/com_ycloud_mediarecord_MediaNative.c
LOCAL_SRC_FILES += android/com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox.cpp
LOCAL_SRC_FILES += android/com_ycloud_audio_AudioConverter.cpp
LOCAL_SRC_FILES += android/com_ycloud_audio_AudioPlaybackRateProcessor.cpp
LOCAL_SRC_FILES += android/FFmpegAacEncoder.cpp
LOCAL_SRC_FILES += android/kissfft/kiss_fft.c
LOCAL_SRC_FILES += android/gpufilter.c
LOCAL_SRC_FILES += android/GLESNativeTools.c
LOCAL_SRC_FILES += common.c
LOCAL_SRC_FILES += yc_ffprobe.c
LOCAL_SRC_FILES += yc_ffmpeg.c
LOCAL_SRC_FILES += yc_ffmpeg_filter.c
LOCAL_SRC_FILES += yc_ffmpeg_opt.c
LOCAL_SRC_FILES += ffmpeg_h264_muxer_mp4.c
LOCAL_SRC_FILES += cmdutils.c
LOCAL_SRC_FILES += android/WeightedWindow.cpp

LOCAL_SRC_FILES += x264/Buffer.cpp
LOCAL_SRC_FILES += x264/CommonUtils.cpp
LOCAL_SRC_FILES += x264/ImageUtil.cpp
LOCAL_SRC_FILES += x264/JNIContext.cpp
LOCAL_SRC_FILES += x264/JNIHelper.cpp
LOCAL_SRC_FILES += x264/x264Encoder.cpp 
LOCAL_SRC_FILES += x264/x264softencoder.cpp
LOCAL_SRC_FILES += x264/DumpUtil.cpp
#LOCAL_SRC_FILES += x264/ffmpegextract.cpp
LOCAL_SRC_FILES += x264/JVideoEncodedData.cpp
#LOCAL_SRC_FILES += x264/VideoPackUtil.cpp
LOCAL_SRC_FILES += x264/AdaptivePicBuffer.cpp
#LOCAL_SRC_FILES += x264/GLESNativeTool.cpp.cpp

LOCAL_SRC_FILES += muxer/FfmMediaMuxerNative.cpp
LOCAL_SRC_FILES += muxer/FfmMuxer.cpp
LOCAL_SRC_FILES += muxer/FfmMediaFormat.cpp

LOCAL_SRC_FILES += decoder/JniLog.cpp
LOCAL_SRC_FILES += decoder/NativeFfmpeg.cpp
LOCAL_SRC_FILES += decoder/FFmpegDemuxDecode.cpp
LOCAL_SRC_FILES += decoder/FFmpegDemuxDecodeJNI.cpp
LOCAL_SRC_FILES += decoder/FFmpegAudioFileReader.cpp

LOCAL_SHARED_LIBRARIES := $(FFMPEG_MODULE_NAME) ycmediayuv $(AUDIOENGINE_MODULE_NAME)
LOCAL_MODULE := ycmedia
include $(BUILD_SHARED_LIBRARY)

