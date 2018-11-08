package com.ycloud.mediaprocess;

import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.utils.YYLog;

/**
 * Created by DZHJ on 2017/6/5.
 */

public class VideoReverse extends MediaBase {
    private String  mVideoPath;
    private String mOutputPath;

    private long mMaxBitRate = 2500000;
    private String mPreset = "superfast";
    private long mBufsize = 5000000;
    private int mCRF = 21;

    public void setVideoPath(String sourcePath,String outputPath){
        mVideoPath =sourcePath;
        mOutputPath = outputPath;
    }

    public void reverse(){
        StringBuilder cmd = new StringBuilder();
        cmd.append("ffmpeg -y -noautorotate -i " + mVideoPath );
        cmd.append(" -movflags faststart -strict -2 -profile:v high");
        cmd.append(" -maxrate " + mMaxBitRate);
        cmd.append(" -bufsize " + mBufsize);
        cmd.append(" -crf " + mCRF);
        cmd.append(" -preset " + mPreset);
        cmd.append(" -g 1 -c copy -vf reverse -vcodec libx264 ");
        cmd.append(mOutputPath);

        YYLog.info(this,"VideoReverse:" + cmd.toString());

        executeCmd(cmd.toString());
    }
}
