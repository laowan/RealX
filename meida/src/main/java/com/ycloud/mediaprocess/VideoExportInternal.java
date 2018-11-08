package com.ycloud.mediaprocess;

import android.content.Context;
import android.text.TextUtils;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.common.TransitionInfo;
import com.ycloud.api.process.IMediaListener;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.common.Constant;
import com.ycloud.common.GlobalConfig;
import com.ycloud.gpuimagefilter.filter.FFmpegFilterSessionWrapper;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.VideoGpuFilter;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class VideoExportInternal extends MediaBase {

    public static final String TAG = VideoExportInternal.class.getSimpleName();

    private VideoFilter mVideoFilter = null;
    private long mMaxBitRate;
    private int mCRF;
    private int mGop;

    private String mOutputPath;
    private String mInputPath;
    private String mCacheDir;
    private String mPreset;
    private String mBufsize;

    private Context mContext;

    private VideoGpuFilter mVideoGpuFilter = null;
    private FFmpegFilterSessionWrapper mFFmpegFilterSessionWrapper = null;
    private int mFinalFps = GlobalConfig.getInstance().getRecordConstant().EXPORT_FRAME_RATE;

    //是否需要冲着frame rate，默认重置为30，保证流畅度，某些场景下保持导出前视频的frame rate
    public boolean mResetFrameRate = false;

    public VideoExportInternal(Context context, String sourcePath, String outputPath, VideoFilter videoFilter) {
        super();
        mContext = context;
        mInputPath = sourcePath;
        mOutputPath = outputPath;
        mVideoFilter = videoFilter;

        setExcuteCmdId(MediaNative.libffmpeg_cmd_video_effect);
        mCacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
        mFFmpegFilterSessionWrapper = new FFmpegFilterSessionWrapper();

        MediaInfo mediaInfo = MediaUtils.getMediaInfo(sourcePath);
        if (mediaInfo != null) {
            setTotalFrame(mediaInfo.total_frame);
            VideoProcessTracer.getInstace().setFrameRate((int) (mediaInfo.frame_rate));

            if ((int) mediaInfo.frame_rate < 25 || ((int) mediaInfo.frame_rate > 60)) {
                mResetFrameRate = true;
            }

            if (mediaInfo.v_rotate == 90.0 || mediaInfo.v_rotate == -270.0 || mediaInfo.v_rotate == -90 || mediaInfo.v_rotate == 270) {
                mVideoGpuFilter = new VideoGpuFilter(mediaInfo.height, mediaInfo.width, mContext, mFFmpegFilterSessionWrapper.getSessionID(), videoFilter != null ? videoFilter.getTransitionList() : null);
            } else {
                mVideoGpuFilter = new VideoGpuFilter(mediaInfo.width, mediaInfo.height, mContext, mFFmpegFilterSessionWrapper.getSessionID(), videoFilter != null ? videoFilter.getTransitionList() : null);
            }

            if (videoFilter != null) {
                mVideoGpuFilter.setBgmMusicRhythmInfo(videoFilter.mBackgroundMusicRhythmPath, videoFilter.mBackgroundMusicStart);
            }

            setVideoGpuFilter(mVideoGpuFilter);
        } else {
            YYLog.e(TAG, "mediaInfo is null sourcePath:" + sourcePath);
        }

        YYLog.info(this, "VideoExportInternal end!");
    }

    @Override
    public void release() {
        super.release();

        mFFmpegFilterSessionWrapper = null;
    }

    /**
     * 获取ffmpeg filter session
     */
    public FFmpegFilterSessionWrapper getFFmpegFilterSessionWrapper() {
        return mFFmpegFilterSessionWrapper;
    }

    /**
     * @param bitRate the mMaxBitRate to set
     */
    public void setMaxBitRate(int bitRate) {
        mMaxBitRate = bitRate;
    }

    public void setBgmMusicRhythmInfo(String path, int start) {
        if (mVideoGpuFilter != null && path != null && !path.isEmpty()) {
            mVideoGpuFilter.setBgmMusicRhythmInfo(path, start);
        }
    }

    public void setGop(int gop) {
        mGop = gop;
    }

    public void setPreset(String preset) {
        mPreset = preset;
        VideoProcessTracer.getInstace().setPreset(mPreset);
    }

    public void setBufsize(String bufsize) {
        mBufsize = bufsize;
    }

    private boolean execute() {
        return filterVideoExport();
    }

    private void processAudio(MediaInfo mediaInfo) {
        String bgmPath = null;
        String magicAudioPath = null;
        String pureAudioPath = mCacheDir + "pureAudio.wav";
        int audioCount = 0;
        if (mVideoFilter != null) {
            bgmPath = mVideoFilter.getExportBgm();
            magicAudioPath = mVideoFilter.getMagicAudioFilePath();
        }
        if (bgmPath == null && magicAudioPath == null) {
            return;
        }
        if (bgmPath != null) {
            audioCount++;
        }
        if (magicAudioPath != null) {
            audioCount++;
        }

        boolean ret;
        if (mediaInfo.audio_codec_name != null) {
            AudioProcessInternal audioProcessInternal = new AudioProcessInternal();
            //extract audio from recorded video
            ret = audioProcessInternal.extractAudioTrack(mInputPath, pureAudioPath);
            if (ret == true) {
                audioCount++;
            }else {
                pureAudioPath = null;
            }
        }else {
            pureAudioPath = null;
        }
        //Reset bgm first
        mVideoFilter.setExportBgm(null);
        String transcodeAudioPath = mCacheDir + "transcodeAudio.wav";
        if (audioCount > 1) {
            //Should mix audio
            AudioMixInternal audioMixInternal = new AudioMixInternal();
            String mixAudioPath = mCacheDir + "mixAudio.wav";
            audioMixInternal.setOutputPath(mixAudioPath);
            boolean needClip = true;

            if (pureAudioPath != null) {
                audioMixInternal.addAudioMixBean(pureAudioPath, 0, 0, mVideoFilter.VIDEO_VOLUME_TIMES * mVideoFilter.mVideoVolume);
                needClip = false;
            }
            if (bgmPath != null) {
                audioMixInternal.addAudioMixBean(bgmPath, 0, 0, mVideoFilter.mMusicVolume);
            }

            if (magicAudioPath != null) {
                audioMixInternal.addAudioMixBean(magicAudioPath, 0, 0, 1);
            }
            ret = audioMixInternal.executeInternal();

            if (ret == false) {
                YYLog.error(TAG, "AudioMixInternal fail!");
            }else {
                if (needClip) {
                    audioToWav(mixAudioPath, transcodeAudioPath, mediaInfo.video_duration);
                    mVideoFilter.setExportBgm(transcodeAudioPath);
                }else {
                    mVideoFilter.setExportBgm(mixAudioPath);
                }
            }
        }else {
            ret = false;
            if (bgmPath != null) {
                ret = audioToWav(bgmPath, transcodeAudioPath, mediaInfo.video_duration);
            }
            if (magicAudioPath != null) {
                ret = audioToWav(magicAudioPath, transcodeAudioPath, mediaInfo.video_duration);
            }
            if (ret) {
                mVideoFilter.setExportBgm(transcodeAudioPath);
            }
        }
    }

    private boolean filterVideoExport() {
        FileUtils.createFile(mOutputPath);
        String cmd;

        MediaInfo mediaInfo = MediaUtils.getMediaInfo(mInputPath);
        if (mediaInfo == null) {
            YYLog.error(TAG, "filterVideoExport media probe return null, mInputPath:" + mInputPath);
            if(mMediaListener != null) {
                mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "media probe returns null");
            }
            return false;
        }

        processAudio(mediaInfo);

        String cmdStart;
        if (mVideoFilter != null && mVideoFilter.getTransitionList() != null) {
            cmdStart = "ffmpeg -y ";
            List<TransitionInfo> vFilterTransitionList = mVideoFilter.getTransitionList();
            for (TransitionInfo transitionInfo : vFilterTransitionList) {
                cmdStart += "-i \"" + transitionInfo.mVideoPath + "\" ";
            }
        } else {
            cmdStart = "ffmpeg -y -i " + "\"" + mInputPath + "\" ";
        }

        if (null != mVideoFilter) {
            if (!TextUtils.isEmpty(mVideoFilter.getExportBgm())) {
                cmdStart += "-i " + "\"" + mVideoFilter.getExportBgm() + "\" ";
            }
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        Date curDate = new Date(System.currentTimeMillis());
        String timeStr = formatter.format(curDate);
        VideoProcessTracer.getInstace().setExportTime(timeStr);

        StringBuilder sbCmdEnd = new StringBuilder();
        sbCmdEnd.append(" -movflags faststart");

        if (null != mVideoFilter && mVideoFilter.isAudioDisabled()) {
            sbCmdEnd.append(" -an");
        }
        sbCmdEnd.append(" -strict -2 -vcodec libx264 -c:a libfdk_aac -ar 44100 -profile:v high");
        if (mGop > 0) {
            sbCmdEnd.append(" -g " + mGop);
        }
        sbCmdEnd.append(" -maxrate " + mMaxBitRate);
        sbCmdEnd.append(" -bufsize " + mBufsize);
        sbCmdEnd.append(" -crf " + mCRF);
        sbCmdEnd.append(" -preset " + mPreset);
        String comment = VideoProcessTracer.getInstace().generateComment() + "[ffmpeg_export]";
        VideoProcessTracer.getInstace().reset();
        if (comment != null) {
            sbCmdEnd.append(" -metadata comment=" + comment);
        }

        sbCmdEnd.append(" -max_muxing_queue_size 9999");

        if(mResetFrameRate) {
            sbCmdEnd.append(" -r " + mFinalFps);
        }

        sbCmdEnd.append(" \"" + mOutputPath + "\"");

        String cmdEnd = sbCmdEnd.toString();

        if (null != mVideoFilter) {
            if (!TextUtils.isEmpty(mVideoFilter.getExportBgm())) {
                cmdEnd = " -map 1:a -map 0:v " + cmdEnd;
            } else {
                int vol = (int) (100 * mVideoFilter.getmVideoVolume());
                if (vol != 100) {
                    cmdStart = cmdStart + " -vol " + vol;
                }
            }


            String filterComplexString = getFilterComplexString();
            if (filterComplexString != null) {
                cmd = cmdStart + filterComplexString + cmdEnd;
            } else {
                cmd = cmdStart + cmdEnd;
            }
        } else {
            cmd = cmdStart + cmdEnd;
        }

        return executeCmd(cmd);
    }

    public void setCRF(int crf) {
        VideoProcessTracer.getInstace().setCrf(crf);
        mCRF = crf;
    }

    public void export() {
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                execute();
            }
        });
    }

    private boolean audioToWav(String inputPath, String outputPath, double duration) {
        AudioTranscodeInternal audioTranscode = new AudioTranscodeInternal();
        audioTranscode.setMediaListener(new IMediaListener() {
            @Override
            public void onProgress(float progress) {
            }

            @Override
            public void onError(int errType, String errMsg) {
                if (mMediaListener != null) {
                    mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "audioToWav error," + errMsg);
                }
            }

            @Override
            public void onEnd() {

            }
        });

        audioTranscode.setPath(inputPath, outputPath);
        audioTranscode.setMediaTime(0, duration);
        boolean ret = audioTranscode.execute();

        return ret;
    }


    private String getFilterComplexString() {
        StringBuilder filterComplexSb = new StringBuilder();
        addTransitionFilter(filterComplexSb, mVideoFilter);

        String cmd = filterComplexSb.toString();
        if (TextUtils.isEmpty(cmd))
            return null;

        int index = cmd.lastIndexOf(",");
        if (-1 != index)
            cmd = cmd.substring(0, index);

        cmd = " -filter_complex \"" + cmd + "\" ";
        return cmd;
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        if (mVideoGpuFilter != null) {
            mVideoGpuFilter.setMediaInfoRequireListener(listener);
        }
    }

    private static void addTransitionFilter(StringBuilder cmdMiddle, VideoFilter videoFilter) {
        if (videoFilter.getTransitionList() != null) {
            for (int i = 0; i < videoFilter.getTransitionList().size(); i++) {
                if (i == 0) {
                    cmdMiddle.append("[" + i + ":v]setpts=PTS-STARTPTS[v" + i + "];");
                } else {
                    float unityTransitionStartPts = 0.0f;
                    int j = 1;
                    while (j <= i) {
                        unityTransitionStartPts += videoFilter.getTransitionList().get(j - 1).mVideoDuration;
                        unityTransitionStartPts -= videoFilter.getTransitionList().get(j).mTransitionDuration;
                        j++;
                    }
                    cmdMiddle.append("[" + i + ":v]setpts=PTS-STARTPTS+" + unityTransitionStartPts + "/TB[v" + i + "];");
                }
            }

            for (int i = 0; i < videoFilter.getTransitionList().size(); i++) {
                cmdMiddle.append("[v" + i + "]");
                if (i == 1) {
                    cmdMiddle.append("overlay,format=yuv420p");
                    if (i < (videoFilter.getTransitionList().size() - 1)) {
                        cmdMiddle.append("[ov0];");
                    } else {
                        cmdMiddle.append(",");
                    }
                } else if (i > 1 && i != (videoFilter.getTransitionList().size() - 1)) {
                    cmdMiddle.append("[ov" + (i - 2) + "]");
                    cmdMiddle.append("overlay,format=yuv420p");

                    if (i < (videoFilter.getTransitionList().size() - 1)) {
                        cmdMiddle.append("[ov" + (i - 1) + "];");
                    } else {
                        cmdMiddle.append(",");
                    }
                }
            }
        }
    }
}
