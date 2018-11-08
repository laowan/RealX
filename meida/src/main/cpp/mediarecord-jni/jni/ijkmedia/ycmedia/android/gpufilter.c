/*****************************************************************************
 * aout.c
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <stdio.h>
#include <assert.h>
#include <string.h>
#include <stdint.h>
#include <pthread.h>
#include <jni.h>
#include "common.h"

#define LOG_TAG "gpufilter"
#define GPU_FILTER_CLASS_NAME	"com/ycloud/gpufilter/LibGpuFilter"
#define GPU_THREAD_NAME "gpufilter"

typedef struct
{
    jobject     j_libGpuFilter;   /// Pointer to the LibVLC Java object 
    void*	outdirectbuffer;
    jint	inSize;
    jint	outSize;
    jmethodID 	filter;
} gpufilter_sys_t;


extern int jni_attach_thread(JNIEnv **env, const char *thread_name);
extern void jni_detach_thread();
extern int jni_get_env(JNIEnv **env); 

extern void set_gpufilter_func(int (*filter_frame)(unsigned char *src, unsigned char * dst,int width,int height,int insize,int outsize,void *a_sys)
				,int (*gpufilter_init)(char *filtername, int width, int height,void **a_sys)
				,void (*filter_close)(void *a_sys),void (*log)(const char *fmt, ...),void* a_sys);
jlong getLong(JNIEnv *env, jobject thiz, const char* field) {
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    jfieldID fieldMP = (*env)->GetFieldID(env, clazz,
                                          field, "J");
    return (*env)->GetLongField(env, thiz, fieldMP);
}
void setLong(JNIEnv *env, jobject item, const char* field, jlong value) {
    jclass cls;
    jfieldID fieldId;

    /* Get a reference to item's class */
    cls = (*env)->GetObjectClass(env, item);

    /* Look for the instance field s in cls */
    fieldId = (*env)->GetFieldID(env, cls, field, "J");
    if (fieldId == NULL)
        return;

    (*env)->SetLongField(env, item, fieldId, value);
}


int jni_gpufilter_init(char *filtername, int width, int height,void **a_sys)
{
    int isAttached = 0;
    gpufilter_sys_t *p_sys=(gpufilter_sys_t*)(*a_sys);
    if (!p_sys)
        goto enomem;
    if(p_sys->j_libGpuFilter == NULL)
	goto enomem;
    JNIEnv *p_env;
    if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread (&p_env, GPU_THREAD_NAME) != 0)
    	{
        ALOGE("Could not attach the gpufilter thread to the JVM !");
        goto eattach;
   	 }
        isAttached = 1;
    }

   
    jclass cls =  (*p_env)->GetObjectClass (p_env, p_sys->j_libGpuFilter);
    if ((*p_env)->ExceptionCheck (p_env))
    {
        ALOGE ("Unable to GetObjectClass!");
#ifndef NDEBUG
        (*p_env)->ExceptionDescribe (p_env);
#endif
        (*p_env)->ExceptionClear (p_env);
	 goto error;
    }

    //create new instance
    jmethodID methodIdCreateGpuFilter = (*p_env)->GetMethodID (p_env, cls,
                                                        "<init>", "()V" );
     if (!methodIdCreateGpuFilter)
    {
        ALOGE ("Method methodIdCreateGpuFilter() could not be found!");
        goto error;
    }
    jobject filterobject = (*p_env)->NewObject(p_env, cls, methodIdCreateGpuFilter);
   // if(p_sys->j_libGpuFilter != NULL)
    //  (*p_env)->DeleteGlobalRef (p_env, p_sys->j_libGpuFilter);
  //   free (p_sys);
     p_sys=calloc (1, sizeof (gpufilter_sys_t));
    *a_sys=p_sys;
    p_sys->j_libGpuFilter=(*p_env)->NewGlobalRef(p_env, filterobject);
    setLong(p_env, filterobject, "mGpuInstance", (jlong)(intptr_t) p_sys);
    // Call the init function.
    jmethodID methodIdInitGpuFilter = (*p_env)->GetMethodID (p_env, cls,
                                                        "init", "(Ljava/lang/String;II)I");

   
    if (!methodIdInitGpuFilter)
    {
        ALOGE ("Method methodIdInitGpuFilter() could not be found!");
        goto error;
    }
    while (1) {
	jstring filter_name = (*p_env)->NewStringUTF(p_env, filtername);

        jint ret=(*p_env)->CallIntMethod (p_env, p_sys->j_libGpuFilter, methodIdInitGpuFilter,
                                  filter_name, width, height);
	(*p_env)->DeleteLocalRef(p_env, filter_name);
        if ((*p_env)->ExceptionCheck (p_env) == 0) {
            break;
        }
	if(ret>0)
	  break;
#ifndef NDEBUG
        (*p_env)->ExceptionDescribe (p_env);
#endif
        (*p_env)->ExceptionClear (p_env);
        goto error;
    }
     jfieldID fid = (*p_env)->GetFieldID(p_env, cls,"m_outBuffer", "Ljava/nio/ByteBuffer;");
     if(fid == NULL)
    	 goto error;
     jobject buf = (*p_env)->GetObjectField(p_env,  p_sys->j_libGpuFilter, fid);
     if(buf == NULL)
	goto error;
    p_sys->outdirectbuffer=(*p_env)->GetDirectBufferAddress (p_env, buf);
    p_sys->inSize=width*height*4+1024;
    p_sys->outSize=width*height*4+1024;
    // Get the filter methodId
    p_sys->filter = (*p_env)->GetMethodID (p_env, cls, "filter_frame", "(II)V");
    assert (p_sys->filter != NULL);
    if(isAttached>0)
    jni_detach_thread ();
    return 0;

