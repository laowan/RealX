package com.ycloud.ymrmodel;

import com.ycloud.api.common.SampleType;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;

import javax.xml.parsers.SAXParser;

/**
 * Created by Administrator on 2016/9/22.
 */
public class JVideoEncodedData
{
    //Notice!!!. assigne value by JNI Layer, so don't modify the name and type of field.
    public int      mFrameType = VideoConstant.VideoFrameType.kVideoUnknowFrame;
    public long     mPts = 0;  //单位MS
    public long     mDts = 0;  //单位MS.
    public long     mDataLen = 0;

    public int      mWidth = 0;
    public int      mHeight = 0;

    //public byte[]   iData = null;
    public ByteBuffer mByteBuffer = null;  //may directBuffer.

    //默认是264软编码，不同的编码， 则设置这个参数.
    public VideoEncoderType mEncodeType = VideoEncoderType.SOFT_ENCODER_X264;
    public JVideoEncodedData() {
        super();
    }

    public void releaseVideoByteBuffer() {
        if (mByteBuffer != null) {
            nativeRelaseVideoByteBuffer();
            mByteBuffer = null;
        }
    }

    public YYMediaSample toYYMediaSample() {
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
        sample.mDataByteBuffer = mByteBuffer;
        sample.mBufferSize = (int)mDataLen;
        sample.mBufferOffset = 0;
        sample.mYYPtsMillions = mPts;
        sample.mAndoridPtsNanos = mPts*1000*1000;
        sample.mDtsMillions = mDts;
        sample.mFrameType = mFrameType;
        sample.mEncoderType = mEncodeType;
        sample.mSampleType = SampleType.VIDEO;
        sample.mWidth = mWidth;
        sample.mHeight = mHeight;

        return sample;
    }


    private native void nativeRelaseVideoByteBuffer();
    public static native void nativeClassInit();
    static {
        nativeClassInit();
    }
}
