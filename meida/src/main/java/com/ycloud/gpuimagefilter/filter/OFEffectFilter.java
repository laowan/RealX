package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.EffectFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/22.
 */

public class OFEffectFilter extends BaseFilter {
    private final String TAG = "OFEffectFilter";
    private OrangeFilter.OF_FrameData mFrameData = null;
    private boolean mIsUseEffect = false;
    private String mEffectFilePath = null;

    private final int EFFECT_MODE_SURPORTSEEKING = 1;
    private int mStartRecordFlag = -1;

    public OFEffectFilter() {
        super();
        //设置标识：使用filterGroup中公用的texture和FBO资源
        setFrameBufferReuse(true);
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);

        mFrameData = new OrangeFilter.OF_FrameData();
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

    private void updateParams(String effectFilePath) {
        if (effectFilePath != null) {
            int indexOfSplit = effectFilePath.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "EffectFilter param is invalid:" + effectFilePath + ",just return!!!");
                return;
            }
            String dir = effectFilePath.substring(0, indexOfSplit);
            if (-1 == mFilterId) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, effectFilePath, dir);
            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, effectFilePath, dir);
            }

            if (mFilterId <= 0) {
                YYLog.error(TAG, "createEffectFromFile failed,just return");
                mIsUseEffect = false;
                return;
            }

            OrangeFilter.OF_EffectInfo effectInfo = new OrangeFilter.OF_EffectInfo();
            OrangeFilter.getEffectInfo(mOFContext, mFilterId, effectInfo);
            if (effectInfo.version >= 4) {
                OrangeFilter.setEffectMirrorMode(mOFContext, mFilterId, OrangeFilter.MirrorMode_NotMirror);
            }else {
                OrangeFilter.setEffectMirrorMode(mOFContext, mFilterId, OrangeFilter.MirrorMode_ForceMirror);
            }

            mIsUseEffect = true;
        } else {
            mIsUseEffect = false;
        }
    }

    @Override
    protected void updateParams() {
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            BaseFilterParameter param = it.next().getValue();
            int surportSeeking = ((EffectFilterParameter) (param)).mSurportSeeking;
            int startRecordFlag = ((EffectFilterParameter) (param)).mStartRecordFlag;
            int SeekTimeOffset = ((EffectFilterParameter) (param)).mSeekTimeOffset;

            if (((EffectFilterParameter) (param)).mEffectParam != null && mEffectFilePath != ((EffectFilterParameter) (param)).mEffectParam) {
                mEffectFilePath = ((EffectFilterParameter) (param)).mEffectParam;
                updateParams(mEffectFilePath);
                YYLog.info(TAG, "updateParams mEffectFilePath=" + mEffectFilePath);
            }


            if (SDKCommonCfg.getRecordModePicture() && SeekTimeOffset > 0) {
                YYLog.info(TAG, "seekEffectAnimation  " + SeekTimeOffset);
                OrangeFilter.seekEffectAnimation(mOFContext, mFilterId, SeekTimeOffset);
            }

            if (EFFECT_MODE_SURPORTSEEKING == surportSeeking && mStartRecordFlag != startRecordFlag && mFilterId > 0) {
                YYLog.info(TAG, "updateParams surportSeeking=" + surportSeeking + " mStartRecordFlag=" + mStartRecordFlag + " startRecordFlag=" + startRecordFlag);
                mStartRecordFlag = startRecordFlag;

                if (param.mStartPtsMs > 0) {
                    OrangeFilter.seekEffectAnimation(mOFContext, mFilterId, (int) param.mStartPtsMs);
                    param.mStartPtsMs = -1;
                } else {
                    OrangeFilter.restartEffectAnimation(mOFContext, mFilterId);
                }
            }

            if ((param.mOPType & FilterOPType.OP_SET_UICONFIG) > 0) {
                setFilterUIConf(param.mUIConf);
            }
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (true == mIsUseEffect) {
            storeOldFBO();

            mFrameData.audioFrameData = sample.mAudioFrameData;
            mFrameData.faceFrameDataArr = sample.mFaceFrameDataArr;
            mFrameData.segmentFrameData = sample.mSegmentFrameData;

            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameData);

            //根据需要应用avatar效果
            if (sample.mAvatarId != -1) {
                OrangeFilter.applyAvatar(mOFContext, sample.mAvatarId, mFrameData);
            }

            OrangeFilter.applyEffect(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, mFrameData);


            if (mFBOReuse) {
                super.swapTexture(sample);
            } else {
                super.drawToFrameBuffer(sample);
            }

            recoverOldFBO();
        }

        deliverToDownStream(sample);
        return true;
    }
}
