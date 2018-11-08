/************************************************************************************
* Copyright (c) 2018,YY.Inc
* All rights reserved.
*
* FileNameï¿?FFmpegDemuxDecode.cpp
* Descriptionï¿?Demux/decode video/audio from a multimedia container by FFmpeg
*
* Versionï¿?1.0
* Authorï¿?Created by jtzhu
* Dateï¿?2018ï¿?1ï¿?2ï¿?*************************************************************************************/

#include <iostream>
#include <stdint.h>
#include <stdio.h>
#include <unistd.h>
#include "FFmpegDemuxDecode.h"
#include "x264/Common.h"
#define INT64_MIN		(-9223372036854775807LL - 1)
#define INT64_MAX		(9223372036854775807LL)

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"

#define TAG "[ymrsdk] FFmpegDemuxDecoder "
#define FFLOG LOGD

/*
 * Demo of using FFmpegDecoder class to demux/demux from video container (MP4 AVI FLV ...)
 */
#if 0
static unsigned int videoFrameCnt = 0, audioFrameCnt = 0;
static unsigned int videoPacketCnt = 0, audioPacketCnt = 0;

void FFDataCallBackFun(AVMediaType type, AVFrame *pFrame)
{
	if (type == AVMEDIA_TYPE_VIDEO) { 			// TODO. copy data from here
		videoFrameCnt++;
		printf("[%d] received video frame data, PTS : %ld \n",videoFrameCnt,pFrame->pkt_pts);
	} else if (type == AVMEDIA_TYPE_AUDIO) {	// TODO. copy data from here
		audioFrameCnt++;
		printf("[%d] received audio frame data, PTS : %ld \n",audioFrameCnt, pFrame->pkt_pts);
	}
}

void FFDemuxDataCallBack(AVMediaType type, AVPacket *pkt)
{
	if (type == AVMEDIA_TYPE_VIDEO) { 			// TODO. copy data from here
		videoPacketCnt++;
		printf("[%d] received video packet data, PTS : %ld \n",videoPacketCnt,pkt->pts);
	} else if (type == AVMEDIA_TYPE_AUDIO) {	// TODO. copy data from here
		audioPacketCnt++;
		printf("[%d] received audio packet data, PTS : %ld \n",audioPacketCnt, pkt->pts);
	}
}

void FFEventCallBackFun(FFEVENT_CODE event, void *pData)
{
	printf("received FFmpegDecoder event %d \n", event);
}

int main() {
	FFInitParam param;
	param.decodeCB = FFDataCallBackFun;
	param.demuxCB = FFDemuxDataCallBack;
	param.evnetCB = FFEventCallBackFun;
	param.src_file = "1.mp4";
	param.decType = FF_DEC_TYPE_VIDEO;   	//  Transcoding only need decode video packet.
	param.demuxType = FF_DEMUX_TYPE_AUDIO;	//	Transcoding no need to decode audio packet,just keep it encoded in packet
	FFmpegDemuxDecoder *dec = new FFmpegDemuxDecoder(&param);
	dec->start();
	pthread_join(dec->getThreadId(), NULL);
	delete dec;
	printf("main exit.");
	return 0;
}
#endif

extern "C" uint32_t FFGetTickCount()
{
	struct timespec tsNowTime;
	clock_gettime(CLOCK_MONOTONIC, &tsNowTime);
	return (uint32_t)((uint64_t)tsNowTime.tv_sec*1000 + (uint64_t)tsNowTime.tv_nsec/1000000);
}

//namespace YYFFmpeg {

