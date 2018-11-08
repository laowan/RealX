/************************************************************************************
* Copyright (c) 2018,YY.Inc
* All rights reserved.
*
* FileName:FFmpegDemuxDecodeJNI.cpp
* Description:FFmpegDemuxDecode JNI layer
*
* Version:1.0
* Author:Created by jtzhu
* Date:2018/01/26
*************************************************************************************/
#include <sys/prctl.h>
#include <stdio.h>
#include "FFmpegDemuxDecodeJNI.h"
#include "../x264/JNIHelper.h"
#include "../x264/Macros.h"
#include "../x264/Common.h"
#include "FFmpegDemuxDecode.h"

#define CHECK(x, msg)					\
  if (x) {								\
    } else {							\
    	LOGE(TAG" %s:%d: %s", __FILE__,	\
                        __LINE__, msg);	\
    }
	
#define CHECK_EXCEPTION(jni, msg) 		\
    if (jni->ExceptionCheck()) {  		\
	      jni->ExceptionDescribe();   	\
	      jni->ExceptionClear();      	\
	      CHECK(0, msg);              	\
	}									\


#undef JNIEXPORT
#define JNIEXPORT __attribute__((visibility("default")))

#define TAG "[ymrsdk] FFmpegDemuxDecodeJNI"

static JavaVM* g_jvm = NULL;  
static pthread_once_t g_jni_ptr_once = PTHREAD_ONCE_INIT;
static pthread_key_t g_jni_ptr;
static char *classPath = "com/ycloud/mediafilters/FFmpegDemuxDecodeFilter";
static FFmpegDemuxDecodeCtx *gCtx = NULL; // ??????????,????

extern "C" uint32_t FFGetTickCounts()
{
	struct timespec tsNowTime;
	clock_gettime(CLOCK_MONOTONIC, &tsNowTime);
	return (uint32_t)((uint64_t)tsNowTime.tv_sec*1000 + (uint64_t)tsNowTime.tv_nsec/1000000);
}

static JNIEnv* GetEnv()
{
	void* env = NULL;
	jint status = g_jvm->GetEnv(&env, JNI_VERSION_1_6);
	CHECK(((env != NULL) && (status == JNI_OK)) ||
		((env == NULL) && (status == JNI_EDETACHED)),
		"Unexpected GetEnv return: ");
	return reinterpret_cast<JNIEnv*>(env);
}

static void ThreadDestructor(void* prev_jni_ptr)
{
	if (!GetEnv())
		return;
	CHECK((GetEnv() == prev_jni_ptr), "Detaching from another thread ");
	jint status = g_jvm->DetachCurrentThread();
	CHECK((status == JNI_OK), "Failed to detach thread: ");
	CHECK(!GetEnv(), "Detaching was a successful no-op???");
}

static void CreateJNIPtrKey()
{
	CHECK(!pthread_key_create(&g_jni_ptr, &ThreadDestructor),
		"pthread_key_create");
}

static std::string GetThreadId()
{
	char buf[21];  // Big enough to hold a kuint64max plus terminating NULL.
	CHECK(snprintf(buf, sizeof(buf), "%llu", (unsigned long long)pthread_self()) <= (int)sizeof(buf),
		"Thread id is bigger than uint64??");
	return std::string(buf);
}

static std::string GetThreadName()
{
	char name[17];
	CHECK(prctl(PR_GET_NAME, name) == 0, "prctl(PR_GET_NAME) failed");
	name[16] = '\0';
	return std::string(name);
}

static JNIEnv* AttachCurrentThreadIfNeeded()
{
	JNIEnv* jni = GetEnv();
	if (jni)
		return jni;
	CHECK(!pthread_getspecific(g_jni_ptr), "TLS has a JNIEnv* but not attached?");

	char* name = strdup((GetThreadName() + " - " + GetThreadId()).c_str());
	JavaVMAttachArgs args;
	args.version = JNI_VERSION_1_6;
	args.name = name;
	args.group = NULL;

	JNIEnv* env = NULL;
	CHECK(!g_jvm->AttachCurrentThread(&env, &args), "Failed to attach thread");
	free(name);
	CHECK(env, "AttachCurrentThread handed back NULL!");
	jni = reinterpret_cast<JNIEnv*>(env);
	CHECK(!pthread_setspecific(g_jni_ptr, jni), "pthread_setspecific");
	return jni;
}


extern "C"  void register_FFmpegDemuxDecodeMethod(JNIEnv *env );
extern "C"  void YYinit_FFmpegDemuxDecode(JavaVM *jvm, void *reserved)
{
	CHECK(!g_jvm, "JNI_OnLoad called more than once!");
	g_jvm = jvm;
	CHECK(g_jvm, "JNI_OnLoad handed NULL?");
	CHECK(!pthread_once(&g_jni_ptr_once, &CreateJNIPtrKey), "pthread_once");

	JNIEnv* jni;
	if (jvm->GetEnv(reinterpret_cast<void**>(&jni), JNI_VERSION_1_6) != JNI_OK) {
		LOGE(TAG"jvm->GetEnv fail");
	}

	register_FFmpegDemuxDecodeMethod(jni);
}

