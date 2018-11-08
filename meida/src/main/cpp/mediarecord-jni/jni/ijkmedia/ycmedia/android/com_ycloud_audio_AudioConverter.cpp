////////////////////////////////////////////////////////////////////////////////
///
//
////////////////////////////////////////////////////////////////////////////////
#include <android/log.h>
#include "com_ycloud_audio_AudioConverter.h"
#include "audio_toolbox_impl.h"
#define LOGV(...)   __android_log_print((int)ANDROID_LOG_INFO, "AUDIO_CONVERTER", __VA_ARGS__)

JNIEXPORT jlong JNICALL Java_com_ycloud_audio_AudioConverter_create
    (JNIEnv *env, jobject thiz, jint src_sample_rate, jint src_channels, jint dst_sample_rate, jint dst_channels)
{
	static int kFRAME_SIZE_MS = 10;
    jlong handle = (jlong)IAudioResamplerEx::Create((uint32_t)src_sample_rate * kFRAME_SIZE_MS / 1000, src_sample_rate, (uint32_t)src_channels, (uint32_t)dst_sample_rate * kFRAME_SIZE_MS / 1000, (uint32_t)dst_sample_rate, (uint32_t)dst_channels);
    LOGV("create success, handle=%ld", (long)handle);
    return handle;
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioConverter_destory
  (JNIEnv *env, jobject thiz, jlong handle)
{
    IAudioResamplerEx *ptr = (IAudioResamplerEx *)handle;
    delete ptr;
    LOGV("destroy success, handle=%ld", (long)handle);
}

JNIEXPORT jint JNICALL Java_com_ycloud_audio_AudioConverter_process
  (JNIEnv *env, jobject thiz, jlong handle, jbyteArray in_data, jint in_len, jbyteArray out_data, jint out_len)
{
    IAudioResamplerEx *ptr = (IAudioResamplerEx *)handle;
    jbyte* in_data_ptr = env->GetByteArrayElements(in_data, NULL);
    jbyte* out_data_ptr = env->GetByteArrayElements(out_data, NULL);
	int frameSize = ptr->GetSrcSamples() * 2;
	int dstframeSize = ptr->GetDestSamples() * 2;

    jint ret = 0;
    if(ptr != NULL)
    {
		jbyte* src_ptr = in_data_ptr;
		jbyte* dst_ptr = out_data_ptr;
		while(in_len >= frameSize) {
			ptr->Convert((short*)src_ptr, frameSize/2, (short*)dst_ptr, dstframeSize/2);
			ret += dstframeSize;
			in_len -= frameSize;
			src_ptr += frameSize;
			dst_ptr += dstframeSize;
		}
    }
    
    env->ReleaseByteArrayElements(in_data, in_data_ptr, 0);
    env->ReleaseByteArrayElements(out_data, out_data_ptr, 0);
    return ret;
}
