package com.ycloud.mediaprocess;

import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.ymrmodel.AudioMixBean;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 重构audio mix类：简化设计，规范接口
 * Created by jinyongqing on 2017/11/7.
 */

public class AudioMixInternal extends MediaBase {
    private final static String TAG = AudioMixInternal.class.getSimpleName();
    private static final int SAMPLE_RATE = 44100;
    public static final String MIX_DURATION_FIRST = "first";
    public static final String MIX_DURATION_LONGEST = "longest";

    private static final String PROCESS_FILE_SUFFIX = ".wav";
    private List<AudioMixBean> mAudioMixBeanList;
    private String mOutputPath;
    private String mMixDuration;

    public AudioMixInternal() {
        mAudioMixBeanList = new ArrayList<>();
        mMixDuration = MIX_DURATION_FIRST; //amix duration默认使用first
    }

    /**
     * 设置mix后audio的输出路径
     * @param outputPath
     */
    public void setOutputPath(String outputPath) {
        mOutputPath = outputPath;
    }

    /**
     * 设置多个音频mix后的时长
     * @param mixDuration
     */
    public void setMixDuration(String mixDuration) {
        mMixDuration = mixDuration;
    }

    /**
     * 添加需要mix的音频源
     *
     * @param path      音频源路径
     * @param delayTime 音频从指定位置混合
     * @param startTime 音频前面部分裁剪后混合
     * @param volume    音频音量大小
     */
    public void addAudioMixBean(String path, double delayTime, double startTime, float volume) {
        AudioMixBean audioMixBean = new AudioMixBean(path, delayTime, startTime, volume, PROCESS_FILE_SUFFIX);
        addAudioMixBean(audioMixBean);
    }

    /**
     * 添加需要mix 的音频源实例
     *
     * @param audioMixBean
     */
    public void addAudioMixBean(AudioMixBean audioMixBean) {
        mAudioMixBeanList.add(audioMixBean);
    }

    /**
     * 清空所有mix的音频源
     */
    public void removeAllAudioMixBean() {
        mAudioMixBeanList.clear();
    }

    /**
     * 音频混合前预处理
     * 1.sample rate不统一的情况
     * 2.音频前面部分裁剪后混合的情况
     * 3.音频从指定位置混合的情况
     */
    private void preProcessAudioMixBean() {
        MediaInfo info;
        int processIndex = 0;
        for (AudioMixBean bean : mAudioMixBeanList) {
            info = MediaProbe.getMediaInfo(bean.mFilepath, true);
            if (info == null) {
                YYLog.error(TAG, "probe audio mix bean error, path:" + bean.mFilepath);
                continue;
            }

            if (info.audioSampleRate == SAMPLE_RATE && bean.mDelayTime == 0 && bean.mStartTime == 0) {
                YYLog.info(TAG, "no need to pre process audio mix bean:" + bean.mFilepath);
                continue;
            }

            String transOutputPath = FileUtils.getFileDir() + File.separator + processIndex + bean.mProcessFileSuffix;
            processIndex++;
            StringBuilder cmdTrans = new StringBuilder("ffmpeg -y -i \"" + bean.mFilepath + "\"");

            //sample rate不统一的情况
            if (info.audioSampleRate != SAMPLE_RATE) {
                cmdTrans.append(" -ar " + SAMPLE_RATE);
            }
            //音频前面部分裁剪后混合的情况
            if (bean.mStartTime != 0) {
                cmdTrans.append(" -ss " + bean.mStartTime);
            }
            //音频从指定位置混合的情况
            if (bean.mDelayTime != 0) {
                cmdTrans.append(" -filter_complex adelay=" + bean.mDelayTime + "|" + bean.mDelayTime);
            }

            cmdTrans.append(" \"" + transOutputPath + "\"");
            boolean isSuccess = executeCmd(cmdTrans.toString());
            if (isSuccess) {
                bean.mProcessFilePath = transOutputPath;
            }
        }
    }

    /**
     * 音频混合函数
     *
     * @return true:成功 false:失败
     */
    public boolean executeInternal() {
        //音频预处理：1.采样率 2.start位置 3.delay位置
        preProcessAudioMixBean();

        //处理混音
        StringBuilder cmd = new StringBuilder("ffmpeg -y");

        if (mAudioMixBeanList.size() == 0) {
            YYLog.info(TAG, "no input audio to mix");
            return false;
        }

        for (int i = 0; i < mAudioMixBeanList.size(); i++) {
            cmd.append(" -i \"" + mAudioMixBeanList.get(i).mProcessFilePath + "\" ");
        }

        if (mAudioMixBeanList.size() > 1) {
            cmd.append(" -filter_complex \"");

            for (int i = 0; i < mAudioMixBeanList.size(); i++) {
                cmd.append("[" + i + ":a] pan=stereo|c0=" + mAudioMixBeanList.get(i).mVideoVolume + "*c0|c1=" + mAudioMixBeanList.get(i).mVideoVolume + "*c1 [a" + i + "],");
            }

            for (int i = 0; i < mAudioMixBeanList.size(); i++) {
                cmd.append("[a" + i + "]");
            }

            cmd.append("amix=inputs=" + mAudioMixBeanList.size() + ":duration=" + mMixDuration + ",pan=stereo|c0=");

            for (int i = 0; i < mAudioMixBeanList.size(); i++) {
                cmd.append("c" + i);
                if (i < mAudioMixBeanList.size() - 1) {
                    cmd.append("+");
                }
            }
            cmd.append("|c1=");
            for (int i = 0; i < mAudioMixBeanList.size(); i++) {
                int tmp = i + mAudioMixBeanList.size();
                cmd.append("c" + tmp);
                if (i < mAudioMixBeanList.size() - 1) {
                    cmd.append("+");
                }
            }
            cmd.append(",pan=stereo|c0=c0+c1|c1=c0+c1 [a]\" -map [a] ");
        }

        cmd.append("\"" + mOutputPath + "\"");

        return executeCmd(cmd.toString());
    }
}
