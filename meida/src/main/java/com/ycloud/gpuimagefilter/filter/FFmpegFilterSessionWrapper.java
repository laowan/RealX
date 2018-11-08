package com.ycloud.gpuimagefilter.filter;

import com.ycloud.api.common.FilterGroupType;
import com.ycloud.api.common.FilterType;
import com.ycloud.common.Constant;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.BlurFilterParameter;
import com.ycloud.gpuimagefilter.param.OFEditStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.param.ParamUtil;
import com.ycloud.gpuimagefilter.utils.FilterIDManager;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import java.util.List;
import java.util.Map;

/**
 * Created by jinyongqing on 2017/8/21.
 */

public class FFmpegFilterSessionWrapper {
    private static final String TAG = "FFmpegFilterSessionWrapper";

    private FilterSession mFilterSession;
    public FFmpegFilterSessionWrapper() {
        mFilterSession = FilterCenter.getInstance().createFilterSession();
    }

    public FFmpegFilterSessionWrapper(int sessionID) {
        mFilterSession = FilterCenter.getInstance().createFilterSession(sessionID);
    }

    public int getSessionID() {
        return mFilterSession.getSessionID();
    }

    public void setFilterJson(String filterJson) {
        mFilterSession.addFilter(filterJson, true);
    }

    public int addEditStickerEffect() {
        int editTickerEffectFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_EDIT_STICKER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP);
        YYLog.info(TAG, "addEditStickerEffect id=" + editTickerEffectFilterID);
        if (editTickerEffectFilterID >= 0) {
            return editTickerEffectFilterID;
        } else {
            return FilterIDManager.NO_ID;
        }

    }

    /**
     * 添加编辑贴纸特效
     *
     * @param path        贴纸路径
     * @param trackConfig 贴纸配置参数 取值是0和1，0表示不允许随跟踪算法缩放旋转，1表示可以
     * @return paramID  贴纸参数id
     */
    public int setEditStickerEffectPath(String path, int editTickerEffectFilterID, int trackConfig) {
        int paramID = -1;
        if (editTickerEffectFilterID >= 0) {
            OFEditStickerEffectFilterParameter mEffectFilterParameter = new OFEditStickerEffectFilterParameter();
            mEffectFilterParameter.mEffectDirectory = path;
            mEffectFilterParameter.mOPType = FilterOPType.OP_SET_EFFECT_PATH;
            mEffectFilterParameter.mParameterID = 0;
            mEffectFilterParameter.mTrackerConfigFlag = trackConfig;
            paramID = mFilterSession.addFilterParameter(editTickerEffectFilterID, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID);
        }

        YYLog.info(TAG, "setEditStickerEffectPath filterId=" + editTickerEffectFilterID + ",paramId=" + paramID + ",path=" + path);
        return paramID;
    }


    /**
     * 改变编辑贴纸特效时间
     *
     * @param startTime 贴纸生效开始时间
     * @param endTime   贴纸生效结束时间
     * @param paramId
     * @param editTickerEffectFilterID
     */
    public void changeEditStickerEffectParam(long startTime, long endTime, int paramId, int editTickerEffectFilterID) {
        YYLog.info(TAG, "changeEditStickerEffectParam startTime=" + startTime + " endTime=" + endTime +
                " filterId=" + editTickerEffectFilterID + " paramId=" + paramId);
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramId);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter mEffectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            mEffectFilterParameter.mStartPtsMs = startTime;
            mEffectFilterParameter.mEndPtsMs = endTime;
            mEffectFilterParameter.mOPType |= FilterOPType.OP_CHANGE_TIME;
            mEffectFilterParameter.mParameterID = paramId;
            mEffectFilterParameter.mOPTypeSave |= mEffectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramId, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 传入合成后要加的模糊效果
     *
     * @param path  模糊效果effect路径
     * @param ratio 导出视频比例
     */
    public void addBlurEffect(String path, float ratio) {
        addBlurEffectInternal(path, ratio);
    }


    public void addBlurEffectInternal(String path, float ratio) {
        int blurFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_BLUR_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP);
        YYLog.info(TAG, "addBlurEffect id=" + blurFilterID);

        if (blurFilterID >= 0) {
            BlurFilterParameter mBlurFilterParameter = new BlurFilterParameter();
            mBlurFilterParameter.mEffectPath = path;
            mBlurFilterParameter.mRatio = ratio;
            mFilterSession.addFilterParameter(blurFilterID, mBlurFilterParameter);
        } else {
            YYLog.error(TAG, "addBlurEffect error id = " + blurFilterID);
        }
    }

    /*--------------new interface of of effect-----------------*/

    /**
     * 添加filter
     *
     * @param filterType      filter类型
     * @param filterGroupType filterGroup类型
     */
    public int addFilter(int filterType, String filterGroupType) {
        //如果group传的是null，按照default filter group处理
        filterGroupType = (filterGroupType == null ? FilterGroupType.DEFAULT_FILTER_GROUP : filterGroupType);

        int filterId = mFilterSession.addFilter(filterType, filterGroupType);
        BaseFilterParameter param = ParamUtil.newParameter(filterType);
        int paramId = mFilterSession.addFilterParameter(filterId, param);

        if (filterId >= 0) {
            YYLog.info(TAG, "addFilter filterId=" + filterId + ",paramId=" + paramId + ",filterType=" + filterType + ",filterGroupType=" + filterGroupType);
            return filterId;
        } else {
            return FilterIDManager.NO_ID;
        }
    }

    /**
     * 更新filter的参数
     *
     * @param filterId   filter的标识
     * @param filterConf filter需要更新的参数：key：参数类型 value：参数具体值，可以为任意类型或者集合
     *                   map支持多个entry,表明一次性更新多个参数
     */
    public void updateFilterConf(int filterId, Map<Integer, Object> filterConf) {
        if (filterId == FilterIDManager.NO_ID || mFilterSession == null) {
            YYLog.error(TAG, "updateFilterConf error, filterId is invalid");
            return;
        }

        List<BaseFilterParameter> paramLilst = mFilterSession.getFilterParameters(filterId);
        if (paramLilst == null) {
            YYLog.error(TAG, "updateFilterConf error, paramLilst is null");
            return;
        }
        //目前只支持一个filter对应一个param的情况
        BaseFilterParameter param = paramLilst.get(0);
        if (param == null) {
            YYLog.error(TAG, "updateFilterConf error, param is null");
            return;
        }

        //更新param的对应变量
        param.mOPType = FilterOPType.NO_OP;
        for (Map.Entry<Integer, Object> entry : filterConf.entrySet()) {
            param.updateWithConf(entry);
        }
        mFilterSession.modifyFilterParameter(filterId, param.mParameterID, param);
    }

    /**
     * 删除filter
     *
     * @param filterId filter对应的id
     */
    public void removeFilter(int filterId) {
        YYLog.info(TAG, Constant.MEDIACODE_PLAYER_FILTER + "removeFilter, filterId=" + filterId);

        if (filterId != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(filterId);
        }
    }

    /*--------------new interface of of effect-----------------*/
}
