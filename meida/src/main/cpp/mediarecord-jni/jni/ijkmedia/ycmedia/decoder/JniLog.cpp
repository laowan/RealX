/*!
    \file JniLog.cpp
*/
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <pthread.h>
#include "YYJni.h"

#define YYLOG_TAG_SIZE (1024)
#define YYLOG_BUFFER_SIZE ((1 << 20) + 64)

static pthread_mutex_t s_yylog_mutex;
static char s_yylog_tag[YYLOG_TAG_SIZE] = {0};
static char s_yylog_buffer[YYLOG_BUFFER_SIZE] = {0};
static volatile int s_yylog_level = ANDROID_LOG_SILENT;
static volatile const char* s_yylog_module = "yy-jni";

static JavaVM* s_jvm = NULL;
static jclass ref_class_yylog = NULL;
static jmethodID methodID_yylog = NULL;

static JNIEnv* getEnv(int* detach)
{
    JNIEnv* env = NULL;

    if(s_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK && env != NULL)
    {
        *detach = 0;
    }
    else if(s_jvm->AttachCurrentThread(&env, NULL) == JNI_OK && env != NULL)
    {
        *detach = 1;
    }
    return env;
}

static void yylog_print(int level, const char* tag, int tagLen, const char* msg, int msgLen)
{
    int need_detach = 0;
    JNIEnv* env = NULL;
    jstring jstag = NULL, jsmsg = NULL;

    if(methodID_yylog != NULL && s_jvm != NULL)
    {
        if((env = getEnv(&need_detach)) != NULL)
        {
#if 1
            jstag = env->NewStringUTF(tag);
            jsmsg = env->NewStringUTF(msg);
            env->CallStaticVoidMethod(ref_class_yylog, methodID_yylog, level, jstag, jsmsg);
            DELETE_LOCAL_REF(env, jstag);
            DELETE_LOCAL_REF(env, jsmsg);
#else
            env->CallObjectMethod(ref_object_yylog_tag, methodID_ByteBuffer_rewind);
            env->CallObjectMethod(ref_object_yylog_tag, methodID_ByteBuffer_limit_I, tagLen);
            env->CallObjectMethod(ref_object_yylog_buffer, methodID_ByteBuffer_rewind);
            env->CallObjectMethod(ref_object_yylog_buffer, methodID_ByteBuffer_limit_I, msgLen);
            /*
             * JNI ERROR (app bug): local reference table overflow (max=512)  java.nio.DirectByteBuffer
             */
            env->CallStaticVoidMethod(ref_class_yylog, methodID_yylog, level, ref_object_yylog_tag, ref_object_yylog_buffer);
#endif
            if(need_detach) s_jvm->DetachCurrentThread();
        }
    }
}

void yylog_init(JavaVM* jvm, const char* path)
{
    JNIEnv* env = NULL;
    jclass clazz = NULL;

    if(jvm == NULL || path == NULL)
    {
        return;
    }
    s_jvm = jvm;
    pthread_mutex_init(&s_yylog_mutex, NULL);
    if(s_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
    {
        return;
    }
    if((clazz = env->FindClass(path)) != NULL)
    {
        ref_class_yylog = (jclass)env->NewWeakGlobalRef(clazz);
        DELETE_LOCAL_REF(env, clazz);
        methodID_yylog = env->GetStaticMethodID(ref_class_yylog, "yylog", "(ILjava/lang/String;Ljava/lang/String;)V");
    }
    __android_log_print(ANDROID_LOG_DEBUG, "svplayer", "%s -> methodID_yylog = %p", path, methodID_yylog);
}

void yylog_deinit(void)
{
    int need_detach = -1;
    JNIEnv* env = NULL;

    if((env = getEnv(&need_detach)) != NULL)
    {
        methodID_yylog = NULL;
        DELETE_WEAK_GLOBAL_REF(env, ref_class_yylog);
        if(need_detach) s_jvm->DetachCurrentThread();
    }
    pthread_mutex_destroy(&s_yylog_mutex);
}

void yylog_set(int level, const char* module)
{
    s_yylog_level = level;
    s_yylog_module = module;
}

void yylog(const char* func, int line, int level, const char* tag, const char* fmt, ...)
{
    va_list args;
    int tagLen = 0, msgLen = 0;

    if(level >= s_yylog_level)
    {
        pthread_mutex_lock(&s_yylog_mutex);
        if(tag != NULL)
        {
            tagLen = snprintf(s_yylog_tag, YYLOG_TAG_SIZE, "[%s][%s]", s_yylog_module, tag);
        }
        else
        {
            tagLen = snprintf(s_yylog_tag, YYLOG_TAG_SIZE, "[%s]", s_yylog_module);
        }
        if(func != NULL && line >= 0)
        {
            msgLen = snprintf(s_yylog_buffer, YYLOG_BUFFER_SIZE, "[%s:%d] ", func, line);
        }
        va_start(args, fmt);
        msgLen += vsnprintf(s_yylog_buffer + msgLen, YYLOG_BUFFER_SIZE - msgLen, fmt, args);
        va_end(args);
        yylog_print(level, s_yylog_tag, tagLen, s_yylog_buffer, msgLen);
        pthread_mutex_unlock(&s_yylog_mutex);
    }
}
