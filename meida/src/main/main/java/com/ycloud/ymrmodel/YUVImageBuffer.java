package com.ycloud.ymrmodel;

import java.nio.ByteBuffer;

/**
 * Created by kele on 2016/12/19.
 */

public class YUVImageBuffer extends ImageBuffer
{
    /**YUVImageBuffer 编码携带的参数.*/
    public long mPts = 0;
    public int  mFrameRate = 0;
    public int mBitRate = 0;
    public boolean mLowDelay = false;
    public boolean mEndOfStream = false;
    public String mEncodeParameter = null;

    public ByteBuffer  mRgbBuffer = null;


    @Override
    public int imageSize() {
        return mWidth*mHeight*3/2;
    }
}
