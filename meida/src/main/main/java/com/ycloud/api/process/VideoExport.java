package com.ycloud.api.process;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.common.BlackList;
import com.ycloud.common.GlobalConfig;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.gpuimagefilter.filter.FFmpegFilterSessionWrapper;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.mediafilters.MemInputFilter;
import com.ycloud.mediaprocess.MediaExportSession;
import com.ycloud.mediaprocess.VideoExportInternal;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;


/**
 * 视频导出接口
 */
public class VideoExport {
    private static final String TAG = VideoExport.class.getSimpleName();

    boolean mUserVideoSession = true;
    private VideoExportInternal mVideoExport = null;
    private MediaExportSession mExportSession = null;
    private int mWidth = 540;
    private int mHeight = 960;
    private boolean mIsSaceLocal = false;
    private VideoEncoderConfig mEncodeConf = null;

    private Context mContext;
    private String mSourcePath;
    private String mOutputPath;
    private IMediaListener mMediaListener;

    static {
        try {
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            YYLog.e(TAG, "load so fail");
            e.printStackTrace();
        }
    }

    private void checkUseExportSession() {
        if (TimeEffectParameter.instance().IsExistTimeEffect()) {
            mUserVideoSession = true;
            YYLog.info(TAG, "IsExistTimeEffect  true,  mUserVideoSession = true .");
            return ;
        }

        if (SDKCommonCfg.getUseMediaExportSession()) {
            mUserVideoSession = true;
            YYLog.info(TAG, "force use media export session,mUserVideoSession = true .");
            return;
        }

        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (/*GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1 && */currentapiVersion < android.os.Build.VERSION_CODES.LOLLIPOP) {  // LOLLIPOP Android 5.0
            YYLog.info(TAG, "Android version: " + currentapiVersion + " < Android 5.0, mUserVideoSession = false . ");
            mUserVideoSession = false;
            return;
        }
        DeviceUtil deviceUtil = new DeviceUtil();
        boolean isRoot = deviceUtil.isRoot();
        if (isRoot) {
            YYLog.info(TAG, "Android isRoot " + isRoot + " mUserVideoSession = false .");
            mUserVideoSession = false;
            return;
        }

        String model = DeviceUtil.getPhoneModel();
        if (BlackList.inBlack(model)) {
            YYLog.info(TAG, "Android model " + model + " in BlackList, mUserVideoSession = false .");
            mUserVideoSession = false;
            return;
        }

        if(RecordDynamicParam.getInstance().useFfmpegExport(model)) {
            YYLog.info(TAG, "Android model " + model + " in Dynamical BlackList(server config), mUserVideoSession = false .");
            mUserVideoSession = false;
            return;
        }
    }

