package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.ColorTableFilterParameter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/17.
 */

public class OFColorTableFilter extends BaseFilter {
    private final String TAG = "OFColorTableFilter";
    private boolean mIsUseEffect = false;

    public OFColorTableFilter() {
        super();
    }

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
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            BaseFilterParameter param = it.next().getValue();

            String colorTableParam = ((ColorTableFilterParameter) (param)).mColorTableParam;
            if (null == colorTableParam) {
                mIsUseEffect = false;
                return;
            }

            int indexOfSplit = colorTableParam.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "ColorTableFilter param is invalid:" + colorTableParam + ",just return!!!");
                return;
            }

            String dir = colorTableParam.substring(0, indexOfSplit);
            if (-1 == mFilterId) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, colorTableParam, dir);
            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, colorTableParam, dir);
            }
            mIsUseEffect = true;

            YYLog.info(TAG, "updateParams mColorTableParam=" + ((ColorTableFilterParameter) (param)).mColorTableParam);
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (true == mIsUseEffect) {
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
