package com.ycloud.api.common;

/**
 * Created by dzhj on 2017/8/9.
 */

public class TransitionInfo {
    /**
     * 视频路径
     */
    public String mVideoPath;
    /**
     * 视频文件中视轨的时长，以s为单位
     */
    public float mVideoDuration;
    /**
     * 转场时长，以s为单位
     */
    public float mTransitionDuration;
    /**
     * 转场类型
     */
    public TransitionType mTransitionType;
}
