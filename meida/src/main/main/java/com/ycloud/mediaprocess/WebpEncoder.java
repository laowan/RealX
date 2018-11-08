package com.ycloud.mediaprocess;

import com.ycloud.api.process.IMediaListener;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.utils.MediaUtils;

/**
 * Created by DZHJ on 2017/2/7.
 */

public class WebpEncoder extends MediaBase{

    private int  mFrameRate=3;
    private int mWidth = 100;
    private int mHeight=100;
    private String mSourcePath = null;
    private String mOutputPath = null;
    private int mImageQuality =75;

    IMediaListener mMediaListener;

    public WebpEncoder(){
        setExcuteCmdId(MediaNative.libffmpeg_cmd_transcode);
    }

    public boolean encode() {
        StringBuilder sb =new StringBuilder();
        sb.append("ffmpeg -i "+mSourcePath +" ");
        sb.append("-s "+mWidth+"x"+mHeight+" ");
        sb.append("-r "+ mFrameRate+" ");
        sb.append("-qscale "+ mImageQuality+" ");
        sb.append(mOutputPath);
        String cmd =sb.toString();

        boolean isSuccess = executeCmd(cmd);

        return isSuccess;
    }

    public void setDimensions(int width, int height) {
        mWidth =width;
        mHeight = height;
    }

    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public void setPath(String sourcePath, String outputPath) {
        mSourcePath=sourcePath;
        mOutputPath = outputPath;
        MediaInfo mediaInfo = MediaUtils.getMediaInfo(mSourcePath);
        setTotalFrame(mediaInfo.total_frame);
    }

    public void setImageQuality(int imageQuality){
        mImageQuality = imageQuality;
    }

    public void setMediaListener(IMediaListener mediaListener){
        mMediaListener = mediaListener;
    }
}
