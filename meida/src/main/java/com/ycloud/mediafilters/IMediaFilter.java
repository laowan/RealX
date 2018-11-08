package com.ycloud.mediafilters;
/**
 * Created by wangqiming on 16/11/17.
 */


import com.ycloud.ymrmodel.YYMediaSample;

/**
 * TODO: Add a class header comment!
 */

public interface IMediaFilter {
    public final String TAG = IMediaFilter.class.getSimpleName();
    boolean processMediaSample(YYMediaSample sample, Object upstream);
}