FFmpegDemuxDecoder::FFmpegDemuxDecoder(FFInitParam *param) {
	fmt_ctx = NULL;
	video_dec_ctx = NULL;
	audio_dec_ctx = NULL;
	video_stream = NULL;
	audio_stream = NULL;
	video_stream_idx = -1;
	audio_stream_idx = -1;
	frame = NULL;
	av_init_packet(&pkt);
	if (param != NULL) {
		int fileNameLen = 0;
		unsigned char *str = NULL;
		if (param->src_file != NULL) {
			fileNameLen = strlen(param->src_file);
			str = (unsigned char*)malloc(fileNameLen + 1);
			if (str != NULL) {
				strcpy(str,param->src_file);
				str[fileNameLen] = '\0';
			}
		}
		src_filename = str;
		decodeCallBack = param->decodeCB;
		demuxCallBack = param->demuxCB;
		evnetCallBack = param->evnetCB;
		decType = param->decType;
		demuxType = param->demuxType;
		cpu_core_count = param->CPUCoreCnt;
		snapShotMode = param->snapShotMode;
		snapShotCnt = param->snapShotCnt;
		mStartTime = param->startTime;
		mDurationForSnapshot = param->duration;
		LOGD(TAG"decType :%d demuxType:%d cpu_core:%d snapShotMode:%d snapShotCnt:%d stime:%ld drationSnap:%ld.\n",
								decType,demuxType,cpu_core_count,snapShotMode,snapShotCnt, param->startTime, param->duration);
	}
	threadid = -1;
	running = 0;
	decodeTime = 0;
	frame_cnt = 0;
	seek_step = -1;
	seek_target = 0;
	decode_frame_count = 0;
	m_Rotate = 0;
}

FFmpegDemuxDecoder::~FFmpegDemuxDecoder() {

}

int FFmpegDemuxDecoder::start()
{
	int ret = FF_FAILURE;
	if ((ret = DemuxerInit()) != FF_SUCCESS) {
		return ret;
	}
	if ((ret = DecoderInit()) != FF_SUCCESS) {
		return ret;
	}

	return startDemuxDecode();
}

int FFmpegDemuxDecoder::stop()
{
	running = 0;
	return FF_SUCCESS;
}

int FFmpegDemuxDecoder::release()
{
	if (video_dec_ctx != NULL) {
		avcodec_close(video_dec_ctx);
	}
	if (audio_dec_ctx != NULL) {
		avcodec_close(audio_dec_ctx);
	}
	if (fmt_ctx != NULL) {
		avformat_close_input(&fmt_ctx);
	}
	av_frame_free(&frame);
	if (src_filename != NULL) {
		free(src_filename);
	}
	return FF_SUCCESS;
}

int FFmpegDemuxDecoder::openDecoder(int *stream_idx, AVFormatContext *fmt_ctx, enum AVMediaType type)
{
    int ret = -1;
    AVStream *st = NULL;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;
    const char *typeStr = av_get_media_type_string(type);

    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
    	FFLOG(TAG"Could not find %s stream in input file '%s' .\n",typeStr, src_filename);
        return FF_ERROR_STREAM_NOT_FOUND;
    }

	*stream_idx = ret;
	st = fmt_ctx->streams[*stream_idx];

	/* find decoder for the stream */
	dec_ctx = st->codec;
	dec = avcodec_find_decoder(dec_ctx->codec_id);
	if (!dec) {
		FFLOG(TAG"Failed to find %s codec\n",typeStr);
		return FF_ERROR_DECODER_NOT_FOUND;
	}

	dec_ctx->thread_count = cpu_core_count;
	dec_ctx->thread_type = FF_THREAD_FRAME;
	if (snapShotMode == 1) {
		dec_ctx->thread_type = FF_THREAD_SLICE;
		dec_ctx->flags |= CODEC_FLAG_LOW_DELAY;
	}

	if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
		FFLOG(TAG"Failed to open %s codec\n",typeStr);
		return FF_ERROR_OPEN_DECODER;
	}
    return FF_SUCCESS;
}

int FFmpegDemuxDecoder::DemuxerInit()
{
	if (NULL == src_filename) {
		FFLOG(TAG"Could not open source file NULL .\n");
		return FF_ERROR_OPEN_FILE;
	}
	/* register all formats and codecs */
	av_register_all();
	/* open input file, and allocate format context */
	if (avformat_open_input(&fmt_ctx, src_filename, NULL, NULL) < 0) {
		FFLOG(TAG"Could not open source file %s. \n", src_filename);
		return FF_ERROR_OPEN_FILE;
	}
	/* retrieve stream information */
	if (avformat_find_stream_info(fmt_ctx, NULL) < 0) {
		FFLOG(TAG"Could not find stream information. \n");
		return FF_ERROR_FIND_STREAMINFO;
	}

	return FF_SUCCESS;
}

