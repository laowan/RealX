package com.ycloud.mediaprocess;

import com.ycloud.api.process.MediaInfo;
import com.ycloud.common.Constant;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

public class AudioTranscodeInternal extends MediaBase {

    private  static  final String TAG = AudioTranscodeInternal.class.getSimpleName();

    private double mStartTime = -1;
    private double mTotalTime = -1;

    private String mOutputPath;

    private String mInputPath;

    public AudioTranscodeInternal() {
        super();

        setExcuteCmdId(MediaNative.libffmpeg_cmd_transcode);
    }

    public void setMediaTime(double startTime, double totalTime) {
        if (startTime < 0 || totalTime < 0) {
            return;
        }
        mStartTime = startTime;
        mTotalTime = totalTime;
    }

    public boolean execute() {
        FileUtils.createFile(mOutputPath);
        if (!FileUtils.checkPath(mInputPath) || !FileUtils.checkFile(mOutputPath)) {
            return false;
        }
        String cmd = null;
        if (-1 != mStartTime && -1 != mTotalTime) {
            cmd = "ffmpeg -y -i \"" + mInputPath + "\" -strict -2 -ss " + mStartTime + " -t " + mTotalTime + " \"" + mOutputPath + "\"";
        } else {
            cmd = "ffmpeg -y -i \"" + mInputPath + "\" -strict -2 \"" + mOutputPath + "\"";
        }

        boolean ret = executeCmd(cmd);
        YYLog.info(this,"audioTranscode isSuccessed:" + ret);

        return ret;
    }

    public void transcode(){
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                execute();
            }
        });

    }

    public void setPath(String inputPath, String outputPath) {
        mInputPath = inputPath;
        mOutputPath = outputPath;

        MediaInfo mediaInfo = MediaUtils.getMediaInfo(inputPath);
        if(mediaInfo != null) {
            setTotalFrame(mediaInfo.total_frame);
        }else {
            if(mMediaListener != null) {
                mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "ffprobe error");
            }
        }
    }
}