error:
    if(isAttached>0)
    jni_detach_thread ();
eattach:
    free (p_sys);
    p_sys=NULL;
enomem:
    return -1;
}

/**
 * filter the frame
 **/
int jni_filter_frame(unsigned char *src, unsigned char * dst,int width,int height,int insize,int outsize,void *a_sys)
{
    //  LOGI ("jni_filter_frame insize=%d,insize=%d,outsize=%d,poutsize=%d,width=%d,height=%d",insize,p_sys->inSize,outsize,p_sys->outSize,width,height);
     gpufilter_sys_t *p_sys=(gpufilter_sys_t*)a_sys;
     if (!p_sys || p_sys->j_libGpuFilter == NULL || insize != outsize)
	return;
    JNIEnv *p_env;
    int isAttached = 0;

    /* How ugly: we constantly attach/detach this thread to/from the JVM
     * because it will be killed before aout_close is called.
     * aout_close will actually be called in an different thread!
     */
    if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread (&p_env, GPU_THREAD_NAME) != 0)
    	{
        ALOGE("Could not attach the gpufilter thread to the JVM !");
        goto eattach;
   	 }
        isAttached = 1;
    }

     memset(dst,0,outsize);
     memcpy(p_sys->outdirectbuffer,src,insize);
    (*p_env)->CallVoidMethod (p_env, p_sys->j_libGpuFilter, p_sys->filter,
                              width,
                              height);
    memcpy(dst,p_sys->outdirectbuffer,outsize);
eattach:
    if(isAttached>0)
    jni_detach_thread ();
    // FIXME: check for errors
}

void jni_filter_close(void *a_sys)
{
    ALOGI ("Closing filter");
    gpufilter_sys_t *p_sys=(gpufilter_sys_t*)a_sys;
    if(p_sys == NULL)
	return;

    JNIEnv *p_env;
    int isAttached = 0;
    if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread (&p_env, GPU_THREAD_NAME) != 0)
    	{
        ALOGE("Could not attach the gpufilter thread to the JVM !");
        goto eattach;
   	 }
        isAttached = 1;
    }

    // Call the close function.
    jclass cls = (*p_env)->GetObjectClass (p_env, p_sys->j_libGpuFilter);
    jmethodID methodIdCloseFilter = (*p_env)->GetMethodID (p_env, cls, "uninit", "()V");
    if (!methodIdCloseFilter)
        ALOGE ("Method release() could not be found!");
    (*p_env)->CallVoidMethod (p_env, p_sys->j_libGpuFilter, methodIdCloseFilter);
    if ((*p_env)->ExceptionCheck (p_env))
    {
        ALOGE ("Unable to close filter!");
#ifndef NDEBUG
        (*p_env)->ExceptionDescribe (p_env);
#endif
        (*p_env)->ExceptionClear (p_env);
    }
    (*p_env)->DeleteGlobalRef (p_env, p_sys->j_libGpuFilter);
    free (p_sys);
    p_sys=NULL;
eattach:
    if(isAttached>0)
    jni_detach_thread ();
}

static void jni_log(char* logmsg)
{
  ALOGE ("%s",logmsg);
}

gpufilter_sys_t *getGpuInstance(JNIEnv *env, jobject thiz)
{
    return (gpufilter_sys_t*)(intptr_t)getLong(env, thiz, "mGpuInstance");
}


void   Java_com_ycloud_gpufilter_LibGpuFilter_gpufilterctx(JNIEnv *env, jobject thiz) 
{
    gpufilter_sys_t *p_sys=getGpuInstance(env,thiz);
     ALOGI ("gpufilterctx");
    if(p_sys != NULL)
	return;
    p_sys = calloc (1, sizeof (*p_sys));
    p_sys->j_libGpuFilter =(*env)->NewGlobalRef(env, thiz);
    setLong(env, thiz, "mGpuInstance", (jlong)(intptr_t) p_sys);
    set_gpufilter_func(jni_filter_frame,jni_gpufilter_init,jni_filter_close,jni_log,p_sys);
}

void   Java_com_ycloud_gpufilter_LibGpuFilter_gpufilterrelease(JNIEnv *env, jobject thiz) 
{
    gpufilter_sys_t *p_sys=getGpuInstance(env,thiz);
    if(p_sys == NULL)
	return;
    assert(p_sys);

    JNIEnv *p_env;
    int isAttached = 0;
     if (jni_get_env(&p_env) < 0) {
        if (jni_attach_thread (&p_env, GPU_THREAD_NAME) != 0)
    	{
        ALOGE("Could not attach the gpufilter thread to the JVM !");
        goto eattach;
   	 }
        isAttached = 1;
    }
    (*p_env)->DeleteGlobalRef (p_env, p_sys->j_libGpuFilter);
eattach:
    if(isAttached>0)
    jni_detach_thread ();
    p_sys->j_libGpuFilter=NULL;
    free (p_sys);
    p_sys=NULL;
    
    setLong(env, thiz, "mGpuInstance", 0);
     ALOGI ("gpufilterctx release");
    set_gpufilter_func(NULL,NULL,NULL,NULL,NULL);
}
