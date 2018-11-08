package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Message;
import android.provider.MediaStore;

import com.ycloud.api.common.SampleType;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.svplayer.EGLBase;
import com.ycloud.svplayer.FfmpegCodecWrapper;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.svplayer.MediaInfo;
import com.ycloud.svplayer.NativeFfmpeg;
import com.ycloud.svplayer.surface.I420ToSurface;
import com.ycloud.utils.MediaFormatHelper;
import com.ycloud.utils.YMRThread;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;
import com.yy.hiidostatis.api.HiidoSDK;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Administrator on 2018/3/9.
 */

public class FfmpegDecoderFilter extends AbstractMediaDecoderFilter
        implements YMRThread.Callback,
        MediaBufferQueue.InputCallback,
        MediaBufferQueue.OutputCallback<YYMediaSample>,
        NativeFfmpeg.Callback
{
    private final int MSG_DECODER = 20000;
    private final int MSG_END_STREAM = 20001;
    private YMRThread   mDecoderThread = null;

    private boolean  mOutputTextureMode = false;
    private MediaFilterContext mFilterContext = null;

    protected ByteBuffer    mOutputBuffer = null;
    protected ByteBuffer    mInputBuffer = null;
    protected YYMediaSample mLastDecodeSample = null;
    protected NativeFfmpeg mNativeFfmpgDecoder;
    protected boolean       mInputEndOfStream = false;
    protected boolean       mOutputEndOfStream = false;

    protected int           mWidth = 0;
    protected int           mHeight = 0;
    protected int           mPlanWidth = 0;
    protected int           mPlanHeight = 0;

    private boolean         mError = false;

    protected MediaBufferQueue<YYMediaSample>  mInputBufferQueue = null;
    protected MediaBufferQueue<YYMediaSample>  mOutputBufferQueue = null;


    protected AbstractYYMediaFilter mOutputFilter  = null;

    protected TreeMap<Long, Long> mDts2PtsMap = new TreeMap<>();
    protected AtomicInteger       mSampleCounter = new AtomicInteger(0);

    public FfmpegDecoderFilter(MediaFilterContext context) {
        mFilterContext = context;
    }

    public void setmOutputTextureMode(boolean enable) {
        mOutputTextureMode = enable;
    }

    public void setInputBufferQueue(MediaBufferQueue<YYMediaSample> inputBufferQueue) {
        mInputBufferQueue = inputBufferQueue;
        mInputBufferQueue.setOutputCallback(this);
    }

    public void setOutputBufferQueue(MediaBufferQueue<YYMediaSample> outputBufferQueue) {
        mOutputBufferQueue = outputBufferQueue;
        mOutputBufferQueue.setInputCallback(this);
    }

    public void setOutputFilter(AbstractYYMediaFilter outputFilter) {
        mOutputFilter = outputFilter;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void initDecoder(MediaFormat format) {
       super.initDecoder(format);

       int frameType = MediaFormatHelper.getFrameFormat(format);
       mNativeFfmpgDecoder = new NativeFfmpeg();
       mNativeFfmpgDecoder.setCallback(this);
       mNativeFfmpgDecoder.create(MediaFormatHelper.getFrameFormat(format), format);

      if(frameType == MediaConst.FRAME_TYPE_H264 || frameType == MediaConst.FRAME_TYPE_HEVC) {
          //yuv I420.
          int width = format.getInteger(MediaFormat.KEY_WIDTH);
          int height = format.getInteger(MediaFormat.KEY_HEIGHT);

          mPlanWidth = mWidth = width;
          mPlanHeight = mHeight = height;

          //16 aligne.
          width = (width /16)*16 +  (width % 16 == 0? 0 : 16);
          height =(height /16)*16 +  (height % 16 == 0? 0 : 16);

          mOutputBuffer = ByteBuffer.allocateDirect((int)(width*height*1.5));
          mInputBuffer = ByteBuffer.allocateDirect((int)(width*height*1.5));
      } else {
          //TODO. audio aac.
      }

       mDecoderThread = new YMRThread("ffmpegDecoder");
       mDecoderThread.setCallback(this);
       mDecoderThread.start();

       YYLog.info(this,"initDecoder finish");
    }

    @Override
    public void releaseDecoder() {
        super.releaseDecoder();
        mDecoderThread.stop();
        mNativeFfmpgDecoder.destroy();
        mOutputBuffer = null;
        mInputBufferQueue = null;
    }

    @Override
    public void decodeFrame() {
        //send a decode frame message.
        mDecoderThread.sendEmptyMessage(MSG_DECODER);
    }

    private void decodeFrameDelay(long delayMs) {
        //send a decode frame message.
        mDecoderThread.sendEmptyMessageDelayed(MSG_DECODER, delayMs);
    }

    public void doDecodeFrame() {
        if(mInputEndOfStream) {
            processEndofStream();
            return;
        }

        //input
        if (mInputBufferQueue == null)
            return;

        while(true && !mError) {
            if(mLastDecodeSample != null) {
                if(!tryToDeliverToOuputFilter(mLastDecodeSample)) {
                    break;
                } else {
                    mLastDecodeSample.decRef();
                    mLastDecodeSample = null;
                }
            }

            YYMediaSample inputSample = mInputBufferQueue.peek();
            if (inputSample == null) {
                return;
            }

            if(inputSample.mEndOfStream) {
                //native flush
                YYLog.info(this, "decoder recv end of input stream");
                mInputEndOfStream = true;
                processEndofStream();
                return;
            }


            //input decoder according to dts, dts more less, input more earlier.
            // output according to pts, pts more less, output more earlier
            //
            mOutputBuffer.rewind();
            mDts2PtsMap.put(Long.valueOf(inputSample.mYYPtsMillions), Long.valueOf(inputSample.mYYPtsMillions));

            mInputBuffer.rewind();
            mInputBuffer.put(inputSample.mDataByteBuffer);
            boolean keyFrame = (inputSample.mFrameType  == VideoConstant.VideoFrameType.kVideoIFrame);
            //direct Buffer..
            int decodeRet = mNativeFfmpgDecoder.decode(mInputBuffer, mOutputBuffer, keyFrame);

            //return -2 as s specific ret num indicate retry is needed
            if (decodeRet == -2) {
                YYLog.warn(TAG, "allocate new buffer:width=" + mPlanWidth + ",height=" + mPlanHeight);
                mOutputBuffer = ByteBuffer.allocateDirect((int) (mPlanWidth * mPlanHeight * 1.5));
                decodeRet = mNativeFfmpgDecoder.decode(mInputBuffer, mOutputBuffer, keyFrame);
            }

            if (decodeRet > 0) {
                mInputBufferQueue.remove();
                //int
                YYMediaSample outputSample  = YYMediaSampleAlloc.instance().alloc();

                Iterator<Map.Entry<Long, Long>> it = mDts2PtsMap.entrySet().iterator();
                if(it.hasNext()) {
                    outputSample.mYYPtsMillions = it.next().getValue();
                    it.remove();
                }

                outputSample.mDataByteBuffer = ByteBuffer.allocate(mOutputBuffer.remaining());
                outputSample.mDataByteBuffer.put(mOutputBuffer);
                outputSample.mDataByteBuffer.position(0);
                outputSample.mBufferOffset = 0;
                outputSample.mDeliverToEncoder = true;
                outputSample.mWidth = mWidth;
                outputSample.mHeight = mHeight;
                outputSample.mPlanWidth = mPlanWidth;
                outputSample.mPlanHeight = mPlanHeight;
                outputSample.mSampleType = SampleType.VIDEO;
                outputSample.mEncodeWidth = mFilterContext.getVideoEncoderConfig().getEncodeWidth();
                outputSample.mEncodeHeight = mFilterContext.getVideoEncoderConfig().getEncodeHeight();
                outputSample.mAndoridPtsNanos = outputSample.mYYPtsMillions*1000000;
                StateMonitor.instance().NotifyDecoder(MediaConst.MEDIA_TYPE_VIDEO, outputSample.mYYPtsMillions);

                if(!tryToDeliverToOuputFilter(outputSample))
                    break;

                outputSample.decRef();
            } else if (decodeRet == 0) {
                mInputBufferQueue.remove();
            } else {
                YYLog.error(this, "decoder error!!!");
                mError = true;
                //error
            }
        }
    }

    private boolean tryToDeliverToOuputFilter(YYMediaSample sample) {
        if (mOutputFilter == null || !mOutputFilter.processMediaSample(sample, this)) {
            mLastDecodeSample = sample;
            decodeFrameDelay(100);
            return false;
        } else {
            return true;
        }
    }

    public void processEndofStream() {
        mInputEndOfStream = true;

        if(mOutputEndOfStream)
            return;

        while(true) {
            if (mLastDecodeSample != null) {
                if(!tryToDeliverToOuputFilter(mLastDecodeSample)) {
                    break;
                } else {
                    if(mLastDecodeSample.mEndOfStream) {
                        YYLog.info(this, "processEndOfStream, end of stream 1");
                        mOutputEndOfStream = true;
                        mLastDecodeSample.decRef();
                        mLastDecodeSample = null;
                        return;
                    }

                    mLastDecodeSample.decRef();
                    mLastDecodeSample = null;
                }
            }

            mOutputBuffer.rewind();
            //direct Buffer..
            YYLog.info(this, "decoder flush begin!!");
            int decodeRet = mNativeFfmpgDecoder.flush(mOutputBuffer);
            YYLog.info(this, "decoder flush end!!");
            if (decodeRet > 0) {
                YYMediaSample outputSample = YYMediaSampleAlloc.instance().alloc();

                Iterator<Map.Entry<Long, Long>> it = mDts2PtsMap.entrySet().iterator();
                if (it.hasNext()) {
                    outputSample.mYYPtsMillions = it.next().getValue();
                    it.remove();
                }

                outputSample.mDataByteBuffer = ByteBuffer.allocate(mOutputBuffer.remaining());
                outputSample.mDataByteBuffer.put(mOutputBuffer);
                outputSample.mDataByteBuffer.position(0);
                outputSample.mBufferOffset = 0;
                outputSample.mDeliverToEncoder = true;
                outputSample.mWidth = mWidth;
                outputSample.mHeight = mHeight;
                outputSample.mPlanWidth = mPlanWidth;
                outputSample.mPlanHeight = mPlanHeight;
                outputSample.mEncodeWidth = mFilterContext.getVideoEncoderConfig().getEncodeWidth();
                outputSample.mEncodeHeight = mFilterContext.getVideoEncoderConfig().getEncodeHeight();
                outputSample.mSampleType = SampleType.VIDEO;
                outputSample.mAndoridPtsNanos = outputSample.mYYPtsMillions*1000000;
                StateMonitor.instance().NotifyDecoder(MediaConst.MEDIA_TYPE_VIDEO, outputSample.mYYPtsMillions);

                if(!tryToDeliverToOuputFilter(outputSample)) {
                    break;
                }
                outputSample.decRef();
            } else if (decodeRet == 0) {
                YYLog.info(this, "FfmpegDecoderFilter processEndOfStream flush decoder: EGAIN");
            } else if(decodeRet == -2){
                StateMonitor.instance().NotifyDecoderEnd(MediaConst.MEDIA_TYPE_VIDEO);
                YYLog.info(this, "FfmpegDecoderFilter end of stream begin!");
                //error, delived the end of stream..
                YYMediaSample outputSample = YYMediaSampleAlloc.instance().alloc();
                outputSample.mEndOfStream = true;
                outputSample.mDeliverToEncoder = true;
                outputSample.mSampleType = SampleType.VIDEO;

                if(!tryToDeliverToOuputFilter(outputSample))
                    break;

                outputSample.decRef();
                YYLog.info(this, "FfmpegDecoderFilter end of stream end!");
                mOutputEndOfStream = true;
                break;
            } else {
                YYLog.info(this, "FfmpegDecoderFilter processEndOfStream flush decoder fail:" + decodeRet);
                //throw a excecption..
                break;
            }
        }
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_DECODER:
                doDecodeFrame();
                break;
            default:
                break;
        }
    }

    @Override
    public void getMediaSample(SampleType sample) {
        if(sample != SampleType.VIDEO)
            return;

        //send a message to ffmpeg decoder thread.
        decodeFrame();
    }

    @Override
    public void outputMediaSample(YYMediaSample sample) {
        decodeFrame();
    }

    @Override
    public void onFormatChanged(MediaInfo info) {
        mWidth = info.width;
        mHeight = info.height;
        mPlanWidth = info.planeWidth;
        mPlanHeight = info.planeHeight;
        YYLog.warn(TAG, "onFormatChanged:width=" + mWidth + ",height=" + mHeight + ",planeWidth=" + mPlanWidth + ",planeHeight=" + mPlanHeight);
    }
}
