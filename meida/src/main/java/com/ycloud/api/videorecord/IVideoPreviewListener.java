package com.ycloud.api.videorecord;

/**
 * Created by jinyongqing on 2018/2/6.
 *
 * 相机预览画面开始渲染的回调，用于业务层根据此回调来启动跳转，移除封面等操作
 */

public interface IVideoPreviewListener {
    /**
     * 相机预览开始的回调，用以表明帧已经开始渲染
     */
    void onStart();
}
