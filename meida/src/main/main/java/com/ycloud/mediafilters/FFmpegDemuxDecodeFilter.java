package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Build;

import com.ycloud.api.common.SampleType;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2018/1/26.
 * FFmpeg extractor and decoder java layer wrapper .
 */
public class FFmpegDemuxDecodeFilter extends AbstractYYMediaFilter {
    public static final String TAG = "FFmpegDemuxDecodeFilter";
    private int mWidth = 0;
    private int mHeight = 0;
    private int mOutputWidth = 0;
    private int mOutputHeight = 0;
    private int mSnapShotCnt = 0;
    private int mRotate = 0;
    private long mStartTime = 0;
    private long mDuration = 0;
    private long mContext = 0;
    private ByteBuffer mFrameBuffer = null;
    private String mFileName = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private Object mLock = new Object();

    static {
        try {
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            YYLog.e(TAG, "load so fail");
            e.printStackTrace();
        }
    }

    public void init(String path, int width, int height, int snapShotCnt) {
        synchronized (mLock) {
            mFileName = path;
            mContext = FFmpegDemuxDecodeCreatCtx();
            if (mContext == 0) {
                YYLog.error(TAG, "init failed, mContext == 0, mContext:" + mContext);
                return;
            }
            mOutputWidth = width;
            mOutputHeight = height;
            mSnapShotCnt = snapShotCnt;
            mInited.set(true);
            YYLog.info(TAG, "Init OK .");
        }
    }

    public void setSnapshotRange(int startTime, int duration) {
        synchronized (mLock) {
            mStartTime = startTime;
            mDuration = duration;
        }
    }

    public void start() {
        synchronized (mLock) {
            if (!mInited.get()) {
                YYLog.error(TAG, "Should init first !");
                return;
            }
            long mCPUCoreCount = Runtime.getRuntime().availableProcessors();
            if (mFileName == null || mFileName.isEmpty()) {
                YYLog.error(TAG, "Snapshot file path is NULL !");
                return;
            }
            FFmpegDemuxDecodeStart(mContext, mFileName, mCPUCoreCount, mSnapShotCnt, mStartTime, mDuration);
        }
    }

    public void deInit() {
        synchronized (mLock) {
            if (!mInited.get()) {
                YYLog.error(TAG, "Should init first !");
                return;
            }

            if (mContext != 0) {
                FFmpegDemuxDecodeRelease(mContext);
                mContext = 0;
            }

            if (mFrameBuffer != null) {
                mFrameBuffer = null;
            }
            mInited.set(false);
            YYLog.info(TAG, "deInit OK .");
        }
    }

    // called by native decoder thread, do not modify the method signature
    public ByteBuffer MallocByteBuffer(int width, int height, int rotate) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return null;
        }

        if (mWidth == width && mHeight == height && null != mFrameBuffer) {
            return mFrameBuffer;
        }
        mWidth = width;
        mHeight = height;
        mRotate = rotate;
        try {
            mFrameBuffer = ByteBuffer.allocateDirect(width * height * 3 / 2);  // YUV420P
        } catch (Throwable e) {
            YYLog.error(this, "allocate frame buffer failed: " + e.getMessage());
        }
        return mFrameBuffer;
    }

    // called by native decoder thread, do not modify the method signature
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void onVideoFrameDataReady(int size, long pts) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return;
        }
        //YYLog.info(TAG,"onVideoFrameDataReady size " + size + " pts " + pts);
        final YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
        if (size > 0) {
            sample.mSampleType = SampleType.VIDEO;
            sample.mYYPtsMillions = pts;
            sample.mAndoridPtsNanos = pts * 1000000;
            sample.mWidth = mWidth;
            sample.mHeight = mHeight;
            sample.mDisplayRotation = mRotate;
            sample.mEncodeWidth = mOutputWidth;
            sample.mEncodeHeight = mOutputHeight;
            sample.mDeliverToEncoder = true;
            ByteBuffer buffer = ByteBuffer.allocateDirect(size);
            if (mFrameBuffer == null || mFrameBuffer.remaining() > size) {
                YYLog.error(TAG, "frameBuffer size and sample buffer size not equal, just return");
                sample.decRef();
                return;
            }
            try {
                buffer.put(mFrameBuffer);
            } catch (BufferOverflowException e) {
                YYLog.error(TAG, "Exception " + e.getMessage());
            }
            mFrameBuffer.rewind();
            buffer.rewind();
            sample.mDataByteBuffer = buffer;
            sample.mBufferSize = size;
        } else {
            YYLog.info(TAG, " video end of stream");
            sample.mEndOfStream = true;
            sample.mDeliverToEncoder = true;
            sample.mDataByteBuffer = null;
            sample.mBufferOffset = 0;
            sample.mBufferSize = 0;
            sample.mWidth = mWidth;
            sample.mHeight = mHeight;
            sample.mEncodeWidth = mOutputWidth;
            sample.mEncodeHeight = mOutputHeight;
            sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }
        deliverToDownStream(sample);
        sample.decRef();
    }

    private native long FFmpegDemuxDecodeCreatCtx();

    private native void FFmpegDemuxDecodeStart(long context, String path, long CPUCount, long snapShotCnt, long startTime, long duration);

    private native long FFmpegDemuxDecodeRelease(long context);
}
