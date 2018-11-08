package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.StretchFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by jinyongqing on 2018/2/12.
 */

public class OFStretchFilter extends BaseFilter {
    private final String TAG = OFStretchFilter.class.getSimpleName();
    private boolean mIsUseEffect = false;

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
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
        //YYLog.info(TAG, "updateParams thinFaceParam=");
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            StretchFilterParameter stretchFilterParameter = (StretchFilterParameter) it.next().getValue();

            if ((stretchFilterParameter.mOPType & FilterOPType.OP_SET_EFFECT_PATH) > 0) {
                updateParamPath(stretchFilterParameter.mEffectPath);
            }

            if ((stretchFilterParameter.mOPType & FilterOPType.OP_CHANGE_LEVEL) > 0) {
                OrangeFilter.OF_EffectInfo info = new OrangeFilter.OF_EffectInfo();
                OrangeFilter.getEffectInfo(mOFContext, mFilterId, info);
                for (int i = 0; i < info.filterCount; i++) {
                    int filter = info.filterList[i];
                    OrangeFilter.setFilterParamf(mOFContext, filter, 0, stretchFilterParameter.mLevel);
                }
            }
        }
    }

    private void updateParamPath(String path) {
        if (path != null) {
            int indexOfSplit = path.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "OFStretchFilter param is invalid:" + path + ",just return!!!");
                return;
            }

            String dir = path.substring(0, indexOfSplit);
            if (-1 == mFilterId) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, path, dir);

                if (mFilterId <= 0) {
                    YYLog.error(TAG, "createEffectFromFile failed.just return");
                    mIsUseEffect = false;
                    return;
                }
            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, path, dir);
            }
            mIsUseEffect = true;
        } else {
            mIsUseEffect = false;
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (mIsUseEffect) {
            storeOldFBO();
            OrangeFilter.applyEffect(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, null);

            super.drawToFrameBuffer(sample);
            recoverOldFBO();
        }

        deliverToDownStream(sample);
        return true;
    }
}
