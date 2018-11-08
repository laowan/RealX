package com.ycloud.mediarecord;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.ResolutionType;
import com.ycloud.api.videorecord.IAudioRecordListener;
import com.ycloud.api.videorecord.IVideoPreviewListener;
import com.ycloud.api.videorecord.IVideoRecordListener;
import com.ycloud.api.videorecord.MediaRecordErrorListener;
import com.ycloud.common.GlobalConfig;
import com.ycloud.mediarecord.audio.AudioVoiceChangerToolbox;
import com.ycloud.utils.YYLog;

import java.util.HashMap;

/**
 * sdk配置非全局配置参数类，一般由业务设置，只对本次录制，导出等有效
 * 由全局配置参数（包含服务端下方）参数初始化
 * 可以由业务方设置，每次设置仅对本次录制／导出等有效
 * Modified by jinyongqing on 2017/11/22
 * Created by DZHJ on 2017/3/28.
 */

public class RecordConfig {

    public static String TAG = RecordConfig.class.getSimpleName();

    private int mCameraId;
    private int mCaptureWidth;
    private int mCaptureHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mBitRate;
    private int mFrameRate;
    private int mVideoGopSize;
    private boolean mEnableAudioRecord;
    public boolean mExposureCompensation;
    private String mRecordFilePath;
    private HashMap<String, String> mMetadataMap;
    private float mRecordSpeed;
    private int mVoiceChangeMode;  //变声模式，取值参考audio_toolbox_impl.h的VoiceEffectOption
    private String mSnapShotPath;
    private String mSnapShotFileNamePrefix;
    private float mSnapFrequency;
    private int mSnapshotQuality;
    public int mOfDeviceLevel = OrangeFilter.OF_DeviceLevel_0;  //OrangeFilter分级，默认是0，最高

    IVideoRecordListener mRecordListener;
    IAudioRecordListener mAudioRecordListener;
    IVideoPreviewListener mPreviewListener;
    MediaRecordErrorListener mErrorListener;


    public RecordConfig() {
        init();
    }

