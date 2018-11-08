package com.ycloud.api.config;

/**
 * Created by Administrator on 2018/5/14.
 */

public class RecordContants1080P extends RecordContants {
    public RecordContants1080P(){  // for Picture mode
        super();
        CAPTURE_VIDEO_WIDTH_PIC = 1080;
        CAPTURE_VIDEO_HEIGHT_PIC = 1440; // 保证4：3
    }
}
