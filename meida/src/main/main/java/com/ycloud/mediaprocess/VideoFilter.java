package com.ycloud.mediaprocess;

import android.content.Context;
import android.text.TextUtils;

import com.ycloud.api.common.TransitionInfo;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.AudioMixBean;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoFilter implements IVideoFilter {

    public static final String TAG = VideoFilter.class.getSimpleName();
    public static final float VIDEO_VOLUME_TIMES = 2.0f;

    public String mBackgroundMusicFilePath = "";
    public String mBackgroundMusicRhythmPath = null;
    public int mBackgroundMusicStart = 0;
    public float mVideoVolume = 1.0f;
    public float mMusicVolume = 0.0f;
    private double mDuration = 50.0;
    private String mExportBgm;

    public String mBgMusicPath; //处理后的配乐路径（配乐剪切等）
    public String mMixedAudioPath; //合成了音效后的audio path

    private Map<Integer, AudioMixBean> mSoundEffectMap; //视频添加的音效集合
    private AudioMixInternal mAudioMixInternal;  //配乐音效混合类

    private String mCacheDir;
    private String mMagicAudioFilePath;

	static {
		try {
			System.loadLibrary("ycmedia");
		} catch (UnsatisfiedLinkError e) {
			YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
		}
	}

    public VideoFilter(Context context) {
        mAudioMixInternal = new AudioMixInternal();
        mAudioMixInternal.setMixDuration(AudioMixInternal.MIX_DURATION_LONGEST);
        mSoundEffectMap = new HashMap<>();
        mCacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
    }

    public void setMagicAudioFilePath(String magicAudioFilePath) {
        mMagicAudioFilePath = magicAudioFilePath;
    }

    public String getMagicAudioFilePath() {
        return mMagicAudioFilePath;
    }

    /**
     * 设置bgm的节奏识别文件
     *
     * @param path 节奏识别文件路径，null代表移除节奏识别
     */
    public void setBackgroundMusicRhythmPath(String path) {
        mBackgroundMusicRhythmPath = path;
    }

    public void setBackgroundMusic(final String musicFilePath, float videoVolume, float musicVolume) {
        setBackgroundMusic(musicFilePath, videoVolume, musicVolume, 0);
    }

    public void setBackgroundMusic(final String musicFilePath, float videoVolume, float musicVolume, int backgroundMusicStart) {
        setBackgroundMusic(musicFilePath, videoVolume, musicVolume, backgroundMusicStart, mDuration);
    }

	public int setBackgroundMusic(final String musicFilePath, float videoVolume, float musicVolume, int backgroundMusicStart, double duration) {
		if (!TextUtils.isEmpty(musicFilePath) && !MediaUtils.isSupportAudioFormat(musicFilePath)) {
			YYLog.warn(TAG, "music format is not support");
			return -1;
		}
        mBackgroundMusicFilePath = musicFilePath;
        mVideoVolume = videoVolume;
        mMusicVolume = musicVolume;
        mBackgroundMusicStart = backgroundMusicStart;
        return 0;

		/*
        //背景音乐剪切处理
		String tmpBgMusicPath = mCacheDir + "bgm_clip.wav";
		mBgMusicPath = musicFilePath;
		boolean isSuccess = mAudioProcessInternal.clipAudio(musicFilePath, tmpBgMusicPath, (double)backgroundMusicStart / 1000, duration);
		if (isSuccess) {
			mBgMusicPath = tmpBgMusicPath;
		}

		//设置背景音乐后，需要进行合成音效处理
		isSuccess = mixSoundEffect();
		if (!isSuccess) {
			mMixedAudioPath = null;
		}
		*/
    }

    private void removeBackgroundMusic() {
        setBackgroundMusic(null, 1.0f, 0);
        setBackgroundMusicRhythmPath(null);
    }

    private void addSoundEffect(int index, String path, long delay) {
        if (mSoundEffectMap != null) {
            //如果之前没有调节过音量,音效音量默认为1
            if (mMusicVolume == 0.0f) {
                mMusicVolume = 1.0f;
            }
            //保存添加的音效
            AudioMixBean audioMixBean = new AudioMixBean(path, delay, 0, 1, ".wav");
            mSoundEffectMap.put(index, audioMixBean);
            boolean isSuccess = mixSoundEffect();
            if (!isSuccess) {
                mMixedAudioPath = null;
            }
        }
    }

    private void removeSoundEffect(int index) {
        if (mSoundEffectMap != null) {
            //移除删除的音效
            mSoundEffectMap.remove(index);
            boolean isSuccess = mixSoundEffect();
            if (!isSuccess) {
                mMixedAudioPath = null;
            }
        }
    }

    //背景音乐合成方法
    private boolean mixSoundEffect() {
        long startTime = System.currentTimeMillis();
        //添加音效列表中的音效
        for (AudioMixBean bean : mSoundEffectMap.values()) {
            mAudioMixInternal.addAudioMixBean(bean);
        }

        //添加配乐
        if (!TextUtils.isEmpty(mBackgroundMusicFilePath)) {
            AudioMixBean audioMixBean = new AudioMixBean(mBgMusicPath, 0, 0, mMusicVolume, ".wav");
            mAudioMixInternal.addAudioMixBean(audioMixBean);
        }

        //混音处理
        mMixedAudioPath = mCacheDir + "audio_mix.wav";
        mAudioMixInternal.setOutputPath(mMixedAudioPath);
        boolean isSuccess = mAudioMixInternal.executeInternal();
        mAudioMixInternal.removeAllAudioMixBean();
        YYLog.info(TAG, "mixSoundEffect cost:" + (System.currentTimeMillis() - startTime));

        return isSuccess;
    }

    /**
     * @return the mBackgroundMusicFilePath
     */
    public String getBackgroundMusicFilePath() {
        return mBackgroundMusicFilePath;
    }

    /**
     * @param backgroundMusicFilePath the mBackgroundMusicFilePath to set
     */
    public void setBackgroundMusicFilePath(String backgroundMusicFilePath) {
        mBackgroundMusicFilePath = backgroundMusicFilePath;
    }

    /**
     * 设置导出时的背景音乐
     *
     * @param exportBgm
     */
    public void setExportBgm(String exportBgm) {
        mExportBgm = exportBgm;
    }

    /**
     * 获取导出时的背景音乐
     *
     * @return
     */
    public String getExportBgm() {
        return mExportBgm;
    }

    /**
     * ˙
     *
     * @return the mVideoVolume
     */
    public float getmVideoVolume() {
        return mVideoVolume;
    }

    /**
     * @param videoVolume the mVideoVolume to set
     */
    public void setVideoVolume(float videoVolume) {
        mVideoVolume = videoVolume;
    }

    /**
     * @return the mMusicVolume
     */
    public float getMusicVolume() {
        return mMusicVolume;
    }

    /**
     * @param mMusicVolume the mMusicVolume to set
     */
    public void setMusicVolume(float mMusicVolume) {
        this.mMusicVolume = mMusicVolume;
    }

    /**
     * @return the mIsAudioDisabled
     */
    public boolean isAudioDisabled() {
        return (mVideoVolume == 0 && mMusicVolume == 0 && mMagicAudioFilePath == null);
    }
    
    private List<TransitionInfo> mTransitionList;

    public List<TransitionInfo> getTransitionList() {
        return mTransitionList;
    }

    public void setVideosTransitionInfo(List<TransitionInfo> transitionInfoList) {
		mTransitionList = transitionInfoList;
    }
}
