package com.ycloud.mediafilters;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;


import com.ycloud.api.common.SampleType;
import com.ycloud.common.Constant;
import com.ycloud.gpuimage.adapter.GlCliper;
import com.ycloud.gpuimage.adapter.GlTextureImageReader;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.mediacodec.videocodec.X264SoftEncoder;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.utils.TimeUtil;
import com.ycloud.ymrmodel.ImageBufferPool;
import com.ycloud.ymrmodel.JVideoEncodedData;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.ycloud.ymrmodel.YUVImageBuffer;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.utils.ImageFormatUtil;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;
import com.yy.mediaframeworks.gpuimage.adapter.GlPboReader;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

@SuppressLint("NewApi")
public class X264SoftEncoderFilter extends AbstractEncoderFilter implements Runnable {
    static {
        try {
            System.loadLibrary("audioengine");
            System.loadLibrary("ycmedia");
            JVideoEncodedData.nativeClassInit();
        } catch (UnsatisfiedLinkError e) {
            YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
        }
    }

    private static final int MSG_FRAME_AVAILABLE = 1;
    private static final int MSG_QUIT = 2;

    private AtomicInteger mBitRateReqInKbps = new AtomicInteger(0);
    //private String mConfigStr = "preset=yyveryfast:bframes=0:b-pyramid=none:threads=2:sliced-threads=0:rc-lookahead=0:sync-lookahead=1:mbtree=0:force-cfr=0:me=dia:chroma_me=0:psy=0:b-adapt=0:keyint=72:min-keyint=72:";

    //Hight quality config.
    //private String mHQConfigStr = "preset=yyveryfast:bframes=0:b-pyramid=none:threads=2:sliced-threads=0:rc-lookahead=0:sync-lookahead=1:mbtree=0:force-cfr=0:me=dia:chroma_me=0:psy=0:b-adapt=0:keyint=72:min-keyint=72:";
    private String mConfigStr = "preset=yyveryfast:bframes=2:b-pyramid=none:threads=0:sliced-threads=0:rc-lookahead=0:sync-lookahead=1:mbtree=0:force-cfr=0:chroma_me=0:psy=0:b-adapt=0:keyint=90:min-keyint=90:subme=1:weightp=0:weightb=0:8x8dct=0:aq-mode=0:";
    private String mNoBFrameConfigStr = "preset=yyveryfast:bframes=0:b-pyramid=none:threads=0:sliced-threads=0:rc-lookahead=0:sync-lookahead=1:mbtree=0:force-cfr=0:chroma_me=0:psy=0:b-adapt=0:keyint=90:min-keyint=90:subme=1:weightp=0:weightb=0:8x8dct=0:aq-mode=0:";
    //private String mConfigStr = "preset=yyveryfast:bframes=0:b-pyramid=none:threads=0:sliced-threads=0:rc-lookahead=0:sync-lookahead=1:mbtree=0:force-cfr=0:chroma_me=0:psy=0:b-adapt=0:keyint=72:min-keyint=72:";

    private final String mLowDelayConfigStr = "preset=yy:keyint=72:min-keyint=72:scenecut=0:bframes=0:b-adapt=0:b-pyramid=none:threads=2:sliced-threads=0:ref=2:subme=3:me=dia:analyse=i4x4,i8x8,p8x8,b8x8:direct=spatial:" +
            "weightp=0:weightb=0:8x8dct=1:cabac=1:deblock=0,0:psy=0:trellis=0:aq-mode=0:rc-lookahead=0:sync-lookahead=0:mbtree=0:force-cfr=0:";

    private ImageBufferPool mYUVImagePool = null;
    private YUVInputBufferQueue mYuvImageQueue = null;
    private X264SoftEncoder mEncoder = null;

    private static String mEncoderNameCurrent = "X264Soft";
    private int mEncodeWidth = 0;
    private int mEncodeHeight = 0;

    //new add
    private volatile EncoderHandler mHandler;
    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning = false;

    private GlTextureImageReader mGlImageReader = null;
    private GlCliper mGLCliper = null;
    private MediaFilterContext mFilterContext = null;
    private long encodeTime = 0;
    private long readPixelTime = 0;
    private long mStartedTimeMs = 0;

