package com.ycloud.svplayer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;

import com.ycloud.svplayer.surface.I420ToSurface;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by DZHJ on 2017/8/26.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class FfmpegCodecWrapper implements ICodec {
    private static final String TAG = FfmpegCodecWrapper.class.getSimpleName();

    static private int BUFFER_POOL_SIZE = 8;

    FfmpegBufferInfoPool mInputFfmpegBufferPool;
    FfmpegBufferInfoPool mOutputFfmpegBufferPool;

    private NativeFfmpeg mNativeFfmpeg;

    private Surface mSurface;

    FfmpegDecoderThread mFfmpegDecoderThread;

    private I420ToSurface mI420ToSurface;

    private MediaFormat mFormat;

    MediaInfo mMediaInfo;

    private ByteBuffer[] mFrameData = new ByteBuffer[3];

    private Handler mGLHandler;
    private EGLBase mEglBase;

    private Object mFlushLock;
    private Object mReleaseLock;

    private boolean mConfigured;

    public FfmpegCodecWrapper() {
        mGLHandler = new Handler();
        mNativeFfmpeg = new NativeFfmpeg();
        mNativeFfmpeg.setCallback(new NativeFfmpeg.Callback() {
            @Override
            public void onFormatChanged(final MediaInfo info) {
                mGLHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMediaInfo = info;
                        mFrameData[0] = ByteBuffer.allocateDirect(info.planeSize).order(ByteOrder.nativeOrder());
                        mFrameData[1] = ByteBuffer.allocateDirect(info.planeSize >> 2).order(ByteOrder.nativeOrder());
                        mFrameData[2] = ByteBuffer.allocateDirect(info.planeSize >> 2).order(ByteOrder.nativeOrder());
                        mI420ToSurface.updateVertexBuffer(info);
                    }
                });

            }
        });
        mFfmpegDecoderThread = new FfmpegDecoderThread(TAG);
        mFfmpegDecoderThread.start();
        mFlushLock = new Object();
        mReleaseLock = new Object();
        mConfigured =false;

    }

    @Override
    public void configure(MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {

        mFormat = format;
        mNativeFfmpeg.create(MediaConst.FRAME_TYPE_H264, format);

        mInputFfmpegBufferPool = new FfmpegBufferInfoPool(BUFFER_POOL_SIZE, getVideoWidth() * getVideoHeight() * 4);
        mOutputFfmpegBufferPool = new FfmpegBufferInfoPool(BUFFER_POOL_SIZE, getVideoWidth() * getVideoHeight() * 2);

        mSurface = surface;
        mEglBase = new EGLBase(mSurface);
        mEglBase.eglSetup();
        mEglBase.makeCurrent();
        mI420ToSurface = new I420ToSurface(mEglBase.getSurfaceWidth(),mEglBase.getSurfaceHeight());
        mI420ToSurface.init();
        int rotateMode = getVideoRotation(format);
        mI420ToSurface.setRotateMode(rotateMode);
        mConfigured = true;
        YYLog.info(TAG,"configure finish");
    }

    /**
     * Returns the rotation of the video in degree.
     * Only works on API21+, else it always returns 0.
     * @return the rotation of the video in degrees
     */
    public int getVideoRotation(MediaFormat format) {
        int rotation =0;
        try {
            // rotation-degrees is available from API21, officially supported from API23 (KEY_ROTATION)
            rotation = (format != null && format.containsKey("rotation-degrees") ?
                    format.getInteger("rotation-degrees") : 0);
        } catch (Exception e) {
            YYLog.error(TAG, "get rotation-degrees fail");
        }

        if(rotation <0) {
            rotation +=360;
        }

        return  rotation;
    }

    public int getVideoWidth() {
        return mFormat != null ? (int) (mFormat.getInteger(MediaFormat.KEY_HEIGHT)
                * mFormat.getFloat(MediaExtractor.MEDIA_FORMAT_EXTENSION_KEY_DAR)) : 0;
    }

    public int getVideoHeight() {
        return mFormat != null ? mFormat.getInteger(MediaFormat.KEY_HEIGHT) : 0;
    }

    @Override
    public void start() {
        mFfmpegDecoderThread.startDecode();
    }

    @Override
    public void stop() {
        YYLog.info(TAG, "stop decode");
        flush();
        mFfmpegDecoderThread.stopDecode();
    }

    @Override
    public void release() {
        YYLog.error(TAG,"release");
        mEglBase.makeCurrent();
        mI420ToSurface.release();
        mEglBase.release();

        synchronized (mReleaseLock){
            mFfmpegDecoderThread.release();
            try {
                mReleaseLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                YYLog.error(TAG,"release error," + e.getMessage());
            }
        }
        mConfigured = false;
    }

    @Deprecated
    @Override
    public ByteBuffer[] getInputBuffers() {
        return null;
    }

    @Deprecated
    @Override
    public ByteBuffer[] getOutputBuffers() {
        return null;
    }

    @Override
    public int dequeueInputBuffer(long timeoutUs) {
        return mInputFfmpegBufferPool.dequeueUnusedByteBuffer();
    }

    @Override
    public void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags) {

        FfmpegBufferInfo ffmpegBufferInfo = mInputFfmpegBufferPool.getFfmpegBufferInfo(index);
        ffmpegBufferInfo.index = index;
        ffmpegBufferInfo.presentationTimeUs = presentationTimeUs;
        ffmpegBufferInfo.offset = offset;
        ffmpegBufferInfo.size = size;
        ffmpegBufferInfo.flags = flags;

        //TODO:由于NativeFfmpeg.cpp中根据position判断大小，因此这里需要调整position到末尾，后续在由于NativeFfmpeg修正后再改回来
        mInputFfmpegBufferPool.getFfmpegBufferInfo(index).buf.position(mInputFfmpegBufferPool.getFfmpegBufferInfo(index).buf.limit());

        mInputFfmpegBufferPool.queueUsedByteBuffer(index);

    }

    @Override
    public int dequeueOutputBuffer(MediaCodec.BufferInfo info, long timeoutUs) {
        int index = mOutputFfmpegBufferPool.dequeueUsedByteBuffer();
        if (index != FfmpegBufferInfoPool.INVALID_INDEX) {
            FfmpegBufferInfo ffmpegBufferInfo = mOutputFfmpegBufferPool.getFfmpegBufferInfo(index);
            info.offset = ffmpegBufferInfo.offset;
            info.presentationTimeUs = ffmpegBufferInfo.presentationTimeUs;
            info.size = ffmpegBufferInfo.size;
            info.flags = ffmpegBufferInfo.flags;
            return index;
        }

        return FfmpegBufferInfoPool.INVALID_INDEX;
    }

    @Override
    public MediaFormat getOutputFormat() {
        return null;
    }

    @Override
    public void releaseOutputBuffer(int index, boolean render) {

        if (render) {
            FfmpegBufferInfo ffmpegBufferInfo = mOutputFfmpegBufferPool.getFfmpegBufferInfo(index);
            int position = ffmpegBufferInfo.buf.position();
            ffmpegBufferInfo.buf.limit(position + mMediaInfo.planeSize);
            mFrameData[0].clear();
            mFrameData[0].put(ffmpegBufferInfo.buf).rewind();

            position += mMediaInfo.planeSize;
            ffmpegBufferInfo.buf.limit(position + (mMediaInfo.planeSize >> 2));
            mFrameData[1].clear();
            mFrameData[1].put(ffmpegBufferInfo.buf).rewind();

            position += mMediaInfo.planeSize >> 2;
            ffmpegBufferInfo.buf.limit(position + (mMediaInfo.planeSize >> 2));
            mFrameData[1].clear();
            mFrameData[2].put(ffmpegBufferInfo.buf).rewind();

            mEglBase.makeCurrent();
            //将数据送到到mSurface
            mI420ToSurface.drawFrame(mMediaInfo, mFrameData);
            mEglBase.eglSwapBuffers();
        }

        mOutputFfmpegBufferPool.queueUnusedByteBuffer(index);
    }

    @Override
    public void flush() {
        YYLog.info(TAG,"flush");
        if (mConfigured == true) {
            synchronized (mFlushLock) {
                mFfmpegDecoderThread.flush();
                try {
                    mFlushLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    YYLog.error(TAG, "flush error," + e.getMessage());
                }
            }
        }
    }

    @Override
    public ByteBuffer getInputBuffer(int index) {
        return mInputFfmpegBufferPool.getFfmpegBufferInfo(index).buf;
    }

    @Override
    public ByteBuffer getOutputBuffer(int index) {
        return mOutputFfmpegBufferPool.getFfmpegBufferInfo(index).buf;
    }

    private class FfmpegBufferInfoPool {
        public static final int INVALID_INDEX = -1;
        private ArrayList<FfmpegBufferInfo> mFfmpegBufferInfoArray = null;
        private int mBufSize = 0;
        private int mCapacity = 0;
        private ConcurrentLinkedQueue<Integer> mUnusedIndexQueue;
        private ConcurrentLinkedQueue<Integer> mUsedIndexQueue;

        public FfmpegBufferInfoPool(int capacity, int size) {
            mCapacity = capacity;
            mBufSize = size;
            mFfmpegBufferInfoArray = new ArrayList<>();
            mUnusedIndexQueue = new ConcurrentLinkedQueue<>();
            mUsedIndexQueue = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < mCapacity; i++) {
                FfmpegBufferInfo ffmpegBufferInfo = new FfmpegBufferInfo();
                ffmpegBufferInfo.index = i;
                ByteBuffer buffer = ByteBuffer.allocateDirect(size);
                buffer.order(ByteOrder.nativeOrder());
                buffer.clear();
                ffmpegBufferInfo.buf = buffer;
                mFfmpegBufferInfoArray.add(ffmpegBufferInfo);
                mUnusedIndexQueue.add(new Integer(i));
            }
        }

        public int dequeueUnusedByteBuffer() {
            if (mUnusedIndexQueue.size() > 0) {
                return mUnusedIndexQueue.poll().intValue();
            }

            return INVALID_INDEX;
        }

        public void queueUnusedByteBuffer(int index) {
            FfmpegBufferInfo ffmpegBufferInfo = mFfmpegBufferInfoArray.get(index);
            ffmpegBufferInfo.clear();
            mUnusedIndexQueue.add(new Integer(index));
        }

        public int dequeueUsedByteBuffer() {
            if (mUsedIndexQueue.size() > 0) {
                return mUsedIndexQueue.poll().intValue();
            }
            return INVALID_INDEX;
        }

        public void queueUsedByteBuffer(int index) {
            mUsedIndexQueue.add(new Integer(index));
        }

        public FfmpegBufferInfo getFfmpegBufferInfo(int index) {
            return mFfmpegBufferInfoArray.get(index);
        }

        public int getUsedBufferSize() {
            return mUsedIndexQueue.size();
        }

        public int getUnusedBufferSize() {
            return mUnusedIndexQueue.size();
        }

        public void reset() {
            mUsedIndexQueue.clear();
            mUnusedIndexQueue.clear();
            for (int i = 0; i < mCapacity; i++) {
                FfmpegBufferInfo ffmpegBufferInfo = mFfmpegBufferInfoArray.get(i);
                ffmpegBufferInfo.clear();
                mUnusedIndexQueue.add(new Integer(i));
            }
        }

        public void release() {

        }
    }

    public final static class FfmpegBufferInfo {

        /**
         * buffer在bufferPool中的index
         */
        public int index;
        /**
         * The start-offset of the data in the buffer.
         */
        public int offset;

        /**
         * The amount of data (in bytes) in the buffer.  If this is {@code 0},
         * the buffer has no data in it and can be discarded.  The only
         * use of a 0-size buffer is to carry the end-of-stream marker.
         */
        public int size;

        /**
         * The presentation timestamp in microseconds for the buffer.
         * This is derived from the presentation timestamp passed in
         * with the corresponding input buffer.  This should be ignored for
         * a 0-sized buffer.
         */
        public long presentationTimeUs;

        public int flags;
        ByteBuffer buf;

        public void clear() {
            offset = 0;
            size = 0;
            presentationTimeUs = 0;
            flags = 0;
            buf.clear();
        }
    }

    private class FfmpegDecoderThread extends HandlerThread  implements Handler.Callback{
        public static  final  int THEAD_WAIT_TIME= 10;
        public static  final  int MSG_DECODE_FRAME =1;
        public static  final  int MSG_FLUSH_BUFFER =2;
        public static  final  int MSG_STOP_DECODE_FRAME =3;
        public static  final  int MSG_RELEASE =4;

        private Handler mHandler;

        public FfmpegDecoderThread(String name) {
            super(name);
        }

        @Override
        public synchronized void start() {
            super.start();
            // Create the handler that will process the messages on the handler thread
            mHandler = new Handler(this.getLooper(), this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DECODE_FRAME:
                    decodeFrameInternal();
                    break;
                case  MSG_FLUSH_BUFFER:
                    flushInternal();
                    break;
                case  MSG_STOP_DECODE_FRAME:
                    mHandler.removeMessages(MSG_DECODE_FRAME);
                    break;
                case MSG_RELEASE:
                    releaseInternal();
                    break;
                default:
                    break;
            }
            return true;
        }

        public void flush() {
            mHandler.sendEmptyMessage(MSG_FLUSH_BUFFER);
        }

        private void flushInternal() {
            synchronized (mFlushLock) {
                mInputFfmpegBufferPool.reset();
                mOutputFfmpegBufferPool.reset();
                mFlushLock.notify();
            }
        }

        private void releaseInternal() {
            YYLog.error(TAG,"releaseInternal");
            synchronized (mReleaseLock) {
                mNativeFfmpeg.destroy();
                mInputFfmpegBufferPool.release();
                mInputFfmpegBufferPool = null;
                mOutputFfmpegBufferPool.release();
                mOutputFfmpegBufferPool = null;
                mReleaseLock.notify();
            }
        }

        private void decodeFrameInternal() {
            if (mInputFfmpegBufferPool!=null && mInputFfmpegBufferPool.getUsedBufferSize() >0 &&
                    mOutputFfmpegBufferPool!=null && mOutputFfmpegBufferPool.getUnusedBufferSize()>0) {
                int inputIndex = mInputFfmpegBufferPool.dequeueUsedByteBuffer();
                int outputIndex = mOutputFfmpegBufferPool.dequeueUnusedByteBuffer();
                FfmpegBufferInfo inputFfmpegBufferInfo = mInputFfmpegBufferPool.getFfmpegBufferInfo(inputIndex);
                FfmpegBufferInfo outputFfmpegBufferInfo = mOutputFfmpegBufferPool.getFfmpegBufferInfo(outputIndex);

                int ret = mNativeFfmpeg.decode(inputFfmpegBufferInfo.buf, outputFfmpegBufferInfo.buf, (inputFfmpegBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);

                if (ret < 0) {
                    YYLog.error(TAG, "mNativeFfmpeg decoder error");
                    mOutputFfmpegBufferPool.queueUnusedByteBuffer(outputIndex);
                } else if (ret == 0) {

                    YYLog.info(TAG, "mNativeFfmpeg decoder null");
                    mOutputFfmpegBufferPool.queueUnusedByteBuffer(outputIndex);

                } else if (ret > 0) {
                    //TODO:在ffmpeg存在缓存的情况下，如何确定输入帧的时间戳，offset等的信息呢？？？？？
                    outputFfmpegBufferInfo.offset = 0;
                    outputFfmpegBufferInfo.presentationTimeUs = inputFfmpegBufferInfo.presentationTimeUs;
                    outputFfmpegBufferInfo.size = outputFfmpegBufferInfo.buf.limit();
                    outputFfmpegBufferInfo.flags = inputFfmpegBufferInfo.flags;
                    mOutputFfmpegBufferPool.queueUsedByteBuffer(outputIndex);
                }

                mInputFfmpegBufferPool.queueUnusedByteBuffer(inputFfmpegBufferInfo.index);
                mHandler.sendEmptyMessage(MSG_DECODE_FRAME);
            } else{
                mHandler.sendEmptyMessageDelayed(MSG_DECODE_FRAME,THEAD_WAIT_TIME);
            }
        }

        public void release() {
            mHandler.sendEmptyMessage(MSG_RELEASE);
        }

        public void startDecode() {
            mHandler.sendEmptyMessage(MSG_DECODE_FRAME);
        }

        public void stopDecode(){
            mHandler.sendEmptyMessage(MSG_STOP_DECODE_FRAME);
        }
    }
}
