#include <jni.h>
#include "x264Encoder.h"
#include "Macros.h"
#include "JNIHelper.h"
#include "Mediabase.h"
//#include "VideoEncodedListBuffer.h"
 #include "JVideoEncodedData.h"
#include <string>

NAMESPACE_YYMFW_BEGIN

static const char* const IllegalStateException = "java/lang/IllegalStateException";
const char* const kX264SoftEncoderClassPathName = "com/ycloud/mediacodec/videocodec/X264SoftEncoder";
const char* const kVideoFormatClassJNISig = "Lcom/ycloud/mediacodec/videocodec/VideoStreamFormat;";
const char* const kVideoFormatClassPathName = "com/ycloud/mediacodec/videocodec/VideoStreamFormat";

struct VideoStreamFormatFields_t {
    jfieldID    iCodec; //int
    jfieldID 	iProfile; //int
    jfieldID  	iPicFormat; //int
    jfieldID  	iWidth;  //long
    jfieldID 	iHeight; //long
    jfieldID 	iFrameRate; //int
    jfieldID 	iBitRate; //int
    jfieldID 	iEncodePreset; //long
    jfieldID    iRawCodecId;  //int
    jfieldID  	iCapturePreset; //long
    jfieldID 	iCaptureOrientation; //long
} gVFField;

