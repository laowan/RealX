/************************************************************************************
* Copyright (c) 2018,YY.Inc
* All rights reserved.
*
* FileName： FFmpegDemuxDecodeJNI.h
* Description： FFmpegDemuxDecode JNI layer
*
* Version： 1.0
* Author： Created by jtzhu
* Date： 2018年01月26日
*************************************************************************************/
#ifndef FFMPEG_DEMUX_DECODE_JNI_H
#define FFMPEG_DEMUX_DECODE_JNI_H

#include <stdint.h>
#include <jni.h>
#include <pthread.h>
#include <string>
#include "FFmpegDemuxDecode.h"

struct FFmpegDemuxDecodeCtx;

class FFmpegDemuxDecodeJni {
	public:
		FFmpegDemuxDecodeJni(FFmpegDemuxDecodeCtx *ctx);
		~FFmpegDemuxDecodeJni();	
	
	private:
		FFmpegDemuxDecodeCtx *m_context;
};

struct FFmpegDemuxDecodeCtx
{
	jclass 	java_class;
	jobject java_object;
	jobject javaByteBufferObj;
	jmethodID jm_malloc_byte_buffer;
	jmethodID jm_onVideoFrameDataReady;

	unsigned char* byteBuffer;
	unsigned int picW;
	unsigned int picH;
	FFmpegDemuxDecodeJni *mFFDemDec;
	FFmpegDemuxDecoder *mDecoder;
};


#endif

