package com.ycloud.ymrmodel;

import java.nio.ByteBuffer;

/**
 * 一些directByteBuffer等buffer并不提供array的接口.
 * 这里实现一个可以自动增长的容量的byte的vector容器的封装
 */
public class ByteVector {

    private static final float s_growRation = 1.3f;

    /**内容的长度*/
    private int mSize = 0;
    /** byte的内存数组，length对应是capacity*/
    private byte[]  mBytes = null;

    public ByteVector(int size) {
        reserve(size);
    }

    private int remaining() {
        if(mBytes == null)
            return 0;

        return mBytes.length - mSize;
    }

    public void reserve(int len) {
        if(len <= 0)
            return;
        int remain = remaining();

        if(remain < len || mBytes == null) {
            int realLen = (int)(s_growRation*len + mSize);
            byte[] tmp = new byte[realLen];
            if(mSize > 0 && mBytes != null) {
                System.arraycopy(mBytes, 0, tmp, 0, mSize);
            }
            mBytes = tmp;
        }
    }

    public byte[] getBytes() {
        return mBytes;
    }

    public int size() {
        return mSize;
    }

    /**
     *
     * @param buffer
     * @param len
     */
    public void put(ByteBuffer buffer, int len) {
        if(buffer == null || len <= 0)
            return;

        int cnt = buffer.remaining() > len ? len : buffer.remaining();
        reserve(mSize + cnt);
        buffer.get(mBytes, mSize, cnt);
        mSize += cnt;
    }

    public void clear() {
        mSize = 0;
    }

    public void release() {
        mSize = 0;
        mBytes = null;
    }
}
