//
// Created by Administrator on 2016/9/13.
//q
#include "Common.h"
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JavaVM *gJavaVM = NULL;
extern "C"  void register_X264SoftEncoder_Jni(JNIEnv *env );
extern "C"  void register_ImageUitl_Jni(JNIEnv *env );
//extern "C"  void register_VideoPackUtil_Jni(JNIEnv *env );

extern "C"  void register_VideoEncodedData_Jni(JNIEnv *env );
extern "C"  void unregister_VideoEncodedData_Jni(JNIEnv *env );

extern "C"  void register_FfmMediaMuxer_Jni(JNIEnv *env );



void onJoinLoad(JNIEnv *env) {
    register_X264SoftEncoder_Jni(env);
    register_ImageUitl_Jni(env);
    register_VideoEncodedData_Jni(env);
	register_FfmMediaMuxer_Jni(env);
    //register_VideoPackUtil_Jni(env);
    LOGD("onJoinLoad");
}

void onJNI_Unload(JNIEnv *env) {
  unregister_VideoEncodedData_Jni(env);
}

/*
JNIEXPORT jint JNI_OnLoad(JavaVM * vm , void *reserved )
{
    gJavaVM = vm;
    JNIEnv *env = NULL;
    LOGDXXX("JNI_OnLoad system build.*******************") ;
    if ( vm -> GetEnv((void** )&env , JNI_VERSION_1_6 ) != JNI_OK ) {
        LOGDXXX("Failed to get the environment by using GetEnv()") ;
        return - 1 ;
    }

    register_X264SoftEncoder_Jni(env);
    register_ImageUitl_Jni(env);
    register_VideoEncodedData_Jni(env);
    //register_VideoPackUtil_Jni(env);

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM * vm, void * reserved) {
    JNIEnv *env = NULL;
    LOGDXXX("JNI_Unload *******************");

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGDXXX("Failed to get JNI env");
    } else {
        unregister_VideoEncodedData_Jni(env);
    }
    gJavaVM = NULL;
}
*/

#ifdef __cplusplus
}
#endif