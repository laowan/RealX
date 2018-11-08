package com.ycloud.svplayer;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2017/8/24.
 */

public class AVStream {
    public int   m_startTimeMs; //
    public int   m_totalDurationMs; //
    public int   m_streamIndex;
    public int   m_YYNetCodec;  //@see YCMediaDefine.h
    public int   m_totalFrame; //number of frames in this stream if known or 0
    public boolean  m_isVideoStream;
    public boolean  m_isAudioStream;

    //for ffmpeg codec, ignore it for mediacodec of android or hardware codec of iOS.
    public int  m_codec_type;
    public int  m_codec_id;
    public int  m_codec_tag;

    /* Audio:
     *    Andorid: mediacodec decoder: android: MediaFormat.set("csd-0",m_codecSpecDescription).
     *    ffmpeg decoder: construct the AVCodecParameter codepar, memcpy(codepar.extradata, m_codecSpecDescription).
     *
     * Video:
     *    AVFrame will contains the sps/pps/vps for decoder, so no need to use this.
     */
    public ByteBuffer m_codecSpecDescription;
    public int  m_bits_per_raw_sample;
    public int  m_bits_per_coded_sample;
    public int  m_profile;
    public int  m_leve;
    public int  m_format;

    public long   m_bit_rate;
    //video.
    public int  m_width;
    public int  m_height;
    public int  m_frame_rate;
    public int  m_gop_size;
    public int  m_video_delay;  //B frame number;

    //audio
    public int m_sample_rate;
    public int m_channels;
    public long  m_channel_layout;
    public int m_frame_size;
    public int m_initial_padding;
    public int m_tailing_padding;
    public int m_seek_preroll;

    public static native void nativeClassInit();
}
