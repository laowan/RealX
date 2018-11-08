package com.ycloud.statistics;

import com.ycloud.Version;

/**
 * Created by zhangbin on 2017/1/10.
 */

public class HiidoStatisticsValue {
    public final static int TEST_MODE = 0;
    public final static int PRODUCTION_MODE = 1;
    public final static String SP_NAME = "mediarecorder_statiticsData";

    /**
     * -----Hiido 信息-----
     **/
    public final static String HIDDO_APP_KEY = "b99eb312d3fb0ab7d92113093046edd8";// 应用标识符
    public final static String YCLOUD_MEDIA_SDK = "yymediarecordersdk";// 协议标识符
    public final static String SDK_VER = Version.getVersion();

    /**
     * -----设备信息-----
     **/
    // private static String sjp;// 设备厂商 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private static String sjm;// 设备型号 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private static String sys;// 平台 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private static String mbos;// 系统版本 HiidoSDK字段，由HiidoSDK会自动获取上报
    // private static String cpu;// cpu型号
    // private static String imei;// 手机串号 HiidoSDK字段，由HiidoSDK会自动获取上报

    final static int DATA_TYPE = PRODUCTION_MODE;// 数据类型  发布的sdk请将此置为 PRODUCTION_MODE,测试则用TEST_MODE
    /**
     * 模块信息
     */
    static int Module_Type = -1;// 功能模块 1：录制 2：转码 3：截图

    /*预览、视频采集*/
    static int Preview_Camera_Position = -1;
    static String Preview_Camera_Resolution = "-1";
    static String Preview_View_Size = "-1";
    static String Preview_Filter_List = "-1";

    /*视频编码 输入值*/
    static String Input_Video_Encode_Format = "-1";
    static String Input_Video_Encode_Profile_Level = "-1";
    static String Input_Video_Encode_Resolution = "-1";
    static float Input_Video_Encode_Frame_Rate = -1;
    static int Input_Video_Encode_Bitrate = -1;
    static int Input_Video_Encode_Frame_Interval = -1;

    /*静音开关*/
    static int Input_Enable_Mute = 0;//0：录音 1：静音

    /*音频采集、编码设置 输入值*/
    static String Input_Audio_Encode_Format = "-1";
    static int Input_Audio_Capture_PCM_Format = -1;//AudioFormat.ENCODING_PCM_16BIT
    static int Input_Audio_Encode_SampleRate = -1;
    static int Input_Audio_Encode_BitRate = -1;
    static int Input_Audio_Encode_Channel = -1; //AudioFormat.CHANNEL_IN_MONO

    /*录制输出文件信息*/
    static int Output_Video_Encoder_Type = -1;
    static int Output_Video_MediaCodec_Encode_ColorFormat = -1;
    static String Output_Video_Resolution = "-1"; /*输出视频的分辨率*/
    static float Output_Video_FrameRate = -1.0f;
    static long Output_File_Duration = -1;//输出文件时长
    static long Output_File_Size = -1;//输出文件大小
    static int Output_File_BitRate = -1;//输出文件的比特率

    /*录制状态*/
    static int Record_Result = -1;//执行结果 0：失败 1：成功
    static long Record_CostTime = -1;//从开始录制到结束录制的耗时
    static long Record_FilterProcessTime = -1;// 滤镜处理耗时


    static protected void clear() {
        /**模块信息*/
      //  Module_Type = -1;// 功能模块 1：录制 2：转码 3：截图

    /*预览信息*/
    /*  //1、Camera参数
        Preview_Camera_Position = -1;
        Preview_Camera_Resolution = "-1";
        //2、View 参数
        Preview_View_Size = "-1";*/

        //3、滤镜列表
        /**
         * 0-999为sdk通用滤镜编号，
         1000以上为业务定制滤镜编号，
         如1000-1999为业务1定制，2000-2999为业务2定制
         滤镜编号见“滤镜列表”
         多个滤镜间用逗号隔开，如1,2,3
         */
        Preview_Filter_List = "-1";

    /*视频编码 输入值*/
        Input_Video_Encode_Format = "-1";
        Input_Video_Encode_Profile_Level = "-1";
        Input_Video_Encode_Resolution = "-1";
        Input_Video_Encode_Frame_Rate = -1;
        Input_Video_Encode_Bitrate = -1;
        Input_Video_Encode_Frame_Interval = -1;

    /*静音开关*/
//        Input_Enable_Mute = -1;

    /*音频采集、编码设置 输入值*/
        Input_Audio_Encode_Format = "-1";
        Input_Audio_Capture_PCM_Format = -1;//AudioFormat.ENCODING_PCM_16BIT
        Input_Audio_Encode_SampleRate = -1;
        Input_Audio_Encode_BitRate = -1;
        Input_Audio_Encode_Channel = -1; //AudioFormat.CHANNEL_IN_MONO

    /*录制输出文件信息*/
//        Output_Video_Encoder_Type = -1;
        Output_Video_MediaCodec_Encode_ColorFormat = -1;
        Output_Video_Resolution = "-1"; /*输出视频的分辨率*/
        Output_Video_FrameRate = -1.0f;
        Output_File_Duration = -1;//输出文件时长
        Output_File_Size = -1;//输出文件大小
        Output_File_BitRate = -1;//输出文件的比特率

    /*录制状态*/
        Record_Result = -1;//执行结果
        Record_CostTime = -1;//从开始录制到结束录制的耗时
        Record_FilterProcessTime = -1;// 滤镜处理耗时
    }

}
