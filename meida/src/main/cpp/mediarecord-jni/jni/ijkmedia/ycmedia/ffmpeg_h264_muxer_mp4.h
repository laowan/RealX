//
//  ffmpeg_h264_muxer_mp4.h
//  yymediarecordersdk
//
//  Created by bleach on 2018/1/4.
//  Copyright © 2018年 yy.com. All rights reserved.
//

#ifndef ffmpeg_h264_muxer_mp4_h
#define ffmpeg_h264_muxer_mp4_h
#include "yc_ffmpeg.h"

#if defined __cplusplus
extern "C" {
#endif
    
    typedef struct H264MuxerOutputStream {
        AVStream *st;
        AVCodecContext *enc;
        
        /* pts of the next frame that will be generated */
        int64_t next_pts;
        int samples_count;
        
        AVFrame *frame;
        AVFrame *tmp_frame;
        
        float t, tincr, tincr2;
        
        struct SwsContext *sws_ctx;
        struct SwrContext *swr_ctx;
    } H264MuxerOutputStream;
    
    typedef struct H264MuxerHandler {
        int videoPtsInc;
        int audioPtsInc;
        AVFormatContext * formatContext;
        AVOutputFormat * outputFormat;
        int bitrate;
        int width;
        int height;
        int frameRate;

		char* sps;
		int spsSize;
		char* pps;
		int ppsSize;
        char * outputFilePath;

        char* meta;
        H264MuxerOutputStream videoSt;
        H264MuxerOutputStream audioSt;
    } H264MuxerHandler;
    
    /**
     * 设置编码参数
     * @param bitrate
     * @param width
     * @param height
     * @param frameRate
     * @return 返回句柄
     */
    void h264MuxerInitParams(H264MuxerHandler *h264MuxerHandler, const int bitrate, const int width, const int height, const int frameRate);

	void h264AddVideoTrack(H264MuxerHandler *h264MuxerHandler, const int bitrate, const int width, const int height, const int frameRate, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen, const char* meta);
    
    /**
     * 设置mp4输出路径
     * @param outputFilePath 输出路径
     * @param outputFilePathLen 输出路径长度
     */
    H264MuxerHandler * h264MuxerInitOutputPath(const char * outputFilePath, const int outputFilePathLen);
    
    /**
     * 写入h264流
     * @param h264Data h264流
     * @param h264DataLen 输出路径长度
     * @param isKeyFrame 是否关键帧
     * @param spsData
     * @param spsLen
     * @param ppsData
     * @param ppsLen
     */
    void h264MuxerWriteVideo(H264MuxerHandler * h264MuxerHandler, const void * h264Data, const int h264DataLen, const int isKeyFrame, const void * spsData, const int spsLen, const void * ppsData, const int ppsLen, int64_t ptsMs, int64_t dtsMs);
    
    /**
     * 写入aac流
     * @param aacData aac流
     * @param aacDataLen 输出路径长度
     */
    void h264MuxerWriteAudio(H264MuxerHandler * h264MuxerHandler, const void * aacData, const int aacDataLen);

    /**
     * 关闭视频流写入
     */
    void h264MuxerCloseMp4(H264MuxerHandler * h264MuxerHandler);

#if defined __cplusplus
};
#endif

#endif /* ffmpeg_h264_muxer_mp4_h */