struct field_t {
    jfieldID    mNativeEncoderHandle;
    jfieldID    mVideoFormat;
} gEncField;

    static CX264Encoder* getX264Encoder(JNIEnv* env, jobject thiz)
    {
        return (CX264Encoder*)env->GetLongField(thiz, gEncField.mNativeEncoderHandle);
    }

    static void X264SoftEncoder_classInit(JNIEnv* env, jclass clazz)
    {
        LOGD("X264SoftEncoder_classInit begin");
        gEncField.mNativeEncoderHandle = JNIHelper::getClassFieldID(env, clazz, "mNativeEncoderHandle", "J", kX264SoftEncoderClassPathName);
        gEncField.mVideoFormat = JNIHelper::getClassFieldID(env, clazz, "mVideoFormat", kVideoFormatClassJNISig, kX264SoftEncoderClassPathName);

        jclass fmtClazz = env->FindClass(kVideoFormatClassPathName);
        gVFField.iCodec  = JNIHelper::getClassFieldID(env, fmtClazz, "iCodec", "I", kVideoFormatClassPathName);
        gVFField.iProfile  = JNIHelper::getClassFieldID(env, fmtClazz, "iProfile", "I", kVideoFormatClassPathName);
        gVFField.iPicFormat  = JNIHelper::getClassFieldID(env, fmtClazz, "iPicFormat", "I", kVideoFormatClassPathName);
        gVFField.iWidth  = JNIHelper::getClassFieldID(env, fmtClazz, "iWidth", "J", kVideoFormatClassPathName);
        gVFField.iHeight  = JNIHelper::getClassFieldID(env, fmtClazz, "iHeight", "J", kVideoFormatClassPathName);
        gVFField.iFrameRate  = JNIHelper::getClassFieldID(env, fmtClazz, "iFrameRate", "I", kVideoFormatClassPathName);
        gVFField.iBitRate  = JNIHelper::getClassFieldID(env, fmtClazz, "iBitRate", "I", kVideoFormatClassPathName);
        gVFField.iEncodePreset  = JNIHelper::getClassFieldID(env, fmtClazz, "iEncodePreset", "J", kVideoFormatClassPathName);
        gVFField.iRawCodecId  = JNIHelper::getClassFieldID(env, fmtClazz, "iRawCodecId", "I", kVideoFormatClassPathName);
        gVFField.iCapturePreset  = JNIHelper::getClassFieldID(env, fmtClazz, "iCapturePreset", "J", kVideoFormatClassPathName);
        gVFField.iCaptureOrientation  = JNIHelper::getClassFieldID(env, fmtClazz, "iCaptureOrientation", "J", kVideoFormatClassPathName);

        LOGD("X264SoftEncoder_classInit end");
    }

    static void X264SoftEncoder_InitEncoder(JNIEnv* env, jobject thiz, jobject vstreamFromat, jbyteArray config)
    {
        LOGD("X264SoftEncoder_InitEncoder begin");
        CX264Encoder* pEncoder = getX264Encoder(env, thiz);
        MediaLibrary::VideoStreamFormat param;
        param.iCodec = (MediaLibraryVideoCodec)env->GetIntField(vstreamFromat, gVFField.iCodec);
        param.iProfile = env->GetIntField(vstreamFromat, gVFField.iProfile);
        param.iPicFormat =  (MediaLibraryPictureFormat)env->GetIntField(vstreamFromat, gVFField.iPicFormat);
        param.iWidth = env->GetLongField(vstreamFromat, gVFField.iWidth);
        param.iHeight = env->GetLongField(vstreamFromat, gVFField.iHeight);
        param.iFrameRate = env->GetIntField(vstreamFromat, gVFField.iFrameRate);
        param.iBitRate = env->GetIntField(vstreamFromat, gVFField.iBitRate);
        param.iEncodePreset = env->GetLongField(vstreamFromat, gVFField.iEncodePreset);
        param.iRawCodecId = env->GetIntField(vstreamFromat, gVFField.iRawCodecId);
        param.iCapturePreset = env->GetLongField(vstreamFromat, gVFField.iCapturePreset);
        param.iCapturePreset = env->GetLongField(vstreamFromat, gVFField.iCapturePreset);
        param.iCaptureOrientation = env->GetLongField(vstreamFromat, gVFField.iCaptureOrientation);
        param.iReserve = NULL;
        param.iReserveLen = 0;

        std::string configStr = JNIHelper::jbyteArray2str(env, config);
        pEncoder->Init(&param, configStr);

        LOGD("X264SoftEncoder_InitEncoder, icodec=%d,iProfile=%d, iPicFormat=%d, iWidth=%d, iHeight=%d, iFrameRate=%d, iBiteRate=%d, iCapturePresent=%d",
              param.iCodec, param.iProfile, param.iPicFormat, param.iWidth, param.iHeight, param.iFrameRate, param.iBitRate, param.iCapturePreset);

        LOGD("X264SoftEncoder_InitEncoder end");
    }

    static void X264SoftEncoder_DeinitEncoder(JNIEnv* env, jobject thiz)
    {
        CX264Encoder* pEncoder = getX264Encoder(env, thiz);
        if(pEncoder) {
            pEncoder->DeInit();
        }
    }


    static void X264SoftEncoder_CreateEncoder(JNIEnv* env, jobject thiz)
    {
        LOGD("X264SoftEncoder_CreateEncoder begin");

        CX264Encoder* pEncoder = new CX264Encoder();
        env->SetLongField(thiz, gEncField.mNativeEncoderHandle, (jlong)pEncoder);

        LOGD("X264SoftEncoder_CreateEncoder end");
    }

    static void X264SoftEncoder_DestroyEncoder(JNIEnv* env, jobject thiz)
    {
        CX264Encoder* pEncoder = getX264Encoder(env, thiz);
        if(pEncoder) {
            delete  pEncoder;
        }

        env->SetLongField(thiz, gEncField.mNativeEncoderHandle, (jlong)0L);
    }
 

    static void X264SoftEncoder_adjuestBitRate(JNIEnv* env, jobject thiz, jint bitRate) {
        CX264Encoder* pEncoder = getX264Encoder(env, thiz);
        if(!pEncoder) {
            LOGD("X264SoftEncoder_Process, CX264Encoder is not ready.");
        }
        pEncoder->SetTargetBitrate(bitRate);
    }

    static jobjectArray X264SoftEncoder_Process(JNIEnv* env, jobject thiz, jbyteArray inputByteArray, jint inputLen, jlong pts, jint jframeType)
    {
        CX264Encoder* pEncoder = getX264Encoder(env, thiz);
        if(!pEncoder) {
            LOGD("X264SoftEncoder_Process, CX264Encoder is not ready.");
            return NULL;
        }

        jobjectArray outputByteArray = NULL;
        MediaLibrary::VideoFrameType frameType = (MediaLibrary::VideoFrameType)jframeType;
        MediaLibrary::FrameDesc framedes(frameType, pts, 0, 0);
        //get videoEncodeList from output object.
        MediaLibrary::VideoEncodedList* videoList = NULL;

        jbyte* pInput = (jbyte*) env->GetPrimitiveArrayCritical(inputByteArray, 0);
        int ret = pEncoder->Process((uint8_t*)pInput, inputLen, &framedes, (void**)&videoList);
        if (ret != 0)
        {
            LOGD("X264SoftEncoder_Process Process failed!! ret = %d", ret);
        }
        env->ReleasePrimitiveArrayCritical(inputByteArray, pInput, 0);

        //change the ontput format for java.
        if (videoList != NULL && videoList->iPicData && videoList->iSize > 0) {
            jclass videoEncodedDataClass = JVideoEncodedData::getVideoEncodedDataClass();
            if (videoEncodedDataClass != NULL) {
                outputByteArray = env->NewObjectArray(videoList->iSize, videoEncodedDataClass, NULL);
                if (outputByteArray != NULL) {
                    for (int i = 0; i < videoList->iSize; i++) {
                        jobject jVideoEncodeData = JVideoEncodedData::newVideoEncodeDataObject(
                                env, videoList->iPicData[i]);
                        env->SetObjectArrayElement(outputByteArray, i, jVideoEncodeData);
                    }
                } else {
                    LOGD("X264SoftEncoder_Process, failed to NewObjectArray...");
                }
                //MediaLibrary::FreeBuffer(videoList.iPicData);
            } else {
                LOGD("X264SoftEncoder_Process, failed to get JEncodedData class...");
            }
        } else {
            LOGD("X264SoftEncoder_Process, CX264Encoder no output.");
            if (videoList == NULL)
            {
                LOGD("X264SoftEncoder_Process videoList is NULL");
            }
            else
            {
                if (videoList->iPicData == NULL)
                {   
                    LOGD("X264SoftEncoder_Process iPicData is NULL");
                }
                if (videoList->iSize == 0)
                {
                    LOGD("X264SoftEncoder_Process size is 0");
                }
            }
        }

        return outputByteArray;
    }

	static jobjectArray X264SoftEncoder_Flush(JNIEnv* env, jobject thiz)
	{
	   CX264Encoder* pEncoder = getX264Encoder(env, thiz);
        if(!pEncoder) {
            LOGD("X264SoftEncoder_Flush, CX264Encoder is not ready.");
            return NULL;
        }

        jobjectArray outputByteArray = NULL;
        MediaLibrary::VideoEncodedList* videoList = NULL;

    
        int ret = pEncoder->flush((void**)&videoList);
        if (ret != 0)
        {
            LOGD("X264SoftEncoder_Flush failed!! ret = %d", ret);
        }
   

        //change the ontput format for java.
        if (videoList != NULL && videoList->iPicData && videoList->iSize > 0) {
            jclass videoEncodedDataClass = JVideoEncodedData::getVideoEncodedDataClass();
            if (videoEncodedDataClass != NULL) {
                outputByteArray = env->NewObjectArray(videoList->iSize, videoEncodedDataClass, NULL);
                if (outputByteArray != NULL) {
                    for (int i = 0; i < videoList->iSize; i++) {
                        jobject jVideoEncodeData = JVideoEncodedData::newVideoEncodeDataObject(
                                env, videoList->iPicData[i]);
                        env->SetObjectArrayElement(outputByteArray, i, jVideoEncodeData);
                    }
                } else {
                    LOGD("X264SoftEncoder_Flush, failed to NewObjectArray...");
                }
                //MediaLibrary::FreeBuffer(videoList.iPicData);
            } else {
                LOGD("X264SoftEncoder_Flush, failed to get JEncodedData class...");
            }
        } else {
            LOGD("X264SoftEncoder_Flush, CX264Encoder no output.");
            if (videoList == NULL)
            {
                LOGD("X264SoftEncoder_Flush videoList is NULL");
            }
            else
            {
                if (videoList->iPicData == NULL)
                {   
                    LOGD("X264SoftEncoder_Flush iPicData is NULL");
                }
                if (videoList->iSize == 0)
                {
                    LOGD("X264SoftEncoder_Flush size is 0");
                }
            }
        }

        return outputByteArray;


	}

    static JNINativeMethod gX264SoftEncoderMethods[] = {
            {"nativeClassInit",         "()V",   (void*)X264SoftEncoder_classInit },
            {"nativeAdjuestBitRate",         "(I)V",  (void*)X264SoftEncoder_adjuestBitRate},
            {"nativeInitEncoder",       "(Lcom/ycloud/mediacodec/videocodec/VideoStreamFormat;[B)V", (void*)X264SoftEncoder_InitEncoder },
            {"nativeDeinitEncoder",     "()V",   (void*)X264SoftEncoder_DeinitEncoder },
            {"nativeCreateEncoder",     "()V", (void*)X264SoftEncoder_CreateEncoder },
            {"nativeDestroyEncoder",    "()V",   (void*)X264SoftEncoder_DestroyEncoder },
            {"nativeFlush",    			"()[Lcom/ycloud/ymrmodel/JVideoEncodedData;",   (void*)X264SoftEncoder_Flush },
            {"nativeProcess",           "([BIJI)[Lcom/ycloud/ymrmodel/JVideoEncodedData;",   (void*)X264SoftEncoder_Process },

			
    };

    extern "C"  void register_X264SoftEncoder_Jni(JNIEnv *env ) {
         LOGD("register_X264SoftEncoder_Jni  begin ");
        JNIHelper::registerNativeMethod(env, kX264SoftEncoderClassPathName, gX264SoftEncoderMethods, YYArrayLen(gX264SoftEncoderMethods));
        LOGD("register_X264SoftEncoder_Jni  end");
    }


NAMESPACE_YYMFW_END