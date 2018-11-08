package com.ycloud.api.videorecord;

import com.ycloud.ymrmodel.MediaSampleExtraInfo;

/**
 * sdk请求业务层传递Media Sample附加信息的监听器
 * Created by jinyongqing on 2018/3/15.
 */

public interface IMediaInfoRequireListener {
    /**
     * sdk向业务层请求传递附加信息
     *
     * @param info 当前对应的Sample Extra Info
     */
    void onRequireMediaInfo(MediaSampleExtraInfo info);


    /**
     * sdk向业务层请求传递附加信息
     *
     * @param info 当前时间戳对应的Sample Extra Info
     * @param pts  当前时间戳，单位ms
     */
    void onRequireMediaInfo(MediaSampleExtraInfo info, long pts);
}
