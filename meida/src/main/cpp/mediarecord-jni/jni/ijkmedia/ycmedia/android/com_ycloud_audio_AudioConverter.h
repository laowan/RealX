/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_ycloud_audio_AudioConverter */

#ifndef _Included_com_ycloud_audio_AudioConverter
#define _Included_com_ycloud_audio_AudioConverter
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_ycloud_audio_AudioConverter
 * Method:    create
 * Signature: (IIII)J
 */
JNIEXPORT jlong JNICALL Java_com_ycloud_audio_AudioConverter_create
  (JNIEnv *, jobject, jint, jint, jint, jint);

/*
 * Class:     com_ycloud_audio_AudioConverter
 * Method:    destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_ycloud_audio_AudioConverter_destory
  (JNIEnv *, jobject, jlong);

/*
 * Class:     com_ycloud_audio_AudioConverter
 * Method:    process
 * Signature: (J[S[S)I
 */
JNIEXPORT jint JNICALL Java_com_ycloud_audio_AudioConverter_process
  (JNIEnv *, jobject, jlong, jbyteArray, jint, jbyteArray, jint);


#ifdef __cplusplus
}
#endif
#endif
