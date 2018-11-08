package com.ycloud.api.process;

/**
 * 音视频相关的媒体信息，通过MediaProbe获取
 */
public class MediaInfo {
    // format
    public String filename = null;
    public String format_name = null;
    public String creation_time = null;
    public int nb_streams = 0;
    public double duration = 0.0;    // in seconds
    public long size = 0;            // bytes
    public int bit_rate = 0;        // bytes per second
    public String comment = null;

    // video
    public String v_codec_name = null;
    public int width = 0;
    public int height = 0;
    public float frame_rate = 0;
    public int total_frame = 0;
    public double v_rotate = 0.0;
    //以s为单位
    public double video_duration = 0.0;

    //audio
    public String audio_codec_name = null;
    public double audio_duration = 0.0;
    public int audioBitrate = 0;
    public int audioChannels = 0;
    public int audioSampleRate = 0;
}
