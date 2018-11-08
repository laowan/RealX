package com.ycloud.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Kele on 2018/1/3.
 */

public abstract class AbstractMediaCodecDecoder
{
    protected MediaCodec mMediaCodec = null;
    protected MediaFormat mMediaFomart = null;
    protected static final long TIMEOUT_VALUE_10MS   = 10000;   // 10ms
    protected static final long TIMEOUT_VALUE_5MS = 5*1000; //5ms

    protected MediaCodec.BufferInfo mBufferInfo = null;
    protected ByteBuffer[]  mCodecInputBuffers = null;
    protected ByteBuffer[]  mCodecOutputBuffers = null;
    protected Surface       mDecoderSurface = null;
    protected boolean       mOutputEndOfStream = false;
    protected AtomicReference<Callback>       mCallbacker = new AtomicReference<>(null);

    protected int   mOutputFrameCnt = 0;
    protected int   mInputFrameCnt = 0;
    protected int   mCodecBufferCnt = 0;

    protected int   mOutputProduceCnt = 0;
    protected int   mOutputConsumeCnt = 0;
    protected int   mOutputBufferLimit = 1;  //surface texture has 2 graphicBuffer count, surface texture is async mode, with do replace. it is the singlebuffer mode.

    protected int   mQueueInputBufferTimeoutCnt = 0;


    //tmp solution..
    public void setmOutputConsumeCnt(int cnt) {
        mOutputConsumeCnt = cnt;
    }

    public void setmOutputBufferLimit(int cnt) {
        mOutputBufferLimit = cnt;
    }

    public void startDecode(MediaFormat mediaFormat, Surface surface) {
        doInitDecoder(mediaFormat, surface);
    }

    public void setCallback(Callback callback) {
        mCallbacker.set(callback);
    }

    public void stopDecode() {
    }

    public void releaseDecoder() {
        doReleaseDecoder();
    }

    public void decodeMediaSample(YYMediaSample sample) {
        doDecodeMediaSample(sample);
    }

    public void notifyEndOfStream() {
        doNotifyEndOfStream();
    }

