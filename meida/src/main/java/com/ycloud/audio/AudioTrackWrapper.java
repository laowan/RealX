package com.ycloud.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.ycloud.utils.YYLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Administrator on 2018/1/17.
 */

public class AudioTrackWrapper {
    static final String TAG = "AudioTrackWrapper";
    public static final int kSAMPLE_RATE = 44100;
    public static final int kCHANNEL_COUNT = 2;
    public static final int kPLAY_PERIOD_MS = 20;
    public static final int kSYSTEM_PLAY_DELAY_MS = 60;
    public static final int kPLAY_PERIOD_BYTES = kSAMPLE_RATE * kCHANNEL_COUNT * 2 * kPLAY_PERIOD_MS / 1000;
    static final int kCHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private AudioTrack mAudioTrack;
    private int mAudioTrackBufferSizeInFrame;
    private byte[] mPlayBuffer;
    private byte[] mTmpBuffer;
    private WorkThread mWorkThread;
    private volatile boolean mIsPlaying;
    private volatile boolean mRequestStop;
    Set<IAudioRawDataProducer> mAudioRawDataProducers = new HashSet<>();

    private static AudioTrackWrapper ourInstance = new AudioTrackWrapper();
    private int mWritePosition;
    private int mPlayPosition;

    public static AudioTrackWrapper getInstance() {return ourInstance;}

    public interface IAudioRawDataProducer {
        int onConsumeAudioData(byte[] buffer, int requestLen, int delayMS);
    }

    private AudioTrackWrapper() {
    }

    public void startPlay() {
        synchronized (this) {
            if (!mIsPlaying) {
                mWorkThread = new WorkThread();
                mWorkThread.start();
                mIsPlaying = true;
            }
        }
    }

    public void addAudioRawDataProducer(IAudioRawDataProducer audioRawDataProducer) {
        synchronized (this) {
            mAudioRawDataProducers.add(audioRawDataProducer);
            if (!mIsPlaying) {
                startPlay();
            }
        }
    }

    public void removeAudioRawDataProducer(IAudioRawDataProducer audioRawDataProducer) {
        synchronized (this) {
            mAudioRawDataProducers.remove(audioRawDataProducer);
        }
    }

    public void stopPlay() {
        synchronized (this) {
            mRequestStop = true;
        }
        stopThread();
        //These is happen rarely
        if (!mRequestStop) {
            YYLog.e(TAG, "thread was stop twice");
            synchronized (this) {
                mRequestStop = true;
            }
            stopThread();
        }
    }

    private void stopThread() {
        if (mWorkThread != null) {
            try {
               mWorkThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    private boolean prepare() {
        int bufferSize = kPLAY_PERIOD_BYTES * 4; // 80MS
        int minBufferSize = AudioTrack.getMinBufferSize(kSAMPLE_RATE, kCHANNEL_CONFIG, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize < minBufferSize) {
            bufferSize = (minBufferSize / kPLAY_PERIOD_BYTES + 1) * kPLAY_PERIOD_BYTES;
        }
        mAudioTrackBufferSizeInFrame = bufferSize / 2 / kCHANNEL_COUNT;
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, kSAMPLE_RATE, kCHANNEL_CONFIG, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
        if (mAudioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            YYLog.e(TAG, "create audio track failed : " + mAudioTrack.getState());
            mAudioTrack.release();
            mAudioTrack = null;
            return false;
        }
        mPlayBuffer = new byte[mAudioTrackBufferSizeInFrame * kCHANNEL_COUNT * 2];
        mTmpBuffer = new byte[mAudioTrackBufferSizeInFrame * kCHANNEL_COUNT * 2];
        long b = bufferSize;
        b = b * 1000 / (kSAMPLE_RATE * kCHANNEL_COUNT * 2);
        YYLog.info(TAG, " AudioTrackBufferSizeMS " + b);
        return true;
    }

    private void release() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }

        YYLog.info(TAG, "release");
    }

    private int readData(byte[] buffer, int requestLen, int delayMS) {
        Arrays.fill(buffer, (byte) 0);
        int mixLen = 0;
        synchronized (this) {
            for (IAudioRawDataProducer audioRawDataProducer : mAudioRawDataProducers) {
                Arrays.fill(mTmpBuffer, (byte) 0);
                int readLen = audioRawDataProducer.onConsumeAudioData(mTmpBuffer, requestLen, delayMS);
                if (readLen > 0) {
                    AudioSimpleMixer.mix(mTmpBuffer, 1.0f, buffer, 1, readLen);
                }
                mixLen = readLen > mixLen ? readLen : mixLen;
            }
        }
        return mixLen;
    }

    private boolean shouldStop() {
        synchronized (this) {
            return mRequestStop || mAudioRawDataProducers.isEmpty();
        }
    }

    private class WorkThread extends Thread {
        WorkThread() {
            super("AudioTrackWrapperWorkThread");
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(-14);
            if (!prepare()) {
                synchronized (this) {
                    mIsPlaying = false;
                }
                return;
            }
            mRequestStop = false;
            int len = readData(mPlayBuffer, mAudioTrackBufferSizeInFrame * kCHANNEL_COUNT * 2, 0);
            int freeSpaceInFrame;
            mPlayPosition = 0;
            mWritePosition = 0;
            int writeSize;
            if (len > 0) {
                writeSize = mAudioTrack.write(mPlayBuffer, 0, len);
                mWritePosition = writeSize / kCHANNEL_COUNT / 2;
            }
            int continueSleepCount = 0;
            boolean overrun = false;
            mAudioTrack.play();
            int playPeriodInFrame = kPLAY_PERIOD_BYTES / kCHANNEL_COUNT / 2;

            while (!shouldStop()) {
                mPlayPosition = mAudioTrack.getPlaybackHeadPosition();
                int numFrameInBuffer = mWritePosition - mPlayPosition;
                freeSpaceInFrame = mAudioTrackBufferSizeInFrame - numFrameInBuffer;
                int delayMS = numFrameInBuffer * 1000 / kSAMPLE_RATE;
                if (freeSpaceInFrame >= mAudioTrackBufferSizeInFrame) {
                    YYLog.w(TAG, "overrun " + mWritePosition  + " : " + mPlayPosition);
                    mWritePosition = mPlayPosition;
                    overrun = true;
                }
                if ((freeSpaceInFrame >= playPeriodInFrame) || continueSleepCount > 2) {
                    len = readData(mPlayBuffer, kPLAY_PERIOD_BYTES, delayMS);
                    if (overrun) {
                        if (len <= 0) {
                            len = kPLAY_PERIOD_BYTES;
                        }else {
                            overrun = false;
                        }
                    }
                    if (len > 0 ) {
                        writeSize = mAudioTrack.write(mPlayBuffer, 0, kPLAY_PERIOD_BYTES);
                        mWritePosition += (writeSize / kCHANNEL_COUNT / 2);
                    }
                    continueSleepCount = 0;
                } else {
                    try {
                        sleep(kPLAY_PERIOD_MS);
                        continueSleepCount++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            release();
            synchronized (this) {
                mIsPlaying = false;
                mAudioRawDataProducers.clear();
            }
        }
    }
}
