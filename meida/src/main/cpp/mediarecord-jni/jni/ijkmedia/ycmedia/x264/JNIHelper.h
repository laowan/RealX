//
// Created by Administrator on 2016/9/13.
//

#ifndef TRUNK_JNIHELPER_H
#define TRUNK_JNIHELPER_H

#include <jni.h>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

class JNIHelper {
public:
	static std::string jbyteArray2str(JNIEnv *env, jbyteArray &jstr);
    static std::string jstring2str(JNIEnv *env, jstring jStr);
	static jbyteArray str2jbyteArray(JNIEnv *env, const std::string &str);
	static void registerNativeMethod(JNIEnv* env, const char* className, const JNINativeMethod* gMethods, int methodCnt);
	static jfieldID getClassFieldID(JNIEnv* env, jclass clazz, const char* fieldName, const char* signature, const char* clazzPath);
	static void jniThrowException(JNIEnv* env, const char* className, const char* msg);
	static unsigned char*  newBufferFromByteArray(JNIEnv *env, jbyteArray &jstr);
	static unsigned char*  newBufferFromByteArray(JNIEnv *env, jbyteArray &jstr, jint len, unsigned  int* memLen);
	static void freeBuffer(unsigned char* pBuffer);




};

#ifdef __cplusplus
}
#endif


#endif //TRUNK_JNIHELPER_H
