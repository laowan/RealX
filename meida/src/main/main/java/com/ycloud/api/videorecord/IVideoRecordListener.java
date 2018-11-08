package com.ycloud.api.videorecord;

/**
 * 视频录制进度回调监听器
 */
public interface IVideoRecordListener {
    /**
     * 录制进度回调，用以表明录制的进度
     * @param seconds: 代表已录制的时长,用秒来表示
     */
    void onProgress(float seconds);

    /**
     * startRecord函数的回调
     *  @param successed 是否成功停止
     */
    void onStart(boolean successed);

    /**
     * stopRecord函数的回调，用以异步停止
     * @param successed 是否成功停止
     */
    void onStop(boolean successed);
}
