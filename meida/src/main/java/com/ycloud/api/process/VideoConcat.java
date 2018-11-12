package com.ycloud.api.process;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;
import com.ycloud.common.Constant;
import com.ycloud.common.GlobalConfig;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.mediaprocess.AudioTranscodeInternal;
import com.ycloud.mediaprocess.VideoConcatBase;
import com.ycloud.mediaprocess.VideoConcatNeedEncodeAndDecode;
import com.ycloud.mediaprocess.VideoConcatWithoutEncodeAndDecode;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by dzhj on 17/5/3.
 */

public class VideoConcat {
    public static final String TAG = VideoConcat.class.getSimpleName();
    public final static int YMRConcatEncModeCopy = 0;
    public final static int YMRConcatEncModeX264 = 1;

    private VideoConcatBase mVideoConcat;

    private final String AUDIO_TRANSCODE_TEMP_PATH;
    private String mBackgroundMusicPath;
    private int mBackgroundMusicStart;
    private AudioTranscodeInternal mAudioTranscode;
    private IMediaListener mMediaListener;
    private final ArrayList<String> mVideosPathArray;
    public static final float ADELAY_TIME = 0.2f;

    public VideoConcat(Context context, final ArrayList<String> videosPathArray, final String outputFile) {
        String cacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
        mVideosPathArray = videosPathArray;
        mVideoConcat = new VideoConcatWithoutEncodeAndDecode(cacheDir, videosPathArray, outputFile);
        AUDIO_TRANSCODE_TEMP_PATH = cacheDir + "audioTemp.wav";
    }

    public VideoConcat(Context context, final ArrayList<String> videosPathArray, final String outputFile, int mode) {
        String cacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
        mVideosPathArray = videosPathArray;
        if (mode == YMRConcatEncModeCopy) {
            mVideoConcat = new VideoConcatWithoutEncodeAndDecode(cacheDir, videosPathArray, outputFile);
        } else if (mode == YMRConcatEncModeX264) {
            mVideoConcat = new VideoConcatNeedEncodeAndDecode(videosPathArray, outputFile);
        }

        AUDIO_TRANSCODE_TEMP_PATH = cacheDir + "audioTemp.wav";
    }

    public void setBackgroundMusic(String backgroundMusicPath) {
        mBackgroundMusicPath = backgroundMusicPath;
//        mVideoConcat.setBackgroundMusic(backgroundMusicPath);
    }

    public void setBackgroundMusicStart(int start) {
        YYLog.info(TAG, "setBackgroundMusicRange start:" + start);
        mBackgroundMusicStart = start;
    }

    public void setMediaListener(IMediaListener listener) {
        mMediaListener = listener;
        mVideoConcat.setMediaListener(listener);
    }

    public void cancel() {
        mVideoConcat.cancel();
    }

    public void release() {
        mVideoConcat.release();
    }

    public void execute() {
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                executeInternal();
            }
        });
    }

    public void executeInternal() {
        if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {   // 音视频数据存内存方式不用做合成
            mMediaListener.onEnd();
            YYLog.info(TAG, "jtzhu video concat end ...");
            return;
        }

        long begin = System.currentTimeMillis();
        if (mBackgroundMusicPath != null) {
            mAudioTranscode = new AudioTranscodeInternal();
            mAudioTranscode.setMediaListener(new IMediaListener() {
                @Override
                public void onProgress(float progress) {
                }

                @Override
                public void onError(int errType, String errMsg) {
                    mMediaListener.onError(errType, errMsg);
                }

                @Override
                public void onEnd() {

                }
            });

            mAudioTranscode.setPath(mBackgroundMusicPath, AUDIO_TRANSCODE_TEMP_PATH);
            double videoDuration = getVideosDuration();
            if (videoDuration < ADELAY_TIME) {
                return;
            }
            mAudioTranscode.setMediaTime((double) mBackgroundMusicStart / 1000, (videoDuration - ADELAY_TIME));
            boolean ret = mAudioTranscode.execute();
            if (!ret) {
                YYLog.info(this, "wav transcode failed");
                if (mMediaListener != null) {
                    mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "wav transcode failed");
                }
                return;
            }
            mVideoConcat.setBackgroundMusic(AUDIO_TRANSCODE_TEMP_PATH);
        }

        mVideoConcat.setMediaNativeProgressIntervalTime(100);
        mVideoConcat.concatVideos();

        if (mBackgroundMusicPath != null) {
            File file = new File((AUDIO_TRANSCODE_TEMP_PATH));
            file.delete();
        }
        YYLog.info(this, "concat cost time " + (System.currentTimeMillis() - begin) / 1000.0);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private double getVideosDuration() {
        double ret = 0;
        if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY != 1) {
            if (mVideosPathArray != null && mVideosPathArray.size() > 0) {
                for (int i = 0; i < mVideosPathArray.size(); i++) {
                    MediaInfo mediaInfo = MediaProbe.getMediaInfo(mVideosPathArray.get(i), true);
                    if (mediaInfo == null) {
                        YYLog.info(this, "getVideosDuration mediaprobe error path:" + mVideosPathArray.get(i));
                        if (mMediaListener != null) {
                            mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "getVideosDuration mediaprobe error path:" + mVideosPathArray.get(i));
                        }
                        ret = -1;
                        break;
                    }
                    ret += mediaInfo.duration;
                }
            }
        } else {
            MediaFormat format = VideoDataManager.instance().getVideoMediaFormat();
            if (format != null && format.containsKey(MediaFormat.KEY_DURATION)) {
                ret = format.getLong(MediaFormat.KEY_DURATION);
            }
        }
        return ret;
    }
}