int FFmpegDemuxDecoder::DecoderInit()
{
	if (openDecoder(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
		video_stream = fmt_ctx->streams[video_stream_idx];
		video_dec_ctx = video_stream->codec;
	}

	if (openDecoder(&audio_stream_idx, fmt_ctx, AVMEDIA_TYPE_AUDIO) >= 0) {
		audio_stream = fmt_ctx->streams[audio_stream_idx];
		audio_dec_ctx = audio_stream->codec;
	}

	/* dump input information to stderr */
	av_dump_format(fmt_ctx, 0, src_filename, 0);
	if (!audio_stream && !video_stream) {
		FFLOG(TAG"Could not find audio or video stream in the input, aborting.\n");
		return FF_ERROR_INIT_CONTEXT;
	}

	AVDictionaryEntry *tag = NULL;
	if (video_stream != NULL) {
		tag = av_dict_get(video_stream->metadata, "rotate", tag, 0);
	}
	if (tag != NULL) { 
		m_Rotate = atoi(tag->value) % 360;	
	}  
	
	duration = fmt_ctx->duration;
	double frameRate = 0;
	if (video_dec_ctx != NULL && video_dec_ctx->framerate.den != 0) {
		frameRate = av_q2d(video_dec_ctx->framerate);
	}

	if (snapShotMode == 1) {
		seek_step = duration / snapShotCnt;
		
		// snapshot picture in a time range, not the whole video file.
		if (mStartTime >= 0 && mDurationForSnapshot > 0 && mStartTime < duration) {

			mStartTime = mStartTime * AV_TIME_BASE;
			mDurationForSnapshot = mDurationForSnapshot * AV_TIME_BASE;
		
			if (mStartTime + mDurationForSnapshot > duration ) {
				mDurationForSnapshot = duration - mStartTime;
			}
			
			if (mDurationForSnapshot > 0) {
				seek_step = mDurationForSnapshot / (snapShotCnt); 
				seekTo(mStartTime);
				seek_target += mStartTime;
			} else {
				mDurationForSnapshot = duration - mStartTime;
				seek_step = mDurationForSnapshot / (snapShotCnt); 
				seekTo(mStartTime);
				seek_target += mStartTime;
				FFLOG(TAG"To the last segment of media, mStartTime + mDurationForSnapshot <  duration . new duration : %lld.",mDurationForSnapshot );
			}
		}
	}
	FFLOG(TAG"media duration :%"PRId64" \n", duration);
	FFLOG(TAG"media seek_step :%"PRId64" \n", seek_step);
	FFLOG(TAG"media frame rate:%f rotate:%d \n", frameRate, m_Rotate);
	FFLOG(TAG"media mStartTime :%"PRId64" \n", mStartTime);
	FFLOG(TAG"media mDurationForSnapshot :%"PRId64" \n", mDurationForSnapshot);
	FFLOG(TAG"media seek_target :%"PRId64" \n", seek_target);
	return FF_SUCCESS;
}

int FFmpegDemuxDecoder::startDemuxDecode()
{
	frame = av_frame_alloc();
	if (!frame) {
		FFLOG(TAG"Could not allocate frame.\n");
		return FF_ERROR_NO_MEMORY;
	}
	/* initialize packet, set data to NULL, let the demuxer fill it */
	av_init_packet(&pkt);
	pkt.data = NULL;
	pkt.size = 0;
	running = 1;

	pthread_attr_t attr;
	pthread_attr_init(&attr);
#if 1
    sched_param param;
	int policy = SCHED_RR;
  	int rs = pthread_attr_getschedpolicy(&attr,&policy);
	int priority = sched_get_priority_max(policy);
	pthread_attr_getschedparam(&attr, &param); 
	param.sched_priority = priority;
	pthread_attr_setschedparam(&attr, &param);
#endif
	//pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

    if (pthread_create(&threadid, &attr, &FFmpegDemuxDecoder::demuxDecode_thread, this) < 0) {
    	return FF_ERROR_START_THREAD;
    }

    FFLOG(TAG"Decode demux thread start OK, decType:%d demuxType:%d\n",decType,demuxType);
	return FF_SUCCESS;
}

int FFmpegDemuxDecoder::seekTo(long seek_target) {
	int ret = -1;
	if (pkt.stream_index == video_stream_idx) {	
		int64_t seekTime = av_rescale_q(seek_target,AV_TIME_BASE_Q, fmt_ctx->streams[video_stream_idx]->time_base);
		ret = av_seek_frame(fmt_ctx,video_stream_idx,seekTime, AVSEEK_FLAG_BACKWARD);
		if(ret < 0) {
			LOGE(TAG"seekTo Seeking to %ld failed ret:0x%x \n", seek_target,ret );
		} else {
			LOGI(TAG"seekTo Seeking to %ld OK ret:0x%x seekTime :%lld \n", seek_target,ret,seekTime );
		}
	}
	return ret;
}

int FFmpegDemuxDecoder::seek() {
	int ret = -1;
	if (pkt.stream_index == video_stream_idx) {	
		
		LOGE(TAG"seek seek_target %lld, seek_step:%lld \n", seek_target,seek_step );

	
		seek_target += seek_step;
		int64_t seekTime = av_rescale_q(seek_target, AV_TIME_BASE_Q,fmt_ctx->streams[video_stream_idx]->time_base);
		int64_t seekStreamDuration = fmt_ctx->streams[video_stream_idx]->duration;
		/*
		 * 1. AVSEEK_FLAG_BACKWARD indicates that you want to find closest keyframe having a smaller timestamp than the one you are seeking.
		 *
		 * 2. AVSEEK_FLAG_ANY, you get the frame that corresponds exactly to the timestamp you asked for. But this frame might not be a keyframe, which means that it cannot be fully decoded.
		 */
		int flags = AVSEEK_FLAG_BACKWARD;
#if 0
		if (seekTime> 0 && seekTime < seekStreamDuration)
	   		flags |= AVSEEK_FLAG_ANY; // H.264 I frames don't always register as "keyframes" in FFmpeg
#endif
		ret = av_seek_frame(fmt_ctx,video_stream_idx,seekTime, flags);
		if(ret < 0) {
			LOGE(TAG"seek to %lld failed ret:0x%x \n", seek_target,ret );
			//ret = av_seek_frame(fmt_ctx, video_stream_idx, seekTime,AVSEEK_FLAG_ANY);
		} else {
			LOGE(TAG"seek to %lld ok seekTime:%lld \n", seek_target,seekTime );
		}
	}
	return ret;
}


int FFmpegDemuxDecoder::decodePacket(int *got_frame, int cached)
{
    int ret = 0;
    int decoded = pkt.size;
    *got_frame = 0;
	//int start_time = FFGetTickCount();
    if ((decType == FF_DEC_TYPE_VIDEO || decType == FF_DEC_TYPE_BOTH) && pkt.stream_index == video_stream_idx) {
        /* decode video frame */
        ret = avcodec_decode_video2(video_dec_ctx, frame, got_frame, &pkt);
        if (ret < 0) {
            FFLOG(TAG"Error decoding video frame (%d)\n",ret);
            return ret;
        }
        if (*got_frame) {
			decode_frame_count++;
        	if (decodeCallBack != NULL) {
        		decodeCallBack(AVMEDIA_TYPE_VIDEO, frame, m_Rotate);
        	}
			
			if (snapShotMode == 1) {
				seek();
			}
        }
    } else if ((decType == FF_DEC_TYPE_AUDIO || decType == FF_DEC_TYPE_BOTH) && pkt.stream_index == audio_stream_idx) {
        /* decode audio frame */
        ret = avcodec_decode_audio4(audio_dec_ctx, frame, got_frame, &pkt);
        if (ret < 0) {
        	FFLOG(TAG"Error decoding audio frame (%d)\n", ret);
            return ret;
        }
        /* Some audio decoders decode only part of the packet, and have to be
         * called again with the remainder of the packet data.
         * Sample: fate-suite/lossless-audio/luckynight-partial.shn
         * Also, some decoders might over-read the packet. */
        decoded = FFMIN(ret, pkt.size);
        if (*got_frame) {
        	if (decodeCallBack != NULL) {
        		decodeCallBack(AVMEDIA_TYPE_AUDIO, frame, 0);
			}
        }
    }
	//int end_time = FFGetTickCount();
	//decodeTime += (end_time - start_time);
	//FFLOG(TAG" decode frame cost : %d  total cost : %d avg : %f \n",(end_time - start_time),decodeTime, (float)(decodeTime)/frame_cnt/1000);
    return decoded;
}

unsigned long FFmpegDemuxDecoder::getThreadId()
{
	return threadid;
}

int FFmpegDemuxDecoder::dispenseDemuxPacket(AVPacket *pkt)
{
	if (demuxCallBack == NULL) {
		return FF_FAILURE;
	}
	if ((demuxType == FF_DEMUX_TYPE_VIDEO || demuxType == FF_DEMUX_TYPE_BOTH ) && pkt->stream_index == video_stream_idx) {
		demuxCallBack(AVMEDIA_TYPE_VIDEO, pkt);
	} else if ((demuxType == FF_DEMUX_TYPE_AUDIO || demuxType == FF_DEMUX_TYPE_BOTH ) && pkt->stream_index == audio_stream_idx) {
		demuxCallBack(AVMEDIA_TYPE_AUDIO, pkt);
	}
	return FF_SUCCESS;
}

int cnt = 0;
void* FFmpegDemuxDecoder::demuxDecode_thread(void *arg)
{
	FFmpegDemuxDecoder *thiz = (FFmpegDemuxDecoder *)arg;
	if (thiz == NULL) {
		FFLOG(TAG"Demux and decode thread start Failed.\n");
		return NULL;
	}
	
	if (thiz->evnetCallBack != NULL) {
		thiz->evnetCallBack(FF_EVENT_START, NULL);
	}
	
	int got_frame = 0, ret = -1;
	int start_time = FFGetTickCount();

	 /* read frames from the file */
	while (thiz->running)
	{
		ret = av_read_frame(thiz->fmt_ctx, &thiz->pkt);
		if (ret >= 0 ) 
		{
			AVPacket orig_pkt = thiz->pkt;
			thiz->dispenseDemuxPacket(&thiz->pkt);
		
			if (thiz->snapShotMode == 1 && thiz->decode_frame_count >= thiz->snapShotCnt) {
				LOGD(TAG"snapShotCnt : %d, break. \n", thiz->snapShotCnt);
				break;
			}
			
			/* handle video packet */
			if (thiz->decType == FF_DEC_TYPE_VIDEO && thiz->pkt.stream_index == thiz->video_stream_idx) {
				do {
					ret = thiz->decodePacket(&got_frame, 0);
					if (ret < 0) 
					{
						break;
					}
					thiz->pkt.data += ret;
					thiz->pkt.size -= ret;
				} while (thiz->pkt.size > 0);
			}
			
			/* TODO. handle audio decode */
			
			av_free_packet(&orig_pkt);
		}else {
			LOGD(TAG"av_read_frame ret :0x%x , break.", ret);
			break;
		}
	}


	/* flush cached frames */
	thiz->pkt.data = NULL;
	thiz->pkt.size = 0;
	if (thiz->decType != FF_DEC_TYPE_NONE && thiz->snapShotMode != 1) {
		do {
			thiz->decodePacket(&got_frame, 1);
		} while (got_frame);
	}

	if (thiz->evnetCallBack != NULL) {
		thiz->evnetCallBack(FF_EVENT_FINISH, NULL);
	}
	int end_time = FFGetTickCount();
	int total_cost = end_time-start_time;
	FFLOG(TAG"Demux and decode %s finished. cost :%f s. \n", thiz->src_filename, 
		 									(float)(total_cost)/1000);
	thiz->release();
	return NULL;
}

#pragma GCC diagnostic pop

//} /* namespace YYFFmpeg */
