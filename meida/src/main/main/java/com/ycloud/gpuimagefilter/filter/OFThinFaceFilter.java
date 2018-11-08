package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.ThinFaceFilterParameter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/22.
 */

public class OFThinFaceFilter extends BaseFilter {
    private final String TAG = "OFThinFaceFilter";
    private OrangeFilter.OF_FrameData mFrameData = null;
    private float mLastThinFaceParam = 0.0f;

    public OFThinFaceFilter() {
        super();
        //设置标识：使用filterGroup中公用的texture和FBO资源
        setFrameBufferReuse(true);
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        mFilterId = OrangeFilter.createFilter(mOFContext, OrangeFilter.KFilterBasicThinFace);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);

        mFrameData = new OrangeFilter.OF_FrameData();
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
    public String getFilterName() {
        return TAG;
    }

    @Override
    protected void updateParams() {
        //YYLog.info(TAG, "updateParams thinFaceParam=");
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            BaseFilterParameter param = it.next().getValue();
            float thinFaceParam = ((ThinFaceFilterParameter) (param)).mThinFaceParam;
            if (thinFaceParam >= 0) {
                OrangeFilter.setFilterParamf(mOFContext, mFilterId, OrangeFilter.OF_ParamFilterFaceLifting_ThinIntensity, thinFaceParam);
            }

            if(mLastThinFaceParam != thinFaceParam) {
                YYLog.info(TAG, "updateParams thinFaceParam=" + thinFaceParam);
                mLastThinFaceParam = thinFaceParam;
            }
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        mFrameData.faceFrameDataArr = sample.mFaceFrameDataArr;
        boolean hasFace = (mFrameData.faceFrameDataArr == null ? false : true);

        if (hasFace) {
            storeOldFBO();

            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameData);
            OrangeFilter.applyFilter(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, hasFace == true ? mFrameData : null);

            super.drawToFrameBuffer(sample);

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
