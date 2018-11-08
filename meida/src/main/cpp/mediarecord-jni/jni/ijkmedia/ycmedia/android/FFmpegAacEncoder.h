#ifndef __FFMPEG_AAC_ENCODER_H__
#define __FFMPEG_AAC_ENCODER_H__

#define __STDC_FORMAT_MACROS 1

#ifdef __cplusplus
extern "C" {
#endif
#include "libavutil/avutil.h"
#include "libavutil/attributes.h"
#include "libavutil/opt.h"
#include "libavutil/mathematics.h"
#include "libavutil/imgutils.h"
#include "libavutil/samplefmt.h"
#include "libavutil/timestamp.h"
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/mathematics.h"
#include "libswresample/swresample.h"
#include "libavutil/channel_layout.h"
#include "libavutil/common.h"
#include "libavformat/avio.h"
#include "libavutil/file.h"
#include "libswresample/swresample.h"
#ifdef __cplusplus
}
#endif

#include <stdio.h>

class FFmpegAacEncoder {
public:
    FFmpegAacEncoder();
    ~FFmpegAacEncoder();
    bool init(int sampleRate, int channelCount, int bitRate);
    void deint();
    int inputFrameSize();
    void pushFrame(uint8_t* data, uint32_t len, int64_t pts );
    uint32_t pullFrame(uint8_t* buffer, uint32_t bufferLen, int64_t& pts);
private:
	AVCodecContext *mAudioEncCtx;
	AVFrame* mFrame;
	AVPacket mPacket;
};

#endif