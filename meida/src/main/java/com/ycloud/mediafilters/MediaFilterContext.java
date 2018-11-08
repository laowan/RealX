package com.ycloud.mediafilters;
import android.content.Context;

import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.camera.utils.YMRCameraInfo;
import com.ycloud.mediacodec.VideoDecodeType;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.statistics.MediaStats;

/**
 * Created by kele on 2016/11/21.
 */

public class MediaFilterContext implements IMediaFilterContext
{
    public GlManager mGlManager = null;
    public VideoEncoderConfig mVideoEncoderConfig = new VideoEncoderConfig();
    public VideoEncoderConfig mDefaultVideoEncoderConfig = new VideoEncoderConfig();
    private YMRCameraInfo  mYMRCameraInfo = null;


    private VideoDecodeType  mDecodeType = VideoDecodeType.HARD_DECODE;

    private FilterFlowController mFlowController = new FilterFlowController();

    private MediaStats mMediaStats = new MediaStats();

    public void setFilterFlowController(FilterFlowController controller) {
        mFlowController = controller;
    }

    public  FilterFlowController getFilterFlowController() {
        return mFlowController;
    }

    public RecordConfig getRecordConfig() {
        return mRecordConfig;
    }

    public void setRecordConfig(RecordConfig mRecordConfig) {
        this.mRecordConfig = mRecordConfig;
    }

    private RecordConfig   mRecordConfig = null;

    public Context mAndroidContext = null;

    public MediaFilterContext(Context context) {
        mGlManager = new GlManager();
        mGlManager.waitUntilRun();
        mAndroidContext = context;
    }

    public VideoDecodeType getVideoDecodeType() {
        if(RecordDynamicParam.getInstance().getExportSwDecoder()) {
            mDecodeType = VideoDecodeType.FFMPEG_DECODE;
        }
        return  mDecodeType;
    }

    public int getWatermarkTextureID() {
        return -1;
    }

    public MediaStats getMediaStats() {
        return mMediaStats;
    }

    public VideoEncoderConfig getVideoEncoderConfig() {
        return mVideoEncoderConfig;
    }

    public void setVideoEncodeConfig(final VideoEncoderConfig vconfig) {
        if(getGLManager().checkSameThread()) {
            mVideoEncoderConfig = new VideoEncoderConfig(vconfig);
        } else {
            getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    mVideoEncoderConfig = new VideoEncoderConfig(vconfig);
                }
            });
        }
    }

    public VideoEncoderConfig getDefaultVideoEncoderConfig() {
        return mDefaultVideoEncoderConfig;
    }

    public Context getAndroidContext() {
        return mAndroidContext;
    }

    public void setYMRCameraInfo(YMRCameraInfo cameraInfo) {
        mYMRCameraInfo = new YMRCameraInfo(cameraInfo);
    }

    public YMRCameraInfo getYMRCameraInfo() {
        return mYMRCameraInfo;
    }


    public int getDynamicTextureID() {
        return -1;
    }

    public GlManager getGLManager() {
        return mGlManager;
    }
}
