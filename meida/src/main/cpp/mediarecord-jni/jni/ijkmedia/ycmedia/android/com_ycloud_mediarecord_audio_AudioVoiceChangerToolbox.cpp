////////////////////////////////////////////////////////////////////////////////
///
/// JNI Interface of Voice Change
/// Author: jinyongqing@yy.com
//
////////////////////////////////////////////////////////////////////////////////
#include <android/log.h>
#include "com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox.h"
#include "audio_toolbox_impl.h"
#define LOGV(...)   __android_log_print((int)ANDROID_LOG_INFO, "AUDIO_VOICE_CHANGE", __VA_ARGS__)

JNIEXPORT jlong JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_create
    (JNIEnv *env, jobject thiz, jint sample_rate, jint channels)
{
    jlong handle = (jlong)IVoiceChangerToolbox::Create(sample_rate, channels);
    LOGV("create success, handle=%ld", (long)handle);
    return handle;
}

JNIEXPORT void JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_destroy
  (JNIEnv *env, jobject thiz, jlong handle)
{
    IVoiceChangerToolbox *ptr = (IVoiceChangerToolbox *)handle;
    delete ptr;
    LOGV("destroy success, handle=%ld", (long)handle);
}

JNIEXPORT jint JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_process
  (JNIEnv *env, jobject thiz, jlong handle, jbyteArray inout_data)
{
    IVoiceChangerToolbox *ptr = (IVoiceChangerToolbox *)handle;
    jbyte* inout_data_ptr = env->GetByteArrayElements(inout_data, NULL);
    jsize size_in_byte = env->GetArrayLength(inout_data);

    int16_t nSamples = size_in_byte / 2;
    jint ret = 0;
    if(ptr != NULL)
    {
        ret = (jint)ptr->Process((short*)inout_data_ptr, &nSamples);
    }
    
    env->ReleaseByteArrayElements(inout_data, inout_data_ptr, 0);
    return ret;
}


JNIEXPORT jboolean JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_setVoiceEffectOption
  (JNIEnv *env, jobject thiz, jlong handle, jint mode)
{
    IVoiceChangerToolbox *ptr = (IVoiceChangerToolbox *)handle;
    jboolean ret = (jboolean)ptr->SetVoiceEffectOption(mode);
    LOGV("set voice change mode =%d", (int)mode);
    return ret;
}

