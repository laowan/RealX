package com.ycloud.svplayer;

import com.ycloud.gpuimagefilter.param.ParamUtil;

/**
 * Created by Administrator on 2017/7/19.
 */
public final class MediaConst {
    public static final String[] FRAME_TYPE_TEXT = {"NONE", "PCM", "I420", "NV12", "RGB24", "AAC", "H264", "HEVC", "YYAAC", "YYH264", "YYHEVC"};

    public static final int FRAME_TYPE_NONE   = 0;
    public static final int FRAME_TYPE_PCM    = 1;
    public static final int FRAME_TYPE_I420   = 2;
    public static final int FRAME_TYPE_NV12   = 3;
    public static final int FRAME_TYPE_RGB24  = 4;
    public static final int FRAME_TYPE_AAC    = 5;
    public static final int FRAME_TYPE_H264   = 6;
    public static final int FRAME_TYPE_HEVC   = 7;
    public static final int FRAME_TYPE_YYAAC  = 8;  // without header
    public static final int FRAME_TYPE_YYH264 = 9;  // without start code (0001)
    public static final int FRAME_TYPE_YYHEVC = 10; // without start code (0001)

    public static final int NET_AAC_44K_MONO  = 41;
    public static final int NET_VIDEO_H264    = 2000;
    public static final int NET_VIDEO_H265    = 2002;

    public static final int AAC_FRAME_SAMPLES   = 1024;
    public static final int HEAAC_FRAME_SAMPLES = 2048;

    public static final int MEDIA_EXTRACTOR_TYPE_VIDEO = 0;
    public static final int MEDIA_EXTRACTOR_TYPE_AUDIO = 1;
    public static final int MEDIA_EXTRACTOR_TYPE_SYSTEM = 3;

    public static final int MEDIA_EXTRACTOR_FOR_EXPORT = 0;
    public static final int MEDIA_EXTRACTOR_FOR_PLAYER = 1;

    public static final int MEDIA_TYPE_VIDEO = 0;
    public static final int MEDIA_TYPE_AUDIO = 1;

    // 视频追赶音频时，播放器采取的不同行为，0 ：快速播放渲染视频帧  1：丢帧，直到追赶上音频才开始渲染
    public static final int AV_SYNC_BEHAVIOR_FASTPLAY = 0;
    public static final int AV_SYNC_BEHAVIOR_DROP_FRAME = 1;
}
