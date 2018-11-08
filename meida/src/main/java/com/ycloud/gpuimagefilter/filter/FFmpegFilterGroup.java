package com.ycloud.gpuimagefilter.filter;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Looper;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.common.OFLoader;
import com.ycloud.facedetection.VenusSegmentWrapper;
import com.ycloud.gpuimage.adapter.GlTextureImageReader;
import com.ycloud.gpuimagefilter.utils.FaceDetect;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.gpuimagefilter.utils.SegmentCacheDetectWrapper;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.MediaSampleExtraInfo;
import com.ycloud.ymrmodel.YYMediaSample;
import com.yy.mediaframeworks.gpuimage.adapter.GlPboReader;

import java.nio.ByteBuffer;

/**
 * Created by liuchunyu on 2017/8/17.
 */

public class FFmpegFilterGroup extends FilterGroup {
    private String TAG = "FFmpegFilterGroup";

    protected BaseFilter mTransformFilter;
    protected BaseFilter mSquareFilter;
    private FaceDetect mFaceDetect;  // 人脸检测工具类，不写入filter链表中

    protected Context mContext;

    final protected int FRAMEBUFFER_NUM = 1;
    protected int[] mFrameBuffer;
    protected int[] mFrameBufferTexture;
    private GlTextureImageReader mGlImageReader = null;

    public FFmpegFilterGroup(Context context, int sessionID, Looper looper) {
        super(sessionID, looper);
        mContext = context;
        mTransformFilter = new TransFormTextureFilter();
        ((TransFormTextureFilter) mTransformFilter).setmUsedForPlayer(false);
        mSquareFilter = new SquareFilter();
        //[wqm]暂时还没启用，减少内存申请
//        mFaceDetect = new FaceDetect();
        GlPboReader.checkPboSupport(context);
    }

    public void init(int outputWidth, int outputHeight, boolean isExtTexutre) {
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;

        OpenGlUtils.checkGlError("init start");
        super.init();

        if (mTransformFilter != null) {
            mTransformFilter.init(mOutputWidth, mOutputHeight, isExtTexutre, mOFContext);
        }

        if (mSquareFilter != null) {
            mSquareFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
        }

        //[wqm]暂时还没启用，减少内存申请
//        if (mFaceDetect != null) {
//            mFaceDetect.init(mContext, mOutputWidth, mOutputHeight);
//        }

        initFilterLayout();
        mFrameBuffer = new int[FRAMEBUFFER_NUM];
        mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
        OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);

        mInited = true;

        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    protected void initFilterLayout() {
        mLayout.addPathInFilter(FilterLayout.kAllPathFlag, mTransformFilter);
        mLayout.addPathOutFilter(FilterLayout.kPreviewPathFlag, mSquareFilter);
        mLayout.defaultLayout();
    }

    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");

//        mHumanBodyDetectWrapper.bodiesDetectInfoList.clear();
        if (!mInited) {
            return;
        }
        mInited = false;
        super.destroy();

        mContext = null;

        if (mGlImageReader != null) {
            mGlImageReader.destroy();
            mGlImageReader = null;
        }

        if (mTransformFilter != null) {
            mTransformFilter.destroy();
            mTransformFilter = null;
        }

        if (mSquareFilter != null) {
            mSquareFilter.destroy();
            mSquareFilter = null;
        }

        //[wqm]暂时还没启用，减少内存申请
//        if (mFaceDetect != null) {
//            mFaceDetect.destroy();
//            mFaceDetect = null;
//        }

        if (mOFContext != -1) {
            OFLoader.destroyOrangeFilterContext(mOFContext);
            mOFContext = -1;
        }

        if (mFrameBufferTexture != null && mFrameBuffer != null) {
            OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
            mFrameBufferTexture = null;
            mFrameBuffer = null;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    protected void afterFilterRemove(BaseFilter filter) {
        super.afterFilterRemove(filter);
        if (filter != null) {
            OpenGlUtils.checkGlError("removeFilter end");
        }
    }

    @Override
    protected void afterFilterModify(BaseFilter filter) {
        super.afterFilterModify(filter);
    }

    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (!mInited) {
            return false;
        }

        restoreOutputTexture();
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

        boolean needRhythm = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_AudioBeat) > 0;
        //通过回调附加业务层传递的信息
        if (needRhythm && info != null) {
            sample.mAudioFrameData.beat = info.getRhythmQuality();
            sample.mAudioFrameData.loudness = info.getRhythmStrengthRatio();
            sample.mAudioFrameData.loudnessSmooth = info.getRhythmSmoothRatio();
            sample.mAudioFrameData.frequencyData = info.getRhythmFrequencyData();
        }

        bodyInfoSearch(sample);
        faceInfoSearch(sample);

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
                        segmentDataDetectAndCacheSaveCpu(sample, sample.mRgbaBytes, -1);
                    }
                }
            }
        }
        sample.mRgbaBytes = null;
        sample.mShouldUpsideDown = false;
        mTransformFilter.processMediaSample(sample, upstream);
        return true;
    }

    public void processMediaSample(YYMediaSample sample) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);


        processMediaSample(sample, this);
    }

    public void readRgbaPixel(ByteBuffer rgbaByteBuffer) {
        rgbaByteBuffer.rewind();
        if (mGlImageReader == null) {
            mGlImageReader = new GlTextureImageReader(mOutputWidth, mOutputHeight, true);
        }
        byte[] rgbaBytes = mGlImageReader.read(mFrameBufferTexture[0], mOutputWidth, mOutputHeight);
        if (rgbaBytes == null) {
            OpenGlUtils.saveFrameBuffer(mFrameBuffer[0], rgbaByteBuffer, mOutputWidth, mOutputHeight);
        } else {
            int minLength = rgbaBytes.length > rgbaByteBuffer.remaining() ? rgbaByteBuffer.remaining() : rgbaBytes.length;
            rgbaByteBuffer.put(rgbaBytes, 0, minLength);
        }
        rgbaByteBuffer.rewind();
    }
}
