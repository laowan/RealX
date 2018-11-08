package com.ycloud.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;

import com.ycloud.utils.YMRThread;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Administrator on 2018/1/3.
 */

public abstract class AbstractMediaCodecDecoderAsync extends AbstractMediaCodecDecoder implements YMRThread.Callback
{
    YMRThread   mDecoderThread  = null;
    private final int MSG_START_DECODER = 1;
    private final int MSG_STOP_DECODER = 2;
    private final int MSG_RELEASE_DECODER = 3;
    private final int MSG_END_OF_STREAM = 4;
    private final int MSG_DECODE_SAMPLE = 5;

    protected  ISampleBufferQueue  mSampleQueue = null;

    public interface ISampleBufferQueue {
        public YYMediaSample peek();
        public boolean remove();
    }

    //增加大小控制, 免的上游来的数据一下子过多.
    //private ConcurrentLinkedQueue<YYMediaSample>  mSampleQueue = new ConcurrentLinkedQueue<>();
    private  boolean mInputEndOfStream = false;

    public AbstractMediaCodecDecoderAsync(ISampleBufferQueue sampleQueue)
    {
        super();
        mSampleQueue  = sampleQueue;
        mDecoderThread = new YMRThread("ymrsdk_MediaCodecDecoder");
        mDecoderThread.setPriority(Thread.NORM_PRIORITY + 3);
        mDecoderThread.setCallback(this);
        mDecoderThread.start();
    }

    @Override
    public void startDecode(MediaFormat mediaFormat, Surface surface) {
        mMediaFomart = mediaFormat;
        mDecoderThread.sendMessage(Message.obtain(mDecoderThread.getHandler(), MSG_START_DECODER, 0, 0, surface));
    }

    @Override
    public void stopDecode() {
        mDecoderThread.sendMessage(Message.obtain(mDecoderThread.getHandler(), MSG_STOP_DECODER));
    }

    @Override
    public void releaseDecoder() {
        mDecoderThread.sendMessage(Message.obtain(mDecoderThread.getHandler(), MSG_RELEASE_DECODER));
        mDecoderThread.stop();
    }

    @Override
    public void decodeMediaSample(YYMediaSample sample) {
        mDecoderThread.sendMessage(Message.obtain(mDecoderThread.getHandler(), MSG_DECODE_SAMPLE));
    }

    @Override
    public void notifyEndOfStream() {
        mDecoderThread.sendMessage(Message.obtain(mDecoderThread.getHandler(), MSG_END_OF_STREAM));
    }

    @Override
    protected void processOutputBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo buffIno) {
        super.processOutputBuffer(byteBuffer, buffIno);
    }

    @Override
    protected void processOutFormatChanged(MediaFormat mediaFormat) {
        super.processOutFormatChanged(mediaFormat);
    }

    protected  void decodeSampleLoop() {
       // YYLog.debug(this, "[decoder] video decodeSampleLoop begin");
        while(!mInputEndOfStream) {
            YYMediaSample sample = mSampleQueue.peek();
            YYLog.debug(this, "[decoder] video, sample queue poll finish");
            if (sample != null) {
                if(doDecodeMediaSample(sample)) {
                    mInputEndOfStream = sample.mEndOfStream;
                    YYLog.debug(this, "[decoder] video dequeue sample into codec successfully, mInputEndOfStream: "+mInputEndOfStream + " frameCnt="+mInputFrameCnt + " sampleSize: " +sample.mBufferSize);
                    mSampleQueue.remove();
                } else {
                    //send the loop decoder.
                    decodeMediaSample(null);
                }
            } else {
                YYLog.debug(this, "[decoder] video, no sample, so break");
                break;
            }
            //need drain out?
            doDrainOutput(0);
        }

        //drain out all the decoded packet.
        if(mInputEndOfStream && !mOutputEndOfStream) {
            doDrainOutput(1000);
            mDecoderThread.sendMessage(Message.obtain(mDecoderThread.getHandler(), MSG_DECODE_SAMPLE));
        }
        //YYLog.debug(this, "[decoder] video decodeSampleLoop end");
    }

    @Override
    protected void doReleaseDecoder() {
        super.doReleaseDecoder();
        //clear the queue
        /*
        while(true) {
            YYMediaSample sample = mSampleQueue.poll();
            if(sample == null)
                break;
            else
                sample.decRef();
        }
        */
    }

    @Override
    public void onStart() {
        //do nothing
    }

    @Override
    public void onStop() {
        //do nothing
    }

    @Override
    public void onPause() {
        //do nothing
    }

    @Override
    public void onResume() {
        //do nothing
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_START_DECODER:
                Surface surface = (Surface)msg.obj;
                doInitDecoder(mMediaFomart, surface);
                break;
            case MSG_RELEASE_DECODER:
                doReleaseDecoder();
                break;
            case MSG_STOP_DECODER:
                break;
            case MSG_END_OF_STREAM:
                doNotifyEndOfStream();
                break;
            case MSG_DECODE_SAMPLE:
                decodeSampleLoop();
                break;
        }
    }
}
