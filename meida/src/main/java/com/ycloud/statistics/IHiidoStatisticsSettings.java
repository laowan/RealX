package com.ycloud.statistics;

import com.ycloud.Version;

/**
 * Created by zhangbin on 2017/1/10.
 */

public interface IHiidoStatisticsSettings {

    /** -----设备信息----- **/
    // private String sjp;// 设备厂商 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private String sjm;// 设备型号 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private String sys;// 平台 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private String mbos;// 系统版本 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private String cpu;// cpu型号
    // private String imei;// 手机串号 HiidoSDK字段，由HiidoSDK会自动获取上报

    String KEY_DATA_TYPE = "data_type";// 数据类型
    /**模块信息*/
    String KEY_MODULE_TYPE = "module_type";// 功能模块 1：录制 2：转码 3：截图

    /*预览、视频采集*/
    String KEY_Preview_Camera_Position = "camera_position";
    String KEY_Preview_Camera_Resolution = "camera_resolution";
    String KEY_Preview_View_Size = "view_size";
    String KEY_Preview_Filter_List = "filter_list";

    /*视频编码 输入值*/
    String KEY_Input_Video_Encode_Format = "iv_encodeformat";
    String KEY_Input_Video_Encode_Profile_Level = "iv_profile_level";
    String KEY_Input_Video_Encode_Resolution = "iv_resolution";
    String KEY_Input_Video_Encode_Bitrate = "iv_bitrate";
    String KEY_Input_Video_Encode_Frame_Rate = "iv_framerate";
    String KEY_Input_Video_Encode_Frame_Interval = "iv_iframe_interval";

    /*静音开关*/
    String KEY_Input_Enable_Mute = "mute";

    /*音频采集、编码设置 输入值*/
    String KEY_Input_Audio_Encode_Format = "ia_encodeformat";
    String KEY_Input_Audio_Capture_PCM_Format = "ia_pcmbitdepth";//AudioFormat.ENCODING_PCM_16BIT
    String KEY_Input_Audio_Encode_SampleRate = "ia_samplerate";
    String KEY_Input_Audio_Encode_Channel = "ia_channel"; //AudioFormat.CHANNEL_IN_MONO
    String KEY_Input_Audio_Encode_BitRate = "ia_bitrate";

    /*录制输出文件信息*/
    String KEY_Output_Video_Encoder_Type = "codec_type";
    String KEY_Output_Video_MediaCodec_Encode_ColorFormat = "color_format";
    String KEY_Output_Video_Resolution = "ov_resolution"; /*输出视频的分辨率*/
    String KEY_Output_Video_FrameRate = "ov_framerate";
    String KEY_Output_File_Duration = "ov_duration";//输出文件时长
    String KEY_Output_File_Size = "ov_size";//输出文件大小
    String KEY_Output_File_BitRate = "ov_bitrate";//输出文件的比特率

    /*录制状态*/
    String KEY_Record_Result = "result";//执行结果
    String KEY_Record_CostTime = "record_time";//从开始录制到结束录制的耗时
    String KEY_Record_FilterProcessTime = "filter_process_time";// 滤镜处理耗时


    public enum ExcuteResult {
        /*
        0：失败
        1：成功*/
        SUCCESS(1),
        FAIL(0);

        private int _value;

        private ExcuteResult(int value) {
            _value = value;
        }

        public int value() {
            return _value;
        }
    }

    public enum MODULE_TYPE {
        RECORD(1), // 录制
        TRANSCODE(2), // 转码
        SCREENSHOT(3); // 截图

        private int _value;

        private MODULE_TYPE(int value) {
            _value = value;
        }

        public int value() {
            return _value;
        }
    }

}
