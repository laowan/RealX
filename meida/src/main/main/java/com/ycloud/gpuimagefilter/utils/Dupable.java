package com.ycloud.gpuimagefilter.utils;

/**
 * Created by Administrator on 2017/7/29.
 */

public interface Dupable<K> {
    boolean     isDupable();
    K            duplicate();
}
