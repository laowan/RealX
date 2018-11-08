package com.ycloud.statistics;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.ycloud.utils.ExecutorUtils;
import com.yy.hiidostatis.api.HiidoSDK;
import com.yy.hiidostatis.api.StatisContent;
import com.yy.hiidostatis.api.StatisOption;
import com.yy.hiidostatis.defs.StatisAPI;

public class HiidoStatistics extends HiidoStatisticsValue implements IHiidoStatisticsSettings {
    public static final String TAG = HiidoStatistics.class.getSimpleName();
    private static final boolean mEnable = true;/*开关*/
    //	private Context mContext = null;
    private static StatisAPI mStatisAPI = null;
    private static HiidoStatistics mHiidoStatistics = null;


    // 临时变量，计算耗时用，非上传数据
    public static long startTime = -1;
    public static long endTime = -1;
    /*录制时间计时*/
    public static long startRecordTime = -1;
    public static long stopRecordTime = -1;
    /*算法执行时间*/
    public static long sum_algorithmTime = -1;

    public static boolean Enable() {
        return mEnable;
    }

    public static synchronized HiidoStatistics create(Context ctx, String appVersion, String appFrom) {
        if (null == mHiidoStatistics) {
            mHiidoStatistics = new HiidoStatistics();
        }

        // 初始化海度上传对象
        if (null == mStatisAPI) {
            StatisOption option = new StatisOption();
            option.setAppkey(HIDDO_APP_KEY);
            option.setVer(SDK_VER);

            if (!TextUtils.isEmpty(appVersion)) {
                option.setAppId(appVersion);
            }
            if (!TextUtils.isEmpty(appFrom)) {
                option.setFrom(appVersion);
            }
            mStatisAPI = HiidoSDK.instance().createNewStatisApi();
            mStatisAPI.init(ctx, option);
        }


        return mHiidoStatistics;
    }

    /*
    * 1：录制
    2：转码
    3：截图
    * */
    public static void saveModuleType(MODULE_TYPE moduleType) {
        if (mEnable) {
            Module_Type = moduleType.value();
        }
    }

    /*0：软件编解码
    1：硬件编解码*/
    public static void saveEncoderType(boolean isHWCodec) {
        if (mEnable) {
            Output_Video_Encoder_Type = (isHWCodec ? 1 : 0);
        }
    }

    public static void saveMediaCodecEncodeColorFormat(int colorFormat) {
        if (mEnable && -1 != colorFormat) {
            Output_Video_MediaCodec_Encode_ColorFormat = colorFormat;
        }

    }

    /*Camera参数*/
    public static void savePreviewCameraParams(int camera_position, String camera_resolution) {
        if (mEnable) {
            if (-1 != camera_position)
                Preview_Camera_Position = camera_position;

            if (!TextUtils.isEmpty(camera_resolution))
                Preview_Camera_Resolution = camera_resolution;
        }
    }

    /*滤镜列表*/
    public static void saveFilterLists(String filter_list) {
        if (mEnable) {
            if (!TextUtils.isEmpty(filter_list))
                Preview_Filter_List = filter_list;
        }
    }

    /*View 参数*/
    public static void savePreviewSize(String view_size) {
        if (mEnable) {
            if (!TextUtils.isEmpty(view_size))
                Preview_View_Size = view_size;
        }
    }

    /*视频编码*/
    public static void saveVideoEncoderSettings(String iv_encodeformat, String iv_profile_level, String iv_resolution, float iv_framerate, int iv_bitrate, int iv_iframe_interval) {
        if (mEnable) {
            if (!TextUtils.isEmpty(iv_encodeformat))
                Input_Video_Encode_Format = iv_encodeformat;
            if (!TextUtils.isEmpty(iv_profile_level))
                Input_Video_Encode_Profile_Level = iv_profile_level;
            if (!TextUtils.isEmpty(iv_resolution))
                Input_Video_Encode_Resolution = iv_resolution;
            if (-1 != iv_framerate)
                Input_Video_Encode_Frame_Rate = iv_framerate;
            if (-1 != iv_bitrate)
                Input_Video_Encode_Bitrate = iv_bitrate;
            if (-1 != iv_iframe_interval)
                Input_Video_Encode_Frame_Interval = iv_iframe_interval;
        }
    }

    /*静音*/
    public static void saveMuteSetting(int enbale_mute) {
        if (mEnable) {
            if (-1 != enbale_mute)
                Input_Enable_Mute = enbale_mute;
        }
    }

    /*音频编码*/
    public static void saveAudioEncoderSettings(String ia_encodeformat, int ia_pcmbitdepth, int ia_samplerate, int ia_bitrate, int ia_channel) {
        if (mEnable) {
            if (!TextUtils.isEmpty(ia_encodeformat))
                Input_Audio_Encode_Format = ia_encodeformat;
            if (-1 != ia_pcmbitdepth)
                Input_Audio_Capture_PCM_Format = ia_pcmbitdepth;
            if (-1 != ia_samplerate)
                Input_Audio_Encode_SampleRate = ia_samplerate;
            if (-1 != ia_bitrate)
                Input_Audio_Encode_BitRate = ia_bitrate;
            if (-1 != ia_channel)
                Input_Audio_Encode_Channel = ia_channel;
        }
    }

