////////////////////////////////////////////////////////////////////////////////
///
//
////////////////////////////////////////////////////////////////////////////////
#include <android/log.h>
#include "com_ycloud_audio_AudioPlaybackRateProcessor.h"
#include "SoundTouch.h"
#include "kiss_fft.h"
#include "WeightedWindow.h"
#define LOGV(...)   __android_log_print((int)ANDROID_LOG_INFO, "AudioPlaybackRateProcessor", __VA_ARGS__)

static void short2float(short* pIn, float* pOut, int len) {
    double conv = 1.0 / 32768.0;
    for (int i = 0; i < len; i++) {
        pOut[i] = pIn[i] * conv;
    }
}

inline int saturate(float fvalue, float minval, float maxval)
{
    if (fvalue > maxval)
    {
        fvalue = maxval;
    }
    else if (fvalue < minval)
    {
        fvalue = minval;
    }
    return (int)fvalue;
}

static void float2short(float* pIn, short* pOut, int len) {
    for (int i = 0; i < len; i++) {
        pOut[i] = (short)saturate(pIn[i] * 32768.0f, -32768.0f, 32767.0f);
    }
}

class CAudioPlaybackRateProcessor
{
public:
	CAudioPlaybackRateProcessor() 
	: mpSoundTouch(new soundtouch::SoundTouch())
	, mpSoundTouchBuffer(NULL)
	, mBufferLen(0)
	, mSampleRate(0)
	, mChannels(0)
	{
	}

	~CAudioPlaybackRateProcessor() {
		if(mpSoundTouch != NULL) {
			delete mpSoundTouch;
		}
		if(mpSoundTouchBuffer != NULL) {
			delete[] mpSoundTouchBuffer;
		}
	}

	void init(int sampleRate, int channel, bool voice) {
		mpSoundTouch->setSampleRate(sampleRate);
		mpSoundTouch->setChannels(channel);
		mpSoundTouch->setTempoChange(0);
		mpSoundTouch->setPitchSemiTones(0);
		mpSoundTouch->setRateChange(0);
		mpSoundTouch->setSetting(SETTING_USE_AA_FILTER, 1);
		mpSoundTouch->setSetting(SETTING_USE_QUICKSEEK, 0);
		if(voice) {
			mpSoundTouch->setSetting(SETTING_SEQUENCE_MS, 40);
			mpSoundTouch->setSetting(SETTING_SEEKWINDOW_MS, 15);
			mpSoundTouch->setSetting(SETTING_OVERLAP_MS, 8);
		}
        mSampleRate = sampleRate;
        mChannels = channel;
	}

	void push(short* pData, int len) {
	    if(mSampleRate == 0 || mChannels == 0) {
	        return ;
	    }
		if(len > mBufferLen) {
			if(mpSoundTouchBuffer != NULL) {
				delete[] mpSoundTouchBuffer;
			}
			mpSoundTouchBuffer = new soundtouch::SAMPLETYPE[len];
		   mBufferLen = len;	
		}
		short2float(pData, mpSoundTouchBuffer, len);
		mpSoundTouch->putSamples((const soundtouch::SAMPLETYPE*)mpSoundTouchBuffer, len / mChannels);
	}


	int pull(short* pData, int len) {
	    if(mSampleRate == 0 || mChannels == 0) {
	        return 0;
	    }
	    if(len > mBufferLen) {
			if(mpSoundTouchBuffer != NULL) {
				delete[] mpSoundTouchBuffer;
			}
			mpSoundTouchBuffer = new soundtouch::SAMPLETYPE[len];
		   mBufferLen = len;
		}
		int outSamples = mpSoundTouch->receiveSamples((soundtouch::SAMPLETYPE*)mpSoundTouchBuffer, len / mChannels);
		if (outSamples > 0) {
			float2short(mpSoundTouchBuffer, pData, outSamples * mChannels);
		}
		return outSamples * mChannels;
	}

	void setRate(float rate) {
		mpSoundTouch->setTempo(rate);
	}

	void flush() {
		mpSoundTouch->flush2();
	}

    void clear() {
		mpSoundTouch->clear();
    }

    int numOfBytesAvailable() {
        long samples = mpSoundTouch->numSamples();
        return samples * mChannels;
    }

    int numOfMSAvailable() {
        if(mSampleRate != 0) {
            long samples = mpSoundTouch->numSamples();
            long ms = samples * 1000 / mSampleRate;
            return  ms;
        }else {
            return 0;
        }
    }

    int numOfBytesUnprocess() {
        long samples = mpSoundTouch->numUnprocessedSamples();
        return samples * mChannels;
    }

