package com.ycloud.player;

/**
 * Created by dzhj on 17/7/28.
 */

public class TransitionPts {
    /**
     * 当前正在播放的视频索引
     */
    public int videoIndex;
    /**
     * 当前正在播放视频的内部pts
     */
    public long currentPts;
    /**
     * 需要转场的视频对应的视频内部pts
     */
    public long nextPts;
}
