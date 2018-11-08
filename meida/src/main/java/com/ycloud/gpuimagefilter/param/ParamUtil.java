package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.filter.FilterCenter;
import com.ycloud.gpuimagefilter.utils.FilterClsInfo;
import com.ycloud.utils.YYLog;

/**
 * Created by Administrator on 2017/7/29.
 */

public class ParamUtil {
    public static BaseFilterParameter newParameter(int type) {
        FilterClsInfo clsInfo = FilterCenter.getInstance().getFilterClsInfo(type);
        if (clsInfo != null && clsInfo.mFilterParameterCls != null) {
            try {
                BaseFilterParameter other = (BaseFilterParameter) clsInfo.mFilterParameterCls.newInstance();
                return other;
            } catch (InstantiationException e) {
                e.printStackTrace();
                YYLog.error("ParamUtil", "[exception]: " + e.toString());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                YYLog.error("ParamUtil", "[exception]: " + e.toString());
            }
        }

        return null;
    }
}