    int numOfMSUnprocess() {
        if(mSampleRate != 0) {
            long samples = mpSoundTouch->numUnprocessedSamples();
            long ms = samples * 1000 / mSampleRate;
            return ms;
        }else {
            return 0;
        }
    }

private:
	soundtouch::SoundTouch* mpSoundTouch;
	soundtouch::SAMPLETYPE* mpSoundTouchBuffer;
	int mBufferLen;
	int mSampleRate;
	int mChannels;
};

JNIEXPORT jlong JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_create
    (JNIEnv *env, jobject thiz, jint src_sample_rate, jint src_channels, jboolean voice)
{
	CAudioPlaybackRateProcessor* handle = new CAudioPlaybackRateProcessor();
	handle->init(src_sample_rate, src_channels, voice);
    LOGV("create success, handle=%ld", (long)handle);
    return (long)handle;
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_destroy
  (JNIEnv *env, jobject thiz, jlong handle)
{
    CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
    delete ptr;
    LOGV("destroy success, handle=%ld", (long)handle);
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_push
  (JNIEnv *env, jobject thiz, jlong handle, jbyteArray in_data, jint len) {
    CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
    jbyte* in_data_ptr = env->GetByteArrayElements(in_data, NULL);

	ptr->push((short*)in_data_ptr, len / 2);

    env->ReleaseByteArrayElements(in_data, in_data_ptr, 0);
}

JNIEXPORT jint JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_pull
  (JNIEnv *env, jobject thiz, jlong handle, jbyteArray out_data, jint offset, jint in_len)
{
    CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
    jbyte* out_data_ptr = env->GetByteArrayElements(out_data, NULL);
	short* pOut = (short*)(out_data_ptr + offset);

	int outLen = 0;
    if(ptr != NULL)
    {
        outLen = ptr->pull(pOut, in_len / 2);
    }
    
    env->ReleaseByteArrayElements(out_data, out_data_ptr, 0);
    return outLen * 2;
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_setRate
  (JNIEnv *env, jobject thiz, jlong handle, jfloat rate) {
    CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		ptr->setRate(rate);
	}
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_flush
  (JNIEnv *env, jobject thiz, jlong handle) {
  CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		ptr->flush();
	}
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_clear
  (JNIEnv *env, jobject thiz, jlong handle) {
  CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		ptr->clear();
	}
}

JNIEXPORT int JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_numOfMSAvailable
  (JNIEnv *env, jobject thiz, jlong handle) {
  CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		return ptr->numOfMSAvailable();
	}
	return 0;
}

JNIEXPORT int JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_numOfMSUnprocess
  (JNIEnv *env, jobject thiz, jlong handle) {
  CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		return ptr->numOfMSUnprocess();
	}
	return 0;
}

JNIEXPORT int JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_numOfBytesAvailable
  (JNIEnv *env, jobject thiz, jlong handle) {
  CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		return ptr->numOfBytesAvailable();
	}
	return 0;
}

JNIEXPORT int JNICALL Java_com_ycloud_audio_AudioPlaybackRateProcessor_numOfBytesUnprocess
  (JNIEnv *env, jobject thiz, jlong handle) {
  CAudioPlaybackRateProcessor *ptr = (CAudioPlaybackRateProcessor *)handle;
	if(ptr != NULL) {
		return ptr->numOfBytesUnprocess();
	}
	return 0;
}

class FFTProcessor {
public:
    FFTProcessor(int fftLen) {
        mFFT = NULL;
        mTimeData = NULL;
        mFrequencyData = NULL;
        mFrequencyValueData = NULL;
        mTimeDataLen = 0;
        mFFTLen = fftLen;
        mHanningWindow = NULL;
    }

    ~FFTProcessor() {
        if(mFFT != NULL) {
            kiss_fft_free(mFFT);
            delete[] mTimeData;
            delete[] mFrequencyData;
            delete [] mFrequencyValueData;
            delete mHanningWindow;
        }
    }

