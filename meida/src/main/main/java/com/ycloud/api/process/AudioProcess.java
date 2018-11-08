package com.ycloud.api.process;

import com.ycloud.mediaprocess.AudioProcessInternal;

/**
 * Created by jinyongqing on 2017/11/11.
 */

public class AudioProcess {
    private AudioProcessInternal mAudioProcessInternal;

    public AudioProcess() {
        mAudioProcessInternal = new AudioProcessInternal();
    }

    /**
     * 设置process listener
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mAudioProcessInternal.setMediaListener(listener);
    }

    /**
     * 从video中提取audio track
     *
     * @param inputPath  输入的video路径
     * @param outputPath 输出的audio路径
     */
    public void extractAudioTrack(String inputPath, String outputPath) {
        mAudioProcessInternal.extractAudioTrack(inputPath, outputPath);
    }

    /**
     * @param inputPath  输入的audio路径
     * @param outputPath 输出的audio路径
     * @param startTime  截取的起始时间点,单位ms
     * @param duration   截取的长度（如果超过audio时长，默认截取到末尾）,单位ms
     * @return 是否成功
     */
    public boolean clipAudio(String inputPath, String outputPath, int startTime, int duration) {
        return mAudioProcessInternal.clipAudio(inputPath, outputPath, (double) startTime / 1000, (double) duration / 1000);
    }
}
