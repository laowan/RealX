#include "ffmpegextract.h"
#include "Common.h"
#ifdef __cplusplus
extern "C" {
#endif
#include "ffmpeg/include/libavcodec/avcodec.h"
#ifdef __cplusplus
}
#endif
#include <ffmpeg/include/libavcodec/h264.h>
#include <ffmpeg/include/libavcodec/hevc.h>

#define TAG "ffmpegextract "

void h264_extract(void *priv_data, int *width, int *height, int *fps)
{
	//if (LIBAVCODEC_VERSION_INT != 3680612) {              // use private data strongly depends on libavcodec version
	//	LOGE(TAG"libavcodec version(must be 3680612) not match, abort");
	//	abort();
	//}
	H264Context	*h = (H264Context*)priv_data;
	SPS			*sps = h->sps_buffers[0];

	if (sps) {
		*width = sps->mb_width * 16 - sps->crop_left - sps->crop_right;
		*height = sps->mb_height * 16 - sps->crop_top - sps->crop_bottom;

		//prevent from num_units_in_tick == 0, then set fps = 30 as default
		if (sps->num_units_in_tick)
			*fps = sps->time_scale / (2 * sps->num_units_in_tick);
		else
			*fps = 30;
	}
	else {
		*fps = *width = *height = 0;
	}
}

void hevc_extract(void *priv_data, int *width, int *height, int *fps)
{
	//if (LIBAVCODEC_VERSION_INT != 3680612) {              // use private data strongly depends on libavcodec version
	//	LOGE(TAG"libavcodec version(must be 3680612) not match, abort");
	//	abort();
	//}
	HEVCContext   *h = (HEVCContext*)priv_data;
	HEVCSPS       *sps = (HEVCSPS *)h->sps_list[0]->data;
	int           sps_len = h->sps_list[0]->size;

	if (sps && sps_len > 0) {
		//printf("output_width = %d, output_height = %d\n", sps->output_width, sps->output_height);
		//printf("vui_time_scale = %d, vui_num_units_in_tick = %d\n", sps->vui.vui_time_scale, sps->vui.vui_num_units_in_tick);	
		*width = sps->output_width;
		*height = sps->output_height;

		//prevent from vui_num_units_in_tick == 0, then set fps = 30 as default
		if (sps->vui.vui_num_units_in_tick)
			*fps = sps->vui.vui_time_scale / sps->vui.vui_num_units_in_tick;
		else
			*fps = 30;
	}
	else {
		*fps = *width = *height = 0;
	}
}

int ParseVideoHeader(AVCodecID codecId, uint8_t *extradata, int len, int *pwidthNonNull, int *pheightNonNull) {
	int ret = 0;
	AVCodec *codec = avcodec_find_decoder(codecId);
	if (!codec) {
		LOGE(TAG"AVCodecID(%d) Codec not found", codecId);
		ret = -1;
	}

	AVCodecContext *context = avcodec_alloc_context3(codec);
	if (!context) {
		LOGE(TAG"AVCodecID(%d) Could not allocate video codec context", codecId);
		ret = -2;
	}

	if (extradata && len > 0) {
		context->extradata = extradata;
		context->extradata_size = len;
		context->flags |= CODEC_FLAG_GLOBAL_HEADER;
	}

	context->thread_count = 1;
	context->thread_type = FF_THREAD_SLICE;
	if (avcodec_open2(context, codec, NULL) < 0) {
		LOGE(TAG"AVCodecID(%d) Could not open codec", codecId);
		ret = -3;
	}
	if (context) {
		if (codecId == AV_CODEC_ID_H264) {
			h264_extract(context->priv_data, &(context->width), &(context->height), &(context->framerate.den));
		}
		else {
			hevc_extract(context->priv_data, &(context->width), &(context->height), &(context->framerate.den));
		}
		*pwidthNonNull = context->width;
		*pheightNonNull = context->height;
		avcodec_close(context);
		av_free(context);
		context = 0;
	}
	return ret;
}

void registerACCodecAll()
{
	avcodec_register_all();
}
