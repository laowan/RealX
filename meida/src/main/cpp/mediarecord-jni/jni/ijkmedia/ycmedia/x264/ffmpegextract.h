#ifndef FFMPEG_EXTRACT_H
#define FFMPEG_EXTRACT_H

#ifdef __cplusplus
extern "C" {
#endif
#include "ffmpeg/include/libavutil/opt.h"
#include "ffmpeg/include/libavcodec/avcodec.h"
#include "ffmpeg/include/libavutil/channel_layout.h"
#include "ffmpeg/include/libavutil/common.h"
#include "ffmpeg/include/libavutil/imgutils.h"
#include "ffmpeg/include/libavutil/mathematics.h"
#include "ffmpeg/include/libavutil/samplefmt.h"
#ifdef __cplusplus
}
#endif

int ParseVideoHeader(AVCodecID codecId, uint8_t *extradata, int len, int *pwidthNonNull, int *pheightNonNull);
void registerACCodecAll();

#endif // FFMPEG_EXTRACT_H