extern "C" void YYdeinit_FFmpegDemuxDecode(JavaVM *jvm, void *reserved)
{
	JNIEnv* jni = AttachCurrentThreadIfNeeded();
	if (jni == NULL) {
		return;
	}
	g_jvm = NULL;
}

void JNI_onVideoDataReady(FFmpegDemuxDecodeCtx *context, int dataSize, int64_t pts)
{
	if (!context || !context->jm_onVideoFrameDataReady) {
		LOGD(TAG" jm_onVideoFrameDataReady context null");
		return;
	}

	JNIEnv* env = AttachCurrentThreadIfNeeded();
	if (env == NULL) {
		return;
	}

	if (env && env->IsSameObject(context->java_object, NULL) == JNI_FALSE) {
		env->CallVoidMethod(context->java_object, context->jm_onVideoFrameDataReady, dataSize, pts);
	}
}

unsigned char *JNI_MallocByteBufferIfNeed(int width, int height, int rotate)
{
	if (gCtx == NULL) return NULL;
	FFmpegDemuxDecodeCtx *context = gCtx;

	if (!context->jm_malloc_byte_buffer) {
		LOGD(TAG"create bytebufferifneed method not found");
		return NULL;
	}

	if (!context->javaByteBufferObj || context->picH != (uint32_t)height || context->picW != (uint32_t)width) {
		JNIEnv* env = AttachCurrentThreadIfNeeded();
		if (env == NULL) {
			return;
		}

		if (context->javaByteBufferObj) {
			env->DeleteGlobalRef(context->javaByteBufferObj);
			context->javaByteBufferObj = NULL;
			context->byteBuffer = NULL;
			context->picW = 0;
			context->picH = 0;
		}

		if (env && env->IsSameObject(context->java_object, NULL) == JNI_FALSE) {
			jobject obj = env->CallObjectMethod(context->java_object, context->jm_malloc_byte_buffer, width, height, rotate);
			if (NULL != obj) {
				context->javaByteBufferObj = (env)->NewGlobalRef(obj);
				context->byteBuffer = static_cast<unsigned char*>
					(((env))->GetDirectBufferAddress(context->javaByteBufferObj));
				context->picW = width;
				context->picH = height;
			} else {
				LOGE(TAG"Create byte buffer Failed, oom");	
			}
		}
	}
	return context->byteBuffer;
}

static void FFDataCallBackFun(AVMediaType type, AVFrame *pFrame, int rotate)
{
	if (type == AVMEDIA_TYPE_VIDEO) { 
		
		unsigned char *byteBuffer = JNI_MallocByteBufferIfNeed(pFrame->width, pFrame->height, rotate);
		if (!byteBuffer) {
			LOGD(TAG" Create byte buffer failed!");
			return ;
		}
		int bufferSize = pFrame->width * pFrame->height * 3 / 2; 
		int number_of_written_bytes = av_image_copy_to_buffer(byteBuffer, bufferSize,
                                        (const uint8_t* const *)pFrame->data, (const int*) pFrame->linesize,
                                        AV_PIX_FMT_YUV420P, pFrame->width, pFrame->height, 1);
        if (number_of_written_bytes < 0) {
            LOGD(TAG"Can't copy image to buffer\n");
            return number_of_written_bytes;
        }

		JNI_onVideoDataReady(gCtx, number_of_written_bytes, pFrame->pts);
	} 
	
}

static void FFDemuxDataCallBackFun(AVMediaType type, AVPacket *pkt)
{

}

static void FFEventCallBackFun(FFEVENT_CODE event, void *pData)
{
	LOGD(TAG"FFEventCallBackFun event : %d \n", event);
	if (event == FF_EVENT_FINISH) {
		JNI_onVideoDataReady(gCtx, -1, -1);
	}
}

static void FFmpegDemuxDecodeStart(JNIEnv* env, jobject thiz, jlong context, jstring path, jlong cpucnt, jlong cnt, jlong startTime, jlong duration)
{
	FFInitParam param;
	param.decodeCB = FFDataCallBackFun;
	param.demuxCB = FFDemuxDataCallBackFun;
	param.evnetCB = FFEventCallBackFun;
	param.src_file = env->GetStringUTFChars(path , NULL);  //转换char *类型
	param.decType = FF_DEC_TYPE_VIDEO;
	param.demuxType = FF_DEMUX_TYPE_NONE;
	param.CPUCoreCnt = cpucnt;
	param.snapShotCnt = cnt;
	param.startTime = startTime;
	param.duration = duration;
	if (cnt > 0) {
		param.snapShotMode = 1;
	}
	FFmpegDemuxDecoder *dec = new FFmpegDemuxDecoder(&param);
	int ret = dec->start();
	if (ret != 0) {
		LOGE(TAG" FFmpegDemuxDecodeStart failed, ret : 0x%x \n", ret);
	}
	gCtx->mDecoder = dec;
	env->ReleaseStringUTFChars(path , param.src_file);  	//释放局部引用
}

