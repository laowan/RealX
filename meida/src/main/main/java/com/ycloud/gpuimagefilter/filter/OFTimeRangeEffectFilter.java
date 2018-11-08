package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.TimeRangeEffectFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.gpuimagefilter.utils.IFilterInfoListener;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by jinyongqing on 2017/8/18.
 */

public class OFTimeRangeEffectFilter extends BaseFilter {
    private final String TAG = "OFTimeRangeEffectFilter";
    private boolean mIsUseTimeRangeEffect = false;
    private boolean mIsVisible = true;
    private String mLastEffectDirectory = "";
    private long mStartPtsMs = -1;
    private OrangeFilter.OF_FrameData mFrameData = null;
    private TimeRangeEffectFilterParameter mParam = null;

    private boolean mUseRhythmInfo = true;

    public OFTimeRangeEffectFilter() {
        super();
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);

        mFrameData = new OrangeFilter.OF_FrameData();
        mFrameData.audioFrameData = new OrangeFilter.OF_AudioFrameData();
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mFilterId != -1) {
            OrangeFilter.destroyEffect(mOFContext, mFilterId);
            mFilterId = -1;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    @Override
    protected void updateParams() {
        //特效参数不为空
        if (mFilterInfo.mFilterConfigs != null && !mFilterInfo.mFilterConfigs.entrySet().isEmpty()) {
            mIsUseTimeRangeEffect = true;

            Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();

            //当前设计，该类型特效，filter与param是一一对应关系,所以应该只会迭代一次
            while (it.hasNext()) {
                TimeRangeEffectFilterParameter param = (TimeRangeEffectFilterParameter) it.next().getValue();
                mParam = param;
                mOPType = param.mOPType;

                if ((mOPType & FilterOPType.OP_SET_EFFECT_PATH) > 0) {
                    String effectDirectory = param.mEffectDirectory;
                    setOrangeFilterParams(effectDirectory);
                    YYLog.info(TAG, "OFTimeRangeEffectFilter updateParams:" + effectDirectory);
                }

                if ((mOPType & FilterOPType.OP_SET_VISIBLE) > 0) {
                    mIsVisible = param.mVisible;
                }

                if ((mOPType & FilterOPType.OP_SET_UICONFIG) > 0) {
                    setFilterUIConf(param.mUIConf);
                }
            }
        } else {
            mIsUseTimeRangeEffect = false;
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (mIsUseTimeRangeEffect && mIsVisible && sample.mApplyFilterIDs.contains(mFilterInfo.mFilterID)) {
            if (mUseRhythmInfo && sample.mAudioFrameData != null) {
                mFrameData.audioFrameData = sample.mAudioFrameData;
            }


            //填充frameData所需要的肢体检测和人脸检测信息
            mFrameData.faceFrameDataArr = sample.mFaceFrameDataArr;
            mFrameData.bodyFrameDataArr = sample.mBodyFrameDataArr;

            //填充抠图数据
            mFrameData.segmentFrameData = sample.mSegmentFrameData;

            if (!mParam.mNeedRepeatRender) {
                if (mStartPtsMs == -1) {
                    mStartPtsMs = sample.mTimestampMs;
                    OrangeFilter.pauseEffectAnimation(mOFContext, mFilterId);
                }
            }

            storeOldFBO();

            if (!mParam.mNeedRepeatRender) {
                int delta = (int) (sample.mTimestampMs - mStartPtsMs);
                int pts = delta > 0 ? delta : 0;
                OrangeFilter.seekEffectAnimation(mOFContext, mFilterId, pts);
            }

            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameData);
            OrangeFilter.applyEffect(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, mFrameData);

            super.drawToFrameBuffer(sample);
            recoverOldFBO();
        }

        deliverToDownStream(sample);
        return true;
    }

    private void setOrangeFilterParams(String effectDirectory) {
        if (effectDirectory != null) {
            if (mLastEffectDirectory.equals(effectDirectory)) {
                return;
            }

            int indexOfSplit = effectDirectory.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "TimeRangeEffect param is invalid:" + effectDirectory + ",just return!!!");
                return;
            }

            String dir = effectDirectory.substring(0, indexOfSplit);
            if (mFilterId <= 0) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, effectDirectory, dir);

                if (mFilterId <= 0) {
                    YYLog.error(TAG, "createEffectFromFile failed.just return");
                    mIsUseTimeRangeEffect = false;
                    return;
                }

            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, effectDirectory, dir);
            }

            //回调filterInfo
            OrangeFilter.OF_EffectInfo info = new OrangeFilter.OF_EffectInfo();
            OrangeFilter.getEffectInfo(mOFContext, mFilterId, info);
            if (mFilterInfo != null && mFilterInfo.mEffectInfo != null) {
                mFilterInfo.mEffectInfo.duration = info.duration;
                IFilterInfoListener filterInfoListener = FilterCenter.getInstance().getFilterInfoListener(mFilterInfo.mSessionID);
                if (filterInfoListener != null) {
                    filterInfoListener.onFilterInfo(mFilterInfo.mFilterID, mFilterInfo);
                }

            }

            mLastEffectDirectory = effectDirectory;
            mIsUseTimeRangeEffect = true;
        } else {
            mIsUseTimeRangeEffect = false;
        }
    }
}
