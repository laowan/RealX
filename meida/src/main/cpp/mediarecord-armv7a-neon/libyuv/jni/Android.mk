LOCAL_PATH:= $(call my-dir)

PRO_PATH:=${LOCAL_PATH}/../../..

# Include makefiles here.
MY_ARCH_FLAG := neon
include $(PRO_PATH)/mediarecord-jni/jni/Android.mk

# Include the Android Maven plugin generated makefile
# Important: Must be the last import in order for Android Maven Plugins paths to work
include $(ANDROID_MAVEN_PLUGIN_MAKEFILE)

