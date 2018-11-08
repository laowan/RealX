#include <jni.h>
#include "x264/Macros.h"
#include "x264/JNIHelper.h"
#include "x264/Mediabase.h"
#include "x264/Common.h"
#include <string>
#include "FfmMuxer.h"
#include "FfmMediaFormat.h"

NAMESPACE_YYMFW_BEGIN

static const char* const IllegalStateException = "java/lang/IllegalStateException";
const char* const kFfmMediaMuxerClassPathName = "com/ycloud/mediacodec/engine/FfmMediaMuxer";

jfieldID    gMuxerHandleFieldID = NULL;


static void mediaMuxer_nativeClassInit(JNIEnv* env, jclass cls)
{
    LOGD("mediaMuxer_nativeClassInit begin");
    gMuxerHandleFieldID = JNIHelper::getClassFieldID(env, cls, "mMuxHandle", "J", kFfmMediaMuxerClassPathName);
    LOGD("mediaMuxer_nativeClassInit end");
}

 static FfmMuxer* getFfmMuxer(JNIEnv* env, jobject thiz)
 {
     return (FfmMuxer*)env->GetLongField(thiz, gMuxerHandleFieldID);
 }

static void mediaMuxer_nativeInitMuxer(JNIEnv *env, jobject thiz, jbyteArray filename)
{
    LOGD("mediaMuxer_nativeInitMuxer begin");
    std::string sfilename = JNIHelper::jbyteArray2str(env, filename);
    FfmMuxer* muxer = new FfmMuxer(sfilename);

    env->SetLongField(thiz, gMuxerHandleFieldID, (jlong)muxer);

    LOGD("mediaMuxer_nativeInitMuxer end");
}

static void mediaMuxer_nativeStart(JNIEnv *env, jobject thiz)
{
    FfmMuxer* muxer = getFfmMuxer(env, thiz);
    if(muxer == NULL) {
        return;
    }
    muxer->start();
}

static void mediaMuxer_nativeStop(JNIEnv *env, jobject thiz)
{
    FfmMuxer* muxer = getFfmMuxer(env, thiz);
    if(muxer == NULL) {
        return;
    }
    muxer->stop();
}

static int mediaMuxer_nativeAddStream(JNIEnv *env, jobject thiz, jbyteArray mediaformat)
{
    FfmMuxer* muxer = getFfmMuxer(env, thiz);
    if(muxer == NULL) {
        return -1;
    }
    std::string mediaformatStr = JNIHelper::jbyteArray2str(env, mediaformat);
    return muxer->addStream(mediaformatStr);
}

static void mediaMuxer_nativeSetMeta(JNIEnv *env, jobject thiz, jstring meta)
{
    FfmMuxer* muxer = getFfmMuxer(env, thiz);
    if(muxer == NULL) {
        return;
    }

    std::string metaStr = JNIHelper::jstring2str(env, meta);
    muxer->setMeta(metaStr);
}

static void mediaMuxer_nativeRelease(JNIEnv* env, jobject thiz)
{
    LOGD("mediaMuxer_nativeRelease begin");
    FfmMuxer* muxer = getFfmMuxer(env, thiz);
    if(muxer == NULL) {
        return;
    }

    muxer->stop();
    env->SetLongField(thiz, gMuxerHandleFieldID, (jlong)0);

    //TODO release all the nothings.
    delete muxer;
     LOGD("mediaMuxer_nativeRelease end");
}

static void mediaMuxer_nativeWriteSampleData(JNIEnv *env, jobject thiz, jint streamID, jobject byteBuf, jint offset, jint size, jint keyFlag, jlong pts, jlong dts)
{

    //TODO. remvoe this log
    //LOGD("mediaMuxer_nativeWriteSampleData begin, streamId");
    FfmMuxer* muxer = getFfmMuxer(env, thiz);
    if(muxer == NULL) {
        return;
    }

	bool directMode = false;

	void *data = env->GetDirectBufferAddress(byteBuf);
	
	   //size_t dataSize= 0;
	   jbyteArray byteArray = NULL;
	
	   jclass byteBufClass = env->FindClass("java/nio/ByteBuffer");
	   if(byteBufClass == NULL) {
			LOGE("writeSampleData error, class ByteBuffer is not found!!");
			return;
	   }
	
	   if (data == NULL) {
	   	//not directbuffer
		   jmethodID arrayID =env->GetMethodID(byteBufClass, "array", "()[B");
			if(arrayID == NULL) {
				LOGE("writeSampleData error, array method is not found for nondirect bytebuffer!!");
				if(byteBufClass != NULL) {
					env->DeleteLocalRef(byteBufClass);
				}
				return;
			}
	
		   byteArray =(jbyteArray)env->CallObjectMethod(byteBuf, arrayID);
		   if (byteArray == NULL) {
		   		if(byteBufClass != NULL) {
					env->DeleteLocalRef(byteBufClass);
				}
			   return;
		   }

		   data = env->GetPrimitiveArrayCritical(byteArray, 0);
		   //dataSize = (size_t) env->GetArrayLength(byteArray);
	   } else {
		   //dataSize = (size_t) env->GetDirectBufferCapacity(byteBuf);
		   directMode = true;
	   }

	    muxer->writeSampleData(streamID, (const char*)data+offset, size,  (keyFlag != 0), pts, dts);

		if(!directMode) {
			 env->ReleasePrimitiveArrayCritical(byteArray, data, 0);
		}

	
	if(byteBufClass != NULL) {
		env->DeleteLocalRef(byteBufClass);
	}


	//LOGD("mediaMuxer_nativeWriteSampleData End, streamId");
}


static JNINativeMethod gFfmMediaMuxerMethods[] = {
        {"nativeClassInit",  "()V", (void*)mediaMuxer_nativeClassInit},
        {"nativeInitMuxer",         "([B)V",   (void*)mediaMuxer_nativeInitMuxer },
        {"nativeStart",         "()V",  (void*)mediaMuxer_nativeStart},
        {"nativeStop",       "()V", (void*)mediaMuxer_nativeStop },
        {"nativeAddStream",     "([B)I",   (void*)mediaMuxer_nativeAddStream },
        {"nativeSetMeta",     "(Ljava/lang/String;)V",   (void*)mediaMuxer_nativeSetMeta },
        {"nativeWriteSampleData",     "(ILjava/nio/ByteBuffer;IIIJJ)V", (void*)mediaMuxer_nativeWriteSampleData },
        {"nativeRelease", "()V", (void*)mediaMuxer_nativeRelease},
      
};

extern "C"  void register_FfmMediaMuxer_Jni(JNIEnv *env ) {
     LOGD("register_FfmMediaMuxer_Jni  begin ");
    JNIHelper::registerNativeMethod(env, kFfmMediaMuxerClassPathName, gFfmMediaMuxerMethods, YYArrayLen(gFfmMediaMuxerMethods));
    LOGD("register_FfmMediaMuxer_Jni  end");
}


NAMESPACE_YYMFW_END
