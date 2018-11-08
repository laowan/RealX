package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.OFEditStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.gpuimagefilter.utils.IFilterInfoListener;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by wangyang on 2017/8/17.
 *
 * Modified by jinyongqing on 2017/12/8
 * 增加对手指魔法贴纸对支持，调整贴纸保存对frame data数据为轨迹数据，提高效率
 */


public class OFEditStickerEffectFilter extends BaseFilter {
    private final String TAG = "OFEditStickerEffectFilter";
    private boolean mIsUseEffect = false;
    private boolean mIsVisible = true;
    private OrangeFilter.OF_FrameData mFrameDataFilter = null;
    private OrangeFilter.OF_Rect mTracedRect = null;
    private int mTrackID = -1;
    private long mStartPtsMs = -1;
    private long mEndPtsMs = -1;

    private int mFadeInTime = 0;
    private int mFadeOutTime = 0;

    private int mFilterMaxEndTime = 20000; //特效结束时长的上限，通常是视频的时长，这里默认值用20s

    //是否需要保存轨迹
    private boolean mShouldKeepPos = false;

    //当前帧是否需要保存uiConf
    private boolean mShouldKeepUIConf = false;

    //uiConf的应用模式：全局模式还是帧模式，默认是帧模式，即每一帧都有对应的uiConf
    private boolean mUIConfTraceMode = true;

    private OFEditStickerEffectFilterParameter mParam = null; // 目前只有一个参数

    //上一次设置的tracedDataInfo
    private FrameTracedDataInfo mLastFrameTracedDataInfo = new FrameTracedDataInfo();

    //上一次设置的ui config
    private FrameUIConfInfo mLastFrameUIConfInfo = null;

