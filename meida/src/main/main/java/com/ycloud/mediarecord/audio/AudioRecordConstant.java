package com.ycloud.mediarecord.audio;

/**
 * Created by jinyongqing on 2017/6/12.
 */

public class AudioRecordConstant {
    /*采样率*/
    public static int SAMPLE_RATE = 44100;
    /*声道数*/
    public static int CHANNELS = 1;
    /*frame/buffer/sec*/
    public static final int FRAMES_PER_BUFFER = 25;

    /*每帧采样点数，aac为1024*/
    public static final int SAMPLES_PER_FRAME = 1024;

    /*音频的码率*/
    public static final int AUDIO_BITRATE = 64 * 1000;

    /*音量大小检测频率*/
    public static final int VOLUME_DETECT_FREQ = 5;
}
