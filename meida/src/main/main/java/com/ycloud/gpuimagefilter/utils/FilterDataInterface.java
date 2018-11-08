package com.ycloud.gpuimagefilter.utils;

import com.ycloud.gpuimagefilter.param.BaseFilterParameter;

import java.util.List;

/**
 * Created by Administrator on 2017/7/29.
 */

public interface FilterDataInterface<K> extends Dupable<K> {
    public boolean isDupable();

    public K duplicate();

    public int addFilterParameter(BaseFilterParameter parameter);

    public int resetFilterParameter(BaseFilterParameter parameter);

    public boolean modifyFilterParameter(int paramID, BaseFilterParameter parameter);

    public boolean updateFilterParameter(int paramID, BaseFilterParameter parameter);

    public void modifyFilterZOrder(int zOrder);

    public void removeFilterParameter(int paramID);

    public void removeFilterParameter();

    public BaseFilterParameter getFilterParameter(int paramId);

    List<BaseFilterParameter> getFilterParameters();
}
