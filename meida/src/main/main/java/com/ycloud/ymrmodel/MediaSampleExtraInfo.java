package com.ycloud.ymrmodel;

/**
 * 由业务层传入的音频帧或者视频帧的附加信息
 * Created by jinyongqing on 2018/3/15.
 */

public class MediaSampleExtraInfo {
    private float rhythmQuality;
    private float rhythmStrengthRatio;
    private float rhythmSmoothRatio;
    private byte[] rhythmFrequencyData;

    public float getRhythmSmoothRatio() {
        return rhythmSmoothRatio;
    }

    public void setRhythmSmoothRatio(float rhythmSmoothRatio) {
        this.rhythmSmoothRatio = rhythmSmoothRatio;
    }

    public float getRhythmStrengthRatio() {

        return rhythmStrengthRatio;
    }

    public void setRhythmStrengthRatio(float rhythmStrengthRatio) {
        this.rhythmStrengthRatio = rhythmStrengthRatio;
    }

    public float getRhythmQuality() {
        return rhythmQuality;
    }

    public void setRhythmQuality(float rhythmQuality) {
        this.rhythmQuality = rhythmQuality;
    }

    public byte[] getRhythmFrequencyData() {
        return rhythmFrequencyData;
    }

    public void setRhythmFrequencyData(byte[] rhythmFrequencyData) {
        this.rhythmFrequencyData = rhythmFrequencyData;
    }

}
