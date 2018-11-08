package com.ycloud.mediacodec.utils;

import com.ycloud.api.common.CodecMode;

/**
 * Created by DZHJ on 2017/3/20.
 */

public class MediacodecUtils {


    /**
     * 是否使用mediacodec编码
     *
     * @param codecmode
     */
    public static boolean getCodecType(CodecMode codecmode) {
        switch (codecmode) {
            case AUTO:
                if (HwCodecConfig.isHw264DecodeEnabled() && HwCodecConfig.isHw264EncodeEnabled()) {
                    return true;
                } else {
                    return false;
                }

            case MEDIACODEC:
                return true;

            case FFMPEG:
                return false;

            default:
                return false;
        }
    }
}
