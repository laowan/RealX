#ifndef __FFMPEG_AUDIO_FILE_READER_H__
#define __FFMPEG_AUDIO_FILE_READER_H__

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


class FFmpegAudioFileReader{
public:
    FFmpegAudioFileReader(uint32_t outSampleRate, uint32_t outChannelCount);
    ~FFmpegAudioFileReader();
    bool open(const char* pPath);
    uint32_t lenInMS();
    uint32_t sampleRate();
    uint32_t channelCount();
    uint32_t currentPositionInMS();
    void seek(uint32_t positionInMS);
    uint32_t readFrame(uint8_t* buffer, uint32_t bufferLen);
private:
    bool _openDecoder(int *stream_idx, AVFormatContext *fmt_ctx, enum AVMediaType type);
    void _close();
    uint32_t mOutSampleRate;
    uint32_t mOutChannelCount;
	AVFormatContext *mFmtCtx;
	AVCodecContext *mAudioDecCtx;
	AVStream *mAudioStream;
	int mAudioStreamIdx;
	AVFrame *mFrame;
	AVPacket* mPkt;
	SwrContext *mSwrCtx;
};

#endif