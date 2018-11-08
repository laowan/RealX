package com.ycloud.ymrmodel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by kele on 2016/10/26.
 */

public class ByteBufferPool {
    private ConcurrentLinkedQueue<ByteBuffer> mFreeByteArray = null;
    private int mBufSize = 0;
    private int mCapacity = 0;

    public ByteBufferPool(int capacity, int size) {
        mCapacity = capacity;
        mBufSize = size;
        mFreeByteArray = new ConcurrentLinkedQueue<ByteBuffer>();
        for(int i = 0; i < capacity; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(size);
            buffer.order(ByteOrder.nativeOrder());
            mFreeByteArray.offer(buffer);
        }
    }

    public ByteBuffer newByteBuffer() {
        return mFreeByteArray.poll();
    }

    public void freeByteBuffer(ByteBuffer buf) {
        if(buf != null) {
            buf.clear();
            mFreeByteArray.offer(buf);
        }
    }
}