    protected void processOutputBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo buffIno) {
        //do nothing.
        Callback callback = mCallbacker.get();
        if(callback != null) {
            callback.onOutputBuffer(byteBuffer, buffIno, buffIno.presentationTimeUs, mMediaFomart);
        }
    }

    protected void processOutputSurface(long ptsUs) {
        Callback callback = mCallbacker.get();
        if(callback != null) {
            callback.onOutputSurface(mMediaFomart, ptsUs);
        }
    }

    protected void onError(String msg) {
        Callback callback = mCallbacker.get();
        if(callback != null) {
            callback.onError(msg);
        }
    }

    protected void processOutFormatChanged(MediaFormat mediaFormat) {
        //do nothing.
        Callback callback = mCallbacker.get();
            if(callback != null) {
            callback.onFormatChanged(mMediaFomart);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected boolean doDecodeMediaSample(YYMediaSample sample) {
        try {
            int result = mMediaCodec.dequeueInputBuffer(TIMEOUT_VALUE_5MS);
            if (result >= 0) {
                int len = 0;
                ByteBuffer buffer = mCodecInputBuffers[result];
                buffer.clear();

                if(!sample.mEndOfStream) {
                    buffer.put(sample.mDataByteBuffer);
                    len = sample.mBufferSize;
                    mInputFrameCnt++;
                }
                //System.arraycopy();
                mMediaCodec.queueInputBuffer(
                        result,
                        0,
                        len,
                        sample.mAndoridPtsNanos / 1000, sample.mEndOfStream? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                return true;
            } else {
                mQueueInputBufferTimeoutCnt++;
            }
        } catch(Throwable t) {
            onError("mediacodec.dequeueInputBuffer error: " + t.toString());
            YYLog.error(this, "[exception] doDecodeMediaSample: " +t.toString());
        }
         return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    protected void doNotifyEndOfStream() {
        if(mMediaCodec != null) {
            //notify the mediacodec
            mMediaCodec.signalEndOfInputStream();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected boolean doInitDecoder(MediaFormat mediaFormat, Surface surface) {
        YYLog.info(this, "doInitDecoder mediaFormat=" + (mediaFormat == null ? "null" : mediaFormat.toString()));
        mMediaFomart = mediaFormat;
        mDecoderSurface = surface;

        try {
            //TODO. select the decoder.
            mMediaCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
            mMediaCodec.configure(mediaFormat, surface, null, 0);

            mMediaCodec.start();
            mCodecInputBuffers = mMediaCodec.getInputBuffers();
            mCodecOutputBuffers = mMediaCodec.getOutputBuffers();
            mBufferInfo = new MediaCodec.BufferInfo();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                YYLog.info(this, String.format("createDecoderByType(%s) = %s", mediaFormat.getString(MediaFormat.KEY_MIME), mMediaCodec.getName()));
            } else {
                YYLog.info(this, String.format("createDecoderByType(%s) = %s",  mediaFormat.getString(MediaFormat.KEY_MIME), mMediaCodec.toString()));
            }
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            YYLog.error(this, "[exception] InitDecoder: " + t.toString() + ",mediaFormat:" + mediaFormat.toString());
            onError("MediaCodec.doInitDecoder error: " + t.toString());
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void doReleaseDecoder() {
        try {
            if (mMediaCodec != null) {
                //signal the end of stream
                if(!mOutputEndOfStream) {
                    doNotifyEndOfStream();
                }
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;

                //TODO. debug
                YYLog.info(this, "MediaCodecDecoder.queueInputBuffer timeout count: " + mQueueInputBufferTimeoutCnt);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            YYLog.error(this, "[exception] doReleaseDecoder: " + t.toString());
        } finally {
            if (mMediaCodec != null) {
                mMediaCodec.release();
                mMediaCodec = null;
            }
        }
        mBufferInfo = null;
        mCodecInputBuffers = null;
        mCodecOutputBuffers = null;
    }

    protected boolean checkOutputBufferLimit() {
        return (mOutputProduceCnt - mOutputConsumeCnt <  mOutputBufferLimit);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void doDrainOutput(long timeoutUs) {
        //如果解码到surface texture上， surface texture在android源码中默认只有2个graphic buffer的size,当然各个OEM厂商可能不可能
        //但是2个buffer size是基本的.
        //需要check一下buffer size是否满了，如果满了，再往里面丢帧，则是覆盖，导致丢帧了， 而解码一般来说是不可以丢帧的.
        if (!checkOutputBufferLimit()) {
            YYLog.debug(this, "[decoder] AbstractMediaCodecDecoder outputBuffer limit, so don't drain output!!");
            return;
        }

        try {
            final int result = mMediaCodec.dequeueOutputBuffer(mBufferInfo, timeoutUs);
            if (result >= 0) {
                ByteBuffer buffer = mCodecOutputBuffers[result];
                if (buffer != null && buffer.remaining() != mBufferInfo.size) {
                    // for {xiaomi 4, meitu m4} it does not set limit value.
                    // for meitu m4, @buffer is null if output surface is valid
                    buffer.position(mBufferInfo.offset).limit(mBufferInfo.offset + mBufferInfo.size);
                }

                //YYLog.info(this, "[decoder] pts="+(mBufferInfo.presentationTimeUs/1000) + " size="+mBufferInfo.size);
                //mOutputFrameCnt++;
                mOutputFrameCnt++;
                mOutputProduceCnt++;
                if (mDecoderSurface == null) {
                    processOutputBuffer(buffer, mBufferInfo);
                } else {
                    processOutputSurface(mBufferInfo.presentationTimeUs);
                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    mMediaCodec.releaseOutputBuffer(result, true);
//                    YYLog.info(this, Constant.MEDIACODE_PTS_EXPORT + "decode to queue step 1: add pts to queue");
                    //input into surface texture queue, the frame is not output.
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    //notify the end of stream
                    Callback callback = mCallbacker.get();
                    if (callback != null) {
                        mOutputEndOfStream = true;
                        callback.onEndOfInputStream();
                    }
                }
            } else if (result == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mCodecOutputBuffers = mMediaCodec.getOutputBuffers();
                YYLog.info(this, "output buffers have been changed.");
            } else if (result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mMediaCodec.getOutputFormat();
                YYLog.info(this, "output format has been changed from " + mMediaFomart + " to " + format);
                mMediaFomart = format;
                processOutFormatChanged(mMediaFomart);
            }
        } catch (Exception e) {
            YYLog.error(this, "doDrainOutput exception:" + e.getMessage());
        }
    }

    public interface Callback {
        void onOutputBuffer(ByteBuffer buffer, MediaCodec.BufferInfo buffInfo, long ptsUs, MediaFormat mediaFormat);
        void onOutputSurface(MediaFormat format, long ptsUs);
        void onFormatChanged(MediaFormat mediaFormat);
        void onEndOfInputStream();
        void onError(String errMsg); //硬编码出错.
    }
}