FFmpegDemuxDecodeJni::FFmpegDemuxDecodeJni(FFmpegDemuxDecodeCtx *context)
:m_context(context)
{

}

FFmpegDemuxDecodeJni::~FFmpegDemuxDecodeJni()
{

}

static long FFmpegDemuxDecodeRelease(JNIEnv *env, jobject thiz, jlong context)
{
	LOGT(TAG"release context:0x%x  gCtx:0x%x ", context, gCtx);
	FFmpegDemuxDecodeCtx *bufCtx = (FFmpegDemuxDecodeCtx *)context;
	if (!bufCtx) {
		return -1;
	}

	if (bufCtx->mDecoder != NULL) {
		LOGT(TAG" stop, decoder: %p", bufCtx->mDecoder);
		bufCtx->mDecoder->stop();
		LOGT(TAG" wait, decoder thread exit, 0x%x. ", bufCtx->mDecoder->getThreadId());
		pthread_join(bufCtx->mDecoder->getThreadId(), NULL);
		delete bufCtx->mDecoder;
		bufCtx->mDecoder = NULL;
	}
	
	if(bufCtx->javaByteBufferObj){
		env->DeleteGlobalRef(bufCtx->javaByteBufferObj);
		bufCtx->javaByteBufferObj = NULL;
		bufCtx->byteBuffer = NULL;
	}
	LOGT(TAG" release, context: %p", bufCtx);
	if (bufCtx->mFFDemDec) {
		delete bufCtx->mFFDemDec;
		bufCtx->mFFDemDec = NULL;
	}
	if (bufCtx->java_class) {
		env->DeleteWeakGlobalRef(bufCtx->java_class);
		bufCtx->java_class = NULL;
	}
	if (bufCtx->java_object) {
		env->DeleteGlobalRef(bufCtx->java_object);
		bufCtx->java_object = NULL;
	}
	free(bufCtx);
	bufCtx = NULL;
	return 0;
}


static long FFmpegDemuxDecodeCreateCtx(JNIEnv *env, jobject thiz)
{
	jclass jc_sv = (jclass)env->FindClass(classPath);
	if (!jc_sv) {
		LOGT(TAG"FFmpegDemuxDecode::create failed, ");
		return 0;
	}
	jmethodID jm_malloc_byte_buffer = env->GetMethodID(jc_sv, "MallocByteBuffer", "(III)Ljava/nio/ByteBuffer;");
	if (!jm_malloc_byte_buffer) {
		LOGT(TAG"FFmpegDemuxDecode get MallocByteBuffer method failed.");
		env->DeleteLocalRef(jc_sv);
		return 0;
	}
	jmethodID jm_onVideoFrameDataReady = env->GetMethodID(jc_sv, "onVideoFrameDataReady", "(IJ)V");
	if (!jm_onVideoFrameDataReady) {
		LOGT(TAG"FFmpegDemuxDecode get onVideoFrameDataReady method failed");
		env->DeleteLocalRef(jc_sv);
		return 0;
	}
	FFmpegDemuxDecodeCtx *bufCtx = (FFmpegDemuxDecodeCtx *)malloc(sizeof(FFmpegDemuxDecodeCtx));
	if (!bufCtx) {
		LOGT(TAG"FFmpegDemuxDecode new buffer context error");
		return 0;
	}
	FFmpegDemuxDecodeJni *buf = new FFmpegDemuxDecodeJni(bufCtx);
	if (!buf) {
		LOGT(TAG"FFmpegDemuxDecode new frame buffer error");
		return 0;
	}
	bufCtx->java_class = (jclass)env->NewWeakGlobalRef(jc_sv);
	bufCtx->java_object = (jobject)env->NewGlobalRef(thiz);
	bufCtx->jm_malloc_byte_buffer = jm_malloc_byte_buffer;
	bufCtx->jm_onVideoFrameDataReady = jm_onVideoFrameDataReady;
	bufCtx->mFFDemDec = buf;
	bufCtx->javaByteBufferObj = NULL;
	bufCtx->byteBuffer = NULL;
	bufCtx->picW = 0;
	bufCtx->picH = 0;
	env->DeleteLocalRef(jc_sv);
	gCtx = bufCtx;
	LOGD(TAG"create context ok, context:0x%x \n", gCtx);
	return (jlong)bufCtx;
}

// method called from Java layer
static JNINativeMethod gFFmpegDemuxDecodeMethods[] = {
		{"FFmpegDemuxDecodeStart",	 	"(JLjava/lang/String;JJJJ)V", 	(void*)FFmpegDemuxDecodeStart},
		{"FFmpegDemuxDecodeCreatCtx",	"()J",						(void*)FFmpegDemuxDecodeCreateCtx},	
		{"FFmpegDemuxDecodeRelease",	"(J)J",						(void*)FFmpegDemuxDecodeRelease},
};

extern "C"  void register_FFmpegDemuxDecodeMethod(JNIEnv *env ) {
	JNIHelper::registerNativeMethod(env, classPath,
									gFFmpegDemuxDecodeMethods, YYArrayLen(gFFmpegDemuxDecodeMethods));
}

