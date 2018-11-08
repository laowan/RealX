#include "ffmpeg_h264_muxer_mp4.h"
#include <stdlib.h>
#include <stdio.h>
#include "libavutil/opt.h"
#include "libavutil/mathematics.h"
#include "libavutil/timestamp.h"
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavutil/avassert.h"


#include <android/log.h>

#define LOGX_TAG "ffmux"

#define  LOGD(...)      __android_log_print(ANDROID_LOG_DEBUG,LOGX_TAG,__VA_ARGS__)
#define  LOGI(...)      __android_log_print(ANDROID_LOG_DEBUG,LOGX_TAG,__VA_ARGS__)
#define  LOGE(...)      __android_log_print(ANDROID_LOG_DEBUG,LOGX_TAG,__VA_ARGS__)
#define  LOGT(...)      __android_log_print(ANDROID_LOG_DEBUG,LOGX_TAG,__VA_ARGS__)



#define H264_MUXER_SCALE_FLAGS SWS_BICUBIC
#define H264_MUXER_STREAM_DURATION 10.0


int h264MuxerCreateMp4(H264MuxerHandler * h264MuxerHandler, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen);


static AVFrame * h264MuxerAllocPicture(enum AVPixelFormat pix_fmt, int width, int height) {
    AVFrame * picture = NULL;
    int ret;
    
    picture = av_frame_alloc();
    if (!picture) {
        return NULL;
    }
    
    picture->format = pix_fmt;
    picture->width  = width;
    picture->height = height;
    
    /* allocate the buffers for the frame data */
    ret = av_frame_get_buffer(picture, 32);
    if (ret < 0) {
        fprintf(stderr, "Could not allocate frame data.\n");
        return NULL;
    }
    
    return picture;
}

static AVFrame * h264MuxerAllocAudioFrame(enum AVSampleFormat sample_fmt, uint64_t channel_layout, int sample_rate, int nb_samples) {
    AVFrame * frame = av_frame_alloc();
    int ret;
    
    if (!frame) {
        fprintf(stderr, "Error allocating an audio frame\n");
        return NULL;
    }
    
    frame->format = sample_fmt;
    frame->channel_layout = channel_layout;
    frame->sample_rate = sample_rate;
    frame->nb_samples = nb_samples;
    
    if (nb_samples) {
        ret = av_frame_get_buffer(frame, 0);
        if (ret < 0) {
            fprintf(stderr, "Error allocating an audio buffer\n");
            return NULL;
        }
    }
    
    return frame;
}

