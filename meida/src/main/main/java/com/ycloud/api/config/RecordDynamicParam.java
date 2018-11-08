package com.ycloud.api.config;

import android.os.Build;
import android.text.TextUtils;

import com.ycloud.common.Constant;
import com.ycloud.common.GlobalConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * 服务端下发参数，主要是用来动态调整sdk的默认全局参数
 * Created by jinyongqing on 2017/6/3.
 */

public class RecordDynamicParam {
    private static String TAG = RecordDynamicParam.class.getSimpleName();
    private static RecordDynamicParam mRecordDynamicParam;

    private String mDynParams;
    private String mHardEncodeParameters;
    private boolean mForbidSetVideoStabilization;

    private boolean mUseFfmpegExport = false;

    //新合成使用软解码.
    private boolean mExportSwDecoder = false;

    //编辑页使用ffmpeg decode
    private boolean mUseFfmpegDecode = false;


    //预览播放器是否使用原来的ijkPlayer.
    private boolean mUseIJKPlayer;

    public static synchronized RecordDynamicParam getInstance() {
        if (mRecordDynamicParam == null) {
            mRecordDynamicParam = new RecordDynamicParam();
        }
        return mRecordDynamicParam;
    }

    private RecordDynamicParam() {
        mDynParams = null;
        mUseIJKPlayer = false;
        mForbidSetVideoStabilization = (Build.MODEL.compareToIgnoreCase("HUAWEI NXT-AL10") == 0);//华为HUAWEI NXT-AL10 得到的实际像素变小,预览时会比系统相机看到的像素大;
        mHardEncodeParameters = "";
    }


    private void setExportParameter(final String dynamicParam) {
        if (TextUtils.isEmpty(dynamicParam)) {
            YYLog.info(TAG, "setUseFffmpegExport with error parameter..");
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(dynamicParam);
            if (jsonObject != null) {
                if (!jsonObject.isNull("ffmpeg-export")) {
                    int useFfmpg  = jsonObject.optInt("ffmpeg-export");
                    if(useFfmpg == 1) {
                        mUseFfmpegExport = true;
                    } else {
                        mUseFfmpegExport = false;
                    }
                }

                if(!jsonObject.isNull("export_swdecoder")) {
                    int export_swdecoder  = jsonObject.optInt("export_swdecoder");
                    if(export_swdecoder == 1) {
                        mExportSwDecoder = true;
                    } else {
                        mExportSwDecoder = false;
                    }
                }
            }
        }catch (JSONException e) {
            YYLog.info(TAG, "setUseFffmpegExport with exception: " + e.toString());
        }

        YYLog.info(TAG, "setUseFffmpegExport success: ExportSwDecoder=" + mExportSwDecoder + " mUseFfmpegExport=" + mUseFfmpegExport);
    }

    public synchronized boolean useFfmpegExport(String model) {
        return mUseFfmpegExport;
    }

    /**
     * 服务端动态参数下发，用于调整代码里配置的全局静态参数，如编码类型，crf等
     *
     * @param dynamicParam
     */
    public void setDynamicParam(final String dynamicParam) {
        YYLog.info(TAG, Constant.MEDIACODE_SERVER_PARAM + "phone model is:" + DeviceUtil.getPhoneModel() + " dynamic param: " + dynamicParam);
        if (TextUtils.isEmpty(dynamicParam)) {
            YYLog.warn(TAG, Constant.MEDIACODE_SERVER_PARAM + "parse json is invalid");
            return;
        }

        mDynParams = dynamicParam;
        YYLog.info(this, Constant.MEDIACODE_SERVER_PARAM + "parse json: " + dynamicParam);

        //只有全局配置初始化后才能设置动态下发参数，否则会被后续初始化覆盖
        if (GlobalConfig.getInstance().isInit()) {
            applyParamToGlobalConf();
        }

        setExportParameter(dynamicParam);
    }


