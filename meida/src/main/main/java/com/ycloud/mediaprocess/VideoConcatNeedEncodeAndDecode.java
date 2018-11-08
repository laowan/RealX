package com.ycloud.mediaprocess;

import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.common.GlobalConfig;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.YYLog;

import java.util.ArrayList;

/**
 * Created by dzhj on 17/5/2.
 */

public class VideoConcatNeedEncodeAndDecode extends VideoConcatBase{

    public static final String TAG = VideoConcatNeedEncodeAndDecode.class.getSimpleName();
    private String mBackgroundMusicPath;
    private double mBgMusicStart = 0;

    private ArrayList<String> mVideosPathArray;
    private String mOutputFile;

    //old concat method:
    /*
    ffmpeg -i opening.mp4 -i episode.mp4 -i ending.mp4 －i k.mp3 -filter_complex
    '[0:0] [0:1] [0:2] [1:0] [1:1] [1:2] [2:0] [2:1] [2:2]
    concat=n=3:v=1:a=2 [v] [a1] [a2]'
    -map '[v]' -map '[a1]' -map '[a2]' output.mkv
    */

    //new concat method:
    /*
    ffmpeg -y -filter_complex
    "movie=1.mp4, scale=540:960, setsar=sar=1[v0];
    movie=2.mp4, scale=540:960, setsar=sar=1[v1];
    movie=3.mp4, scale=540:960, setsar=sar=1[v2];
    movie=4.mp4, scale=540:960, setsar=sar=1[v3];
    [v1][v0][v2][v3]concat=n=4:v=1:a=0[outv];
    amovie=2.mp4[a1];
    amovie=3.mp4[a2];[a2]asetpts=PTS-STARTPTS+8.6/TB[outa2];
    amovie=4.mp4[a3];[a3]asetpts=PTS-STARTPTS+11.99/TB[outa3];
    [a1][outa2][outa3]concat=n=3:v=0:a=1[outa]"
    -map [outv] -map [outa] out.mp4
     */

    public VideoConcatNeedEncodeAndDecode(final ArrayList<String> videosPathArray, final String outputFile){
        mVideosPathArray =videosPathArray;
        mOutputFile = outputFile;
        setExcuteCmdId(MediaNative.libffmpeg_cmd_video_concat);

        MediaInfo mediaInfo = MediaUtils.getMediaInfo(videosPathArray.get(0));
        if (mediaInfo != null){
            setTotalFrame(mediaInfo.total_frame);
        }
    }

    public  void setBackgroundMusic(String backgroundMusicPath){
        mBackgroundMusicPath = backgroundMusicPath;
    }

    public void  concatVideos(){
        StringBuilder inputSb = new StringBuilder();
        StringBuilder videoOutTag = new StringBuilder();
        StringBuilder audioOutTag = new StringBuilder();
        ArrayList<MediaInfo> fileInfoList = new ArrayList<>();

        int videoWidth = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_WIDTH;
        int videoHeight = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_HEIGHT;

        StringBuilder filterComplexSb = new StringBuilder();
        filterComplexSb.append(" -filter_complex \"");

        if(mBackgroundMusicPath != null) {
            for(int i=0;i< mVideosPathArray.size();i++){
                inputSb.append(" -i " + mVideosPathArray.get(i));
            }
            inputSb.append(" -itsoffset 0.2");
            inputSb.append(" -i " + mBackgroundMusicPath);

            /*concat时需要统一:video分辨率,宽高像素比,否则ffmpeg会执行失败*/
            for(int i =0; i < mVideosPathArray.size(); i++) {
                filterComplexSb.append(" [" + i + ":v]scale=" + videoWidth + ":" + videoHeight + ",setsar=sar=1[v" + i + "];");
            }
        }

        int maxFps = 30;

        for (int i = 0; i < mVideosPathArray.size(); i++){
            MediaInfo info = MediaProbe.getMediaInfo(mVideosPathArray.get(i), true);
            if (info != null){
                fileInfoList.add(info);
            }
        }

        YYLog.info(TAG, "set concat fps:" + maxFps);

        if(mBackgroundMusicPath == null) {
            int audioTrackNum = 0;
            boolean muteGap = false;
            float videoDuration = 0.0f;
            for(int fileIndex = 0; fileIndex < mVideosPathArray.size(); fileIndex++){
                filterComplexSb.append("movie="+mVideosPathArray.get(fileIndex)+",scale="+videoWidth+":"+videoHeight+", setsar=sar=1[v"+fileIndex+"];");
                videoOutTag.append("[v"+fileIndex+"]");
            }

            filterComplexSb.append(videoOutTag+"concat=n="+mVideosPathArray.size()+":v=1:a=0[outv]");

            for (int fileIndex = 0; fileIndex < mVideosPathArray.size(); fileIndex++){
                if (fileIndex >= fileInfoList.size()){
                    continue;
                }
                MediaInfo info = fileInfoList.get(fileIndex);
                if (info == null){
                    continue;
                }
                if (info.audioChannels > 0){
                    if (audioTrackNum == 0){
                        filterComplexSb.append(";");
                    }
                    filterComplexSb.append("amovie="+mVideosPathArray.get(fileIndex)+"[a"+fileIndex+"];");
                    if (muteGap){
                        filterComplexSb.append("[a"+fileIndex+"]asetpts=PTS-STARTPTS+"+videoDuration+"/TB[outa"+fileIndex+"];");
                        audioOutTag.append("[outa"+fileIndex+"]");
                    }else{
                        audioOutTag.append("[a"+fileIndex+"]");
                    }
                    audioTrackNum++;
                    muteGap = false;
                }else{
                    muteGap = true;
                }
                videoDuration+=info.duration;
            }

            if (audioTrackNum > 0) {
                filterComplexSb.append(audioOutTag+"concat=n="+audioTrackNum+":v=0:a=1[outa]");
            }

            filterComplexSb.append("\"");
            filterComplexSb.append(" -map [outv]");
            if (audioTrackNum > 0){
                filterComplexSb.append(" -map [outa]");
            }
        } else {
            for (int i = 0; i < mVideosPathArray.size(); i++) {
                videoOutTag.append(" [v" + i + "]");
            }
            filterComplexSb.append(videoOutTag+"concat=n="+mVideosPathArray.size()+" [v]\"");
            filterComplexSb.append(" -map [v]");
            filterComplexSb.append(" -map "+mVideosPathArray.size()+":a");
        }

        filterComplexSb.append(" -threads 2");
        filterComplexSb.append(" -c:v libx264 ");
        filterComplexSb.append(" -preset "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_PRESET);
        filterComplexSb.append(" -crf "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_CRF);
        filterComplexSb.append(" -profile:v "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_PROFILE);
        filterComplexSb.append(" -maxrate "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE);
        filterComplexSb.append(" -bufsize "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE * 2);
        filterComplexSb.append(" -movflags faststart");
        filterComplexSb.append(" -c:a libfdk_aac");
        filterComplexSb.append(" -ar 44100");
        filterComplexSb.append(" -strict -2");
        filterComplexSb.append(" -r " + maxFps);
//        filterComplexSb.append(" -metadata:s:v:0 rotate=0");
        filterComplexSb.append(" "+ mOutputFile);

        String cmd = "ffmpeg -y"+ inputSb.toString()+ filterComplexSb.toString();
        boolean ret =  executeCmd(cmd);

        YYLog.info(TAG,"VideoConcatNeedEncodeAndDecode finished ret:"+ret);
    }

