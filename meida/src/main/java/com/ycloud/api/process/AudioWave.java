package com.ycloud.api.process;

import com.ycloud.mediaprocess.AudioWaveInternal;
import com.ycloud.utils.YYLog;

import java.io.IOException;

public class AudioWave {
    private static final String TAG = AudioWave.class.getSimpleName();

    private Thread mLoadWaveThread;
    private AudioWaveInternal mAudioWaveInternal;

    /**
     * 初始化对象, 根据音频文件获取波形图数据, 设置监听回调
     * @param filePath
     * @param progressListener
     */
    public AudioWave(final String filePath, final ProgressListener progressListener) {
        mLoadWaveThread = new Thread() {
            public void run() {
                try {
                    mAudioWaveInternal = new AudioWaveInternal();
                    mAudioWaveInternal.parseAudioWave(filePath, progressListener);
                } catch (IOException ex) {
                    YYLog.info(TAG, "AudioWave readWave failed :" + filePath + ", ex:" + ex.getMessage());
                }
            }
        };
        mLoadWaveThread.start();
    }

    public String getFiletype() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getFiletype();
        }

        return "";
    }

    public int getFileSizeBytes() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getFileSizeBytes();
        }

        return 0;
    }

    public int getAvgBitrateKbps() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getAvgBitrateKbps();
        }

        return 0;
    }

    public int getSampleRate() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getSampleRate();
        }

        return 0;
    }

    public int getChannels() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getChannels();
        }

        return 0;
    }

    public int getNumSamples() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getNumSamples();
        }

        return 0;
    }

    /**
     * 波形图帧数
     * @return
     */
    public int getNumFrames() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getNumFrames();
        }

        return 0;
    }

    public int getSamplesPerFrame() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getSamplesPerFrame();
        }

        return 0;
    }

    /**
     * 波形图每帧数据(0 ~ 255)
     * @return
     */
    public int[] getFrameGains() {
        if (mAudioWaveInternal != null) {
            return mAudioWaveInternal.getFrameGains();
        }

        return null;
    }

    // 处理信息回调
    public interface ProgressListener {
        /**
         * 处理进度, 返回值用于判断是否取消处理
         * @param fractionComplete 范围0.0~1.0
         */
        boolean reportProgress(double fractionComplete);

        /**
         * 处理完成回调
         */
        void finishedProgress();

        /**
         * 处理失败回调
         */
        void failedProgress();
    }
}