    private AtomicInteger mSyncFrameCnt = new AtomicInteger(0);
    private int mCameraFacing = -1;

    //全I帧模式.
    private boolean mIFrameMode = true;
    private boolean mStoped = false;
    private boolean mRecordMode = false;  //是否是录制阶段，录制阶段的pts需要从开始录制开始计算.

    private int mDiscardCnt = 0;
    private long mGlReadCost = 0;
    private long mYuvTransCost = 0;
    private long mGlReadCnt = 0;

    private long mEncodeCost = 0;
    private long mEncodeCnt = 0;
    private long mMuxCost = 0;
    private int mInputFrameCnt = 0;
    private boolean mInputEndOfStream = false;

    private long mIdleTime = 0;
    private long mLastDecodeTimeStamp = 0;
    private long mDecodeBreakCnt = 0;

    private Thread mEncodeThread = null;

    public static class YUVInputBufferQueue {
        private Object mLock = new Object();
        private AtomicInteger mBufferCnt = new AtomicInteger(0);
        private int mMinBufferCnt = 15;
        private int mMaxBufferCnt = 30;

        private long mWaitTimeMs = 0;

        ConcurrentLinkedQueue<YUVImageBuffer> mSampleBufferQueue = new ConcurrentLinkedQueue<YUVImageBuffer>();

        public YUVInputBufferQueue(int minCnt, int maxCnt) {
            mMinBufferCnt = minCnt;
            mMaxBufferCnt = maxCnt;
        }

        public long getWaitTimeMs() {
            return mWaitTimeMs;
        }

        public void offer(YUVImageBuffer buffer, long timeoutMs) throws InterruptedException {
            if (mBufferCnt.get() > mMaxBufferCnt) {
                synchronized (mLock) {
                    if (mBufferCnt.get() > mMaxBufferCnt) {
                        //rare
                        long beginTimeMs = System.currentTimeMillis();
                        mLock.wait(timeoutMs);
                        mWaitTimeMs += System.currentTimeMillis() - beginTimeMs;
                    }
                }
            }
            mSampleBufferQueue.offer(buffer);
            mBufferCnt.getAndAdd(1);
        }

        public YUVImageBuffer poll() {
            YUVImageBuffer ret = mSampleBufferQueue.poll();
            if (ret != null) {
                if (mBufferCnt.decrementAndGet() == mMinBufferCnt) {
                    synchronized (mLock) {
                        if (mBufferCnt.get() == mMinBufferCnt) {
                            mLock.notifyAll();
                        }
                    }
                }
            }
            return ret;
        }

        public int size() {
            return mBufferCnt.get();
        }
    }

    //不支持stop之后， 然后再start, 直接重现new一个新的softEncodeFilter就可以了.
    public X264SoftEncoderFilter(MediaFilterContext filterContext, boolean recordMode) {
        mFilterContext = filterContext;
        mRecordMode = recordMode;
        YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264SoftEncoderFilter constructor!!");
    }

    @Override
    public VideoEncoderType getEncoderFilterType() {
        return VideoEncoderType.SOFT_ENCODER_X264;
    }

