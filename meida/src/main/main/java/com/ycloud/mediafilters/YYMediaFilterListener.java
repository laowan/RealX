package com.ycloud.mediafilters;

import com.ycloud.api.common.SampleType;

/**
 * Created by Administrator on 2018/1/12.
 */

public interface YYMediaFilterListener
{
    public void onFilterInit(AbstractYYMediaFilter filter);
    public void onFilterDeInit(AbstractYYMediaFilter filter);
    public void onFilterEndOfStream(AbstractYYMediaFilter filter);
    public void onFilterProcessMediaSample(AbstractYYMediaFilter filter, SampleType sampleType, long ptsMs);
    public void onFilterError(AbstractYYMediaFilter filter, String errMsg);
}
