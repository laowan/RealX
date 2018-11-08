package com.ycloud.ymrmodel;

/**
 * Created by jinyongqing on 2017/11/7.
 */

public class AudioMixBean {
    public String mFilepath;
    public float mVideoVolume = 1;
    public double mDelayTime = 0;
    public double mStartTime = 0;
    public String mProcessFileSuffix;
    public String mProcessFilePath;

    public AudioMixBean(String audioPath, double delayTime, double startTime, float volume, String processFileSuffix) {
        mFilepath = audioPath;
        mDelayTime = delayTime;
        mStartTime = startTime;
        mVideoVolume = volume;
        mProcessFileSuffix = processFileSuffix;
        mProcessFilePath = audioPath;
    }
}
