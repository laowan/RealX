package com.ycloud.mediaprocess;

import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;

public class ImagesSpliceVideo extends MediaBase {
    private String mOutputFile;
    private String mOutputConfigFile;
    private static final int mDefaultFrameRate = 25;
    private int mFrameRate = -1;
    private int mGop = -1;
    public ImagesSpliceVideo() {
        super();
        setExcuteCmdId(MediaNative.libffmpeg_cmd_video_concat);
    }

    public void setOutputFile(String outputFile) {
        mOutputFile = outputFile;
    }
    public void setConfigFile(String path) {
        mOutputConfigFile = path;
    }
    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }
    public void setGop(int gop) {
        mGop = gop;
    }

    public boolean excuteSync() {
        return execute();
    }
    protected boolean execute() {

        StringBuilder cmd = new StringBuilder();
        cmd.append("ffmpeg -f concat -y ");
        cmd.append("-safe 0 -i " + mOutputConfigFile + " ");
        if (mFrameRate > 0) {
            cmd.append("-vf fps=" + mFrameRate + " ");
        } else {
            cmd.append("-vf fps=" + mDefaultFrameRate + " ");
        }
        cmd.append("-c:v libx264 ");
        cmd.append("-crf 18 ");
        //cmd.append("-vf " + "\"scale=" + mWidth + ":" + mHeight + "\" ");// 图片前期处理已做好裁剪
        cmd.append("-pix_fmt yuv420p ");
        if (mGop > 0) {
            cmd.append("-g " + mGop + " ");
        }
        cmd.append(mOutputFile);

        return executeCmd(cmd.toString());
    }

    public void setTotalFrameCount(int totalFrame) {
       setTotalFrame(totalFrame);
    }
}