    /**
     * 构造函数
     *
     * @param context
     * @param sourcePath  视频输入路径
     * @param outputPath  视频导出添加特效
     * @param videoFilter 视频导出添加特效
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public VideoExport(Context context, String sourcePath, String outputPath, VideoFilter videoFilter) {
        this(context, sourcePath, outputPath, videoFilter, false);
    }

    /**
     * 导出视频到本地的构造方法
     *
     * @param context
     * @param sourcePath  视频输入路径
     * @param outputPath  视频输出路径
     * @param videoFilter 视频导出添加特效
     * @param saveLocal   是否保存本地
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public VideoExport(Context context, String sourcePath, String outputPath, VideoFilter videoFilter, boolean saveLocal) {
        checkUseExportSession();
        if (!mUserVideoSession) {
            if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {
                MemInputFilter filter = new MemInputFilter(null);
                filter.exportAVFromMemToMp4(sourcePath);
            }
            mVideoExport = new VideoExportInternal(context, sourcePath, outputPath, videoFilter);
            if (saveLocal) {
                mVideoExport.setMaxBitRate(GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_BITRATE);
                mVideoExport.setCRF(GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_CRF);
                mVideoExport.setPreset(GlobalConfig.getInstance().getRecordConstant().EXPORT_PRESET);
                mVideoExport.setGop(GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_GOP);
                mVideoExport.setBufsize(Integer.toString(GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_BITRATE * 2));
            } else {
                mVideoExport.setMaxBitRate(GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE);
                mVideoExport.setCRF(GlobalConfig.getInstance().getRecordConstant().EXPORT_CRF);
                mVideoExport.setPreset(GlobalConfig.getInstance().getRecordConstant().EXPORT_PRESET);
                mVideoExport.setGop(GlobalConfig.getInstance().getRecordConstant().EXPORT_GOP);
                mVideoExport.setBufsize(Integer.toString(GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE * 2));
            }

            if (videoFilter != null) {
                mVideoExport.setBgmMusicRhythmInfo(videoFilter.mBackgroundMusicRhythmPath, videoFilter.mBackgroundMusicStart);
            }
        } else {
            int frameRate = GlobalConfig.getInstance().getRecordConstant().EXPORT_FRAME_RATE;
            boolean sourceFileExist = FileUtils.checkPath(sourcePath);
            if (sourceFileExist) {
                MediaInfo mediaInfo = MediaUtils.getMediaInfo(sourcePath);
                if (mediaInfo != null) {
                    frameRate = (int)(mediaInfo.frame_rate + 0.5f);
                }
            }
            mExportSession = new MediaExportSession(context, sourcePath, outputPath);
            VideoEncoderConfig encoderConfig = new VideoEncoderConfig();
            if (VideoDataManager.instance().getFrameRate() != GlobalConfig.getInstance().getRecordConstant().EXPORT_FRAME_RATE) {
                frameRate = VideoDataManager.instance().getFrameRate();
            }
            if (saveLocal) {
                encoderConfig.setBitRate(GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_BITRATE);
                encoderConfig.setFrameRate(frameRate);
                encoderConfig.setGopSize(GlobalConfig.getInstance().getRecordConstant().SAVE_LOCAL_GOP);
            } else {
                encoderConfig.setBitRate(GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE);
                encoderConfig.setFrameRate(frameRate);
                encoderConfig.setGopSize(GlobalConfig.getInstance().getRecordConstant().EXPORT_GOP);
            }
            encoderConfig.setIFrameMode(false);
            encoderConfig.setVideoEncoderType(VideoEncoderType.SOFT_ENCODER_X264);

            if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {
                MediaFormat mVideoMediaFormat = VideoDataManager.instance().getVideoMediaFormat();
                if (mVideoMediaFormat != null) {
                    if (mVideoMediaFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                        mWidth = mVideoMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                    }
                    if (mVideoMediaFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                        mHeight = mVideoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    }
                }
            } else {
                // 本地导入视频，STORE_DATA_IN_MEMORY == 0,这里不改变本地导入视频的分辨率
                MediaInfo mediaInfo = MediaProbe.getMediaInfo(sourcePath, false);
                if (mediaInfo != null) {
                    mWidth = mediaInfo.width;
                    mHeight = mediaInfo.height;

                    mWidth += ((mWidth % 16) == 0 ? 0 : (16 - (mWidth % 16)));
                    mHeight += ((mHeight % 16) == 0 ? 0 : (16 - (mHeight % 16)));

                    encoderConfig.setEncodeSize(mWidth, mHeight);
                }
            }

            //本地导入的视频的分辨率的大小不应该在这里裁剪，应该在导入时候做裁剪.
            //默认使用544 * 960的编码分辨率，是因为大部分手机的屏幕是9:16的，防止编辑视频加的特效等在观看端出现丢失.
            /*if(mWidth > mHeight)
            {
                int width = mWidth, height = mHeight;
                width += ((width % 16) == 0? 0 : (16-(width % 16)));
                height += ((height % 16) == 0? 0 : (16-(height % 16)));

                encoderConfig.setEncodeSize(width, height);
                YYLog.info(TAG, "setEncodeSize mWidth " + width + " height " + height);
            } else if(mWidth < 540 && mHeight < 960) {
                //编码不放大图片， 按照9:16来进行设置编码参数.
                float ratioMax = Math.max(encoderConfig.getEncodeWidth() / mWidth, encoderConfig.getEncodeHeight() / mHeight);
                int fixWidth = Math.round(encoderConfig.getEncodeWidth()/ratioMax);
                int fixHeigth = Math.round(encoderConfig.getEncodeHeight()/ratioMax);

                //编码器16字节对齐.
                fixWidth += ((fixWidth % 16) == 0? 0 : (16-(fixWidth % 16)));
                fixHeigth += ((fixHeigth % 16) == 0? 0 : (16-(fixHeigth % 16)));

                if(fixWidth < encoderConfig.getEncodeWidth() &&  fixHeigth < encoderConfig.getEncodeHeight()) {
                    encoderConfig.setEncodeSize(fixWidth, fixHeigth);
                    YYLog.info(TAG, "setEncodeSize fixWidth " + fixWidth + " fixHeight " + fixHeigth);
                }
            }*/

