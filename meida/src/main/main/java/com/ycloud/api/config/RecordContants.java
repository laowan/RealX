package com.ycloud.api.config;

import com.ycloud.mediacodec.VideoEncoderType;

/**
 * Created by DZHJ on 2017/8/18.
 */

public class RecordContants {

    /*Record Params*/
    public int CAPTURE_VIDEO_WIDTH_PIC;
    public int CAPTURE_VIDEO_HEIGHT_PIC;
    public int RECORD_VIDEO_WIDTH_PIC;
    public int RECORD_VIDEO_HEIGHT_PIC;
    public int CAPTURE_VIDEO_WIDTH;
    public int CAPTURE_VIDEO_HEIGHT;
    public int RECORD_VIDEO_WIDTH;
    public int RECORD_VIDEO_HEIGHT;
    public int RECORD_BITRATE;
    public int RECORD_FRAME_RATE;
    public int RECORD_GOP;

    public int CAPTURE_PICTURE_WIDTH;
    public int CAPTURE_PICTURE_HEIGHT;

    public VideoEncoderType VIDEO_ENCODE_TYPE;

    //transcode and export params
    public int EXPORT_CRF;
    public int EXPORT_BITRATE;
    public int EXPORT_HQ_BITRATE;
    public String EXPORT_PROFILE;
    public String EXPORT_BUFSIZE;
    public int EXPORT_FRAME_RATE;
    public int EXPORT_GOP;

    public int TRANSCODE_FRAME_RATE;
    // 本地视频转码 码率
    public int TRANSCODE_BITRATE;
    public int TRANSCODE_WIDTH;
    public int TRANSCODE_HEIGHT;
    public String EXPORT_PRESET;

    //截图质量
    public int SNAPSHOT_QUALITY;

    //编码次数
    public static int RECORD_ENCODE_ONCE;
    public static int RECORD_ENCODE_TWICE;
    public static int RECORD_SOFT_ENCODE_ONCE;

    //保存到本地的码率
    public static int SAVE_LOCAL_BITRATE;
    public static int SAVE_LOCAL_CRF;
    public static int SAVE_LOCAL_GOP;

    //保存一份音视频数据在内存中
    public static int STORE_DATA_IN_MEMORY;
    public static boolean RECORD_MODE_PICTURE = false;

    //是否使用新的导出模式，如果为true，则强制用新的导出，优先级比其他判断条件（机型，系统）优先级高
    public static boolean USE_MEDIA_EXPORT_SESSION;

    //是否使用CPU版本抠图，默认是false
    public static boolean USE_CPU_SEGMENT_MODE = false;

    public RecordContants() {
          /*Record Params*/
        CAPTURE_VIDEO_WIDTH = 720;
        CAPTURE_VIDEO_HEIGHT = 1280;
        RECORD_VIDEO_WIDTH = 540;//480;
        RECORD_VIDEO_HEIGHT = 960;//640;

        // The default aspect ration is 3:4 in Picture Mode
        CAPTURE_VIDEO_WIDTH_PIC = 720;
        CAPTURE_VIDEO_HEIGHT_PIC = 960;
        RECORD_VIDEO_WIDTH_PIC = 720;//480;
        RECORD_VIDEO_HEIGHT_PIC = 960;//640;



        RECORD_BITRATE = 8 * 1000 * 1000;
        RECORD_FRAME_RATE = 30;
        RECORD_GOP = 1;

        VIDEO_ENCODE_TYPE = VideoEncoderType.HARD_ENCODER_H264;

        //transcode and export params
        EXPORT_CRF = 23;
        EXPORT_BITRATE = (int) (2.5 * 1000 * 1000);
        EXPORT_HQ_BITRATE = (int)(3.5*1000*1000);
        EXPORT_PROFILE = "high";
        EXPORT_BUFSIZE = "5M";
        EXPORT_FRAME_RATE = 30;
        EXPORT_GOP = 5;

        TRANSCODE_FRAME_RATE = 30;
        // 本地视频转码 码率
        TRANSCODE_BITRATE = 24 * 1000 * 1000;
        TRANSCODE_WIDTH = 540;
        TRANSCODE_HEIGHT = 960;
        EXPORT_PRESET = "veryfast";

        //截图质量
        SNAPSHOT_QUALITY = 90;

        //编码次数
        RECORD_ENCODE_ONCE = 1;
        RECORD_ENCODE_TWICE = 2;
        RECORD_SOFT_ENCODE_ONCE = 3;

        //下载到本地视频的参数设置
        SAVE_LOCAL_BITRATE = 6 * 1024 * 1024;
        SAVE_LOCAL_CRF = 21;
        SAVE_LOCAL_GOP = 12;

        //保存一份音视频数据在内存中
        STORE_DATA_IN_MEMORY = 1;

    }
}
