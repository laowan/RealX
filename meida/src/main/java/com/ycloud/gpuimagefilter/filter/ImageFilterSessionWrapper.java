package com.ycloud.gpuimagefilter.filter;

import com.ycloud.api.common.FilterGroupType;
import com.ycloud.common.Constant;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.ParamUtil;
import com.ycloud.gpuimagefilter.utils.FilterIDManager;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2018/5/17.
 */

public class ImageFilterSessionWrapper {
    private static final String TAG = "ImageFilterSessionWrapper";
    private FilterSession mFilterSession;

    public ImageFilterSessionWrapper() {
        mFilterSession = FilterCenter.getInstance().createFilterSession();
    }

    public int getSessionID() {
        return mFilterSession.getSessionID();
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


    /*---------------------------------------------------*/

//    private int getZOrderIDByFilterType(int filterType) {
//        int zOrderID = FilterLayout.generateZOrderID();
//        return zOrderID;
//    }
//
//
//    private int mEffectFilterID = FilterIDManager.NO_ID;
//    private EffectFilterParameter mEffectFilterParam;
//    public void addEmojiWithDirecory(String directory) {
//        FilterConfigParse.FilterConfigInfo filterConfigInfo = FilterConfigParse.parseFilterConf(directory);
//        if (filterConfigInfo != null) {
//
//            //step 1:先添加表情
//            if (filterConfigInfo.mEmojiEffectPath != null) {
//                mEffectFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_EFFECT));
//                mEffectFilterParam = new EffectFilterParameter();
//                mEffectFilterParam.mEffectParam = filterConfigInfo.mEmojiEffectPath;
//                mEffectFilterParam.mSurportSeeking = filterConfigInfo.mSupportSeeking;
//                mFilterSession.resetFilterParameter(mEffectFilterID, mEffectFilterParam);
//            }
//        }
//    }

}
