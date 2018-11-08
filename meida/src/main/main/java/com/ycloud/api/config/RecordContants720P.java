package com.ycloud.api.config;

/**
 * Created by DZHJ on 2017/4/21.
 */

public class RecordContants720P extends  RecordContants {

    public RecordContants720P(){
        super();
        RECORD_VIDEO_WIDTH = 720;//480;
        RECORD_VIDEO_HEIGHT = 1280;//640;
        RECORD_BITRATE = 30*1000 * 1000;

        EXPORT_CRF = 25;
        EXPORT_BITRATE = 3*1000*1000;
        TRANSCODE_BITRATE = 30*1000 * 1000;

        CAPTURE_VIDEO_WIDTH_PIC = 960;
        CAPTURE_VIDEO_HEIGHT_PIC = 1280;  // 保证4：3
    }
}
