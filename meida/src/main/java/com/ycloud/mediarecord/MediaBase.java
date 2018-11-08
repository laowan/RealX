package com.ycloud.mediarecord;

import android.os.Bundle;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.process.IMediaListener;
import com.ycloud.common.Constant;
import com.ycloud.utils.YYLog;

import java.util.concurrent.atomic.AtomicBoolean;

public class MediaBase {
    private String TAG = "MediaBase";
    private MediaNative mMediaNative;
    private int mTotalFrame = 0;
    protected IMediaListener mMediaListener = null;

    private final AtomicBoolean misCmdExecuting= new AtomicBoolean(false);
    private int mExcuteCmdId = 0;
    private boolean mIsCancel = false;
    private float initProgress = 0.0f;
    private IFfmpegCallback mFfmpegCallBack  = new IFfmpegCallback() {
        @Override
        public void onCallback(Bundle b) {
              processEvent(b);
        }
    };

    public MediaBase(){
        mMediaNative = new MediaNative();
    }

    public void setInitializeProgress(float progress) {
        initProgress = progress;
    }
    // Unit : Ms
    public void setMediaNativeProgressIntervalTime(long time) {
        mMediaNative.mediaSetProgressIntervalNative(time * 1000);
    }

    private void processEvent(Bundle b) {
        float progress = 0.00f;
        int frame_num = b.getInt("frame_num", -1);
        if (0 != mTotalFrame)
            progress = frame_num / (float) mTotalFrame;
        b.putFloat("progress", progress);
        if (progress < 1.0) {
            // frame_num最后会返回帧相同帧数，导致porgress会连续出现几个1.0,故屏蔽掉它，在mediaProcessNative方法有返回值才回调mEfeectListener.onProgress(1.0f);
            if (null != mMediaListener) {

                if (Float.compare(initProgress, 0.0f) != 0) {
                    float progressRatio = (1.0f - initProgress)/1.0f;
                    progress = progress * progressRatio + initProgress;
                }

                YYLog.info(TAG, " onProgress : " + progress);
                mMediaListener.onProgress(progress);
            }
        }
    }

    protected boolean executeCmd(String cmd) {
        mIsCancel = false;
        boolean isSuccess = false;

        if (cmd.length() > 3000) {
            for (int i = 0; i < cmd.length(); i += 3000) {
                if (i + 3000 < cmd.length())
                    YYLog.info(TAG, "execute cmd" + i + ":" + cmd.substring(i, i + 3000));
                else
                    YYLog.info(TAG, "execute cmd" + i + ":" + cmd.substring(i, cmd.length()));
            }
        } else {
            YYLog.info(TAG, "execute cmd:" + cmd);
        }

        if (SDKCommonCfg.getRecordModePicture()) {   // 进度回调时间间隔 20 ms
            mMediaNative.mediaSetProgressIntervalNative(20000);
        }

        try {
            int result = Integer.parseInt(mediaProcess(cmd));
            isSuccess = (result == 0);
            if (!mIsCancel && mMediaListener != null) {
                switch (result) {
                    case Constant.MediaNativeResult.FFMPEG_EXECUTE_SUCCESS:
                        YYLog.info(TAG, "execute cmd success");
                        mMediaListener.onEnd();
                        break;
                    case Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL:
                        YYLog.error(TAG, "execute cmd fail");
                        mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "execute cmd fail");
                        break;
                    case Constant.MediaNativeResult.FFMPEG_PRE_CMD_RUNNING:
                        YYLog.warn(TAG, "another cmd is running!!!");
                        mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_PRE_CMD_RUNNING, "another cmd is running!!!");
                        break;
                    case Constant.MediaNativeResult.FFMPEG_EXECUTE_ERROR:
                        YYLog.error(TAG, "execute cmd error");
                        mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_ERROR, "execute cmd error");
                        break;
                }
            }
        } catch (NumberFormatException e) {
            YYLog.error(TAG, "execute cmd fail with unknown result");
            if (mMediaListener != null) {
                mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "execute cmd fail with unknown result");
            }
        }

        return isSuccess;
    }

    private String mediaProcess(String cmd) {
        String result;

        long wait_time = 1000;           // 等待其他任务执行完超时时间 1 秒
        while (isFFMpegRunning()) {      // 性能比较差的手机（美图M4)，其他ffmpeg任务还在跑，等待
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
            }
            YYLog.error(TAG, "Another FFmpeg task is running! Wait ...  cmd: " + cmd);
            wait_time -= 10;
            if (wait_time <= 0) {
                YYLog.error(TAG, "Another FFmpeg task is running! Wait 1000 ms Timeout !!!");
                return String.valueOf(Constant.MediaNativeResult.FFMPEG_PRE_CMD_RUNNING);
            }
        }


        synchronized (misCmdExecuting) {
            mMediaNative.resetMediaProcessNative();
        }
        if (misCmdExecuting.compareAndSet(false,true)) {
            mMediaNative.setFfmpegCallback(mFfmpegCallBack);
            result = mMediaNative.mediaProcessNative(mExcuteCmdId, cmd);
            mMediaNative.setFfmpegCallback(null);
            misCmdExecuting.set(false);
        } else {
            result = Integer.toString(Constant.MediaNativeResult.FFMPEG_PRE_CMD_RUNNING);
        }
        synchronized (misCmdExecuting) {
            mMediaNative.resetMediaProcessNative();
        }

        return result;
    }

    public void release() {
        mMediaNative.release();
    }

    public void cancel() {
        synchronized (misCmdExecuting) {
            YYLog.info(TAG, "cancelMediaProcessNative in" + this.getClass().getSimpleName());
            mMediaNative.cancelMediaProcessNative();
        }
        mIsCancel = true;
    }

    public boolean isFFMpegRunning() {
        return mMediaNative.mediaIsFFmpegRunningNative();
    }

    public boolean isFFMpegProcessCancelled() {
        synchronized (misCmdExecuting) {
            return mMediaNative.mediaIsFFmpegProcessCancelledNative();
        }
    }

    /**
     * 设置进度及错误监听
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mMediaListener = listener;
    }

    protected void setExcuteCmdId(int cmdId) {
        mExcuteCmdId = cmdId;
    }

    protected void setTotalFrame(int totalFrame) {
        mTotalFrame = totalFrame;
    }

    public void setVideoGpuFilter(VideoGpuFilter videoGpuFilter) {
        mMediaNative.setVideoGpuFilter(videoGpuFilter);
    }
}
