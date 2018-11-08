package com.ycloud.ymrmodel;


import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by kele on 2016/12/19.
 */

public class ImageBufferPool<T extends ImageBuffer> {

    Class<T> mImageClazz = null;
    private ConcurrentLinkedQueue<T> mFreeArray = null;
    private int mCapacity = 0;

    private int mWidth = 0;
    private int mHeight = 0;
    private int mImageFormat = 0;
    private int mImageByteSize;

    private AtomicInteger mBufferIndex = new AtomicInteger(0);

    AtomicInteger mBufferCnt = new AtomicInteger(0);

    public ImageBufferPool(int imageWidth, int imageHeight, int capacity, int imageFormat, Class<T> imageClazz, int imageByteSize) {
        mImageClazz = imageClazz;
        mCapacity = capacity;
        mFreeArray = new ConcurrentLinkedQueue<T>();
        mWidth = imageWidth;
        mHeight = imageHeight;
        mImageByteSize = imageByteSize;
        this.mImageFormat = imageFormat;
        for(int i = 0; i < capacity; i++) {
            T element = newImageBuffer(imageWidth, imageHeight);
            mFreeArray.offer(element);
        }
        mBufferCnt.set(capacity);
    }


    private T newImageBuffer(int width, int height) {
        T  element = null;
        try {
            element = mImageClazz.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }

        element.mWidth = width;
        element.mHeight = height;
        element.mImageFormat = mImageFormat;

        int imageSize = mImageByteSize;
        if(imageSize == 0) {
            imageSize  = element.imageSize();
        }

        if(mBufferIndex.getAndAdd(1) % 30 == 0) {
            YYLog.info(this, "[mem] newImageBuffer imageSize=" + imageSize
                    + " mImageByteSize=" + mImageByteSize
                    + " bufferIndex="+mBufferIndex.get());
        }

        element.mDataBuffer = ByteBuffer.allocate(imageSize);
        element.mDataBuffer.order(ByteOrder.nativeOrder());
        return element;
    }


    public T newBuffer(int width, int height) {
        if(mWidth != width || mHeight != height) {
            mWidth = width;
            mHeight = height;
            //reset the freeArray..
        }

        T e = mFreeArray.poll();
        while(e != null) {
            mBufferCnt.decrementAndGet();
            if(e.mWidth != width || e.mHeight != height) {
                e = mFreeArray.poll();
            } else {
                break;
            }
        }

        if(e == null) {
            e = newImageBuffer(mWidth, mHeight);
        }

        return e;
    }

    public void freeBuffer(T buf) {
        if(buf.mHeight != mHeight || buf.mWidth != mWidth)
            return;

        if(buf != null) {
            buf.clear();
            if(mBufferCnt.get() < mCapacity) {
                mFreeArray.offer(buf);
            }
        }
    }
}
