package com.ycloud.mediacodec.utils;

import android.media.MediaCodec;
import android.os.Build;

public class H264SurfaceEncoderUtils {
    static String mCodecName = null;

    static {
        MediaCodec encoder = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {

            try {
                encoder = MediaCodec.createEncoderByType("video/avc");
                if (encoder != null) {
                    mCodecName = encoder.getName();
                    encoder.release();
                    encoder = null;

                    if (isDisabledCodec(mCodecName))
                        mCodecName = null;
                }
            } catch (Throwable t) {
                if (encoder != null) {
                    encoder.release();
                    encoder = null;
                }

                mCodecName = null;
//            YLog.error("H264SurfaceEncoderUtils", "find hard encoder fail, reason:" + t);
            }

            if (encoder != null) {
                encoder.release();
                encoder = null;
            }
        }
    }


    private static boolean isDisabledCodec(String name) {
        if (name.startsWith("OMX.google.")) {
            return true;
        }
        // packet video
        if (name.startsWith("OMX.PV.")) {
            return true;
        }
        if (name.startsWith("OMX.ittiam")) {
            return true;
        }
        if (name.endsWith(".sw.dec")) {
            return true;
        }
        return !name.startsWith("OMX.");
    }

    public static String getCodecName() {
        return mCodecName;
    }

    public static boolean IsAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }
      return mCodecName != null;
  }
}
