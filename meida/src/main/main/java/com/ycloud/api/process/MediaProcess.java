package com.ycloud.api.process;

import com.ycloud.mediaprocess.MediaProcessInternal;

/**
 * Created by Administrator on 2017/12/7.
 */
public class MediaProcess {
    private MediaProcessInternal mMediaProcessInternal;

    public MediaProcess() {
        mMediaProcessInternal = new MediaProcessInternal();
    }

    /**
     * 设置process listener
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mMediaProcessInternal.setMediaListener(listener);
    }

    /**
     * 从video中提取audio track
     *
     * @param inputPath  输入的video路径
     * @param outputPath 输出的audio路径
     */
    public void extractAudioTrack(String inputPath, String outputPath) {
        mMediaProcessInternal.extractAudioTrack(inputPath, outputPath);
    }

    /**
     * @param inputPath  输入的audio路径
     * @param outputPath 输出的audio路径
     * @param startTime  截取的起始时间点,单位ms
     * @param duration   截取的长度（如果超过audio时长，默认截取到末尾）,单位ms
     * @return 是否成功
     */
    public boolean clipAudio(String inputPath, String outputPath, int startTime, int duration) {
        return mMediaProcessInternal.clipAudio(inputPath, outputPath, (double) startTime / 1000, (double) duration / 1000);
    }


    /** 使用Android 自带的 MediaExtractor 和 MediaMuxer 只支持全I帧写入，MediaMuxer(android 7.0系统才支持B帧)。所以只能选FFMPEG.
     * @param inputPath  输入的video路径
     * @param outputPath 输出的video路径
     * @param startTime  截取的起始时间点,单位ms
     * @param duration   截取的长度（如果超过video时长，默认截取到末尾）,单位ms
     * @return 是否成功
     */
    public boolean clipVideo(String inputPath, String outputPath, int startTime, int duration) {
        return mMediaProcessInternal.clipVideo(inputPath, outputPath, (double) startTime / 1000, (double) duration / 1000);
    }

    public void cancel(){
        mMediaProcessInternal.cancel();
    }
}
