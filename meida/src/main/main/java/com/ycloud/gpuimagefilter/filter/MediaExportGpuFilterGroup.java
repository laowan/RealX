package com.ycloud.gpuimagefilter.filter;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.facedetection.VenusSegmentWrapper;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.gpuimagefilter.utils.SegmentCacheDetectWrapper;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.statistics.MediaStats;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.MediaSampleExtraInfo;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Kele on 2018/1/2.
 */

public class MediaExportGpuFilterGroup extends FFmpegFilterGroup {
    //run in async mode.
    private Handler mExportHandler = null;
    private final int MSG_INIT = 1;
    private final int MSG_DRAW_SAMPLE =2;
    private AbstractYYMediaFilter   mOutPathFilter = null;

    private boolean mTextureMode = false;
    private MediaFormat mMediaFormat = null;

    protected MediaStats mMediaStats  =null;
    protected AtomicBoolean  mEnable = new AtomicBoolean(true);

    private ConcurrentLinkedQueue<YYMediaSample>  mSampleQueue = null;

    private boolean mEndOfStream = false;

    public MediaExportGpuFilterGroup(Context context, int sessionID, Looper looper, MediaStats stats) {
        super(context, sessionID, looper);
        mMediaStats = stats;
    }

    public void stop() {
        mEnable.set(true);
        StateMonitor.instance().NotifyGPUEnd(MediaConst.MEDIA_TYPE_VIDEO);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public int getRotation() {
        int rotation = 0;
        try {
            rotation = (mMediaFormat != null && mMediaFormat.containsKey("rotation-degrees") ?
                    mMediaFormat.getInteger("rotation-degrees") : 0);
        } catch (Exception e) {
            YYLog.error(this, "get rotation-degrees fail");
        }
        if (rotation < 0) {
            rotation += 360;
        }
        return rotation;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void init(MediaFormat mediaFormat) {
        if(!mInited) {
            mMediaFormat = mediaFormat;
            mSampleQueue = new ConcurrentLinkedQueue<>();

            int width = mMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

            int v_rotate = getRotation();
            if(v_rotate ==90 || v_rotate == 270) {
                init(height, width, true);
            }
            else {
                init(width, height, true);
            }

            StateMonitor.instance().NotifyGPUStart(MediaConst.MEDIA_TYPE_VIDEO);
        }
    }

    @Override
    public void startListen() {
        if (!mStartListen.getAndSet(true)) {
            FilterCenter.getInstance().addFilterObserver(this, mLooper, mSessionID);
            mFilterHandler = new Handler(mLooper, null) {
                @Override
                public void handleMessage(Message msg) {

                    //post into sync data task into looper. 这里直接从FilterCenter中取出filterInfo列表，然后添加到gpu FilterGroup中
                    FilterDataStore.OperResult<Integer, FilterInfo> res = FilterCenter.getInstance().getFilterSnapshot(mSessionID);
                    YYLog.info(this, "startListen:   " + mFilterCenterSnapshotVer + " currentResVers: " + res.mSnapshotVer);
                    doFilterBatchAdd(res.mFilterList);
                    mFilterCenterSnapshotVer = res.mSnapshotVer;
                }
            };

            if(mLooper.getThread().getId() == Thread.currentThread().getId()) {

                //post into sync data task into looper. 这里直接从FilterCenter中取出filterInfo列表，然后添加到gpu FilterGroup中
                FilterDataStore.OperResult<Integer, FilterInfo> res = FilterCenter.getInstance().getFilterSnapshot(mSessionID);
                YYLog.info(this, "startListen " + mFilterCenterSnapshotVer + " currentResVers: " + res.mSnapshotVer);
                doFilterBatchAdd(res.mFilterList);
                mFilterCenterSnapshotVer = res.mSnapshotVer;
            } else {
                mFilterHandler.sendEmptyMessage(100);
            }
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if(mEndOfStream || !mInited || !mEnable.get()) {
            YYLog.debug(this, "MediaExportGpuFilterGroup.processMediaSample, but fail: endOfStream="+mEndOfStream
                        + " mInited="+mInited +  " mEnable="+mEnable);
            return false;
        }

        restoreOutputTexture();

        if(!sample.mEndOfStream) {
            StateMonitor.instance().NotifyGPU(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);
        }
        sample.mTimestampMs = sample.mAndoridPtsNanos / 1000000;

        //注意要放到sample的时间戳赋值之后
        if (mUseFilterSelector) {
            mVideoFilterSelector.processMediaSample(sample, upstream);
        }

        //如果上层设置了sample处理的监听，则process每一帧前要通过回调通知上层，供上层对当前帧填充附加信息，或者做额外处理
        MediaSampleExtraInfo info = new MediaSampleExtraInfo();
        if (mMediaInfoRequireListener != null) {
            mMediaInfoRequireListener.onRequireMediaInfo(info, sample.mTimestampMs);
        }

        int requiredFrameData = getRequiredFrameData(sample);
        /*
        //附加节奏信息
        if ((requiredFrameData & OrangeFilter.OF_RequiredFrameData_AudioBeat) > 0) {
            rhythmDetection(sample);
        }*/

        //通过回调附加业务层传递的信息
        boolean needRhythm = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_AudioBeat) > 0;
        if (mMediaInfoRequireListener != null && info != null && needRhythm) {
            mMediaInfoRequireListener.onRequireMediaInfo(info, sample.mTimestampMs);
            sample.mAudioFrameData.beat = info.getRhythmQuality();
            sample.mAudioFrameData.loudness = info.getRhythmStrengthRatio();
            sample.mAudioFrameData.loudnessSmooth = info.getRhythmSmoothRatio();
            sample.mAudioFrameData.frequencyData = info.getRhythmFrequencyData();
        }

        mMediaStats.onGLProcessInput();

        bodyInfoSearch(sample);
        faceInfoSearch(sample);

        if(sample.mDataByteBuffer != null && sample.mTextureId == -1) {
            //input the sample queue.
            drawAsync(sample);
        } else if(sample.mTextureId != -1) {
            mTextureMode = true;
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            sample.mRgbaBytes = null;
            sample.mShouldUpsideDown = true;
            //合成到时候抠图,由于解码处理到OES纹理，先经过transformTextureFilter转换之后,再传到抠图SDK处理
            ((TransFormTextureFilter) mTransformFilter).processMediaSample(sample, upstream, false);
            //抠图信息
            mNeedSegment = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_BackgroundSegment) > 0;
            //填充抠图信息
            if (mNeedSegment) {
                if (!mSegmentInited) {
                    mVenusSegmentWrapper = new VenusSegmentWrapper(mContext, mOutputWidth, mOutputHeight);
                    if (!SDKCommonCfg.getUseCpuSegmentMode()) {
                        mVenusSegmentWrapper.init();
                    } else {
                        mVenusSegmentWrapper.initWithCpu();
                    }
                    mSegmentInited = true;
                }

                //读取抠图缓存到data，并且还原抠图数据
                if (mVenusSegmentWrapper != null) {
                    if (!SDKCommonCfg.getUseCpuSegmentMode()) {
                        SegmentCacheDetectWrapper.SegmentCacheDetectRes detectRes = segmentCacheSearchAndDataRestore(sample);
                        if (!detectRes.isFound) {
                            segmentDataDetectAndCacheSave(sample);
                        }
                    } else {
                        SegmentCacheDetectWrapper.SegmentCacheDetectRes detectRes = segmentCacheSearchAndDataRestoreCpu(sample, 60);
                        if (!detectRes.isFound) {
                            byte[] imageData = getVideoImageData(sample);
                            segmentDataDetectAndCacheSaveCpu(sample, imageData, -1);
                        }
                    }
                }
            }

            mTransformFilter.deliverToDownStream(sample);

            return true;
        }

        //texture mode
        if(((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || sample.mEndOfStream) && mTextureMode)
        {
            mEndOfStream = true;
            mOutPathFilter.processMediaSample(sample, this);
        }

        YYLog.debug(this, "export gpu filter group: processMediaSample");
        return false;
    }

    @Override
    public void destroy() {
        super.destroy();
        //destroy the sample queue
        if(mSampleQueue != null) {
            while(true) {
                YYMediaSample sample = mSampleQueue.poll();
                if(sample == null)
                    break;

                sample.decRef();
            }
        }
    }

    @Override
    protected  void initFilterLayout() {
        mLayout.addPathInFilter(FilterLayout.kAllPathFlag, mTransformFilter);
        performLayout();
    }

    public AbstractYYMediaFilter setFilterGroupOutPath(AbstractYYMediaFilter encoderOutput) {
        mLayout.addPathOutFilter(FilterLayout.kAllPathFlag, encoderOutput);
        performLayout();

        mOutPathFilter = encoderOutput;
        return this;
    }

    private void drawAsync(YYMediaSample sample) {
        if(sample != null) {
            mSampleQueue.add(sample);
            sample.addRef();
        }
        mExportHandler.sendMessage(Message.obtain(mExportHandler, MSG_DRAW_SAMPLE));
    }

    private void drawInternal() {
        while(!mEndOfStream) {
            YYMediaSample sample = mSampleQueue.poll();
            if(sample == null) {
                break;
            }

            if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
            {
                mEndOfStream = true;
                break;
            }
        }
    }

    class GPUFilterGroupHandler extends  Handler {
        MediaExportGpuFilterGroup  mGpuFilterGroup = null;

        public GPUFilterGroupHandler(MediaExportGpuFilterGroup group, Looper looper) {
            super(looper);
            mGpuFilterGroup = group;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT:
                    int width = msg.arg1;
                    int height = msg.arg2;
                    mSampleQueue = new ConcurrentLinkedQueue<>();
                    init(width, height, false);
                    break;
                case MSG_DRAW_SAMPLE:
                    drawInternal();
                    break;
                default:
                    break;
            }
        }
    }

}
