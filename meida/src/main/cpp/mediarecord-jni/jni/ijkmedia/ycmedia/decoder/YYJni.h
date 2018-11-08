/*!
    \file YYJni.h
*/
#ifndef _YY_JNI_H_
#define _YY_JNI_H_

#include <jni.h>
#include <android/log.h>
#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

void yylog_init(JavaVM* jvm, const char* path);
void yylog_deinit(void);
void yylog_set(int level, const char* module);
void yylog(const char* func, int line, int level, const char* tag, const char* fmt, ...);

#ifdef __cplusplus
}
#endif

#ifndef YY_LOGTAG
#define YY_LOGTAG "yysdk"
#endif

//#define YYLOGV(...) yylog(__func__, __LINE__, ANDROID_LOG_VERBOSE, YY_LOGTAG, __VA_ARGS__)
//#define YYLOGD(...) yylog(__func__, __LINE__, ANDROID_LOG_DEBUG, YY_LOGTAG, __VA_ARGS__)
//#define YYLOGI(...) yylog(__func__, __LINE__, ANDROID_LOG_INFO, YY_LOGTAG, __VA_ARGS__)
//#define YYLOGW(...) yylog(__func__, __LINE__, ANDROID_LOG_WARN, YY_LOGTAG, __VA_ARGS__)
//#define YYLOGE(...) yylog(__func__, __LINE__, ANDROID_LOG_ERROR, YY_LOGTAG, __VA_ARGS__)
//#define YYLOG_LINE() YYLOGD("line = %d", __LINE__)
#define YYLOGD ALOGD
#define YYLOGI ALOGI
#define YYLOGW ALOGW
#define YYLOGE ALOGE

#define SAFE_FREE(p)  if(p){free(p);p=NULL;}
#define DELETE_LOCAL_REF(env, obj)  if(obj!=NULL){env->DeleteLocalRef(obj);obj=NULL;}
#define DELETE_GLOBAL_REF(env, obj) if(obj!=NULL){env->DeleteGlobalRef(obj);obj=NULL;}
#define DELETE_WEAK_GLOBAL_REF(env, obj) if(obj!=NULL){env->DeleteWeakGlobalRef(obj);obj=NULL;}

#define JNI_FIELDID(name, field) fieldID_##name_##field
#define JNI_METHODID(name, method) methodID_##name_##method
#define JNI_DEFINE_FIELDID(name, field) static jfieldID JNI_FIELDID(name, field) = NULL;
#define JNI_DEFINE_METHODID(name, method) static jmethodID JNI_METHODID(name, method) = NULL;
#define JNI_LOAD_FIELDID(env, clazz, name, field, sig) {JNI_FIELDID(name, field) = (env)->GetFieldID(clazz, #field, sig);YYLOGD("fieldID_"#name"_"#field" = %p", JNI_FIELDID(name, field));}
#define JNI_LOAD_METHODID(env, clazz, name, method, sig) {JNI_METHODID(name, method) = (env)->GetMethodID(clazz, #method, sig);YYLOGD("methodID_"#name"_"#method" = %p", JNI_METHODID(name, method));}

#endif //_YY_JNI_H_