            //encoderConfig.setEncodeSize(mWidth, mHeight);
            YYLog.info(this, "[VideoExport] video size: width-"+mWidth + " height-"+mHeight
                    + " EncodeWidth - " + encoderConfig.getEncodeWidth() + " EncodeHeight - " + encoderConfig.getEncodeHeight());

            mExportSession.setVideoEncodeConfig(encoderConfig);
            if (videoFilter != null) {
                mExportSession.setVideoVolume(videoFilter.mVideoVolume);
                mExportSession.setBgmMusicPath(videoFilter.getExportBgm(), videoFilter.mMusicVolume);
                mExportSession.setMagicAudioPath(videoFilter.getMagicAudioFilePath());
                mExportSession.setBgmMusicRhythmInfo(videoFilter.mBackgroundMusicRhythmPath, videoFilter.mBackgroundMusicStart);
            }

            mEncodeConf = encoderConfig;
        }

        mIsSaceLocal = saveLocal;

        mContext = context;
        mSourcePath = sourcePath;
        mOutputPath = outputPath;
    }

    /**
     * 获取ffmpeg session接口
     */
    public FFmpegFilterSessionWrapper getFFmpegFilterSessionWrapper() {
        if (!mUserVideoSession) {
            return mVideoExport.getFFmpegFilterSessionWrapper();
        } else {
            return mExportSession.getmExportGpuFilterSessionWrapper();
        }
    }

    /**
     * 视频导出
     */
    public void export() {
        mContext = null;
        mSourcePath = null;
        mOutputPath = null;

        if (!mUserVideoSession) {
            mVideoExport.export();
        } else {
            mExportSession.startExport();
        }
    }

    /**
     * 视频导出，保持较高质量
     */
    public void exportWithHighQlty() {
        YYLog.info(this, "[export] exportWithHighQlty");
        if (!mUserVideoSession) {
            mVideoExport.setCRF(1);
            mVideoExport.mResetFrameRate = false;
        } else {
            mExportSession.setVideoEncodeHighQuality();
        }
        export();
    }

    /**
     * 设置x264软编恒定质量编码模式的视频编码质量值，范围【15~30】, 内部默认值23.
     * quality值加6，输出码率大概减少一半；quality值减6，输出码率翻倍.
     * 从主观上讲，18~28是一个合理的范围，18往往被认为从视觉上看是近似无损的,码率较高
     * @param quality
     */
    public void setExportVideoQuality(float quality) {
        YYLog.info(TAG, "setExportVideoQuality " + quality);
        if (quality < 15 || quality > 30) {
            YYLog.warn(TAG, "quality not available : " + quality);
            return;
        }
        if (mExportSession != null ) {
            mExportSession.setVideoQuality(quality);
        }
    }

    /**
     * 设置导出视频的最高编码码率，单位兆（M)
     * @param bitrate 导出视频的最高码率
     */
    public void setMaxExportBitrate(float bitrate) {
        YYLog.info(TAG, "setMaxExportBitrate " + bitrate + " (Mb)");
        if (mExportSession != null) {
            mExportSession.setMaxExportBitrate((int)(bitrate * 1024 * 1024));
        }
    }

    /**
     * 释放
     */
    public void release() {
        if (!mUserVideoSession) {
            mVideoExport.release();
        } else {
            mExportSession.release();
        }
    }

    /**
     * 设置进度及错误监听
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mMediaListener = listener;
        if (!mUserVideoSession) {
            mVideoExport.setMediaListener(listener);
        } else {
            mExportSession.setMediaListener(listener);
        }
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        if (!mUserVideoSession) {
            mVideoExport.setMediaInfoRequireListener(listener);
        } else {
            mExportSession.setMediaInfoRequireListener(listener);
        }
    }

    /**
     * 取消导出操作
     */
    public void cancel() {
        if (!mUserVideoSession) {
            mVideoExport.cancel();
        } else {
            mExportSession.cancel();
        }
        //TODO[lsh]
    }

    /**
     * 设置导出的视频比例
     * 目前规则是，以长的一边为准来调整
     *
     * @param ratio 导出视频的比例
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void setExportSize(float ratio) {
        YYLog.info(TAG, "setExportSize ratio=" + ratio);
        if (mWidth >= mHeight) {
            mHeight = (int) (mWidth / ratio);
        } else {
            mWidth = (int) (mHeight * ratio);
        }

        if (mEncodeConf != null) {
            mEncodeConf.setEncodeSize(mWidth, mHeight);
            mExportSession.setVideoEncodeConfig(mEncodeConf);
        }
    }
}
