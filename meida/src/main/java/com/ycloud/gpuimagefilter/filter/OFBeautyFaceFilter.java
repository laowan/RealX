package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.BeautyFaceFilterParameter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/17.
 */

public class OFBeautyFaceFilter extends BaseFilter {
    private final String TAG = "OFBeautyFaceFilter";

    public OFBeautyFaceFilter() {
        super();
        //设置标识：使用filterGroup中公用的texture和FBO资源
        setFrameBufferReuse(true);
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        mFilterId = OrangeFilter.createFilter(oFContext, OrangeFilter.KFilterBeauty5);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mFilterId != -1) {
            OrangeFilter.destroyFilter(mOFContext, mFilterId);
            mFilterId = -1;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    protected void updateParams() {
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            BaseFilterParameter param = it.next().getValue();
            float beautyFaceParam = ((BeautyFaceFilterParameter) (param)).mBeautyFaceParam;
            if (beautyFaceParam >= 0) {
                OrangeFilter.setFilterParamf(mOFContext, mFilterId, OrangeFilter.KParamBeautyIntensity, beautyFaceParam);
            }

            YYLog.info(TAG, "updateParams beautyFaceParam=" + beautyFaceParam);
        }
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        OpenGlUtils.checkGlError("OFBeautyFilter processMediaSample start");
        storeOldFBO();

        OrangeFilter.applyFilter(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D,
                mTextures[0], GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, null);

        if (mFBOReuse) {
            super.swapTexture(sample);
        } else {
            super.drawToFrameBuffer(sample);
        }
        recoverOldFBO();

        OpenGlUtils.checkGlError("processMediaSample end");
        deliverToDownStream(sample);
        return true;
    }
}
