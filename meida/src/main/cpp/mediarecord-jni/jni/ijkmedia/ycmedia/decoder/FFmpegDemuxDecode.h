/************************************************************************************
* Copyright (c) 2018,YY.Inc
* All rights reserved.
*
* FileName: FFmpegDemuxDecode.h
* Description: Demux/decode video/audio from a multimedia container by FFmpeg
*
* Version: 1.0
* Author: Created by jtzhu
* Date:2018/01/26
*************************************************************************************/
#ifndef FFMPEGDEMUXDECODE_H_
#define FFMPEGDEMUXDECODE_H_

#define __STDC_FORMAT_MACROS 1

#ifdef __cplusplus
extern "C" {
#endif
#include "libavutil/avutil.h"
#include "libavutil/imgutils.h"
#include "libavutil/samplefmt.h"
#include "libavutil/timestamp.h"
#include "libavformat/avformat.h"
#ifdef __cplusplus
}
#endif

typedef enum {
	FF_FAILURE					=	-1,
	FF_SUCCESS					=	0x00000000,
	FF_ERROR_BASE				=	0x80000000,
	FF_ERROR_OPEN_FILE			=	0x80000001,
	FF_ERROR_FIND_STREAMINFO	=	0x80000002,
	FF_ERROR_STREAM_NOT_FOUND	=	0x80000003,
	FF_ERROR_DECODER_NOT_FOUND	=	0x80000004,
	FF_ERROR_OPEN_DECODER		=	0x80000005,
	FF_ERROR_INIT_CONTEXT		=	0x80000006,
	FF_ERROR_NO_MEMORY			=	0x80000007,
	FF_ERROR_START_THREAD		=	0x80000008,
}FFERROR_CODE;

typedef enum {
	FF_EVENT_START,
	FF_EVENT_STOP,
	FF_EVENT_FINISH,
	FF_EVENT_ERROR,
}FFEVENT_CODE;

typedef enum {
	FF_DEC_TYPE_AUDIO,
	FF_DEC_TYPE_VIDEO,
	FF_DEC_TYPE_BOTH,
	FF_DEC_TYPE_NONE,
}FFDEC_TYPE;

typedef enum {
	FF_DEMUX_TYPE_AUDIO,
	FF_DEMUX_TYPE_VIDEO,
	FF_DEMUX_TYPE_BOTH,
	FF_DEMUX_TYPE_NONE,
}FFDEMUX_TYPE;

typedef void (*FFDecodingDataCallBack)(AVMediaType type, AVFrame *pFrame, int rotate);
typedef void (*FFDemuxingDataCallBack)(AVMediaType type, AVPacket *pkt);
typedef void (*FFEventCallBack)(FFEVENT_CODE event, void *pData);
typedef struct FFInitParam {
	const char *src_file;
	FFDEC_TYPE decType;
	FFDEMUX_TYPE demuxType;
	FFDecodingDataCallBack decodeCB;
	FFDemuxingDataCallBack demuxCB;
	FFEventCallBack evnetCB;
	long CPUCoreCnt;
	int snapShotMode;
	int snapShotCnt;
	long startTime;
	long duration;
}FFInitParam;

//namespace YYFFmpeg {

class FFmpegDemuxDecoder {
public:
	FFmpegDemuxDecoder(FFInitParam *param);
	virtual ~FFmpegDemuxDecoder();
	int start();
	int stop();
	unsigned long getThreadId();

private:
	int DemuxerInit();
	int DecoderInit();
	int startDemuxDecode();
	int openDecoder(int *stream_idx, AVFormatContext *fmt_ctx, enum AVMediaType type);
	int decodePacket(int *got_frame, int cached);
	int dispenseDemuxPacket(AVPacket *pkt);
	int release();
	int seek();
	int seekTo(long target);
	static void* demuxDecode_thread(void *arg);

private:
	AVFrame *frame;
	AVPacket pkt;
	AVFormatContext *fmt_ctx;
	AVCodecContext *video_dec_ctx;
	AVCodecContext *audio_dec_ctx;
	AVStream *video_stream;
	AVStream *audio_stream;
	FFDecodingDataCallBack decodeCallBack;
	FFDemuxingDataCallBack demuxCallBack;
	FFEventCallBack evnetCallBack;
	FFDEC_TYPE decType;
	FFDEMUX_TYPE demuxType;
	const char *src_filename;
	int video_stream_idx;
	int audio_stream_idx;
	int running;
	unsigned long threadid;
	int cpu_core_count;
	int snapShotMode;
	int snapShotCnt;
	int64_t mStartTime;
	int64_t mDurationForSnapshot;
	int64_t seek_step;
	int64_t seek_target;
	int64_t duration;
	int decode_frame_count;
	int decodeTime;
	int frame_cnt;
	int m_Rotate;
};


//} /* namespace YYFFmpeg */

#endif /* FFMPEGDECODER_H_ */