/* Add an output stream. */
static void h264MuxerAddStream(H264MuxerHandler * h264MuxerHandler, H264MuxerOutputStream *ost, AVFormatContext *oc, AVCodec **codec, enum AVCodecID codec_id) {
    AVCodecContext * c = NULL;
    int i;
    
    /* find the encoder */
    *codec = avcodec_find_encoder(codec_id);
    if (!(*codec)) {
        fprintf(stderr, "Could not find encoder for '%s'\n",
                avcodec_get_name(codec_id));
        return;
    }
    
    ost->st = avformat_new_stream(oc, NULL);
    if (!ost->st) {
        fprintf(stderr, "Could not allocate stream\n");
        return;
    }
    ost->st->id = oc->nb_streams-1;
    c = avcodec_alloc_context3(*codec);
    if (!c) {
        fprintf(stderr, "Could not alloc an encoding context\n");
        return;
    }
    ost->enc = c;
    
    switch ((*codec)->type) {
        case AVMEDIA_TYPE_AUDIO:
            c->sample_fmt  = (*codec)->sample_fmts ?
            (*codec)->sample_fmts[0] : AV_SAMPLE_FMT_FLTP;
            c->bit_rate    = 64000;
            c->sample_rate = 44100;
            if ((*codec)->supported_samplerates) {
                c->sample_rate = (*codec)->supported_samplerates[0];
                for (i = 0; (*codec)->supported_samplerates[i]; i++) {
                    if ((*codec)->supported_samplerates[i] == 44100)
                        c->sample_rate = 44100;
                }
            }
            c->channels       = 2;//av_get_channel_layout_nb_channels(c->channel_layout);
            c->channel_layout = AV_CH_LAYOUT_STEREO;
            /*
            if ((*codec)->channel_layouts) {
                c->channel_layout = (*codec)->channel_layouts[0];
                for (i = 0; (*codec)->channel_layouts[i]; i++) {
                    if ((*codec)->channel_layouts[i] == AV_CH_LAYOUT_STEREO)
                        c->channel_layout = AV_CH_LAYOUT_STEREO;
                }
            }
            c->channels        = av_get_channel_layout_nb_channels(c->channel_layout);
            */
            ost->st->time_base = (AVRational){ 1, c->sample_rate };
            break;
            
        case AVMEDIA_TYPE_VIDEO:
            c->codec_id = codec_id;
            
            c->bit_rate = h264MuxerHandler->bitrate;
            /* Resolution must be a multiple of two. */
            c->width    = h264MuxerHandler->width;
            c->height   = h264MuxerHandler->height;
            /* timebase: This is the fundamental unit of time (in seconds) in terms
             * of which frame timestamps are represented. For fixed-fps content,
             * timebase should be 1/framerate and timestamp increments should be
             * identical to 1. */
            ost->st->time_base = (AVRational){ 1, h264MuxerHandler->frameRate };
            c->time_base       = ost->st->time_base;
            
            c->gop_size      = 5; /* emit one intra frame every twelve frames at most */
            c->pix_fmt       = AV_PIX_FMT_YUV420P;
            if (c->codec_id == AV_CODEC_ID_MPEG2VIDEO) {
                /* just for testing, we also add B-frames */
                c->max_b_frames = 2;
            }
            if (c->codec_id == AV_CODEC_ID_MPEG1VIDEO) {
                /* Needed to avoid using macroblocks in which some coeffs overflow.
                 * This does not happen with normal video, it just happens here as
                 * the motion of the chroma plane does not match the luma plane. */
                c->mb_decision = 2;
            }
            
            break;
            
        default:
            break;
    }
    
    /* Some formats want stream headers to be separate. */
    if (oc->oformat->flags & AVFMT_GLOBALHEADER) {
        c->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
}

static void h264MuxerSetStartCode(unsigned char * data) {
    data[0] = 0x0;
    data[1] = 0x0;
    data[2] = 0x0;
    data[3] = 0x01;
}

static int startWithStartCode(unsigned char* data, int len) {
	if(len < 4 || data == NULL)
		return 0;

	if(data[0] == 0x00 && data[1] == 0x00) {
		return ((data[2] == 0x00 && data[3] == 0x01) || (data[2] == 0x01));
	}

	return 0;
}



static void h264MuxerOpenVideo(AVFormatContext *oc, AVCodec *codec, H264MuxerOutputStream *ost, AVDictionary *opt_arg, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen) {
    int ret;
    AVCodecContext * c = ost->enc;
    AVDictionary * opt = NULL;
    
    av_dict_copy(&opt, opt_arg, 0);
    
    /* open the codec */
    ret = avcodec_open2(c, codec, &opt);
    unsigned char * sps_pps = (unsigned char *)malloc(sizeof(unsigned char) * (8 + spsLen + ppsLen));

	int sps_pps_len = 0;

	if(!startWithStartCode(spsData, spsLen)) {
		h264MuxerSetStartCode(sps_pps);
		sps_pps_len += 4;
	}
	memcpy(&sps_pps[sps_pps_len], spsData, spsLen);
	sps_pps_len += spsLen;

	if(!startWithStartCode(ppsData,ppsLen)) {
		h264MuxerSetStartCode(&sps_pps[sps_pps_len]);
		sps_pps_len += 4;
	}
	memcpy(&sps_pps[sps_pps_len], ppsData, ppsLen);
	sps_pps_len += ppsLen;

	LOGD("sps_pps_len=%d", sps_pps_len);

    c->extradata_size = sps_pps_len;
    c->extradata = (uint8_t*)av_malloc(sps_pps_len + AV_INPUT_BUFFER_PADDING_SIZE);
    memcpy(c->extradata, sps_pps, sps_pps_len);

    if (sps_pps != NULL) {
        free(sps_pps);
    }
	
    av_dict_free(&opt);
    if (ret < 0) {
		LOGE("Could not open video codec: %s\n", av_err2str(ret));
        //fprintf(stderr, "Could not open video codec: %s\n", av_err2str(ret));
        return;
    }
    
    /* allocate and init a re-usable frame */
    ost->frame = h264MuxerAllocPicture(c->pix_fmt, c->width, c->height);
    if (!ost->frame) {
		LOGE("Could not allocate video frame");
        //fprintf(stderr, "Could not allocate video frame\n");
        return;
    }
    
    /* If the output format is not YUV420P, then a temporary YUV420P
     * picture is needed too. It is then converted to the required
     * output format. */
    ost->tmp_frame = NULL;
    if (c->pix_fmt != AV_PIX_FMT_YUV420P) {
        ost->tmp_frame = h264MuxerAllocPicture(AV_PIX_FMT_YUV420P, c->width, c->height);
        if (!ost->tmp_frame) {
			LOGE("Could not allocate temporary picture");
            //fprintf(stderr, "Could not allocate temporary picture\n");
            return;
        }
    }
    
    /* copy the stream parameters to the muxer */
    ret = avcodec_parameters_from_context(ost->st->codecpar, c);
    if (ret < 0) {
        fprintf(stderr, "Could not copy the stream parameters\n");
        return;
    }
}

static void h264MuxerOpenAudio(AVFormatContext *oc, AVCodec *codec, H264MuxerOutputStream *ost, AVDictionary *opt_arg) {
    AVCodecContext * c = ost->enc;
    int nb_samples;
    int ret;
    AVDictionary * opt = NULL;
    
    /* open it */
    av_dict_copy(&opt, opt_arg, 0);
    ret = avcodec_open2(c, codec, &opt);
    av_dict_free(&opt);
    if (ret < 0) {
        fprintf(stderr, "Could not open audio codec: %s\n", av_err2str(ret));
        return;
    }
    
    /* init signal generator */
    ost->t     = 0;
    ost->tincr = 2 * M_PI * 110.0 / c->sample_rate;
    /* increment frequency by 110 Hz per second */
    ost->tincr2 = 2 * M_PI * 110.0 / c->sample_rate / c->sample_rate;
    
    if (c->codec->capabilities & AV_CODEC_CAP_VARIABLE_FRAME_SIZE) {
        nb_samples = 10000;
    } else {
        nb_samples = c->frame_size;
    }
    
    ost->frame     = h264MuxerAllocAudioFrame(c->sample_fmt, c->channel_layout, c->sample_rate, nb_samples);
    ost->tmp_frame = h264MuxerAllocAudioFrame(AV_SAMPLE_FMT_S16, c->channel_layout, c->sample_rate, nb_samples);
    
    /* copy the stream parameters to the muxer */
    ret = avcodec_parameters_from_context(ost->st->codecpar, c);
    if (ret < 0) {
		LOGE("Could not copy the stream parameters\n");
        //fprintf(stderr, "Could not copy the stream parameters\n");
        return;
    }
    
    /* create resampler context */
    ost->swr_ctx = swr_alloc();
    if (!ost->swr_ctx) {
		LOGE("Could not allocate resampler context\n");
        //fprintf(stderr, "Could not allocate resampler context\n");
        return;
    }
    
    /* set options */
    av_opt_set_int       (ost->swr_ctx, "in_channel_count",   c->channels,       0);
    av_opt_set_int       (ost->swr_ctx, "in_sample_rate",     c->sample_rate,    0);
    av_opt_set_sample_fmt(ost->swr_ctx, "in_sample_fmt",      AV_SAMPLE_FMT_S16, 0);
    av_opt_set_int       (ost->swr_ctx, "out_channel_count",  c->channels,       0);
    av_opt_set_int       (ost->swr_ctx, "out_sample_rate",    c->sample_rate,    0);
    av_opt_set_sample_fmt(ost->swr_ctx, "out_sample_fmt",     c->sample_fmt,     0);
    
    /* initialize the resampling context */
    if ((ret = swr_init(ost->swr_ctx)) < 0) {
        fprintf(stderr, "Failed to initialize the resampling context\n");
        return;
    }
}

void h264AddVideoTrack(H264MuxerHandler *h264MuxerHandler, const int bitrate, const int width, const int height, const int frameRate, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen, const char* meta)
{
    h264MuxerHandler->bitrate = bitrate;
    h264MuxerHandler->width = width;
    h264MuxerHandler->height = height;
    h264MuxerHandler->frameRate = frameRate;
    h264MuxerHandler->meta = meta;

	h264MuxerCreateMp4(h264MuxerHandler, spsData, spsLen, ppsData, ppsLen);
}

void h264MuxerInitParams(H264MuxerHandler *h264MuxerHandler, const int bitrate, const int width, const int height, const int frameRate) {
    //H264MuxerHandler * h264MuxerHandler = malloc(sizeof(H264MuxerHandler));
    //if (h264MuxerHandler == NULL) {
    //    return NULL;
   // }
    
    //h264MuxerHandler->formatContext = NULL;
    //h264MuxerHandler->outputFormat = NULL;
    h264MuxerHandler->bitrate = bitrate;
    h264MuxerHandler->width = width;
    h264MuxerHandler->height = height;
    h264MuxerHandler->frameRate = frameRate;
    //H264MuxerOutputStream videoSt = {0};
    //h264MuxerHandler->videoSt = videoSt;
    //H264MuxerOutputStream audioSt = {0};
    //h264MuxerHandler->audioSt = audioSt;
   
    //return h264MuxerHandler;
}

H264MuxerHandler * h264MuxerInitOutputPath(const char * outputFilePath, const int outputFilePathLen) {

	H264MuxerHandler * h264MuxerHandler = malloc(sizeof(H264MuxerHandler));
	 if (h264MuxerHandler == NULL) {
		 return NULL;
	 }

	h264MuxerHandler->formatContext = NULL;
    h264MuxerHandler->outputFormat = NULL;
    H264MuxerOutputStream videoSt = {0};
    h264MuxerHandler->videoSt = videoSt;
    H264MuxerOutputStream audioSt = {0};
    h264MuxerHandler->audioSt = audioSt;

    h264MuxerHandler->outputFilePath = malloc(sizeof(char) * 1024);
    memset(h264MuxerHandler->outputFilePath, 0, sizeof(char) * 1024);
    memcpy(h264MuxerHandler->outputFilePath, outputFilePath, outputFilePathLen);
    h264MuxerHandler->videoPtsInc = 0;
    h264MuxerHandler->audioPtsInc = 0;
    
    return h264MuxerHandler;
}

int h264MuxerCreateMp4(H264MuxerHandler * h264MuxerHandler, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen) {
    int ret = 0; // 成功返回0，失败返回-1
    
    do {
        if (h264MuxerHandler->formatContext != NULL) {
            ret = 0;
            break;
        }
        
        AVCodec * video_codec = NULL;
        AVCodec * audio_codec = NULL;
        AVDictionary * opt = NULL;
        
        av_register_all();
        
        /* allocate the output media context */
        avformat_alloc_output_context2(&(h264MuxerHandler->formatContext), NULL, NULL, h264MuxerHandler->outputFilePath);
        if (!h264MuxerHandler->formatContext) {
			LOGE("Could not deduce output format from file extension: using MPEG.\n");
            //printf("Could not deduce output format from file extension: using MPEG.\n");
            avformat_alloc_output_context2(&(h264MuxerHandler->formatContext), NULL, "mpeg", h264MuxerHandler->outputFilePath);
        }
        if (!h264MuxerHandler->formatContext) {
            ret = -1;
            break;
        }
        
        h264MuxerHandler->outputFormat = h264MuxerHandler->formatContext->oformat;
        
        /* Add the audio and video streams using the default format codecs
         * and initialize the codecs. */
        if (h264MuxerHandler->outputFormat->video_codec != AV_CODEC_ID_NONE) {
            h264MuxerAddStream(h264MuxerHandler, &(h264MuxerHandler->videoSt), h264MuxerHandler->formatContext, &video_codec, h264MuxerHandler->outputFormat->video_codec);
            h264MuxerOpenVideo(h264MuxerHandler->formatContext, video_codec, &(h264MuxerHandler->videoSt), opt, spsData, spsLen, ppsData, ppsLen);
        }
        if (h264MuxerHandler->outputFormat->audio_codec != AV_CODEC_ID_NONE) {
            h264MuxerAddStream(h264MuxerHandler, &(h264MuxerHandler->audioSt), h264MuxerHandler->formatContext, &audio_codec, h264MuxerHandler->outputFormat->audio_codec);
            h264MuxerOpenAudio(h264MuxerHandler->formatContext, audio_codec, &(h264MuxerHandler->audioSt), opt);
        }
        
        av_dump_format(h264MuxerHandler->formatContext, 0, h264MuxerHandler->outputFilePath, 1);
        
        /* open the output file, if needed */
        if (!(h264MuxerHandler->outputFormat->flags & AVFMT_NOFILE)) {
            ret = avio_open(&(h264MuxerHandler->formatContext->pb), h264MuxerHandler->outputFilePath, AVIO_FLAG_WRITE);
            if (ret < 0) {
				LOGE( "Could not open '%s': %s\n", h264MuxerHandler->outputFilePath, av_err2str(ret));
                //fprintf(stderr, "Could not open '%s': %s\n", h264MuxerHandler->outputFilePath, av_err2str(ret));
                ret = -1;
                break;
            }
        }
        
        /* Write the stream header, if any. */
        av_dict_set(&opt, "movflags", "faststart", 0);

        /* Write MP4 Meta Data. */
        AVDictionary *pMetaData = NULL;
        av_dict_set(&pMetaData, "comment", h264MuxerHandler->meta, 0);
        //LOGD("jyq test meta is %s\n", h264MuxerHandler->meta);
        h264MuxerHandler->formatContext->metadata = pMetaData;

        ret = avformat_write_header(h264MuxerHandler->formatContext, &opt);
        if (ret < 0) {
			LOGE("Error occurred when opening output file: %s\n", av_err2str(ret));
            //fprintf(stderr, "Error occurred when opening output file: %s\n", av_err2str(ret));
            ret = -1;
            break;
        }
        
    } while (0);
    
    if (h264MuxerHandler->outputFilePath != NULL) {
        free(h264MuxerHandler->outputFilePath);
        h264MuxerHandler->outputFilePath = NULL;
    }
    
    return ret;
}

void h264MuxerWriteVideo(H264MuxerHandler * h264MuxerHandler, const void * h264Data, const int h264DataLen, 
								const int isKeyFrame, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen,
								int64_t ptsMs, int64_t dtsMs) {
    if (h264MuxerHandler == NULL) {
		LOGE("Error h264MuxerHandler is null");
        //fprintf(stderr, "Error h264MuxerHandler is null\n");
        return;
    }
    
    if (h264Data == NULL || h264DataLen == 0) {
		LOGE("Error h264 no data");
        //fprintf(stderr, "Error h264 no data\n");
        return;
    }
    
    h264MuxerCreateMp4(h264MuxerHandler, spsData, spsLen, ppsData, ppsLen);
    
    if (h264MuxerHandler->formatContext == NULL) {
        return;
    }
    
    int ret;
    AVPacket pkt = { 0 };

    H264MuxerOutputStream * videoSt = &(h264MuxerHandler->videoSt);
    AVCodecContext * c = videoSt->enc;
    videoSt->frame->pts = videoSt->next_pts++;
    
    av_init_packet(&pkt);
    pkt.data = (uint8_t *)h264Data;
    pkt.size = h264DataLen;
    if (isKeyFrame) {
        pkt.flags |= AV_PKT_FLAG_KEY;
    } else {
        pkt.flags |= 0;
    }

    //pkt.pts = videoSt->frame->pts;
    pkt.pts = ptsMs*1000;
	pkt.dts = dtsMs*1000;
    av_packet_rescale_ts(&pkt, AV_TIME_BASE_Q, videoSt->st->time_base);


	//int32_t ts = av_rescale_q(pkt.pts, videoSt->st->time_base, AV_TIME_BASE_Q) / 1000;
	//int32_t ds= av_rescale_q(pkt.dts, videoSt->st->time_base, AV_TIME_BASE_Q) / 1000;
	//LOGD("[ffmux] h264MuxerWriteVideo, orig_pts=%u orig_dts=%u pkt_pts=%u pkt_dts=%u", (int32_t)ptsMs, (int32_t)dtsMs, ts, ds);
	

    pkt.pos = -1;
    pkt.stream_index = videoSt->st->index;
    ret = av_interleaved_write_frame(h264MuxerHandler->formatContext, &pkt);
    if (ret < 0) {
		LOGE("cannot write video frame");
        //fprintf(stderr, "cannot write video frame\n");
    }
}

void h264MuxerWriteAudio(H264MuxerHandler * h264MuxerHandler, const void * aacData, const int aacDataLen) {
    if (h264MuxerHandler == NULL) {
		LOGE("Error h264MuxerHandler is null");
        //fprintf(stderr, "Error h264MuxerHandler is null\n");
        return;
    }
    
    if (aacData == NULL || aacDataLen == 0) {
		LOGE("Error aac no data");
        //fprintf(stderr, "Error aac no data\n");
        return;
    }
    
    if (h264MuxerHandler->formatContext == NULL) {
        return;
    }
    
    AVPacket pkt = { 0 }; // data and size must be 0;
    int ret;

    H264MuxerOutputStream * audioSt = &(h264MuxerHandler->audioSt);
    AVCodecContext * c = audioSt->enc;
    av_init_packet(&pkt);
    pkt.data = (unsigned char *)aacData;
    pkt.size = aacDataLen;

    audioSt->frame->pts = audioSt->next_pts;
    audioSt->next_pts  += audioSt->samples_count;

    pkt.pts = av_rescale_q(audioSt->samples_count, (AVRational){1, c->sample_rate}, c->time_base);
    audioSt->samples_count += audioSt->frame->nb_samples;
    av_packet_rescale_ts(&pkt, c->time_base, audioSt->st->time_base);
    pkt.pos = -1;
    pkt.stream_index = audioSt->st->index;
    pkt.duration = audioSt->frame->nb_samples;
    ret = av_interleaved_write_frame(h264MuxerHandler->formatContext, &pkt);
    if (ret < 0) {
		LOGE("cannot write audio frame");
        //fprintf(stderr, "cannot write audio frame\n");
    }
}

static void h264MuxerCloseStream(AVFormatContext *oc, H264MuxerOutputStream *ost) {
    avcodec_free_context(&ost->enc);
    av_frame_free(&ost->frame);
    av_frame_free(&ost->tmp_frame);
    sws_freeContext(ost->sws_ctx);
    swr_free(&ost->swr_ctx);
}

void h264MuxerCloseMp4(H264MuxerHandler * h264MuxerHandler) {
    if (h264MuxerHandler == NULL) {
		LOGE("Error h264MuxerHandler is null");
        //fprintf(stderr, "Error h264MuxerHandler is null\n");
        return;
    }
    
    if (h264MuxerHandler->formatContext) {
        av_write_trailer(h264MuxerHandler->formatContext);
    }
    
    h264MuxerCloseStream(h264MuxerHandler->formatContext, &(h264MuxerHandler->videoSt));
    
    h264MuxerCloseStream(h264MuxerHandler->formatContext, &(h264MuxerHandler->audioSt));

    if (!(h264MuxerHandler->outputFormat->flags & AVFMT_NOFILE)) {
        /* Close the output file. */
        avio_closep(&(h264MuxerHandler->formatContext->pb));
    }
    
    if (h264MuxerHandler->formatContext) {
        /* free the stream */
        avformat_free_context(h264MuxerHandler->formatContext);
    }
    
    if (h264MuxerHandler->outputFilePath == NULL) {
        free(h264MuxerHandler->outputFilePath);
        h264MuxerHandler->outputFilePath = NULL;
    }
    
    free(h264MuxerHandler);
    h264MuxerHandler = NULL;
}
