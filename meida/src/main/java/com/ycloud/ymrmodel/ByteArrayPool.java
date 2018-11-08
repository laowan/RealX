package com.ycloud.ymrmodel;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by kele on 2016/10/26.
 */

public class ByteArrayPool {
    private ConcurrentLinkedQueue<byte[]> mFreeByteArray = null;
    private int mBufSize = 0;
    private int mCapacity = 0;

    public ByteArrayPool(int capacity, int size) {
        mCapacity = capacity;
        mBufSize = size;
        mFreeByteArray = new ConcurrentLinkedQueue<byte[]>();
        for(int i = 0; i < capacity; i++) {
            mFreeByteArray.offer(new byte[size]);
        }
    }

    public byte[]  newByteArray() {
      return mFreeByteArray.poll();
    }

    public void freeByteArray(byte[]  array) {
        mFreeByteArray.offer(array);
    }
}
