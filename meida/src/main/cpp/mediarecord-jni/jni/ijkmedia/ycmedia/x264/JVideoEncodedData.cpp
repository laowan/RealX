//
// Created by kele on 2016/9/28.
//

#include "JVideoEncodedData.h"
#include <jni.h>
#include "x264Encoder.h"
#include "Macros.h"
#include "JNIHelper.h"
#include "Mediabase.h"
#include <string>


static const char* const IllegalStateException = "java/lang/IllegalStateException";
const char* const kVideoEncodedDataClassNamePath = "com/ycloud/ymrmodel/JVideoEncodedData";

struct field_t {
    jfieldID    mFrameType; //int
    jfieldID    mPts;    //long
    jfieldID    mDts;   //long
    jfieldID    mDataLen; //long
    jfieldID    mWidth;
    jfieldID    mHeight;
    jfieldID    mByteBuffer; //bytebuffer.
} gVideoEncodedDataField;

static jclass       gEncodedDataClass = NULL;
//static jmethodID    gVideoEncodeDataDefaultConstructor = NULL;

static void VideoEncodeData_classInit(JNIEnv* env, jclass clazz)
{
    LOGD("VideoEncodeData_classInit begin");
    gEncodedDataClass = (jclass)env->NewGlobalRef(clazz);

    gVideoEncodedDataField.mFrameType = JNIHelper::getClassFieldID(env, clazz, "mFrameType", "I", kVideoEncodedDataClassNamePath);
    gVideoEncodedDataField.mPts = JNIHelper::getClassFieldID(env, clazz, "mPts", "J", kVideoEncodedDataClassNamePath);
    gVideoEncodedDataField.mDts = JNIHelper::getClassFieldID(env, clazz, "mDts", "J", kVideoEncodedDataClassNamePath);
    gVideoEncodedDataField.mDataLen = JNIHelper::getClassFieldID(env, clazz, "mDataLen", "J", kVideoEncodedDataClassNamePath);
    gVideoEncodedDataField.mWidth = JNIHelper::getClassFieldID(env, clazz, "mWidth", "I", kVideoEncodedDataClassNamePath);
    gVideoEncodedDataField.mHeight = JNIHelper::getClassFieldID(env, clazz, "mHeight", "I", kVideoEncodedDataClassNamePath);
    gVideoEncodedDataField.mByteBuffer = JNIHelper::getClassFieldID(env, clazz, "mByteBuffer", "Ljava/nio/ByteBuffer;", kVideoEncodedDataClassNamePath);

    /*
    gVideoEncodeDataDefaultConstructor = env->GetMethodID(clazz, "<init>", "void (V)");
    if(gVideoEncodeDataDefaultConstructor == NULL) {
        LOGDXXX("VideoEncodeData_classInit, failed to get %s.construtor method", kVideoEncodedDataClassNamePath);
    }
     */
    LOGD("VideoEncodeData_classInit end");
}

static void VideoEncodeData_relaseVideoByteBuffer(JNIEnv* env, jobject thiz)
{
   //TODO. memory pool.
    //do nothing now...
}


static JNINativeMethod gVideoEncodedDataMethods[] = {
        {"nativeClassInit",         "()V",   (void*)VideoEncodeData_classInit },
         {"nativeRelaseVideoByteBuffer",         "()V",   (void*)VideoEncodeData_relaseVideoByteBuffer },

};

extern "C"  void register_VideoEncodedData_Jni(JNIEnv *env ) {
    JNIHelper::registerNativeMethod(env, kVideoEncodedDataClassNamePath, gVideoEncodedDataMethods, YYArrayLen(gVideoEncodedDataMethods));
}

extern "C"  void unregister_VideoEncodedData_Jni(JNIEnv *env ) {
    if(gEncodedDataClass != NULL) {
        env->DeleteGlobalRef(gEncodedDataClass);
        gEncodedDataClass = NULL;
    }
}

jclass JVideoEncodedData::getVideoEncodedDataClass() {
    return gEncodedDataClass;
}

jobject JVideoEncodedData::newVideoEncodeDataObject( JNIEnv* env,
                                                            MediaLibrary::VideoEncodedData& cVideoData) {
    if(!env)
        return NULL;

    jobject jobj = env->AllocObject(gEncodedDataClass);
    if(jobj != NULL) {
        //TODO. remove this debug info.
        //LOGD("==== VideoEncodedBufferHelper::newVideoEncodeDataObject, ftype=%d, pts=%u, dts=%u, dataLen=%u",
            //cVideoData.iFrameType, cVideoData.iPts, cVideoData.iDts, cVideoData.iDataLen);
        env->SetIntField(jobj, gVideoEncodedDataField.mFrameType, cVideoData.iFrameType);
        env->SetLongField(jobj, gVideoEncodedDataField.mPts, cVideoData.iPts);
        env->SetLongField(jobj, gVideoEncodedDataField.mDts, cVideoData.iDts);
        env->SetLongField(jobj, gVideoEncodedDataField.mDataLen, cVideoData.iDataLen);
        jobject jByteBuffer = env->NewDirectByteBuffer(cVideoData.iData, cVideoData.iDataLen);
        env->SetIntField(jobj, gVideoEncodedDataField.mWidth, cVideoData.iPicWidth);
        env->SetIntField(jobj, gVideoEncodedDataField.mHeight, cVideoData.iPicHeight);
        env->SetObjectField(jobj, gVideoEncodedDataField.mByteBuffer, jByteBuffer);
    } else {
        LOGD(" VideoEncodedBufferHelper::newVideoEncodeDataObject failed!!!");
    }
    return jobj;
}
