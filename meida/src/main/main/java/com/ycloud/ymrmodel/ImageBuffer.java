package com.ycloud.ymrmodel;

import android.graphics.ImageFormat;

import com.ycloud.api.common.SampleType;

import java.nio.ByteBuffer;

/**
 * Created by kele on 2016/12/19.
 */

public class ImageBuffer extends YYMediaSampleBase
{
    //都只是yuv.
    public int mWidth = 0;
    public int mHeight = 0;
    public ByteBuffer mDataBuffer = null;
    public int mImageFormat = ImageFormat.NV21;

    public ImageBuffer() {
        mSampleType = SampleType.VIDEO;
    }

    public int imageSize() { return (mWidth*mHeight* ImageFormat.getBitsPerPixel(mImageFormat)/8);}

    public void clear() {
        if(mDataBuffer != null) {
            mDataBuffer.clear();
            mDataBuffer.position(0);
        }
    }
}