    public boolean startEncode() {
        synchronized (mReadyFence) {
            if (mRunning) {
                YYLog.warn(this, Constant.MEDIACODE_ENCODER + "X264 Encoder thread already running");
                return true;
            }

            if (mStoped) {
                YYLog.error(this, "X264SoftEncoderFilter is stoped");
                return false;
            }
            setEncodeCfg(mFilterContext.getVideoEncoderConfig());
            mSyncFrameCnt.set(0);
            mRunning = true;
            GlPboReader.checkPboSupport(mFilterContext.getAndroidContext());
            mGlImageReader = new GlTextureImageReader(mFilterContext.getVideoEncoderConfig().getEncodeWidth(), mFilterContext.getVideoEncoderConfig().getEncodeHeight());

            mGLCliper = new GlCliper();

            mYUVImagePool = new ImageBufferPool<YUVImageBuffer>(mEncoderConfig.getEncodeWidth(), mEncoderConfig.getEncodeHeight(), 6, ImageFormat.YUV_420_888, YUVImageBuffer.class, 0);
            mYuvImageQueue = new YUVInputBufferQueue(10, 20);

            YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264SoftEncoderFilter startEncode width " + mEncoderConfig.getEncodeWidth() + " height " + mEncoderConfig.getEncodeHeight() + " bitRate " + mEncoderConfig.mBitRate + " mFrameCnt " + mEncoderConfig.mFrameRate);
            //x264关于码流的单位都是kbps
            mBitRateReqInKbps.set(mEncoderConfig.mBitRate / 1024);

            // 和编码线程存在线程同步问题
            synchronized (mReadyFence) {
                if (mEncoderConfig.mEncodeParameter == null || mEncoderConfig.mEncodeParameter.isEmpty()) {
                    if (mEncoderConfig.mFrameRate < X264SoftEncoder.LOW_FRAMERATE) {
                        mEncoderConfig.mEncodeParameter = mNoBFrameConfigStr;
                    } else {
                        mEncoderConfig.mEncodeParameter = mConfigStr;
                    }
                    mFilterContext.getVideoEncoderConfig().mEncodeParameter = mEncoderConfig.mEncodeParameter;  // 网络调整编码参数会改动到此参数，如果不重置会导致编码器不停的重启
                }

                if (mEncoderConfig.mLowDelay) {
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264 startEncode lowDelay");
                    mEncoderConfig.mEncodeParameter = mLowDelayConfigStr;
                } else if (mEncoderConfig.mHighQuality) {
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264 startEncode high quality");
                    //mEncoderConfig.mEncodeParameter = mHQConfigStr;
                }

                mEncodeParam = mEncoderConfig.toString();
            }

            mEncoder = X264SoftEncoder.createEncoder();
            mEncoder.initEncoder(mEncoderConfig, mFilterContext.getRecordConfig());
            mIFrameMode = mEncoderConfig.mIFrameMode;
            YYLog.info(this, "set full I frame mode:" + mIFrameMode);

            mEncodeThread = new Thread(this, SDK_NAME_PREFIX + "x264Encoder");
            mEncodeThread.start();
            while (!mReady) {
                try {
                    YYLog.info(this, "[thdsync] ready fence waitting");
                    mReadyFence.wait();
                    YYLog.info(this, "[thdsync] got ready fence ");
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
//            mFilterContext.getEncodeParamTipsMgr().setEncoderParam(mFilterContext.getVideoEncoderConfig().toString() + ", localConfig:" + mEncodeParam);
        }
        StateMonitor.instance().NotifyEncodeStart(MediaConst.MEDIA_TYPE_VIDEO);
        mDiscardCnt = 0;
        mStartedTimeMs = TimeUtil.getTickCount();
        YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264SoftEncoderFilter startEncode finished!");
        return true;
    }

    public void stopEncode() {

        Thread encodeThread = mEncodeThread;
        synchronized (mReadyFence) {
            if (mStoped) {
                YYLog.info(this, "X264Filter is stoped!!");
                return;
            }
            mStoped = true;

            YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264 stopEncode begin");
            mCameraFacing = -1;

            if (mGlImageReader != null) {
                mGlImageReader.destroy();
                mGlImageReader = null;
            }

            if (mGLCliper != null) {
                mGLCliper.deInit();
                mGLCliper = null;
            }
            mSyncFrameCnt.set(0);
            if (mHandler != null) {
                mHandler.removeMessages(MSG_FRAME_AVAILABLE);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
            }

            mEncodeThread = null;
        }

        if (encodeThread != null) {
            try {
                encodeThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264 stopEncode end, discardCnt=" + mDiscardCnt);
        mDiscardCnt = 0;
    }

    private void deliverEndOfStream() {
        YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264 deliver end of stream to next filter");
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
        sample.mSampleType = SampleType.VIDEO;
        sample.mEndOfStream = true;
        sample.mEncoderType = VideoEncoderType.SOFT_ENCODER_X264;
        deliverToDownStream(sample);
        sample.decRef();
    }

    private void handleEndOfStream() {
        long encodeBegin = System.currentTimeMillis();
        YYLog.info(this, "[x264] flush begin");
        JVideoEncodedData[] outputVideoArray = mEncoder.flush();
        YYLog.info(this, "[x264] flush end");

        mFilterContext.getMediaStats().onVideoEncodeInput();
        if (outputVideoArray != null) {
            mFilterContext.getMediaStats().onVideoEncodeOutput(outputVideoArray.length);
        }

        mEncodeCost += (System.currentTimeMillis() - encodeBegin);
        mFilterContext.getMediaStats().addEncodeCost(System.currentTimeMillis() - encodeBegin);

        for (int i = 0; i < outputVideoArray.length; i++) {
            outputVideoArray[i].mEncodeType = VideoEncoderType.SOFT_ENCODER_X264;
            YYMediaSample sample = outputVideoArray[i].toYYMediaSample();

            long muxBegin = System.currentTimeMillis();
//                    YYLog.info(this,"===================================handleFrameAvailable pts:"+sample.mYYPtsMillions
//                                                    +" dts:"+sample.mDtsMillions+" gap:"
//                                                    +(sample.mYYPtsMillions - sample.mDtsMillions)
//                                                    +" frameType:"+sample.mFrameType
//                                                    + "sampleSize: " + sample.mBufferSize);

            //write into h264 encode. write into files

            deliverToDownStream(sample);
            mMuxCost += (System.currentTimeMillis() - muxBegin);
            mEncodeCnt++;
            sample.decRef();
        }

        deliverEndOfStream();
    }

    private void handleFrameAvailable() {
        if (mLastDecodeTimeStamp != 0) {
            mIdleTime += System.currentTimeMillis() - mLastDecodeTimeStamp;
            mFilterContext.getMediaStats().addSoftEncodeThreadIdleTime(System.currentTimeMillis() - mLastDecodeTimeStamp);
        }

        while (true) {
            if (mStoped)
                break;

            YUVImageBuffer inputData = mYuvImageQueue.poll();
            if (inputData == null) {
                mDecodeBreakCnt++;
                mFilterContext.getMediaStats().addSoftEncodeThreadBreakCount();
                break;
            }

            mFilterContext.getFilterFlowController().onVideoEncodeInput();

            if (inputData.mEndOfStream) {
                YYLog.info(this, "[x264] thread idle time:" + mIdleTime + " breakCnt=" + mDecodeBreakCnt);
                handleEndOfStream();
                StateMonitor.instance().NotifyEncodeEnd(MediaConst.MEDIA_TYPE_VIDEO);
                //deliverEndOfStream();
                break;
            }

            long begin = System.currentTimeMillis();
            if (mEncoder == null) {
                YYLog.error(this, "handleFrameAvailable encoder is null!");
                return;
            }

            if (mEncodeWidth != inputData.mWidth || mEncodeHeight != inputData.mHeight) {
                handleEncodeResolution(inputData.mWidth, inputData.mHeight);
                mEncodeWidth = inputData.mWidth;
                mEncodeHeight = inputData.mHeight;
            }

//            //TODO. 存在线程同步问题, 这里是在编码线程使用了mFilterContext.getVideoEncoderConfig中的东西.
            if (checkEncodeUpdate(inputData.mWidth, inputData.mHeight, inputData.mLowDelay, inputData.mFrameRate, inputData.mBitRate, inputData.mEncodeParameter)) {
                //reset the encoder.
//                    synchronized (mReadyFence) {
                // 低延时模式参数不能改
                if (mEncoderConfig.mEncodeParameter == null || mEncoderConfig.mEncodeParameter.isEmpty()) {
                    if (mEncoderConfig.mFrameRate < X264SoftEncoder.LOW_FRAMERATE) {
                        mEncoderConfig.mEncodeParameter = mNoBFrameConfigStr;
                    } else {
                        mEncoderConfig.mEncodeParameter = mConfigStr;
                    }
                }

                if (mEncoderConfig.mLowDelay) {
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "X264 handleFrame lowDelay");
                    mEncoderConfig.mEncodeParameter = mLowDelayConfigStr;
                }

                YYLog.info(this, Constant.MEDIACODE_ENCODER + "config changed, restart the encoder!! config=" + mEncoderConfig.toString());

                mBitRateReqInKbps.set(mEncoderConfig.mBitRate / 1024);
                X264SoftEncoder.destroyEncoder(mEncoder);
                mEncoder = X264SoftEncoder.createEncoder();
                mEncoder.initEncoder(mEncoderConfig, mFilterContext.getRecordConfig());

                mEncodeParam = mEncoderConfig.toString();
//                    }

//                mFilterContext.getEncodeParamTipsMgr().setEncoderParam(mFilterContext.getVideoEncoderConfig().toString() + ", localConfig:" + mEncodeParam);
            }

            long encodeBegin = System.currentTimeMillis();
            JVideoEncodedData[] outputVideoArray = null;
            if (mSyncFrameCnt.get() > 0 || mIFrameMode) {
//                YYLog.debug(this,Constant.MEDIACODE_ENCODER+"handleFrameAvailable request IFrame ==============");
                outputVideoArray = mEncoder.encode(inputData.mDataBuffer, inputData.mPts, VideoConstant.VideoFrameType.kVideoIFrame);
                mSyncFrameCnt.decrementAndGet();
            } else {
                //YYLog.info(this,Constant.MEDIACODE_ENCODER+"&&&&&&&&&&&&&&&&&&  handleFrameAvailable Normal frame , pts="+inputData.mPts);
                outputVideoArray = mEncoder.encode(inputData.mDataBuffer, inputData.mPts, VideoConstant.VideoFrameType.kVideoUnknowFrame);
            }
            mFilterContext.getMediaStats().onVideoEncodeInput();

            if (outputVideoArray == null) {
                YYLog.error(this, Constant.MEDIACODE_ENCODER + "handleFrameAvailable outputVideoArray null!");
                mYUVImagePool.freeBuffer(inputData);
                return;
            }

            mFilterContext.getMediaStats().onVideoEncodeOutput(outputVideoArray.length);
            mEncodeCost += (System.currentTimeMillis() - encodeBegin);
            mFilterContext.getMediaStats().addEncodeCost(System.currentTimeMillis() - encodeBegin);
//            UploadStatManager.getInstance().endEncode((int)inputData.mPts);

            for (int i = 0; i < outputVideoArray.length; i++) {
                outputVideoArray[i].mEncodeType = VideoEncoderType.SOFT_ENCODER_X264;
                YYMediaSample sample = outputVideoArray[i].toYYMediaSample();

                long muxBegin = System.currentTimeMillis();
//                    YYLog.info(this,"===================================handleFrameAvailable pts:"+sample.mYYPtsMillions
//                                                    +" dts:"+sample.mDtsMillions+" gap:"
//                                                    +(sample.mYYPtsMillions - sample.mDtsMillions)
//                                                    +" frameType:"+sample.mFrameType
//                                                    + "sampleSize: " + sample.mBufferSize);

                //write into h264 encode. write into files

                deliverToDownStream(sample);
                mMuxCost += (System.currentTimeMillis() - muxBegin);
                mEncodeCnt++;

                handleEncodedFrameStats(outputVideoArray[i].mDataLen, inputData.mDataBuffer.array().length, sample.mFrameType);
                sample.decRef();
            }

            for (int i = 0; i < outputVideoArray.length; i++) {
                //release the directBuffer.
                outputVideoArray[i].releaseVideoByteBuffer();
            }
            mYUVImagePool.freeBuffer(inputData);
            //YYLog.debug(this, "X264SoftEncoderFilter.encoded frame : " +mEncodeCnt);

            long end = System.currentTimeMillis();
            if (System.currentTimeMillis() - encodeTime >= 3 * 1000) {
                YYLog.info(this, Constant.MEDIACODE_ENCODER + "processMediaSample encode time, " +
                        " avg= " + mEncodeCost / mEncodeCnt +
                        " totalCost=" + mEncodeCost +
                        " frameCnt=" + mEncodeCnt);

                YYLog.info(this, Constant.MEDIACODE_ENCODER + "processMediaSample mux time, " +
                        " avg= " + mMuxCost / mEncodeCnt +
                        " totalCost=" + mMuxCost +
                        " frameCnt=" + mEncodeCnt);
                encodeTime = System.currentTimeMillis();
            }
//            }
        }
        mLastDecodeTimeStamp = System.currentTimeMillis();
    }

    public void stopRecording() {
        mSyncFrameCnt.set(0);
    }

    private void sendEndOfStreamMessage() {
        YYLog.info(this, "[x264] sendEndOfStreamMessage begin");
        synchronized (mReadyFence) {
            YUVImageBuffer imageBuf = new YUVImageBuffer();
            imageBuf.mDataBuffer = null;
            imageBuf.mEndOfStream = true;


            try {
                mYuvImageQueue.offer(imageBuf, 1 * 1000 * 60);
            } catch (InterruptedException e) {
                YYLog.error(this, "[exception] x264 encoder input queue fail: " + e.toString());
                e.printStackTrace();
            }
            if (mHandler != null) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE));
            }
        }

        YYLog.info(this, "[x264] sendEndOfStreamMessage end");
    }

    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return false;
            }
        }

        //停止了不再处理上游来的sample
        if (mInputEndOfStream || mStoped)
            return false;

