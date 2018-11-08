package com.ycloud.svplayer;

import android.annotation.TargetApi;
import android.os.Build;

import com.ycloud.utils.YYLog;

import java.io.IOException;

/**
 * Created by DZHJ on 2017/8/26.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class DecoderFactory {
    public static final String TAG = DecoderFactory.class.getSimpleName();
    static boolean mUseMediacodec = false;

    static ICodec createDecoderByType(String keyMime, MediaDecoder.CodecType codecType) throws IOException {
        if(codecType.equals(MediaDecoder.CodecType.VIDEO)) {
            if (mUseMediacodec == true) {
                return new MediaCodecWrapper(keyMime);
            } else {
                return new FfmpegCodecWrapper();
            }
        } else {
            return  new MediaCodecWrapper(keyMime);
        }
    }

    public static void setDecodeMode(boolean useMediacodec){
        YYLog.info(TAG,"setDecodeMode:" + useMediacodec);
        mUseMediacodec = useMediacodec;
    }
}