    /**
     * 调整全局静态参数配置
     */
    public void applyParamToGlobalConf() {
        if (TextUtils.isEmpty(mDynParams)) {
            YYLog.info(TAG, "apply dynamic params to global config return for invalid param");
            return;
        }

        try {
            JSONObject mDynamicParamJson = new JSONObject(mDynParams);
            if (mDynamicParamJson != null) {
                //服务端下发配置：基本信息
                if (!mDynamicParamJson.isNull("crf")) {
                    GlobalConfig.getInstance().getRecordConstant().EXPORT_CRF = mDynamicParamJson.optInt("crf");
                }
                if (!mDynamicParamJson.isNull("preset")) {
                    GlobalConfig.getInstance().getRecordConstant().EXPORT_PRESET = mDynamicParamJson.optString("preset");
                }
                if (!mDynamicParamJson.isNull("first-bitrate")) {
                    GlobalConfig.getInstance().getRecordConstant().RECORD_BITRATE = mDynamicParamJson.optInt("first-bitrate");
                }
                if (!mDynamicParamJson.isNull("second-maxbirate")) {
                    GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE = mDynamicParamJson.optInt("second-maxbirate");
                }
                if (!mDynamicParamJson.isNull("profile")) {
                    GlobalConfig.getInstance().getRecordConstant().EXPORT_PROFILE = mDynamicParamJson.optString("profile");
                }
                if (!mDynamicParamJson.isNull("savelocal-crf")) {
                    GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_CRF = mDynamicParamJson.optInt("savelocal-crf");
                }
                if (!mDynamicParamJson.isNull("savelocal-maxbirate")) {
                    GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_BITRATE = mDynamicParamJson.optInt("savelocal-maxbirate");
                }

                //服务端下发配置：条件分支

                if (!mDynamicParamJson.isNull("hard-encode-param")) {
                    setHardEncodeParameters(mDynamicParamJson.optString("hard-encode-param"));
                }
                if (!mDynamicParamJson.isNull("soft-encode") && mDynamicParamJson.optInt("soft-encode") == 1) {
                    GlobalConfig.getInstance().getRecordConstant().VIDEO_ENCODE_TYPE = VideoEncoderType.SOFT_ENCODER_X264;
                }
                if (!mDynamicParamJson.isNull("ijkplayer") && mDynamicParamJson.optInt("ijkplayer") == 1) {
                    setUserIJKPlayer(true);
                }
                if (!mDynamicParamJson.isNull("forbid-setVideoStabilization") && mDynamicParamJson.optInt("forbid-setVideoStabilization") == 1) {
                    mForbidSetVideoStabilization = true;
                }
                if (!mDynamicParamJson.isNull("player-ffmpeg-dec") && mDynamicParamJson.optInt("player-ffmpeg-dec") == 1) {
                    setUseFfmpegDecode(true);
                }
            }
        } catch (JSONException e) {
            YYLog.error(TAG, "parse json record params error");
        }
    }

    public void setUserIJKPlayer(boolean enable) {
        mUseIJKPlayer = enable;
    }

    public boolean getUseIJKPlayer() {
        return mUseIJKPlayer;
    }

    public void setHardEncodeParameters(String hardEncodeParameters) {
        YYLog.info(TAG, "parse hardEncodeParameters from server:" + hardEncodeParameters);
        mHardEncodeParameters = hardEncodeParameters;
    }

    public String getHardEncodeParameters() {
        return mHardEncodeParameters;
    }

    public boolean isForbidSetVideoStabilization() {
        return mForbidSetVideoStabilization;
    }

    public void setUseFfmpegDecode(boolean useFfmpegDecode) {
        mUseFfmpegDecode = useFfmpegDecode;
    }

    public boolean getUseFfmpegDecode() {
        return mUseFfmpegDecode;
    }

    public void setExportSwDecoder(boolean exportSwDecoder) { mExportSwDecoder = exportSwDecoder; }

    public boolean getExportSwDecoder() { return mExportSwDecoder; }
}
