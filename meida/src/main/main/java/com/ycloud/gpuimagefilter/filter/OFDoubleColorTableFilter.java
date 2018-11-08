package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.DoubleColorTableFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by liuchunyu on 2017/8/1.
 */

public class OFDoubleColorTableFilter extends BaseFilter {
    private final String TAG = "OFDoubleColorTableFilter";
    private boolean mIsVisible = true;
    private int mEffect1 = -1;
    private int mEffect2 = -1;
    private float mRatio;
    private boolean mIsVertical;
    private boolean mIsUseEffect = false;

    private String mColorTableParam1 = "";
    private String mColorTableParam2 = "";

    public OFDoubleColorTableFilter() {
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
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mEffect1 != -1) {
            OrangeFilter.destroyEffect(mOFContext, mEffect1);
            mEffect1 = -1;
        }

        if (mEffect2 != -1) {
            OrangeFilter.destroyEffect(mOFContext, mEffect2);
            mEffect2 = -1;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    public void updateParams() {
        updataParamsInternal(0);
    }

    // 如有多个param，则只有一个会生效。目前一个filter对应一个param
    private void updataParamsInternal(long timestampMs) {
        if(mFilterInfo == null || mFilterInfo.mFilterConfigs == null) {
            return;
        }

        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            BaseFilterParameter param = it.next().getValue();

            mIsVisible = param.mVisible;

            long startPtsMs = ((DoubleColorTableFilterParameter) (param)).mStartPtsMs;
            long endPtsMs = ((DoubleColorTableFilterParameter) (param)).mEndPtsMs;
            if ((-1 == startPtsMs && -1 == endPtsMs) // 全局生效
                    || (timestampMs >= startPtsMs && timestampMs < endPtsMs) // 位于生效时间段内
                    ) {
                String colorTableParam1 = ((DoubleColorTableFilterParameter) (param)).mColorTableParam1;
                String colorTableParam2 = ((DoubleColorTableFilterParameter) (param)).mColorTableParam2;
                if (null == colorTableParam1 && null == colorTableParam2) {
                    mIsUseEffect = false;
                    return;
                }

                if (mColorTableParam1 != colorTableParam1) {
                    if (colorTableParam1 != null) {
                        int indexOfSplit1 = colorTableParam1.lastIndexOf("/");
                        if (indexOfSplit1 < 0) {
                            mIsUseEffect = false;
                            YYLog.error(TAG, "colorTableParam1 is invalid:" + colorTableParam1 + ",just return!!!");
                            return;
                        }

                        String dir1 = colorTableParam1.substring(0, indexOfSplit1);
                        if (mEffect1 <= 0) {
                            mEffect1 = OrangeFilter.createEffectFromFile(mOFContext, colorTableParam1, dir1);
                            mFilterId = mEffect1;
                        } else {
                            OrangeFilter.updateEffectFromFile(mOFContext, mEffect1, colorTableParam1, dir1);
                        }
                    } else {
                        if (mEffect1 <= 0) {
                            mEffect1 = OrangeFilter.createEffectFromData(mOFContext,
                                    "{\"version\":1,\"filter_count\":1,\"filter_list\":[{\"type\":\"NV12toRGB\",\"paramf\":{}}]}", "");
                        } else {
                            OrangeFilter.updateEffectFromData(mOFContext, mEffect1,
                                    "{\"version\":1,\"filter_count\":1,\"filter_list\":[{\"type\":\"NV12toRGB\",\"paramf\":{}}]}", "");
                        }
                    }

                    if (mEffect1 <= 0) {
                        YYLog.error(TAG, "updateParams mColorTableParam1=" + colorTableParam1 + " failed");
                        mIsUseEffect = false;
                        return;
                    }

                    mColorTableParam1 = colorTableParam1;
                    YYLog.info(TAG, "updateParams mColorTableParam1=" + colorTableParam1 + " startPtsMs=" + startPtsMs + " endPtsMs=" + endPtsMs);
                }

                if (mColorTableParam2 != colorTableParam2) {
                    if (colorTableParam2 != null) {
                        int indexOfSplit2 = colorTableParam2.lastIndexOf("/");
                        if (indexOfSplit2 < 0) {
                            mIsUseEffect = false;
                            YYLog.error(TAG, "colorTableParam2 is invalid:" + colorTableParam1 + ",just return!!!");
                            return;
                        }
                        String dir2 = colorTableParam2.substring(0, indexOfSplit2);
                        if (mEffect2 <= 0) {
                            mEffect2 = OrangeFilter.createEffectFromFile(mOFContext, colorTableParam2, dir2);
                        } else {
                            OrangeFilter.updateEffectFromFile(mOFContext, mEffect2, colorTableParam2, dir2);
                        }
                    } else {
                        if (mEffect2 <= 0) {
                            mEffect2 = OrangeFilter.createEffectFromData(mOFContext,
                                    "{\"version\":1,\"filter_count\":1,\"filter_list\":[{\"type\":\"NV12toRGB\",\"paramf\":{}}]}", "");
                        } else {
                            OrangeFilter.updateEffectFromData(mOFContext, mEffect2,
                                    "{\"version\":1,\"filter_count\":1,\"filter_list\":[{\"type\":\"NV12toRGB\",\"paramf\":{}}]}", "");
                        }
                    }

                    if (mEffect2 <= 0) {
                        YYLog.error(TAG, "updateParams mColorTableParam2=" + colorTableParam2 + " failed");
                        mIsUseEffect = false;
                        return;
                    }

                    mColorTableParam2 = colorTableParam2;
                    YYLog.info(TAG, "updateParams mColorTableParam2=" + colorTableParam2 + " startPtsMs=" + startPtsMs + " endPtsMs=" + endPtsMs);
                }

                mRatio = ((DoubleColorTableFilterParameter) (param)).mRatio;
                mIsVertical = ((DoubleColorTableFilterParameter) (param)).mIsVertical;

                if ((param.mOPType & FilterOPType.OP_SET_UICONFIG) > 0) {
                    setFilterUIConf(param.mUIConf);
                }

                mIsUseEffect = true;
                break; // 找到一个生效参数
            }
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        updataParamsInternal(sample.mTimestampMs);
        if (true == mIsUseEffect && mIsVisible) {
            storeOldFBO();

            OrangeFilter.applyDoubleEffect(mOFContext, mEffect1, mEffect2, mRatio, mIsVertical,
                    sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0], GLES20.GL_TEXTURE_2D, 0, 0,
                    mOutputWidth, mOutputHeight, null);

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