    public void  concatVideos_old(){
        StringBuilder inputSb = new StringBuilder();

        for(int i=0;i< mVideosPathArray.size();i++){
            inputSb.append(" -i " + mVideosPathArray.get(i));
        }

        if(mBackgroundMusicPath != null) {
            inputSb.append(" -itsoffset 0.2");
            inputSb.append(" -i " + mBackgroundMusicPath);
        }

        StringBuilder filterComplexSb = new StringBuilder();
        filterComplexSb.append(" -filter_complex \"");

        int videoWidth = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_WIDTH;
        int videoHeight = GlobalConfig.getInstance().getRecordConstant().RECORD_VIDEO_HEIGHT;
        /*concat时需要统一:video分辨率,宽高像素比,否则ffmpeg会执行失败*/
        for(int i =0; i < mVideosPathArray.size(); i++) {
            filterComplexSb.append(" [" + i + ":v]scale=" + videoWidth + ":" + videoHeight + ",setsar=sar=1[v" + i + "];");
        }

        if(mBackgroundMusicPath == null) {
            for (int i = 0; i < mVideosPathArray.size(); i++) {
                filterComplexSb.append(" [v" + i + "]");
                filterComplexSb.append(" [" + i + ":a]");
            }
            filterComplexSb.append(" concat=n=" + mVideosPathArray.size());

            filterComplexSb.append(":v=1:a=1 [v] [a]");
            filterComplexSb.append("\"");
            filterComplexSb.append(" -map [v] -map [a]");

        } else {
            for (int i = 0; i < mVideosPathArray.size(); i++) {
                filterComplexSb.append(" [v" + i + "]");
            }
            filterComplexSb.append(" concat=n=1 [v]\"");
            int i = mVideosPathArray.size();
            filterComplexSb.append(" -map [v]");
            filterComplexSb.append(" -map "+mVideosPathArray.size()+":a");
        }

        filterComplexSb.append(" -threads 2");
        filterComplexSb.append(" -c:v libx264 ");
        filterComplexSb.append(" -preset "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_PRESET);
        filterComplexSb.append(" -crf "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_CRF);
        filterComplexSb.append(" -profile:v "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_CRF);
        filterComplexSb.append(" -maxrate "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE);
        filterComplexSb.append(" -bufsize "+ GlobalConfig.getInstance().getRecordConstant().EXPORT_BITRATE * 2);
        filterComplexSb.append(" -movflags faststart");
        filterComplexSb.append(" -c:a aac");
        filterComplexSb.append(" -ar 44100");
        filterComplexSb.append(" -strict -2");
        filterComplexSb.append(" -metadata:s:v:0 rotate=0");

        filterComplexSb.append(" "+ mOutputFile);

        String cmd = "ffmpeg -y"+ inputSb.toString()+ filterComplexSb.toString();
        YYLog.info(TAG,"VideoConcatNeedEncodeAndDecode cmd:"+ cmd);

        boolean ret =  executeCmd(cmd);

        YYLog.info(TAG,"VideoConcatNeedEncodeAndDecode finished ret:"+ret);
    }

}
