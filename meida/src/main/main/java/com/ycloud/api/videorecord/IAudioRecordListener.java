package com.ycloud.api.videorecord;

/**
 * 音频录制过程参数的回调，包括麦克风音量等
 * Created by jinyongqing on 2017/11/17.
 */

public interface IAudioRecordListener {
    /**
     * 音频话筒采集的音量
     *
     * @param avgAmplitude 代表音量大小的平均振幅值
     * @param maxAmplitude 代表音量大小的最大振幅值
     */
    void onVolume(int avgAmplitude, int maxAmplitude);
}