    void process(short* samples, int len, int stride) {
        if(mFFT == NULL) {
            mFFT = kiss_fft_alloc(mFFTLen, 0, NULL, NULL);
            mTimeData = new kiss_fft_cpx[mFFTLen];
            mFrequencyData = new kiss_fft_cpx[mFFTLen];
            mFrequencyValueData = new float[mFFTLen];
            memset(mFrequencyValueData, 0, sizeof(float) * mFFTLen);
            mHanningWindow = new CHanningWindow(mFFTLen);
        }
        int i = 0;
        for (; i < len; ) {
            mTimeData[mTimeDataLen].r = (samples[i] / 32768.0f);
            mTimeData[mTimeDataLen].i = 0.0;
            mTimeDataLen++;
            i += stride;
            if ( mTimeDataLen == mFFTLen) {
                for (int j = 0; j < mFFTLen; j++) {
                    mTimeData[j].r = mHanningWindow->ProcessSample(mTimeData[j].r, j);
                }
                kiss_fft(mFFT, mTimeData, mFrequencyData);
                for (int j = 0; j < mFFTLen / 2; j++) {
                    float a = powf(mFrequencyData[j].i / mFFTLen, 2) ;
                    float b = powf(mFrequencyData[j].r / mFFTLen, 2) ;
                    mFrequencyValueData[j] = (log10(a+b) * 10 + 100) / 100;
                    if(mFrequencyValueData[j] < 0.0) {
                        mFrequencyValueData[j] = 0.0;
                    }
                }
                mTimeDataLen = 0;
            }
        }
    }

    void flush() {
        mTimeDataLen = 0;
        if(mFrequencyValueData != NULL) {
            memset(mFrequencyValueData, 0, sizeof(float) * mFFTLen);
        }
    }

    int frequencyData(float* buffer, int len) {
        if(mFrequencyValueData != NULL) {
            int retLen = len > mFFTLen ? mFFTLen : len;
            memcpy(buffer, mFrequencyValueData, retLen * sizeof(float));
            return retLen;
        }
        return 0;
    }

private:
    kiss_fft_cfg mFFT;
    int mFFTLen;
    kiss_fft_cpx* mTimeData;
    int mTimeDataLen;
    kiss_fft_cpx* mFrequencyData;
    float* mFrequencyValueData;
    CHanningWindow* mHanningWindow;  
};

JNIEXPORT jlong JNICALL Java_com_ycloud_audio_FFTProcessor_create
  (JNIEnv *env, jobject thiz, jint fftLen) {
       FFTProcessor* processor = new FFTProcessor(fftLen);
       return (jlong)processor;
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_FFTProcessor_destroy
  (JNIEnv *env, jobject thiz, jlong pointer) {
       FFTProcessor* processor = (FFTProcessor*)pointer;
       if(processor != NULL) {
            delete processor;
       }
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_FFTProcessor_process
  (JNIEnv *env, jobject thiz, jlong pointer, jbyteArray in_data, jint offset, jint len, jint stride) {
       FFTProcessor* processor = (FFTProcessor*)pointer;
       if(processor != NULL) {
            jbyte* in_data_ptr = env->GetByteArrayElements(in_data, NULL);
            processor->process((short*)(in_data_ptr + offset), len / 2, stride);
            env->ReleaseByteArrayElements(in_data, in_data_ptr, 0);
       }
}

JNIEXPORT void JNICALL Java_com_ycloud_audio_FFTProcessor_flush
  (JNIEnv *env, jobject thiz, jlong pointer) {
       FFTProcessor* processor = (FFTProcessor*)pointer;
       if(processor != NULL) {
            processor->flush();
       }
}

JNIEXPORT jint JNICALL Java_com_ycloud_audio_FFTProcessor_frequencyData
  (JNIEnv *env, jobject thiz, jlong pointer, jfloatArray in_data, jint len) {
  FFTProcessor* processor = (FFTProcessor*)pointer;
        int retLen = 0;
       if(processor != NULL) {
            jfloat* in_data_ptr = env->GetFloatArrayElements(in_data, NULL);
            retLen = processor->frequencyData(in_data_ptr, len);
            env->ReleaseFloatArrayElements(in_data, in_data_ptr, 0);
       }
       return retLen;
}

static JNINativeMethod gFFTProcessorNativeMethods[] = {
        {"create",                  "(I)J", (jlong *) Java_com_ycloud_audio_FFTProcessor_create},
        {"destroy",                  "(J)V", (void *) Java_com_ycloud_audio_FFTProcessor_destroy},
        {"process",                  "(J[BIII)V", ( void *) Java_com_ycloud_audio_FFTProcessor_process},
        {"flush",                  "(J)V", ( void *) Java_com_ycloud_audio_FFTProcessor_flush},
        {"frequencyData",                  "(J[FI)I", ( void *) Java_com_ycloud_audio_FFTProcessor_frequencyData},
};

void initAudio(JNIEnv* env){
    LOGV("initAudio");
    jclass claxx = (env)->FindClass("com/ycloud/audio/FFTProcessor");
    (env)->RegisterNatives(claxx, gFFTProcessorNativeMethods, (sizeof(gFFTProcessorNativeMethods) / sizeof(gFFTProcessorNativeMethods[0])));
}
