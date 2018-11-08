/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox */

#ifndef _Included_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox
#define _Included_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox
#ifdef __cplusplus
extern "C" {
#endif
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoNone
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoNone 0L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoEthereal
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoEthereal 1L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoThriller
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoThriller 2L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoHeavyMetal
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoHeavyMetal 3L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoLorie
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoLorie 4L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoUncle
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoUncle 5L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoDieFat
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoDieFat 6L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoBadBoy
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoBadBoy 7L
#undef com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoWarCraft
#define com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_VeoWarCraft 8L
/*
 * Class:     com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox
 * Method:    create
 * Signature: (II)J
 */
JNIEXPORT jlong JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_create
  (JNIEnv *, jobject, jint, jint);

/*
 * Class:     com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox
 * Method:    destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_destroy
  (JNIEnv *, jobject, jlong);

/*
 * Class:     com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox
 * Method:    process
 * Signature: (J[S[S)I
 */
JNIEXPORT jint JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_process
  (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox
 * Method:    setVoiceEffectOption
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_com_ycloud_mediarecord_audio_AudioVoiceChangerToolbox_setVoiceEffectOption
  (JNIEnv *, jobject, jlong, jint);

#ifdef __cplusplus
}
#endif
#endif
