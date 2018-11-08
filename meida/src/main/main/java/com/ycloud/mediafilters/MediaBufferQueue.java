package com.ycloud.mediafilters;

import com.ycloud.api.common.SampleType;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleBase;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.SAXParser;

/**
 * Created by Kele on 2018/1/8.
 */

public class MediaBufferQueue<T extends YYMediaSampleBase> {
    protected ConcurrentLinkedQueue<T> mSampleQueue = new ConcurrentLinkedQueue<>();
    protected AtomicReference<InputCallback>  mInputCallback = new AtomicReference<>(null);
    protected AtomicReference<OutputCallback> mOutputCallback = new AtomicReference<>(null);

    protected int mMinBufferCnt = 0;
    protected int mMaxBufferCnt = 1000000;
    protected AtomicInteger mBufferCnt = new AtomicInteger(0);
    protected SampleType mSampleType = SampleType.UNKNOWN;

    public MediaBufferQueue(int minBuffer, int maxBuffer, SampleType sampleType) {
        mMinBufferCnt = minBuffer;
        mMaxBufferCnt = maxBuffer;
        mSampleType = sampleType;
    }

    public SampleType getSampleType() {
        return  mSampleType;
    }
    public interface InputCallback {
        void getMediaSample(SampleType sample);
    }

    public interface OutputCallback<T> {
        void outputMediaSample(T sample);
    }

    public void setInputCallback(InputCallback callback) {
        mInputCallback = new AtomicReference<>(callback);
    }

    public void setOutputCallback(OutputCallback callbak) {
        mOutputCallback = new AtomicReference<>(callbak);
    }

    public boolean add(T sample) {
        if(sample == null || sample.mSampleType != mSampleType)
            return false;

        if(mBufferCnt.get() > mMaxBufferCnt) {
            YYLog.debug(this, "MediaBufferQueue.add, bufferCnt exceed the max limit, so return false, bufferCnt="+mBufferCnt.get());
            return false;
        }

        sample.addRef();
        mSampleQueue.add(sample);
        mBufferCnt.addAndGet(1);
        if(mBufferCnt.get() < 0) {
            YYLog.error(this, "add BufferCnt less than 0!!!");
            //mBufferCnt.set(size());
        }
        OutputCallback callback = mOutputCallback.get();
        if(callback != null) {
            callback.outputMediaSample(sample);
        }
        return true;
    }

    public void clear() {
        mSampleQueue.clear();
        mBufferCnt.set(0);
    }

    public T peek() {
        return mSampleQueue.peek();
    }

    //bufferCnt 遵循最终一致性，对于生成/消费者线程模型, 最差的结果是多发几个消息给生成者 和 queue的元素为 max+1.
    //mBufferCnt.set(size())的修正在多线程同步极端情况下, 会导致mBufferCnt的数据出错, 比实际数据增大.
    public boolean remove() {
        boolean ret = false;
        T sample = mSampleQueue.poll();
        if(sample != null) {
            sample.decRef();
            mBufferCnt.decrementAndGet();
            if(mBufferCnt.get() < 0) {
                YYLog.error(this, "BufferCnt less than 0 !!!");
                //mBufferCnt.set(size());
            }
            ret = true;
        }

        if(mBufferCnt.get() < mMinBufferCnt) {
            InputCallback callback = mInputCallback.get();
            if (callback != null) {
                YYLog.debug(this, "MediaBufferQueue.InputCallback.getMediaSample!!");
                callback.getMediaSample(mSampleType);
            }
        }
        return ret;
    }

    public int size() {
        return mSampleQueue.size();
    }
}
