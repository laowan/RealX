package com.ycloud.mediacodec.utils;

import android.os.Build;

import com.ycloud.mediacodec.OMXDecoderRank;

public class H264DecoderUtils {

    private static String mCodecName = null;

    static {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {

            OMXDecoderRank.DecoderInfo decoderInfo = OMXDecoderRank.instance().getBestDecoder();
            if (decoderInfo != null) {
                mCodecName = decoderInfo.name();
            }
        }
    }

    public static String getCodecName(){
        return mCodecName;
    }

    public static boolean IsAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }
        return null != mCodecName;
    }
}
