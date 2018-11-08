package com.ycloud.datamanager;

import com.orangefilter.OrangeFilter;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2017/12/29.
 */

public class YYVideoPacket {
    public ByteBuffer mDataByteBuffer = null;
    public int mBufferOffset = 0;
    public int mBufferSize = 0;
    public int mBufferFlag = 0; //用于传递mediacodec中的buffinfo中的flag
    public long pts;
    public int mFrameType = 0xFF;

    /*肢体识别数据*/
    public OrangeFilter.OF_BodyFrameData[] mBodyFrameDataArr = null;

    /*人脸识别数据*/
    public OrangeFilter.OF_FaceFrameData[] mFaceFrameDataArr = null;
}
