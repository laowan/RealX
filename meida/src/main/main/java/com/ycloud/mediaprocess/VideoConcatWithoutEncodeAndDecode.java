package com.ycloud.mediaprocess;

import android.text.TextUtils;
import com.ycloud.VideoProcessTracer;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.VideoConcat;
import com.ycloud.common.Constant;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;
import java.util.ArrayList;

/**
 * Created by dzhj on 17/5/3.
 */

public class VideoConcatWithoutEncodeAndDecode extends VideoConcatBase {

    public static final String TAG = VideoConcatWithoutEncodeAndDecode.class.getSimpleName();
    private String mBackgroundMusicPath;

    private ArrayList<String> mVideosPathArray;
    private String mOutputFile;

    private double mFirstVideoRatate = 180;
    private double mBgMusicStart = 0;

    private String CONCAT_VIDEO_PATH = null;
    public VideoConcatWithoutEncodeAndDecode(String cacheDir,final ArrayList<String> videosPathArray, final String outputFile){
        mVideosPathArray =videosPathArray;
        mOutputFile = outputFile;
        CONCAT_VIDEO_PATH = cacheDir + "concatlist.txt";
        setExcuteCmdId(MediaNative.libffmpeg_cmd_video_concat);
    }


    public void concatVideos() {
        YYLog.info(this,"concatVideos start");
//        MediaInfo mediaInfo = MediaUtils.getMediaInfo(mVideosPathArray.get(0));
//        if(mediaInfo == null) {
//            YYLog.info(this,"mediaprobe returns null");
//            if(mMediaListener != null) {
//                mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "mediaprobe returns null");
//            }
//            return;
//        }
//        setTotalFrame(mediaInfo.total_frame);
        int TotalFrame = 0;
        StringBuilder strConcatVideo = new StringBuilder();
        String line = System.getProperty("line.separator");
        String videoPath = "";
        VideoProcessTracer.getInstace().setVidePathList(mVideosPathArray);
        for (int i = 0; i < mVideosPathArray.size(); i++) {
            videoPath = mVideosPathArray.get(i);
            MediaInfo mediainfo = MediaUtils.getMediaInfo(mVideosPathArray.get(i));
            if (mediainfo != null) {
                strConcatVideo.append("file " + videoPath);
                strConcatVideo.append(line);
                strConcatVideo.append("\r\n");
                strConcatVideo.append("\n");

                if (i == 0) {
                    if (mediainfo != null) {
                        mFirstVideoRatate = mediainfo.v_rotate;
                    } else {
                        YYLog.info(this, "ffprobe error");
                        if (mMediaListener != null) {
                            mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "ffprobe error");
                        }
                        return;
                    }
                }
                TotalFrame += mediainfo.total_frame;
            }
        }

        if (TextUtils.isEmpty(strConcatVideo.toString())) {
            YYLog.info(this,"strConcatVideo  null");
            return;
        }
        setTotalFrame(TotalFrame);
        FileUtils.createFile(CONCAT_VIDEO_PATH);
        FileUtils.writeFileSdcard(CONCAT_VIDEO_PATH, strConcatVideo.toString());
        boolean ret = concatVideosInternal(CONCAT_VIDEO_PATH, mOutputFile);

        if(ret ==false) {
            YYLog.info(this,"concat error");
            if(mMediaListener != null) {
                mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "concat error");
            }
        }

        YYLog.info(this,"concatVideos end");
    }

    private boolean concatVideosInternal(final String videoListTxtFile, final String outputFile) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("ffmpeg -y -f concat -safe 0 -i ");
        cmd.append(videoListTxtFile);
        if(mBackgroundMusicPath == null) {
            cmd.append(" -c:v copy ");
            cmd.append(" -c:a libfdk_aac");
            cmd.append(" -ar 44100");
            cmd.append(" -strict -2 ");
            //cmd.append(" -af volume=15dB"); 不改变原始音频音量
        }else {
            //加背景音乐时,视频由于渲染大概有200ms延迟，所以这里将音频延迟200ms
            cmd.append(" -itsoffset "+ VideoConcat.ADELAY_TIME);
            cmd.append(" -i \"" + mBackgroundMusicPath+"\"");
            cmd.append(" -c:v copy");
            cmd.append(" -c:a libfdk_aac");
            cmd.append(" -map 0:v:0 -map 1:a:0");
            cmd.append(" -ar 44100");
            cmd.append(" -strict -2 ");
        }
        
        cmd.append(" -movflags faststart");

        if(mFirstVideoRatate == 90 || mFirstVideoRatate == 270){
           cmd.append("-metadata:s:v:0 rotate=" + mFirstVideoRatate);
        }
        cmd.append(" \"" + outputFile + "\"");

        boolean isSuccess =  executeCmd(cmd.toString());

        YYLog.info(TAG, "concat videos cmd isSuccess:" + isSuccess);

        return  isSuccess;
    }

    public  void setBackgroundMusic(String backgroundMusicPath){
        mBackgroundMusicPath = backgroundMusicPath;
    }
}
