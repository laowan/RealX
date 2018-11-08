package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.BlurFilterParameter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * 毛玻璃效果的滤镜，支持输入输出纹理不同，在视频两侧添加毛玻璃效果
 * Created by jinyongqing on 2018/4/22.
 */

public class OFBlurFilter extends BaseFilter {
    private final String TAG = "OFBlurFilter";
    private boolean mIsUseEffect = false;

    OrangeFilter.OF_Texture[] mInputTextureArr = null;
    OrangeFilter.OF_Texture[] mOutputTextureArr = null;

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);

        mInputTextureArr = new OrangeFilter.OF_Texture[1];
        mInputTextureArr[0] = new OrangeFilter.OF_Texture();


        mOutputTextureArr = new OrangeFilter.OF_Texture[1];
        mOutputTextureArr[0] = new OrangeFilter.OF_Texture();

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
            BlurFilterParameter blurFilterParameter = (BlurFilterParameter) it.next().getValue();
            boolean res = updateParamPath(blurFilterParameter.mEffectPath);
            if (res) {
                updateParamRatio(blurFilterParameter.mRatio);
                mIsUseEffect = true;
            } else {
                mIsUseEffect = false;
            }
        }
    }

    private void updateParamRatio(float ratio) {
        if (ratio > 0) {
            if (mOutputWidth >= mOutputHeight) {
                mOutputHeight = (int) (mOutputWidth / ratio);
            } else {
                mOutputWidth = (int) (mOutputHeight * ratio);
            }
            super.destroy();
            init(mOutputWidth, mOutputHeight, false, mOFContext);
        }
    }

    private boolean updateParamPath(String path) {
        if (path != null) {
            int indexOfSplit = path.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "OFStretchFilter param is invalid:" + path + ",just return!!!");
                return false;
            }

            String dir = path.substring(0, indexOfSplit);
            if (-1 == mFilterId) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, path, dir);

                if (mFilterId <= 0) {
                    YYLog.error(TAG, "createEffectFromFile failed.just return");
                    return false;
                }
            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, path, dir);
            }
        }
        return true;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (mIsUseEffect) {
            storeOldFBO();

            mInputTextureArr[0].format = GLES20.GL_TEXTURE_2D;
            mInputTextureArr[0].width = sample.mWidth;
            mInputTextureArr[0].height = sample.mHeight;
            mInputTextureArr[0].target = GLES20.GL_TEXTURE_2D;
            mInputTextureArr[0].textureID = sample.mTextureId;

            mOutputTextureArr[0].format = GLES20.GL_TEXTURE_2D;
            mOutputTextureArr[0].width = mOutputWidth;
            mOutputTextureArr[0].height = mOutputHeight;
            mOutputTextureArr[0].target = GLES20.GL_TEXTURE_2D;
            mOutputTextureArr[0].textureID = mTextures[0];


            OrangeFilter.applyFrame(mOFContext, mFilterId, mInputTextureArr, mOutputTextureArr);

//            ImageStorageUtil.save2DTextureToJPEG(mTextures[0], mOutputWidth, mOutputHeight);

            sample.mWidth = mOutputWidth;
            sample.mHeight = mOutputHeight;

            super.drawToFrameBuffer(sample);
            recoverOldFBO();
        }

        deliverToDownStream(sample);
        return true;
    }
}