//        UploadStatManager.getInstance().beginEncode((int)sample.mYYPtsMillions);
        if ((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || sample.mEndOfStream) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "processMediaSample: end of stream");
            mInputEndOfStream = true;
            sendEndOfStreamMessage();
//            mFilterContext.getMediaStats().setmEndTimeStamp(System.currentTimeMillis());
//            mFilterContext.getMediaStats().dump();
            return false;
        }

        //与stopEncoder, startEncode需要互斥，不然stopEncoder了，还可能在使用mGLCLiper等等.

        synchronized (mReadyFence) {
            if (mStoped)
                return false;

            long begin = System.currentTimeMillis();
            VideoEncoderConfig vEncoderCfg = mFilterContext.getVideoEncoderConfig();
            //只能在这里裁剪了.
            mGLCliper.clip(sample, sample.mEncodeWidth, sample.mEncodeHeight, false);
            mFilterContext.getMediaStats().addClipTextureCost(System.currentTimeMillis() - begin);

            //TODO. 软编码.
            long beginReadMs = System.currentTimeMillis();
            byte[] pixBuf = mGlImageReader.read(sample.mTextureId, sample.mEncodeWidth, sample.mEncodeHeight);
            if (pixBuf == null) {
                YYLog.error(this, "GLImageReader error!!!!");
                return false;
            }
            mFilterContext.getMediaStats().addGLReaderCost(System.currentTimeMillis() - beginReadMs);

            long end = System.currentTimeMillis();
            long transBegin = 0;
            long transEnd = 0;

            YUVImageBuffer imageBuf = (YUVImageBuffer) mYUVImagePool.newBuffer(sample.mEncodeWidth, sample.mEncodeHeight);
            if (imageBuf == null) {
                //never happen
                YYLog.warn(this, Constant.MEDIACODE_ENCODER + "ByteBufferPool is empty!");
                mDiscardCnt++;
                return false;
            }

            transBegin = System.currentTimeMillis();
            ImageFormatUtil.RBGAtoYUV(pixBuf, vEncoderCfg.getEncodeWidth(), vEncoderCfg.getEncodeHeight(), imageBuf.mDataBuffer.array());
            transEnd = System.currentTimeMillis();

//            if(mTestCnt < 5) {
//                ImageStorageUtil.saveYUV2JPEG(imageBuf.mDataBuffer.array(), vEncoderCfg.getEncodeWidth(), vEncoderCfg.getEncodeHeight());
//            }

            if (mRecordMode) {
                imageBuf.mPts = TimeUtil.getTickCount() - mStartedTimeMs;
            } else {
                imageBuf.mPts = sample.mYYPtsMillions;
            }
            imageBuf.mFrameRate = mFilterContext.getVideoEncoderConfig().mFrameRate;
            imageBuf.mBitRate = mFilterContext.getVideoEncoderConfig().mBitRate;
            imageBuf.mLowDelay = mFilterContext.getVideoEncoderConfig().mLowDelay;
            imageBuf.mEncodeParameter = mFilterContext.getVideoEncoderConfig().mEncodeParameter;
            imageBuf.mEndOfStream = mInputEndOfStream;

            //一直等待，因为一个imageBuffer的大小就有2M的数据，目前发现软编码比较慢，需要等待其完成.
            try {
                mYuvImageQueue.offer(imageBuf, 1 * 60 * 1000);
            } catch (InterruptedException e) {
                YYLog.error(this, "[exception] x264 encoder input queue fail: " + e.toString());
                e.printStackTrace();
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE));

            StateMonitor.instance().NotifyEncode(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);

            long glReadCost = end - begin;
            long yuvTransCost = transEnd - transBegin;
            mGlReadCnt++;
            mGlReadCost += glReadCost;
            mYuvTransCost += yuvTransCost;
        }

        //stat
        if (System.currentTimeMillis() - readPixelTime >= 3 * 1000) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "processMediaSample read pixel buffer time, " +
                    " avg=" + (mGlReadCost / mGlReadCnt) +
                    " total=" + mGlReadCost +
                    " framecnt=" + mGlReadCnt);

            YYLog.info(this, Constant.MEDIACODE_ENCODER + "processMediaSample trans rgb2yuv time, " +
                    " avg=" + (mYuvTransCost / mGlReadCnt) +
                    " total=" + mYuvTransCost +
                    " framecnt=" + mGlReadCnt);
            readPixelTime = System.currentTimeMillis();
            mYuvTransCost = 0;
            mGlReadCost = 0;
            mGlReadCnt = 0;
        }

        handleCaptureFrameStats();
        return true;
    }

    public void requestSyncFrame() {
        YYLog.info(this, Constant.MEDIACODE_ENCODER + "requestSyncFrame");
        mSyncFrameCnt.addAndGet(1);
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    @Override
    public void run() {
        YYLog.info(this, "[tracer] run before prepare");
        Looper.prepare();

        try {
            synchronized (mReadyFence) {
                mHandler = new EncoderHandler(this);
                mReady = true;
                mReadyFence.notify();
                YYLog.info(this, "[tracer] run notify ready");
            }
            Looper.loop();
        } catch (Throwable t) {
            t.printStackTrace();
            YYLog.error(this, "[exception] exception occur, " + t.toString());
        } finally {
            YYLog.info(this, "[tracer] Encoder thread exiting");
            synchronized (mReadyFence) {
                mReady = mRunning = false;
                mHandler = null;
            }
        }
    }

    public void adjustBitRate(final int bitRateInKbps) {
        int targetBiteRateInKbps = bitRateInKbps;
        if (mBitRateReqInKbps.get() == targetBiteRateInKbps) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "adjustBitRate, original bitrate is " + targetBiteRateInKbps + " already");
            return;
        }

        YYLog.info(this, Constant.MEDIACODE_ENCODER + "adjustBitRate, target bitRate: " + targetBiteRateInKbps);
        mBitRateReqInKbps.set(targetBiteRateInKbps);

        synchronized (mReadyFence) {
            if (mEncoder != null) {
                mEncoder.adjustBitRate(mBitRateReqInKbps.get());
            }
        }
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<X264SoftEncoderFilter> mWeakEncoder;

        public EncoderHandler(X264SoftEncoderFilter encoder) {
            mWeakEncoder = new WeakReference<X264SoftEncoderFilter>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;

            X264SoftEncoderFilter encoder = mWeakEncoder.get();
            if (encoder == null) {
                YYLog.warn(this, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_FRAME_AVAILABLE:
                    encoder.handleFrameAvailable();
                    break;
                case MSG_QUIT:
                    if (encoder.mEncoder != null) {
                        YYLog.info(this, "[x264] YuvImageQueue offer wait total timeMs: " + encoder.mYuvImageQueue.getWaitTimeMs());
                        X264SoftEncoder.destroyEncoder(encoder.mEncoder);
                        encoder.mEncoder = null;
                    }
                    if (Looper.myLooper() != null) {
                        Looper.myLooper().quit();
                    }
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }


    public static String getEncoderName() {
        return mEncoderNameCurrent;
    }
}
