#include <com_ycloud_mediarecord_MediaNative.h>
#include <libffmpeg_event.h>
#include <assert.h>
#include "common.h"
#include "libavutil/log.h"
#include "com_ycloud_audio_AudioPlaybackRateProcessor.h"

#include "x264/JNIContext.h"

#ifdef __cplusplus
extern "C" {
#endif

#define MN_JNI_VERSION JNI_VERSION_1_4
#define THREAD_NAME LOG_TAG
#define JNI_CLASS_RECORD     "com/ycloud/mediarecord/MediaNative"

static JavaVM *myVm;
static char logStr[1024*1024]={0};

extern int ffmpeg_running;
extern int ffmpeg_process_cancelled;
static long progress_interval = 500000;

jclass media_native;

int jni_attach_thread(JNIEnv **env, const char *thread_name)
{
    JavaVMAttachArgs args;
    jint result;

    args.version = MN_JNI_VERSION;
    args.name = thread_name;
    args.group = NULL;

    result = (*myVm)->AttachCurrentThread(myVm, env, &args);
    return result == 0 ? 0 : -1;
}

void jni_detach_thread()
{
    (*myVm)->DetachCurrentThread(myVm);
}

JavaVM *getJavaVM()
{
    return myVm;
}

int jni_get_env(JNIEnv **env)
{
    return (*myVm)->GetEnv(myVm, (void **)env, MN_JNI_VERSION) == 0 ? 0 : -1;
}

void ffmpeg_event_callback(const libffmpeg_event_t *ev)
{
    JNIEnv *env;

    int isAttached = 0;

    if (jni_get_env(&env) < 0) {
        if (jni_attach_thread(&env, THREAD_NAME) < 0)
            return;
        isAttached = 1;
    }

    /* Creating the bundle in C allows us to subscribe to more events
     * and get better flexibility for each event. For example, we can
     * have totally different types of data for each event, instead of,
     * for example, only an integer and/or string.
     */
    jclass clsBundle = (*env)->FindClass(env, "android/os/Bundle");
    jmethodID clsCtor = (*env)->GetMethodID(env, clsBundle, "<init>", "()V" );
    jobject bundle = (*env)->NewObject(env, clsBundle, clsCtor);

    jmethodID putInt = (*env)->GetMethodID(env, clsBundle, "putInt", "(Ljava/lang/String;I)V" );
    jmethodID putLong = (*env)->GetMethodID(env, clsBundle, "putLong", "(Ljava/lang/String;J)V" );
    //jmethodID putFloat = (*env)->GetMethodID(env, clsBundle, "putFloat", "(Ljava/lang/String;F)V" );
    jmethodID putString = (*env)->GetMethodID(env, clsBundle, "putString", "(Ljava/lang/String;Ljava/lang/String;)V" );

    if (ev->type == libffmpeg_cmd_snapshot_multiple) {
       jstring sFramePts= (*env)->NewStringUTF(env, "frame_pts");
       (*env)->CallVoidMethod(env, bundle, putLong, sFramePts, ev->frame_pts);
       (*env)->DeleteLocalRef(env, sFramePts);
       jstring sFrameNum= (*env)->NewStringUTF(env, "frame_num");
       (*env)->CallVoidMethod(env, bundle, putInt, sFrameNum, ev->frame_num);
       (*env)->DeleteLocalRef(env, sFrameNum);
    } else if (ev->type == libffmpeg_cmd_video_concat) {
       jstring sFrameNum= (*env)->NewStringUTF(env, "frame_num");
       (*env)->CallVoidMethod(env, bundle, putInt, sFrameNum, ev->frame_num);
       (*env)->DeleteLocalRef(env, sFrameNum);
	}else if (ev->type == libffmpeg_cmd_transcode) {
		jstring sFrameNum= (*env)->NewStringUTF(env, "frame_num");
       (*env)->CallVoidMethod(env, bundle, putInt, sFrameNum, ev->frame_num);
       (*env)->DeleteLocalRef(env, sFrameNum);
	}else if (ev->type == libffmpeg_cmd_video_effect) {
		jstring sFrameNum= (*env)->NewStringUTF(env, "frame_num");
       (*env)->CallVoidMethod(env, bundle, putInt, sFrameNum, ev->frame_num);
       (*env)->DeleteLocalRef(env, sFrameNum);
	}else if (ev->type == libffmpeg_cmd_video_cut) {
		jstring sFrameNum= (*env)->NewStringUTF(env, "frame_num");
       (*env)->CallVoidMethod(env, bundle, putInt, sFrameNum, ev->frame_num);
       (*env)->DeleteLocalRef(env, sFrameNum);
	}
	

    /* Set event type */
    jstring sEventType= (*env)->NewStringUTF(env, "event_type");
    (*env)->CallVoidMethod(env, bundle, putInt, sEventType, ev->type);
    (*env)->DeleteLocalRef(env, sEventType);

	/*回调java类中静态方法*/
    jmethodID  methodID = (*env)->GetStaticMethodID(env,media_native, "onEventCallback", "(Landroid/os/Bundle;)V");

    if (methodID) {
        (*env)->CallStaticVoidMethod(env,media_native,methodID,bundle);
    } else {
        ALOGE("EventHandler: failed to get the callback method");
    }

    (*env)->DeleteLocalRef(env, clsBundle);
    (*env)->DeleteLocalRef(env, bundle);
    if (isAttached)
        jni_detach_thread();
}
//java字符串转C字符串
char* jstringTostr(JNIEnv* env, jstring jstr)
{
    char* pStr = NULL;

    jclass     jstrObj   = (*env)->FindClass(env, "java/lang/String");
    jstring    encode    = (*env)->NewStringUTF(env, "utf-8");
    jmethodID  methodId  = (*env)->GetMethodID(env, jstrObj, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray byteArray = (jbyteArray)(*env)->CallObjectMethod(env, jstr, methodId, encode);
    jsize      strLen    = (*env)->GetArrayLength(env, byteArray);
    jbyte      *jBuf     = (*env)->GetByteArrayElements(env, byteArray, JNI_FALSE);

    if (jBuf > 0)
    {
        pStr = (char*)malloc(strLen + 1);

        if (!pStr)
        {
            return NULL;
        }

        memcpy(pStr, jBuf, strLen);

        pStr[strLen] = 0;
    }

    (*env)->ReleaseByteArrayElements(env, byteArray, jBuf, 0);

    return pStr;
}

//C字符串转java字符串
jstring strToJstring(JNIEnv* env, const char* pStr)
{
    int        strLen    = strlen(pStr);
    jclass     jstrObj   = (*env)->FindClass(env, "java/lang/String");
    jmethodID  methodId  = (*env)->GetMethodID(env, jstrObj, "<init>", "([BLjava/lang/String;)V");
    jbyteArray byteArray = (*env)->NewByteArray(env, strLen);
    jstring    encode    = (*env)->NewStringUTF(env, "utf-8");

    (*env)->SetByteArrayRegion(env, byteArray, 0, strLen, (jbyte*)pStr);

    return (jstring)(*env)->NewObject(env, jstrObj, methodId, byteArray, encode);
}
/*
 * Class:     com_ycloud_mediarecord2_MediaNative
 * Method:    media_process
 * Signature:
 */
JNIEXPORT jstring JNICALL jni_media_process(JNIEnv *env, jobject object, jint cmd_type, jstring cmd) {
	int ret = 0;
	int i = 0;
    char** argv = NULL;
    int argc = 0;

    if (cmd == NULL) {
        ALOGE("cmd is NULL, just return");
        return NULL;
    }

    const char *str = (*env)->GetStringUTFChars(env, cmd, 0);
    char *ret_string = NULL;
    jstring out_string = NULL;
    char out_str[512000] = {0};//bhl

    ffmpeg_process_cancelled = 0;
    argv = argv_create(str, &argc);
    if (argv) {
    	if (strcmp(argv[0], "ffprobe") == 0) {
    		ret_string = ffprobe_main(argc, argv);
    	    if (ret_string) {
    	    	out_string = (*env)->NewStringUTF(env, ret_string);
    	    	free(ret_string);
    	    }
    	}
    	else if (strcmp(argv[0], "ffmpeg") == 0) {
            FFmpegCtx *ffmpegctx = malloc(sizeof(FFmpegCtx));
            memset(ffmpegctx, 0 , sizeof(FFmpegCtx));
            ffmpegctx->cmd_type = cmd_type;
            ffmpegctx->ffmpeg_event_cb = ffmpeg_event_callback;

            jclass clazz = (*env)->GetObjectClass(env, object);
            jfieldID videoGpuFilterId = (*env)->GetFieldID(env, clazz, "mVideoGpuFilter", "Lcom/ycloud/mediarecord/VideoGpuFilter;");
            jobject videoGpuFilter = (*env)->GetObjectField(env, object, videoGpuFilterId);
            ffmpegctx->videGpuFilter = videoGpuFilter;

            if(videoGpuFilter != NULL){
                ffmpegctx->isUseGpuFilter = 1;
            } else {
                ffmpegctx->isUseGpuFilter = 0;
            }
			setProgressInterval(progress_interval);

            libffmpeg_instance_t *inst = ffmpeg_new(ffmpegctx);
            ret = ffmpeg_main(inst, argc, argv);
            sprintf(out_str, "%d", ret);
            ALOGE("ffmpeg_main return value : %s",out_str);
            out_string = (*env)->NewStringUTF(env, out_str);
            free(ffmpegctx);
            ffmpeg_release(inst);
            ffmpegctx = NULL;
            inst = NULL;
    	}
    }

    argv_free(argv, argc);
    argv = NULL;

    (*env)->ReleaseStringUTFChars(env, cmd, str);

    return out_string;
}

/*
 * Class:     com_ycloud_mediarecord2_MediaNative
 * Method:    cancel_media_process
 * Signature: ()V
 */
JNIEXPORT void JNICALL jni_cancel_media_process(JNIEnv *env, jobject object) {
    ffmpeg_exit();
}

/*
 * Class:     com_ycloud_mediarecord2_MediaNative
 * Method:    reset cancel state
 * Signature: ()V
 */
JNIEXPORT void JNICALL jni_reset_media_process(JNIEnv *env, jobject object) {
    ffmpeg_process_cancelled = 0;
}

/*
 * Class:     com_ycloud_mediarecord2_MediaNative
 * Method:    check ffmpeg is running
 * Signature: ()I
 */
JNIEXPORT jboolean JNICALL jni_media_is_ffmpeg_running(JNIEnv *env, jobject object) {
	  return ffmpeg_running;
}

/*
 * Class:     com_ycloud_mediarecord2_MediaNative
 * Method:    check is in cancel state
 * Signature: ()I
 */
JNIEXPORT jboolean JNICALL jni_media_is_ffmpeg_process_cancelled(JNIEnv *env, jobject object) {
	  return ffmpeg_process_cancelled;
}

JNIEXPORT void JNICALL jni_set_media_progress_interval(JNIEnv *env, jobject object, jlong interval) {
	  progress_interval = interval;
}


void log_callback_ffmpeg(void *ptr, int level, const char *fmt, va_list vargs) {
    int len = vsprintf(logStr, fmt, vargs);

    JNIEnv *env;

    int isAttached = 0;

    if (jni_get_env(&env) < 0) {
        if (jni_attach_thread(&env, THREAD_NAME) < 0)
            return;
        isAttached = 1;
    }

    jbyteArray byte_array = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, byte_array, 0, len, (jbyte *) logStr);

    jmethodID methodID = (*env)->GetStaticMethodID(env, media_native, "nativeLogCallback",
                                                   "(I[B)V");

    if (methodID) {
        int log_level = 0;

        if (level <= AV_LOG_ERROR) {
            log_level = 6;
            (*env)->CallStaticVoidMethod(env, media_native, methodID, log_level, byte_array);
        }else if (level == AV_LOG_WARNING) {
            log_level = 5;
            (*env)->CallStaticVoidMethod(env, media_native, methodID, log_level, byte_array);
        } else if (level == AV_LOG_INFO) {
            log_level = 4;
            (*env)->CallStaticVoidMethod(env, media_native, methodID, log_level, byte_array);
        }/* else if (level == AV_LOG_DEBUG) {
            log_level = 3;
            (*env)->CallStaticVoidMethod(env, media_native, methodID, log_level, byte_array);
        } else {
            log_level = 4;
            (*env)->CallStaticVoidMethod(env, media_native, methodID, log_level, byte_array);
        }*/

    } else {
        ALOGE("EventHandler: failed to get the nativeLogCallback method");
    }

    if(byte_array != NULL) {
        (*env)->DeleteLocalRef(env, byte_array);
    }
    if (isAttached)
        jni_detach_thread();
}


#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif


static JNINativeMethod g_methods[] = {
        {"mediaProcessNative",                  "(ILjava/lang/String;)Ljava/lang/String;", (void *) jni_media_process},
        {"resetMediaProcessNative",             "()V",                                     (void *) jni_reset_media_process},
        {"cancelMediaProcessNative",            "()V",                                     (void *) jni_cancel_media_process},
        {"mediaIsFFmpegRunningNative",          "()Z",                                     (void *) jni_media_is_ffmpeg_running},
        {"mediaIsFFmpegProcessCancelledNative", "()Z",                                     (void *) jni_media_is_ffmpeg_process_cancelled},
        {"mediaSetProgressIntervalNative",		"(J)V",									   (void *) jni_set_media_progress_interval},
};

void registerNativeFfmpeg(JNIEnv* env);
extern void YYinit_FFmpegDemuxDecode(JavaVM *jvm, void *reserved);
extern void YYdeinit_FFmpegDemuxDecode(JavaVM *jvm, void *reserved);


jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    // Keep a reference on the Java VM.
    myVm = vm;
	JNIEnv* env = NULL;
	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		ALOGE("ycmedia so JNI_OnLoad error.");
        return -1;
    }
    assert(env != NULL);

    // FindClass returns LocalReference
    media_native = (*env)->FindClass(env, JNI_CLASS_RECORD);
    media_native = (*env)->NewGlobalRef(env, media_native);

    (*env)->RegisterNatives(env, media_native, g_methods, NELEM(g_methods) );

    registerNativeFfmpeg(env);
	YYinit_FFmpegDemuxDecode(vm,reserved);

    onJoinLoad(env);
    initAudio(env);
    ALOGI("ycmedia so JNI_OnLoad ok.");
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
 ALOGD("JNI interface unloaded.");
    JNIEnv *env = NULL;

   if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
    		ALOGE("Failed to get JNI env");
            return;
     }

   onJNI_Unload(env);
   YYdeinit_FFmpegDemuxDecode(vm,reserved);
}

#ifdef __cplusplus
}
#endif