    public OFEditStickerEffectFilter() {
        super();
        //设置标识：使用filterGroup中公用的texture和FBO资源
        setFrameBufferReuse(true);
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight + "  " + this);
        mFrameDataFilter = new OrangeFilter.OF_FrameData();
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mFilterId != -1) {
            OrangeFilter.destroyEffect(mOFContext, mFilterId);
            mFilterId = -1;
        }
        mIsUseEffect = false;

        mIsUseEffect = false;

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    private void updateParamPath(OFEditStickerEffectFilterParameter param) {
        if (param.mEffectDirectory != null) {
            int indexOfSplit = param.mEffectDirectory.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "OFEditStickerEffect param is invalid:" + param.mEffectDirectory + ",just return!!!");
                return;
            }

            String dir = param.mEffectDirectory.substring(0, indexOfSplit);
            if (mFilterId <= 0) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, param.mEffectDirectory, dir);

                if(mFilterId <= 0) {
                    YYLog.error(TAG, "createEffectFromFile failed.just return");
                    mIsUseEffect = false;
                    return;
                }

                OrangeFilter.OF_EffectInfo info = new OrangeFilter.OF_EffectInfo();
                OrangeFilter.getEffectInfo(mOFContext, mFilterId, info);

                for (int i = 0; i < info.filterCount; i++) {
                    int filterID = info.filterList[i];
                    String filterType = OrangeFilter.getFilterType(mOFContext, filterID);
                    if (filterType.equals("TrackPlaneAnimationFilter")) {
                        OrangeFilter.TrackPlaneAnimFilterExtData extData = new OrangeFilter.TrackPlaneAnimFilterExtData();
                        OrangeFilter.getFilterExtData(mOFContext, filterID, extData);

                        mFadeInTime = extData.framePartition[0] * extData.timeInterval;
                        mFadeOutTime = extData.framePartition[2] * extData.timeInterval;
                    }
                }

                if (!param.mNeedRepeatRender) {
                    OrangeFilter.pauseEffectAnimation(mOFContext, mFilterId);
                }
            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, param.mEffectDirectory, dir);
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

            if (mTrackID == -1) {
                byte[] mInitBytes = new byte[1];
                mFrameDataFilter.imageData = mInitBytes;
                mFrameDataFilter.width = mOutputWidth;
                mFrameDataFilter.height = mOutputHeight;
                mFrameDataFilter.faceFrameDataArr = new OrangeFilter.OF_FaceFrameData[0];
                mFrameDataFilter.trackOn = true;
                Matrix.setIdentityM(mFrameDataFilter.cameraMat, 0);
                YYLog.info(TAG, "updateParamPath mTrackID =" + mTrackID);
            }

            mIsUseEffect = true;
        } else {
            mIsUseEffect = false;
        }
    }

    @Override
    public FilterInfo getFilterInfo() {
        return super.getFilterInfo();
    }

    @Override
    public void setFilterInfo(FilterInfo filterInfo) {
        super.setFilterInfo(filterInfo);
    }

    @Override
    protected void updateParams() {
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            OFEditStickerEffectFilterParameter param = (OFEditStickerEffectFilterParameter) it.next().getValue();
            mParam = param;
            YYLog.debug(TAG, "updateParams opType =" + param.mOPType + " paramId = " + param.mParameterID + " this=" + this);

            mIsVisible = param.mVisible;

            if ((param.mOPType & FilterOPType.OP_SET_EFFECT_PATH) > 0) {
                updateParamPath(param);
            }

            //除了设置PATH之外的更新filter操作，都要求of filter id有效
            if (!mIsUseEffect) {
                YYLog.error(TAG, "updateParams error because orangeFilter is invalid");
            }

            //改变矩阵相关信息（平移，大小，旋转）
            if ((param.mOPType & (FilterOPType.OP_CHANGE_MATRIX |
                    FilterOPType.OP_CHANGE_ROTATION | FilterOPType.OP_CHANGE_SCALE)) > 0) {
                changeTracker(param.mTranslateX, param.mTranslateY, param.mRotation, param.mScale);

                //更新之前保存的点信息
                if ((param.mOPType & (FilterOPType.OP_CHANGE_ROTATION | FilterOPType.OP_CHANGE_SCALE)) > 0) {
                    batchUpdateTracedFrameDataInfo(param.mScale, param.mRotation);
                }
            }

            //设置贴纸与背景（视频帧或者图片数据）的显示比例
            if ((param.mOPType & FilterOPType.OP_CHANGE_RATIO_TO_BACKGROUND) > 0) {
                OrangeFilter.OF_EffectInfo info = new OrangeFilter.OF_EffectInfo();
                OrangeFilter.getEffectInfo(mOFContext, mFilterId, info);
                for (int i = 0; i < info.filterCount; i++) {
                    int filter = info.filterList[i];
                    String filterType = OrangeFilter.getFilterType(mOFContext, filter);
                    if ("TrackPlaneAnimationFilter".equals(filterType)) {
                        OrangeFilter.OF_Paramf ofParam = new OrangeFilter.OF_Paramf();
                        ofParam.val = param.mRatio2background;
                        ofParam.name = "Width";
                        OrangeFilter.setFilterParamData(mOFContext, filter, "Width", ofParam);
                        break;
                    }
                }
            }

            if ((param.mOPType & FilterOPType.OP_KEEP_MATRIX) > 0) {
                mShouldKeepPos = true;
            }

            if ((param.mOPType & FilterOPType.OP_CHANGE_TIME) > 0) {
                YYLog.info(TAG, "updateParams mStartPtsMs =" + param.mStartPtsMs + " mEndPtsMs = " + param.mEndPtsMs + " this=" + this);
                mStartPtsMs = param.mStartPtsMs;
                mEndPtsMs = param.mEndPtsMs;
                //粒子轨迹贴纸，根据轨迹记录的点改变起始和末尾时间
                if (mParam.mStickerType == OFEditStickerEffectFilterParameter.TYPE_PARTICLE_STICKER && mParam.mTracedDataInfoList != null) {
                    if (mParam.mTracedDataInfoList.size() > 0) {
                        mStartPtsMs = mParam.mTracedDataInfoList.get(0).timestampMs;
                        mParam.mStartPtsMs = mParam.mTracedDataInfoList.get(0).timestampMs;
//                        mParam.mFadeoutStartPtsMs = mParam.mTracedDataInfoList.get(size - 1).timestampMs;
                    }
                    if (mParam.mUIConfInfoList.size() > 0) {
                        mStartPtsMs = mParam.mUIConfInfoList.get(0).timestampMs;
                        mParam.mStartPtsMs = mParam.mUIConfInfoList.get(0).timestampMs;
                    }
                }

                //前摇不为0，需要根据前摇时间调整特效的起始时长
                if (mFadeInTime > 0) {
                    mStartPtsMs = param.mStartPtsMs - mFadeInTime;
                    mParam.mStartPtsMs = (param.mStartPtsMs - mFadeInTime > 0 ? (param.mStartPtsMs - mFadeInTime) : param.mStartPtsMs);
                }

                //后摇不为0，需要根据后摇时间调整特效的起始时长
                if (mFadeOutTime > 0) {
                    mParam.mEndPtsMs = (param.mEndPtsMs + mFadeOutTime > mFilterMaxEndTime ? param.mFadeoutStartPtsMs : param.mEndPtsMs + mFadeOutTime);
                    mParam.mUseFadeout = true;
                }
                /*改变时间区间的时候，打印出当前list的状态和设置的mFadeoutStartPtsMs值*/
                /*for (int i = 0; i < mParam.mTracedDataInfoList.size(); i++) {
                    YYLog.info(TAG, "jyq test frame data list " + i + ": pts = " + mParam.mTracedDataInfoList.get(i).timestampMs);
                }
                YYLog.info(TAG, "jyq test mFadeoutStartPtsMs:" + mParam.mFadeoutStartPtsMs);*/
            }

            //设置粒子的颜色
            if ((param.mOPType & FilterOPType.OP_CHANGE_COLOR) > 0) {
                OrangeFilter.OF_EffectInfo info = new OrangeFilter.OF_EffectInfo();
                OrangeFilter.getEffectInfo(mOFContext, mFilterId, info);
                for (int i = 0; i < info.filterCount; i++) {
                    int filter = info.filterList[i];
                    String filterType = OrangeFilter.getFilterType(mOFContext, filter);
                    if ("TrackParticleFilter".equals(filterType) || "TrackParticleSystemFilter".equals(filterType)) {
                        OrangeFilter.setFilterParamf(mOFContext, filter, OrangeFilter.OF_ParamFilterParticle_ColorR, param.mColorR);
                        OrangeFilter.setFilterParamf(mOFContext, filter, OrangeFilter.OF_ParamFilterParticle_ColorG, param.mColorG);
                        OrangeFilter.setFilterParamf(mOFContext, filter, OrangeFilter.OF_ParamFilterParticle_ColorB, param.mColorB);
                    }

                    if ("CustomLuaFilter".equals(filterType)) {
                        OrangeFilter.OF_ParamColor color = new OrangeFilter.OF_ParamColor();
                        color.name = "Color";
                        color.val[0] = param.mColorR;
                        color.val[1] = param.mColorG;
                        color.val[2] = param.mColorB;
                        color.val[3] = 1;
                        OrangeFilter.setFilterParamData(mOFContext, filter, color.name, color);
                    }
                }
            }

            // 设置文字
            if (((param.mOPType & FilterOPType.OP_ADD_TEXT) > 0) &&
                    param.mTexts != null && param.mTexts.length > 0) {
                OrangeFilter.OF_EffectInfo info = new OrangeFilter.OF_EffectInfo();
                OrangeFilter.getEffectInfo(mOFContext, mFilterId, info);

                int index = 0;
                for (int i = 0; i < info.filterCount; i++) {
                    int filterID = info.filterList[i];
                    String filterType = OrangeFilter.getFilterType(mOFContext, filterID);
                    if (filterType.equals("TrackTextFilter")) {
                        if (filterID > 0 && index < param.mTexts.length) {
                            if (param.mTexts[index] != null && !param.mTexts[index].equals("")) {
                                OrangeFilter.TrackTextFilterExtData extData = new OrangeFilter.TrackTextFilterExtData();
                                extData.text = param.mTexts[index];
                                OrangeFilter.setFilterExtData(mOFContext, filterID, extData);
                            }

                            index++;
                        }
                    }
                }
            }

            if (mParam.mPresetDataArray) {
                //当合成或者合成封面时,如果保存的轨迹信息不为空时，需要将轨迹信息预设给OrangeFilter
                if (mParam.mTracedDataInfoList.size() > 0) {
                    OrangeFilter.OF_EffectTrackDataArr effectTrackDataArr = new OrangeFilter.OF_EffectTrackDataArr();
                    effectTrackDataArr.arr = new OrangeFilter.OF_EffectTrackData[mParam.mTracedDataInfoList.size() - 1];
                    for (int i = 0; i < effectTrackDataArr.arr.length; i++) {
                        effectTrackDataArr.arr[i] = new OrangeFilter.OF_EffectTrackData();

                        effectTrackDataArr.arr[i].timestamp = (int) (mParam.mTracedDataInfoList.get(i).timestampMs - mStartPtsMs);
                        effectTrackDataArr.arr[i].x = mParam.mTracedDataInfoList.get(i).x;
                        effectTrackDataArr.arr[i].y = mParam.mTracedDataInfoList.get(i).y;

//                        YYLog.info(TAG, "jyq test set track data,timestamp:" + effectTrackDataArr.arr[i].timestamp
//                                + ",x:" + effectTrackDataArr.arr[i].x + ",y:" + effectTrackDataArr.arr[i].y);
                    }
                    OrangeFilter.setEffectTrackData(mOFContext, mFilterId, effectTrackDataArr);
                }
            }

            if ((param.mOPType & FilterOPType.OP_SET_UICONFIG_NOT_TRACE) > 0) {
                mUIConfTraceMode = false;
                mShouldKeepUIConf = true;
            }

            if ((param.mOPType & FilterOPType.OP_SET_UICONFIG) > 0) {
                if(mFilterId > 0) {
                    mUIConfTraceMode = true;
                    mShouldKeepUIConf = true;
//                    setFilterUIConf(param.mUIConf);
                }
            }

            param.mOPType = FilterOPType.NO_OP;
        }
    }

    /***
     * @param translateX
     * @param translateY
     * @param rotation
     * @param scale
     */
    private void changeTracker(float translateX, float translateY, float rotation, float scale) {
        YYLog.debug(TAG, "changeTracker " + translateX + " " + translateY + " " + rotation + " " + scale + " this=" + this);

        //保存最近一次的位置信息
        mParam.mTranslateX = translateX;
        mParam.mTranslateY = translateY;
        mParam.mRotation = rotation;
        mParam.mScale = scale;

        OrangeFilter.OF_Transform transform = new OrangeFilter.OF_Transform();
        transform.rotation = rotation;
        transform.translateX = translateX;
        transform.translateY = translateY;
        transform.scale = (scale == 0.0f) ? 1.0f : scale;
        mFrameDataFilter.trackOn = true;
        Matrix.setIdentityM(mFrameDataFilter.cameraMat, 0);
        Matrix.translateM(mFrameDataFilter.cameraMat, 0, transform.translateX, transform.translateY, 0.0f);
        //  Matrix.rotateM(mFrameDataFilter.cameraMat, 0, transform.rotation * 180.0f / (float)Math.PI, 0, 0, 1.0f);
        Matrix.rotateM(mFrameDataFilter.cameraMat, 0, transform.rotation, 0, 0, 1.0f);
        Matrix.scaleM(mFrameDataFilter.cameraMat, 0, transform.scale, transform.scale, 1.0f);
    }


    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        /*YYLog.info(TAG, "jyq test processMediaSample:OFContext=" + mOFContext + ",effect=" + mEffect +
                ",timestamp=" + sample.mTimestampMs + ",startPts=" + mStartPtsMs + ",endPts=" + mEndPtsMs +
                ",fadeoutPts:" + mParam.mFadeoutStartPtsMs);*/
        if (mIsUseEffect && mIsVisible && sample.mApplyFilterIDs.contains(mFilterInfo.mFilterID)) {
            if (mStartPtsMs == -1) {
                mStartPtsMs = sample.mTimestampMs - mFadeInTime;
            }

            storeOldFBO();

            if (!mParam.mNeedRepeatRender) {
                int delta = (int) (sample.mTimestampMs - mStartPtsMs);
                int pts = delta > 0 ? delta : 0;
                OrangeFilter.seekEffectAnimation(mOFContext, mFilterId, pts);
            }

            if (mParam != null) {
                if (mShouldKeepPos) {
                    insertOrUpdateTracedFrameDataInfo(new FrameTracedDataInfo(sample.mTimestampMs, mParam.mTranslateX,
                            mParam.mTranslateY, mParam.mRotation, mParam.mScale, mFrameDataFilter.cameraMat[12], mFrameDataFilter.cameraMat[13]));
                    mShouldKeepPos = false;
                } else {
                    FrameTracedDataInfo frameTracedDataInfo = findTracedFrameDataInfo(sample.mTimestampMs, false);
                    if (frameTracedDataInfo != null && !mLastFrameTracedDataInfo.equals(frameTracedDataInfo)) {
                        changeTracker(frameTracedDataInfo.translateX, frameTracedDataInfo.translateY, frameTracedDataInfo.rotation, frameTracedDataInfo.scale);
                        mLastFrameTracedDataInfo = frameTracedDataInfo;
                    }
                }

                //帧模式下设置uicConf，如果当前帧设置了新的uiConf，立即应用并更新uiConf保存列表；否则查找对应pts的uiConf
                if (mUIConfTraceMode) {
                    //获取当前帧对应的uiConf
                    FrameUIConfInfo frameUIConfInfo;
                    if (mShouldKeepUIConf) {
                        frameUIConfInfo = insertOrUpdateFrameUIConf(sample.mTimestampMs, mParam.mUIConf);
                        mShouldKeepUIConf = false;
                    } else {
                        frameUIConfInfo = findFrameUIConf(sample.mTimestampMs);
                    }

                    //设置当前帧对应的uiConf参数到of
                    if (frameUIConfInfo != null && !frameUIConfInfo.equals(mLastFrameUIConfInfo)) {
                        mLastFrameUIConfInfo = frameUIConfInfo;
                        setFilterUIConf(frameUIConfInfo.uiConf);
                    }
                } else {
                    //全局模式下设置uiConf，所有帧使用相同的uiConf设置
                    if (mShouldKeepUIConf) {
                        FrameUIConfInfo frameUIConfInfo = insertOrUpdateFrameUIConf(0, mParam.mUIConf);
                        mShouldKeepUIConf = false;
                        if (frameUIConfInfo != null) {
                            setFilterUIConf(frameUIConfInfo.uiConf);
                        }
                    }
                }
            }

            //使用淡出效果
            if (mParam.mUseFadeout) {
                if (mParam.mFadeoutStartPtsMs < sample.mTimestampMs) {
                    mFrameDataFilter.trackOn = false;
                } else {
                    mFrameDataFilter.trackOn = true;
                }
            }

            //填充frameData所需要的肢体检测和人脸检测信息
            mFrameDataFilter.faceFrameDataArr = sample.mFaceFrameDataArr;
            mFrameDataFilter.bodyFrameDataArr = sample.mBodyFrameDataArr;

            //填充抠图数据
            mFrameDataFilter.segmentFrameData = sample.mSegmentFrameData;

            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameDataFilter);
            OrangeFilter.applyEffect(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, mFrameDataFilter);

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

    /**
     * 寻找当前帧对应时间点是否有保存的轨迹信息
     * @param currentTimestampMs
     * @return
     */
    private FrameUIConfInfo findFrameUIConf(long currentTimestampMs) {
        if (mParam.mUIConfInfoList.size() == 0) {
            return null;
        }

        int start = 0;
        int end = mParam.mUIConfInfoList.size() - 1;
        int mid;
        while ((start + 1) < end) {
            mid = start + (end - start) / 2;
            FrameUIConfInfo midFrameUIConf = mParam.mUIConfInfoList.get(mid);
            if (midFrameUIConf.timestampMs < currentTimestampMs) {
                start = mid;
            } else if (midFrameUIConf.timestampMs > currentTimestampMs) {
                end = mid;
            } else {
                end = mid;
            }
        }

        FrameUIConfInfo startFrameUIConf = mParam.mUIConfInfoList.get(start);
        if (start == 0 || Math.abs(currentTimestampMs - startFrameUIConf.timestampMs) < 40) {
            return startFrameUIConf;
        }

        FrameUIConfInfo endFrameUIConf = mParam.mUIConfInfoList.get(end);
        if (end == mParam.mUIConfInfoList.size() || Math.abs(endFrameUIConf.timestampMs - currentTimestampMs) < 40) {
            return endFrameUIConf;
        }
        return null;
    }

    /**
     * 更新对应时间点的uiConf信息
     * @param timestampMs frame的时间戳
     * @param conf frame对应的conf
     * @return 当前frame对应FrameUIConfInfo对象
     */
    private FrameUIConfInfo insertOrUpdateFrameUIConf(long timestampMs, Map<String, Object> conf) {
        FrameUIConfInfo info = new FrameUIConfInfo(timestampMs);
        info.uiConf = conf;
        if (mParam.mUIConfInfoList.size() == 0) {
            //有的时候从0添加，但是解码器给的pts第一帧不是0，导致第一帧没加上，这里先针对这个问题做个兼容，
            // 只要收到的第一个添加点是100ms以内，都认为是从0添加
            if (info.timestampMs < 100) {
                info.timestampMs = 0;
            }

            mParam.mUIConfInfoList.add(info);
            FilterCenter.getInstance().updateFilterParameter(mFilterInfo.mFilterID, mParam.mParameterID, mParam, mFilterInfo.mSessionID);
            return info;
        }

        int start = 0;
        int end = mParam.mUIConfInfoList.size();
        int mid;
        while (start < end) {
            mid = (start + end) / 2;
            FrameUIConfInfo midFrameUIConfInfo = mParam.mUIConfInfoList.get(mid);
            if (timestampMs < midFrameUIConfInfo.timestampMs) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        //兼容添加到末尾，flag设为true，但是直到循环播放（timeStamp == 0）才处理这个flag插入的情况
        if (start < 1) {
            return mParam.mUIConfInfoList.get(start);
        }

        if (mParam.mUIConfInfoList.get(start - 1).timestampMs == timestampMs) {
            mParam.mUIConfInfoList.get(start - 1).uiConf.putAll(conf);
        } else {
            mParam.mUIConfInfoList.add(start, info);
        }

        return info;
    }

    /**
     * 寻找当前帧对应时间点是否有保存的轨迹信息
     * @param currentTimestampMs
     * @param strideOne
     * @return
     */
    private FrameTracedDataInfo findTracedFrameDataInfo(long currentTimestampMs, boolean strideOne) {
        if (mParam.mTracedDataInfoList.size() == 0) {
            return null;
        }

        int start = 0;
        int end = mParam.mTracedDataInfoList.size() - 1;
        int mid;
        while ((start + 1) < end) {
            mid = start + (end - start) / 2;
            FrameTracedDataInfo midFrameDataInfo = mParam.mTracedDataInfoList.get(mid);
            if (midFrameDataInfo.timestampMs < currentTimestampMs) {
                start = mid;
            } else if (midFrameDataInfo.timestampMs > currentTimestampMs) {
                end = mid;
            } else {
                end = mid;
            }
        }

        FrameTracedDataInfo startFrameDataInfo = mParam.mTracedDataInfoList.get(start);
        if (start == 0 || Math.abs(currentTimestampMs - startFrameDataInfo.timestampMs) < 40) {
            return startFrameDataInfo;
        }

        FrameTracedDataInfo endFrameDataInfo = mParam.mTracedDataInfoList.get(end);
        if (end == mParam.mTracedDataInfoList.size() || Math.abs(endFrameDataInfo.timestampMs - currentTimestampMs) < 40) {
            return endFrameDataInfo;
        }
        return null;
    }

    /**
     * 保存需要记录轨迹对贴纸的轨迹点，为了提供效率，重构后仅保存点的坐标信息等，在处理sample的时候实时调用orangefilter接口转换为frame data
      * @param tracedDataInfo
     */
    private void insertOrUpdateTracedFrameDataInfo(FrameTracedDataInfo tracedDataInfo) {
        if (mParam.mTracedDataInfoList.size() == 0) {
            //有的时候从0添加，但是解码器给的pts第一帧不是0，导致第一帧没加上，这里先针对这个问题做个兼容，
            // 只要收到的第一个添加点是100ms以内，都认为是从0添加
            if (tracedDataInfo.timestampMs < 100) {
                tracedDataInfo.timestampMs = 0;
            }

            mParam.mTracedDataInfoList.add(tracedDataInfo);
            return;
        }

        int start = 0;
        int end = mParam.mTracedDataInfoList.size() - 1;
        int mid;
        while ((start + 1) < end) {
            mid = start + (end - start) / 2;
            FrameTracedDataInfo midFrameDataInfo = mParam.mTracedDataInfoList.get(mid);
            if (midFrameDataInfo.timestampMs < tracedDataInfo.timestampMs) {
                start = mid;
            } else if (midFrameDataInfo.timestampMs > tracedDataInfo.timestampMs) {
                end = mid;
            } else {
                end = mid;
            }
        }

        FrameTracedDataInfo endFrameDataInfo = mParam.mTracedDataInfoList.get(end);
        if (tracedDataInfo.timestampMs > endFrameDataInfo.timestampMs) {
            mParam.mTracedDataInfoList.add(end + 1, tracedDataInfo);
        } else if (tracedDataInfo.timestampMs < endFrameDataInfo.timestampMs) {
            mParam.mTracedDataInfoList.add(start + 1, tracedDataInfo);
        } else {
            mParam.mTracedDataInfoList.set(end, tracedDataInfo);
        }
    }


    /**
     * 批量改变保存的轨迹点的信息
     *
     * @param scale
     * @param rotation
     */
    private void batchUpdateTracedFrameDataInfo(float scale, float rotation) {
        for (FrameTracedDataInfo frameTracedDataInfo : mParam.mTracedDataInfoList) {
            frameTracedDataInfo.scale = scale;
            frameTracedDataInfo.rotation = rotation;
        }
    }


    public static class FrameTracedDataInfo {
        public long timestampMs;
        public float translateX;
        public float translateY;
        public float rotation;
        public float scale;
        public float x;
        public float y;

        public FrameTracedDataInfo() {
            this.timestampMs = 0;
            this.translateX = -1;
            this.translateY = -1;
            this.rotation = -1;
            this.scale = -1;
        }

        public FrameTracedDataInfo(long timestampMs, float translateX, float translateY, float rotation, float scale, float x, float y) {
            this.timestampMs = timestampMs;
            this.translateX = translateX;
            this.translateY = translateY;
            this.rotation = rotation;
            this.scale = scale;
            this.x = x;
            this.y = y;
        }

        public boolean equals(Object o) {
            if (o instanceof FrameTracedDataInfo) {
                if (((FrameTracedDataInfo) o).translateX == this.translateX && ((FrameTracedDataInfo) o).translateY == this.translateY
                        && ((FrameTracedDataInfo) o).rotation == this.rotation && ((FrameTracedDataInfo) o).scale == this.scale) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 视频帧对应的uiConf信息
     */
    public static class FrameUIConfInfo {
        public long timestampMs;
        public Map<String, Object> uiConf;

        public FrameUIConfInfo(long timestampMs) {
            this.timestampMs = timestampMs;
            uiConf = new HashMap<>();
        }
    }
}