    public void init() {
        mCaptureWidth = GlobalConfig.getInstance().getRecordConstant().CAPTURE_VIDEO_WIDTH;
        mCaptureHeight = GlobalConfig.getInstance().getRecordConstant().CAPTURE_VIDEO_HEIGHT;
        mVideoWidth = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_WIDTH;
        mVideoHeight = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_HEIGHT;
        if (SDKCommonCfg.getRecordModePicture()) {
            mCaptureWidth = GlobalConfig.getInstance().getRecordConstant().CAPTURE_VIDEO_WIDTH_PIC;
            mCaptureHeight = GlobalConfig.getInstance().getRecordConstant().CAPTURE_VIDEO_HEIGHT_PIC;
            mVideoWidth = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_WIDTH_PIC;
            mVideoHeight = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_HEIGHT_PIC;
        }
        mBitRate = GlobalConfig.getInstance().getRecordConstant().RECORD_BITRATE;
        mFrameRate = GlobalConfig.getInstance().getRecordConstant().RECORD_FRAME_RATE;
        mVideoGopSize = GlobalConfig.getInstance().getRecordConstant().RECORD_GOP;
        mEnableAudioRecord = true;
        mExposureCompensation = false;
        mRecordSpeed = 1.0f;
        mVoiceChangeMode = AudioVoiceChangerToolbox.VeoNone;

        mSnapShotPath = "/sdcard/snapshot";
        mSnapShotFileNamePrefix = "test";
        mSnapFrequency = 2;
        mSnapshotQuality = GlobalConfig.getInstance().getRecordConstant().SNAPSHOT_QUALITY;

    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public int getBitRate() {
        return mBitRate;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    public boolean getEnableAudioRecord() {
        return mEnableAudioRecord;
    }

    public IVideoRecordListener getRecordListener() {
        return mRecordListener;
    }

    public IAudioRecordListener getAudioRecordListener() {
        return mAudioRecordListener;
    }

    public IVideoPreviewListener getPreviewListener() {
        return mPreviewListener;
    }

    public void setVideoWidth(int videoWidth) {
        this.mVideoWidth = videoWidth;
    }

    public void setVideoHeight(int videoHeight) {
        this.mVideoHeight = videoHeight;
    }

    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    public void setFrameRate(int frameRate) {
        this.mFrameRate = frameRate;
    }

    public void setEnableAudioRecord(boolean enableAudioRecord) {
        mEnableAudioRecord = enableAudioRecord;
    }

    public void setRecordListener(IVideoRecordListener recordListener) {
        mRecordListener = recordListener;
    }

    public void setAudioRecordListener(IAudioRecordListener audioRecordListener) {
        mAudioRecordListener = audioRecordListener;
    }

    public void setPreviewListener(IVideoPreviewListener previewListener) {
        mPreviewListener = previewListener;
    }

    public String getRecordFilePath() {
        return mRecordFilePath;
    }

    public void setOutputPath(String recordFilePath) {
        this.mRecordFilePath = recordFilePath;
    }

    public MediaRecordErrorListener getErrorListener() {
        return mErrorListener;
    }

    public void setErrorListener(MediaRecordErrorListener errorListener) {
        this.mErrorListener = errorListener;
    }

    public void setVideoGopSize(int videoGopSize) {
        this.mVideoGopSize = videoGopSize;
    }

    public int getVideoGopSize() {
        return this.mVideoGopSize;
    }

    public void setCameraId(int id) {
        mCameraId = id;
    }

    public int getCameraId() {
        return mCameraId;
    }

    public int getCaptureWidth() {
        return mCaptureWidth;
    }

    public void setCaptureWidth(int mCaptureWidth) {
        this.mCaptureWidth = mCaptureWidth;
    }

    public int getCaptureHeight() {
        return mCaptureHeight;
    }

    public void setCaptureHeight(int mCaptureHeight) {
        this.mCaptureHeight = mCaptureHeight;
    }

    public void setRecordSpeed(float recordSpeed) {
        this.mRecordSpeed = recordSpeed;
    }

    public float getRecordSpeed() {
        return this.mRecordSpeed;
    }

    public void setVoiceChangeMode(int mode) {
        this.mVoiceChangeMode = mode;
    }

    public int getVoiceChangeMode() {
        return this.mVoiceChangeMode;
    }

    public String getSnapShotPath() {
        return mSnapShotPath;
    }

    public void setSnapShotPath(String snapShotPath) {
        mSnapShotPath = snapShotPath;
    }

    public float getSnapFrequency() {
        return mSnapFrequency;
    }

    public void setSnapFrequency(float snapFrequency) {
        mSnapFrequency = snapFrequency;
    }

    public String getSnapShotFileNamePrefix() {
        return mSnapShotFileNamePrefix;
    }

    public void setSnapShotFileNamePrefix(String snapShotFileNamePrefix) {
        mSnapShotFileNamePrefix = snapShotFileNamePrefix;
    }

    public int getSnapshotQuality() {
        return this.mSnapshotQuality;
    }

    public void setOfDeviceLevel(int deviceLevel) {
        mOfDeviceLevel = deviceLevel;
    }

    public int getOfDeviceLevel() {
        return mOfDeviceLevel;
    }

    public void setResolutionType(ResolutionType resolutionType) {
        switch (resolutionType) {
            case R540P:
                setVideoWidth(540);
                setVideoHeight(960);
                break;
            case R540X720:
                setVideoWidth(540);
                setVideoHeight(720);
                break;
            case R720P:
                setVideoWidth(720);
                setVideoHeight(1080);
                break;
            case R720X960:
                setVideoWidth(720);
                setVideoHeight(960);
            default:
                YYLog.error(TAG, "ResolutionType unAvaible");
                break;
        }
    }
}