    /*输出信息*/
    public static void saveRecordResult(int result) {
        if (mEnable) {
            if (-1 != result)
                Record_Result = result;
        }
    }

    public static void saveRecordOutputInfo(long ov_size, long ov_duration, String ov_resolution, float ov_framerate, int ov_bitrate) {
        if (mEnable) {
            if (-1 != ov_size)
                Output_File_Size = ov_size;
            if (-1 != ov_duration)
                Output_File_Duration = ov_duration;
            if (!TextUtils.isEmpty(ov_resolution))
                Output_Video_Resolution = ov_resolution;
            if (-1 != ov_framerate)
                Output_Video_FrameRate = ov_framerate;
            if (-1 != ov_bitrate)
                Output_File_BitRate = ov_bitrate;
        }
    }

    /*时间信息*/
    public static void saveRecordTimeInfo(long record_time, long filter_process_time) {
        if (mEnable) {
            if (-1 != record_time)
                Record_CostTime = record_time;
            if (-1 != filter_process_time)
                Record_FilterProcessTime = filter_process_time;
        }
    }

    /**
     * Title: reportStatisticContent
     * Description: 提交日志上传至海度
     * param
     * return void
     */
    public static void reportStatisticContent() {
        Log.d(TAG, "reportStatisticContent");
        if (mEnable) {
            ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
                @Override
                public void run() {
                    StatisContent statisContent = getUploadStatisContent();
//                    LogUtil.d(TAG, "Hiido data act=" + YCLOUD_MEDIA_SDK + " :" + statisContent.toString());
                    mStatisAPI.reportStatisticContent(YCLOUD_MEDIA_SDK, statisContent, true, true);
                    clearTime();
                    clear();
                }
            });
        }
    }

    /**
     * 在每次上传海度日志前，先检查本地是否存在数据，如果存在，则视为上次出错未上传的数据上传
     */
    public static StatisContent getUploadStatisContent() {
        StatisContent statisContent = new StatisContent();
        if (mEnable) {
            try {
                statisContent.put(KEY_DATA_TYPE, DATA_TYPE);
                statisContent.put(KEY_MODULE_TYPE, Module_Type);

                statisContent.put(KEY_Preview_Camera_Position, Preview_Camera_Position);
                statisContent.put(KEY_Preview_Camera_Resolution, Preview_Camera_Resolution);
                statisContent.put(KEY_Preview_View_Size, Preview_View_Size);
                statisContent.put(KEY_Preview_Filter_List, Preview_Filter_List);

                statisContent.put(KEY_Input_Video_Encode_Format, Input_Video_Encode_Format);
                statisContent.put(KEY_Input_Video_Encode_Profile_Level, Input_Video_Encode_Profile_Level);
                statisContent.put(KEY_Input_Video_Encode_Resolution, Input_Video_Encode_Resolution);
                /*本地float 海度double*/
                statisContent.put(KEY_Input_Video_Encode_Frame_Rate, Input_Video_Encode_Frame_Rate);
                statisContent.put(KEY_Input_Video_Encode_Bitrate, Input_Video_Encode_Bitrate);
                statisContent.put(KEY_Input_Video_Encode_Frame_Interval, Input_Video_Encode_Frame_Interval);
                statisContent.put(KEY_Input_Enable_Mute, Input_Enable_Mute);
                statisContent.put(KEY_Input_Audio_Encode_Format, Input_Audio_Encode_Format);
                statisContent.put(KEY_Input_Audio_Capture_PCM_Format, Input_Audio_Capture_PCM_Format);
                statisContent.put(KEY_Input_Audio_Encode_SampleRate, Input_Audio_Encode_SampleRate);
                statisContent.put(KEY_Input_Audio_Encode_BitRate, Input_Audio_Encode_BitRate);
                statisContent.put(KEY_Input_Audio_Encode_Channel, Input_Audio_Encode_Channel);

                statisContent.put(KEY_Output_Video_Encoder_Type, Output_Video_Encoder_Type);
                statisContent.put(KEY_Output_Video_MediaCodec_Encode_ColorFormat, Output_Video_MediaCodec_Encode_ColorFormat);
                statisContent.put(KEY_Record_Result, Record_Result);
                statisContent.put(KEY_Output_File_Size, Output_File_Size);
                statisContent.put(KEY_Output_File_Duration, Output_File_Duration);
                statisContent.put(KEY_Output_Video_Resolution, Output_Video_Resolution);
                /*本地float 海度double*/
                statisContent.put(KEY_Output_Video_FrameRate, Output_Video_FrameRate);
                statisContent.put(KEY_Output_File_BitRate, Output_File_BitRate);

                statisContent.put(KEY_Record_CostTime, Record_CostTime);
                statisContent.put(KEY_Record_FilterProcessTime, Record_FilterProcessTime);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return statisContent;
    }


    private static void clearTime() {
        startTime = -1;
        endTime = -1;
        startRecordTime = -1;
        stopRecordTime = -1;
        sum_algorithmTime = -1;
    }
}
