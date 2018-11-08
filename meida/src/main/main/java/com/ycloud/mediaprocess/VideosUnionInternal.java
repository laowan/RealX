package com.ycloud.mediaprocess;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.common.Constant;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.YYLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by DZHJ on 2017/6/1.
 */

public class VideosUnionInternal extends MediaBase{

    public static final String TAG = VideosUnionInternal.class.getSimpleName();

    private int mTotalWidth;
    private int mTotalHeight;

    private String mOutputVideoPath;

    private List<VideoOverlayInfo> mVideoOverlayInfoList;
    private List<WaterMarkInfo> mWaterMarkInfoList;

    private String mBackgroundColor = "#313131";

    private long mMaxBitRate = 2500000;
    private String mPreset = "veryfast";
    private long mBufsize = 5000000;
    private int mMaxFps = 30;

    private int mCRF = 1;

    private MediaInfo mMediaInfo = null;

    public VideosUnionInternal(){
        setExcuteCmdId(MediaNative.libffmpeg_cmd_video_concat);
    }

    public void setVideos(List<VideoOverlayInfo> videoOverlayInfoList,String outputVideoPath){
        mVideoOverlayInfoList = videoOverlayInfoList;
        mOutputVideoPath = outputVideoPath;

        if(videoOverlayInfoList != null && videoOverlayInfoList.size()>0) {
            mMediaInfo = MediaProbe.getMediaInfo(videoOverlayInfoList.get(0).mVideoPath,true);
            if(mMediaInfo != null) {
                setTotalFrame(mMediaInfo.total_frame);
            } else {
                YYLog.error(this,"mediaprobe ret null");
                mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "mediaprobe ret null");
            }
        }
    }

    public void setWaterMark(List<WaterMarkInfo> waterMarkInfoList){
        mWaterMarkInfoList = waterMarkInfoList;
    }

    public void setTotalResolution(int totalWidth,int totalHeight){
        mTotalWidth = totalWidth;
        mTotalHeight = totalHeight;
    }

    public void setBackgroundColor(String backgroundColor) {
        mBackgroundColor = backgroundColor;
    }

    public void unionWithExport() {
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                executeWithExport();
            }
        });
    }

    private boolean executeWithExport() {
        StringBuilder cmdInput = new StringBuilder();
        StringBuilder cmdFilterComplex = new StringBuilder();

        cmdInput.append("ffmpeg -y ");

        cmdFilterComplex.append(" -filter_complex \"");


        for(int i=0;i<mVideoOverlayInfoList.size();i++){
            VideoOverlayInfo videoOverlayInfo = mVideoOverlayInfoList.get(i);
            cmdInput.append(" -i " + videoOverlayInfo.mVideoPath);
            cmdFilterComplex.append("["+i+":v] setpts=PTS-STARTPTS, scale=" + videoOverlayInfo.mWidth+"x"+videoOverlayInfo.mHeight + " [v"+i+"];");
        }

        cmdFilterComplex.append("[v0] pad=" + mTotalWidth+":"+mTotalHeight + ":"+mVideoOverlayInfoList.get(0).mOverlayX+":"+mVideoOverlayInfoList.get(0).mOverlayY+":"+mBackgroundColor+" [ov0];");


        for (int i = 1; i < mVideoOverlayInfoList.size(); i++) {
            VideoOverlayInfo videoOverlayInfo = mVideoOverlayInfoList.get(i);
            cmdFilterComplex.append("[ov" + (i - 1) + "][v" + i + "] overlay=x=" + videoOverlayInfo.mOverlayX + ":y=" + videoOverlayInfo.mOverlayY + " [ov" + i + "];");
        }

        if(mWaterMarkInfoList != null) {
            for(int i = 0 ;i<mWaterMarkInfoList.size();i++){
                WaterMarkInfo waterMarkInfo = mWaterMarkInfoList.get(i);

                cmdInput.append(" -i " + waterMarkInfo.mPicPath);

                cmdFilterComplex.append("movie=" + waterMarkInfo.mPicPath+"[m"+i+"];");

                if(i==0){
                    cmdFilterComplex.append("[ov"+(mVideoOverlayInfoList.size()-1)+"][m"+i+"]overlay=x=" +waterMarkInfo.mOverlayX+":y=" + waterMarkInfo.mOverlayY+" [om"+i+"];" );
                } else {
                    cmdFilterComplex.append("[om"+(i-1)+"][m"+i+"]overlay=x=" +waterMarkInfo.mOverlayX+":y=" + waterMarkInfo.mOverlayY+" [om"+i+"];" );
                }
            }
        }

        cmdFilterComplex.deleteCharAt(cmdFilterComplex.lastIndexOf(";"));
        cmdFilterComplex.append("\"");
        StringBuilder cmdParam = new StringBuilder();
        cmdParam.append(" -movflags faststart");
        cmdParam.append(" -strict -2 -vcodec libx264 -profile:v high");
        cmdParam.append(" -r "+mMaxFps);
        cmdParam.append(" -c:a aac -ar 44100");
        cmdParam.append(" -maxrate "+ mMaxBitRate);
        cmdParam.append(" -bufsize "+ mBufsize);
        cmdParam.append(" -crf " + mCRF); //合演设置crf 1，吃满所有码率
        cmdParam.append(" -preset " + mPreset);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        Date curDate = new Date(System.currentTimeMillis());
        String timeStr = formatter.format(curDate);
        VideoProcessTracer.getInstace().setExportTime(timeStr);

        String comment = VideoProcessTracer.getInstace().generateComment() + "[ffmpeg_union]";
        VideoProcessTracer.getInstace().reset();
        if(comment != null) {
            cmdParam.append(" -metadata comment="+ comment);
        }

        if(mMediaInfo.audio_codec_name != null) {
            cmdParam.append(" -c:a copy");
        }
        if(mWaterMarkInfoList == null) {
            cmdParam.append(" -map " + "[ov"+(mVideoOverlayInfoList.size()-1)+"]");
        } else {
            cmdParam.append(" -map " + "[om"+(mWaterMarkInfoList.size()-1)+"]");
        }

        if(mMediaInfo.audio_codec_name != null) {
            cmdParam.append(" -map 0:a ");
        } else {
            cmdParam.append(" ");
        }
        cmdParam.append(mOutputVideoPath);

        String cmd = cmdInput.toString() + cmdFilterComplex.toString()+ cmdParam.toString();
        YYLog.info(TAG,"videosUnion:" + cmd);
        return executeCmd(cmd);
    }

    public static class VideoOverlayInfo{
        /**
         * 视频路径
         */
        public String mVideoPath;

        /**
         * 视频位置坐标 X
         */
        public int mOverlayX;

        /**
         * 视频位置坐标Y
         */
        public int mOverlayY;

        /**
         * 合成后该视频宽度
         */
        public int mWidth;

        /**
         * 合成后该视频的高度
         */
        public int mHeight;
    }

    public static class WaterMarkInfo{
        /**
         * 图片路径
         */
        public String mPicPath;

        /**
         * 图片坐标 X
         */
        public int mOverlayX;

        /**
         * 图片坐标Y
         */
        public int mOverlayY;
    }
}
