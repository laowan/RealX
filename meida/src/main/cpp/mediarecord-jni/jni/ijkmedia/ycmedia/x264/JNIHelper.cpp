//
// Created by Administrator on 2016/9/13.
//
#include "JNIHelper.h"
#include "Common.h"

std::string JNIHelper::jbyteArray2str(JNIEnv* env, jbyteArray &jarray)
{
	std::string stemp;
	jsize alen = env->GetArrayLength(jarray);
	if (alen > 0)
	{
		char* rtn = (char*) malloc(alen + 1);
		jbyte* ba = env->GetByteArrayElements(jarray, JNI_FALSE);
		memcpy(rtn, ba, alen);
		rtn[alen] = 0;
		env->ReleaseByteArrayElements(jarray, ba, 0);
		stemp.assign(rtn, alen);
		free(rtn);
	}

	return stemp;
}

std::string JNIHelper:: jstring2str(JNIEnv *env, jstring jStr) 
{
    if (!jStr)
        return "";

    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes, env->NewStringUTF("UTF-8"));

    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string ret = std::string((char *)pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

jbyteArray JNIHelper::str2jbyteArray(JNIEnv* env, const std::string &str)
{
	jbyte *by = (jbyte*)str.c_str();
	jbyteArray jarray = env->NewByteArray(str.length());  // no release
	env->SetByteArrayRegion(jarray, 0, str.length(), by);

	return jarray;
}

void JNIHelper::registerNativeMethod(JNIEnv *env, const char *className,
									 const JNINativeMethod *gMethods, int methodCnt) {

	jclass clazz = env->FindClass(className);
	if(clazz == NULL) {
		LOGD("fail to registerNative method for class %s, could not find class ", className);
		return;
	}

	int ret = env->RegisterNatives(clazz, gMethods, methodCnt);
	if(ret <0) {
		LOGD("fail to registerNative method for class %s, ret=%d ", className, ret);
	} else {
		LOGD("succeed to registerNatives for class %s", className);
	}
}

jfieldID JNIHelper::getClassFieldID(JNIEnv* env, jclass clazz, const char* fieldName, const char* signature, const char* clazzPath)
{
    jfieldID tmpId;
    tmpId = env->GetFieldID(clazz, fieldName, signature);
    if (tmpId == NULL) {
        LOGD("%s.%s", clazzPath, fieldName);
    }
    return tmpId;
}

void JNIHelper::jniThrowException(JNIEnv* env, const char* className, const char* msg) {
	jclass clazz = env->FindClass(className);
	if (!clazz) {
		LOGD("Unable to find exception class %s", className);
		/* ClassNotFoundException now pending */
		return;
	}

	if (env->ThrowNew(clazz, msg) != JNI_OK) {
		LOGD("Failed throwing '%s' '%s'", className, msg);
		/* an exception, most likely OOM, will now be pending */
	}
	env->DeleteLocalRef(clazz);
}

unsigned char *JNIHelper::newBufferFromByteArray(JNIEnv *env, jbyteArray &jarray) {
	jsize alen = env->GetArrayLength(jarray);
	if (alen > 0)
	{
		unsigned  char* rtn = (unsigned  char*) malloc(alen);
		jbyte* ba = env->GetByteArrayElements(jarray, JNI_FALSE);
		memcpy(rtn, ba, alen);
		env->ReleaseByteArrayElements(jarray, ba, 0);
		return rtn;
	}
	return NULL;
}

 unsigned char*  JNIHelper::newBufferFromByteArray(JNIEnv *env, jbyteArray &jarray, jint len, unsigned  int* memLen)
{
	jsize alen = env->GetArrayLength(jarray);
	alen = (alen > len ? len : alen);
	if (alen > 0)
	{
		unsigned  char* rtn = (unsigned  char*) malloc(alen);
		jbyte* ba = env->GetByteArrayElements(jarray, JNI_FALSE);
		memcpy(rtn, ba, alen);
		env->ReleaseByteArrayElements(jarray, ba, 0);
		*memLen = alen;
		return rtn;
	}
	return NULL;

}

void JNIHelper::freeBuffer(unsigned char *pBuffer) {
	if(pBuffer != NULL)
		free(pBuffer);
}
