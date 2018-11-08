package com.ycloud.datamanager;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/1/2.
 */

public class YYAudioPacket {
    public ByteBuffer mDataByteBuffer = null;
    public int mBufferOffset = 0;
    public int mBufferSize = 0;
    public int mBufferFlag = 0; //用于传递mediacodec中的buffinfo中的flag
    public long pts;
}
