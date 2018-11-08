package com.ycloud.utils;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.svplayer.MediaConst;

/**
 * Created by Administrator on 2018/3/9.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaFormatHelper {

    public final static String h264Mine = "video/avc";
    public final static String h265Mine = "video/hevc";

    public static int getFrameFormat(MediaFormat format) {
        if(format == null)
            return MediaConst.FRAME_TYPE_NONE;

        String mime = format.getString(MediaFormat.KEY_MIME);
        if(mime == null && mime.isEmpty()) {
            return MediaConst.FRAME_TYPE_NONE;
        }

        if(mime.equals(h264Mine)) {
            return MediaConst.FRAME_TYPE_H264;
        } else if(mime.equals(h265Mine)) {
            return MediaConst.FRAME_TYPE_HEVC;
        } //aac.

        return MediaConst.FRAME_TYPE_NONE;
    }
}
