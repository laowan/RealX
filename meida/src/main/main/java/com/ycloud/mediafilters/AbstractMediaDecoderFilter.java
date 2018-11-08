package com.ycloud.mediafilters;

import android.media.MediaFormat;

/**
 * Created by Administrator on 2018/1/3.
 */

public class AbstractMediaDecoderFilter extends AbstractYYMediaFilter
{
    protected  MediaFormat     mMediaFormat;

    public  void initDecoder(MediaFormat format) {
        mMediaFormat = format;
    }

    public void decodeFrame(){};

    public void releaseDecoder() {
    }
}
