package com.ycloud.audio;

import android.content.Context;

import com.ycloud.common.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Created by Administrator on 2018/1/22.
 */

public class AudioPlayEditor implements AudioTrackWrapper.IAudioRawDataProducer {
    static final String TAG = "AudioPlayEditor";
    static final long kMAX_EXPORT_SIZE_MS = 1000 * 60; // 1 minute
    private static int kFFT_LEN = 1024;

    static {
        try {
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("audioengine");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
        }
    }

    public enum PLAY_MODE {
        PLAY_MODE_BACKGROUND_MUSIC,
        PLAY_MODE_EFFECT
    }

    private enum PLAY_STATE {
        PLAY_STATE_WAIT_TO_PLAY,
        PLAY_STATE_PLAYING,
        PLAY_STATE_WAIT_TO_PAUSE,
        PLAY_STATE_PAUSE,
        PLAY_STATE_STOP,
    }

    private int mID = 1;
    private Deque<AudioPlayer> mAudioPlayers = new ArrayDeque<>();
    private AudioPlaybackRateProcessor mAudioPlaybackRateProcessor;
    private FFTProcessor mFFTProcessor = new FFTProcessor();
    private IAudioPlayEditorListener mListener;
    private PLAY_STATE mPlayState;

    private long mPlayPositionInMS; // current position in ms, it calculate through the system time have elapse after start
    private volatile long mPlayerPlayPositionInMS = 0; // current position in ms, it calculate through num of data have play after start
    //private boolean mShouldResetConsumePTS;
    private long mLastConsumePTS;
    private volatile long mStopPlayPTS;
    private volatile long mStartPlayPTS;
    private volatile boolean mForcePause;
    private volatile long mNumOfDataInAudioTrackInMS;

    private byte[] mTmpBuffer;
    private byte[] mMixedBuffer;

    private boolean mRequestPause;
    private boolean mRequestStart;

    private volatile boolean mShouldUpdateFFT = false;

    public interface IAudioPlayEditorListener {
        void onAudioPlayStart();

        void onAudioPlayStop(long positionMS);
    }

    public AudioPlayEditor() {
        mPlayState = PLAY_STATE.PLAY_STATE_STOP;
        mPlayPositionInMS = 0;
        mForcePause = false;
        mFFTProcessor.init(kFFT_LEN);
        //mShouldResetConsumePTS = false;
    }

    public void prepare(Context context) {
        String cacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
        AudioFileCacheMgr.getInstance().init(cacheDir);
    }

    public void setListener(IAudioPlayEditorListener listener) {
        synchronized (this) {
            mListener = listener;
        }
    }

    public void release() {
        AudioTrackWrapper.getInstance().removeAudioRawDataProducer(this);
        clearPlayers();
        if (mAudioPlaybackRateProcessor != null) {
            mAudioPlaybackRateProcessor.unint();
            mAudioPlaybackRateProcessor = null;
        }

        mPlayState = PLAY_STATE.PLAY_STATE_STOP;
        mPlayPositionInMS = 0;
        mForcePause = false;
        mFFTProcessor.deinit();
    }

    public int addPlayer(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long delayMS) {
        if (path == null) {
            return -1;
        }
        int id = getID();
        AudioFilePlayer audioPlayer = new AudioFilePlayer(id);
        audioPlayer.prepare(path, beginReadPositionMS, endReadPositionMS, loop);
        audioPlayer.start(delayMS);
        synchronized (this) {
            mAudioPlayers.addFirst(audioPlayer);
        }
        YYLog.info(TAG, "addPlayer %d : %s %d %d %d %d", id, path, beginReadPositionMS, endReadPositionMS, loop ? 1 : 0, delayMS);
        return id;
    }

    public int addMagicPlayer(int positionMS, String[] audioPaths) {
        if (audioPaths == null || audioPaths.length < 3) {
            return -1;
        }
        int id = getID();
        FingerMagicAudioPlayer audioPlayer = new FingerMagicAudioPlayer(id);
        audioPlayer.prepare(audioPaths);
        audioPlayer.start(positionMS);
        synchronized (this) {
            mAudioPlayers.addFirst(audioPlayer);
        }

        YYLog.info(TAG, "addMagicPlayer " + id);
        return id;
    }

    public int addEffectPlayer(int positionMS, String[] audioPaths) {
        if (audioPaths == null || audioPaths.length < 3) {
            return -1;
        }
        int id = getID();
        EffectAudioPlayer audioPlayer = new EffectAudioPlayer(id);
        audioPlayer.prepare(audioPaths);
        audioPlayer.start(positionMS);
        synchronized (this) {
            mAudioPlayers.addFirst(audioPlayer);
        }

        YYLog.info(TAG, "addEffectPlayer " + id);
        return id;
    }

    public int addErasurePlayer(int positionMS) {
        int id = getID();
        ErasureAudioPlayer audioPlayer = new ErasureAudioPlayer(id);
        audioPlayer.start(positionMS);
        synchronized (this) {
            mAudioPlayers.addFirst(audioPlayer);
        }

        YYLog.info(TAG, "addErasurePlayer " + id);
        return id;
    }

    public void removePlayer(int ID) {
        synchronized (this) {
            for (AudioPlayer audioPlayer : mAudioPlayers) {
                if (audioPlayer.ID() == ID) {
                    audioPlayer.release();
                    mAudioPlayers.remove(audioPlayer);
                    break;
                }
            }
        }
        YYLog.info(TAG, "removePlayer " + ID);
    }

    public void clearPlayers() {
        synchronized (this) {
            for (AudioPlayer audioPlayer : mAudioPlayers) {
                if (audioPlayer != null) {
                    audioPlayer.release();
                }
            }
            mAudioPlayers.clear();
        }
        YYLog.info(TAG, "clearPlayers ");
    }

    public void stopPlayAllPlayer(long positionMS) {
        synchronized (this) {
            if (positionMS == -1) {
                positionMS = mPlayPositionInMS;
            }
            for (AudioPlayer audioPlayer : mAudioPlayers) {
                if (audioPlayer != null) {
                    audioPlayer.stop(positionMS);
                }
            }
        }
        YYLog.info(TAG, "stopPlayAllPlayer ");
    }

    public void stopPlayPlayer(int ID, long positionMS) {
        synchronized (this) {
            for (AudioPlayer audioPlayer : mAudioPlayers) {
                if (audioPlayer.ID() == ID) {
                    audioPlayer.stop(positionMS);
                    break;
                }
            }
        }
        YYLog.info(TAG, "stopPlayPlayer " + ID);
    }

    public void setPlayerVolume(int ID, float volume) {
        synchronized (this) {
            for (AudioPlayer audioPlayer : mAudioPlayers) {
                if (audioPlayer.ID() == ID) {
                    audioPlayer.setVolume(volume);
                    break;
                }
            }
        }
    }

    public void start() {
        boolean needToStart = false;
        synchronized (this) {
            mRequestStart = true;
            mRequestPause = false;
            if (mPlayState == PLAY_STATE.PLAY_STATE_STOP) {
                needToStart = true;
                YYLog.info(TAG, "start ");
            } else if (mPlayState == PLAY_STATE.PLAY_STATE_PAUSE) {
                //
                YYLog.info(TAG, "resume " + mPlayPositionInMS);
            } else {
                return;
            }
        }
        if (needToStart) {
            AudioTrackWrapper.getInstance().addAudioRawDataProducer(this);
        }
    }

    public void stop() {
        synchronized (this) {
            mPlayState = PLAY_STATE.PLAY_STATE_STOP;
        }
        AudioTrackWrapper.getInstance().removeAudioRawDataProducer(this);
        mPlayerPlayPositionInMS = 0;
        mPlayPositionInMS = 0;
        YYLog.info(TAG, "stop ");
    }

    public void pause() {
        synchronized (this) {
            mRequestPause = true;
            mRequestStart = false;

            flushAudioPlaybackRateProcessor();
            YYLog.info(TAG, "pause " + mPlayPositionInMS + " >> " + mPlayerPlayPositionInMS);
        }
    }

    public void seek(long positionMS) {
        synchronized (this) {
            //int delayMS = AudioTrackWrapper.getInstance().getDelayInMS();
            //positionMS += delayMS;
            if (mPlayerPlayPositionInMS != positionMS) {
                for (AudioPlayer audioPlayer : mAudioPlayers) {
                    audioPlayer.seek(positionMS);
                }

                mPlayerPlayPositionInMS = positionMS;
                mPlayPositionInMS = positionMS;

                if (mFFTProcessor.isEnable()) {
                    //mShouldUpdateFFT = true;
                }
            }
            YYLog.info(TAG, "seek %d >> %d ", positionMS, mPlayerPlayPositionInMS);
            //mShouldResetConsumePTS = true;
        }
    }

    public long getCurrentPlayPositionMS() {
        synchronized (this) {
            long positionMS = mPlayerPlayPositionInMS;
            /*
            int numOfMSAvailable = 0;
            int numOfMSUnprocess = 0;
            if (mAudioPlaybackRateProcessor != null) {
                numOfMSAvailable = mAudioPlaybackRateProcessor.numOfMSAvailable();
                numOfMSUnprocess = mAudioPlaybackRateProcessor.numOfMSUnprocess();
            }
            */
//            YYLog.info(TAG, " getCurrentPlayPositionMS %d", positionMS);
            return positionMS;
        }
    }

    public void setPlaybackRate(float rate) {
        synchronized (this) {
            if (Float.compare(rate, 1.0f) != 0) {
                if (mAudioPlaybackRateProcessor == null) {
                    mAudioPlaybackRateProcessor = new AudioPlaybackRateProcessor();
                    mAudioPlaybackRateProcessor.init(AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT, false);
                }
            }
            if (mAudioPlaybackRateProcessor != null) {
                mAudioPlaybackRateProcessor.setRate(rate);
            }
        }
        YYLog.info(TAG, "setPlaybackRate " + rate);
    }

    private int getID() {
        synchronized (this) {
            int id = mID++;
            if (mID == Integer.MAX_VALUE) {
                mID = 0;
            }
            return id;
        }
    }

    public void enableFrequencyCalculate(boolean enable) {
        mFFTProcessor.setEnable(enable);
    }

    public int frequencyData(float[] buffer, int len) {
        return mFFTProcessor.frequencyData(buffer, len);
    }

    private boolean isFinish(long positionMS) {
        for (AudioPlayer audioPlayer : mAudioPlayers) {
            if (!audioPlayer.isFinish(positionMS)) {
                return false;
            }
        }
        return true;
    }


    public String record(String path, long durationMS) {
        return record(path, null, durationMS);
    }

    public String record(String path, String fftPath, long durationMS) {
        synchronized (this) {
            boolean isPause = mForcePause;
            mForcePause = true;
            boolean haveMagicAudio = false;
            long curPosition = getCurrentPlayPositionMS();
            boolean shouldCalcFFT = false;
            int lastFFTLen = 0;
            int fftLen = kFFT_LEN * AudioTrackWrapper.kCHANNEL_COUNT * 2;
            FileOutputStream fftFile = null;
            float[] fftDataF = null;
            byte[] fftDataB = null;
            int mixLenMS = 0;

            if (!mAudioPlayers.isEmpty()) {
                if (mFFTProcessor.isEnable() && fftPath != null) {
                    try {
                        fftFile = new FileOutputStream(fftPath);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        fftFile = null;
                    }
                }
                if (fftFile != null) {
                    shouldCalcFFT = true;
                    fftDataF = new float[kFFT_LEN / 2];
                    fftDataB = new byte[kFFT_LEN / 2];
                }
                seek(0);
                WavFileWriter wavFileWriter = new WavFileWriter();
                wavFileWriter.open(path, AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT);
                long positionMS = 0;
                if (durationMS < 0) {
                    durationMS = kMAX_EXPORT_SIZE_MS;
                }
                byte[] mixBuffer = new byte[AudioTrackWrapper.kPLAY_PERIOD_BYTES];
                byte[] tmpBuffer = new byte[AudioTrackWrapper.kPLAY_PERIOD_BYTES];
                while ((positionMS + AudioTrackWrapper.kPLAY_PERIOD_MS) < durationMS) {
                    if (isFinish(positionMS)) {
                        break;
                    }
                    Arrays.fill(mixBuffer, (byte) 0);
                    boolean isErasure = false;
                    for (AudioPlayer player : mAudioPlayers) {
                        player.setErasure(isErasure);
                        int readLen = player.read(tmpBuffer, AudioTrackWrapper.kPLAY_PERIOD_BYTES, positionMS);
                        if (readLen > 0) {
                            haveMagicAudio = true;
                            AudioSimpleMixer.mix(tmpBuffer, player.getVolume(), mixBuffer, 1.0f, readLen);
                            mixLenMS += AudioTrackWrapper.kPLAY_PERIOD_MS;
                        }
                        // first effect audio or erasure audio will cover other effect audio
                        if (player.isErasure(positionMS)) {
                            isErasure = true;
                        }
                    }
                    positionMS += AudioTrackWrapper.kPLAY_PERIOD_MS;
                    wavFileWriter.write(mixBuffer, 0, AudioTrackWrapper.kPLAY_PERIOD_BYTES);

                    if (shouldCalcFFT) {
                        int fftOffset = 0;
                        int inputFFTLen = 0;
                        for (int i = AudioTrackWrapper.kPLAY_PERIOD_BYTES; i > 0; ) {
                            inputFFTLen = fftLen - lastFFTLen;
                            inputFFTLen = inputFFTLen > i ? i : inputFFTLen;
                            mFFTProcessor.process(mixBuffer, fftOffset, inputFFTLen, AudioTrackWrapper.kCHANNEL_COUNT);
                            i -= inputFFTLen;
                            fftOffset += inputFFTLen;
                            if ((inputFFTLen + lastFFTLen) == fftLen) {
                                mFFTProcessor.frequencyData(fftDataF, kFFT_LEN / 2);
                                for (int j = 0; j < kFFT_LEN / 2; j++) {
                                    fftDataB[j] = (byte) (fftDataF[j] * 256);
                                }
                                try {
                                    fftFile.write(fftDataB, 0, kFFT_LEN / 2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        lastFFTLen = inputFFTLen % fftLen;
                    }
                }

                wavFileWriter.close();
                if (fftFile != null) {
                    try {
                        fftFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            seek(curPosition);
            mForcePause = isPause;
            YYLog.info(TAG, "exportAudioFile %d %d %d %s", haveMagicAudio ? 1 : 0, mixLenMS, durationMS, path);
            if (haveMagicAudio) {
                return path;
            } else {
                return null;
            }
        }
    }

    private int readDataFromAudioPlayers(byte[] buffer, int requestLen) {
        int mixLen = 0;
        Arrays.fill(buffer, (byte) 0);
        boolean isErasure = false;
        for (AudioPlayer audioPlayer : mAudioPlayers) {
            audioPlayer.setErasure(isErasure);
            Arrays.fill(mTmpBuffer, (byte) 0);
            int readLen = audioPlayer.read(mTmpBuffer, requestLen, mPlayerPlayPositionInMS);
            if (readLen > 0) {
                AudioSimpleMixer.mix(mTmpBuffer, audioPlayer.getVolume(), buffer, 1.0f, readLen);
            }
            mixLen = readLen > mixLen ? readLen : mixLen;
            // first effect audio or erasure audio will cover other effect audio
            if (audioPlayer.isErasure(mPlayPositionInMS)) {
                isErasure = true;
            }
        }

        int lenMS = (int) (((long) requestLen * AudioTrackWrapper.kPLAY_PERIOD_MS) / AudioTrackWrapper.kPLAY_PERIOD_BYTES);
        synchronized (this) {
            mPlayerPlayPositionInMS += lenMS;
        }
        return requestLen;
    }

    private void flushAudioPlaybackRateProcessor() {
        int len;
        if (mAudioPlaybackRateProcessor != null && mTmpBuffer != null) {
            int numOfMSAvaiable = mAudioPlaybackRateProcessor.numOfMSAvailable();
            int numOfMSUnprocess = mAudioPlaybackRateProcessor.numOfMSUnprocess();
            mAudioPlaybackRateProcessor.flush();

            int numOfMSAvaiable2 = mAudioPlaybackRateProcessor.numOfMSAvailable();
            int numOfMSUnprocess2 = mAudioPlaybackRateProcessor.numOfMSUnprocess();
            YYLog.info(TAG, "flush playback rate processor %d %d >> %d %d", numOfMSAvaiable, numOfMSUnprocess, numOfMSAvaiable2, numOfMSUnprocess2);
        }
    }

    private int readDataFromProcessor(byte[] buffer, int requestLen) {
        int tmpRequestLen = requestLen;
        int offset = 0;
        int outLen = 0;
        while (true) {
            int len;
            do {
                len = mAudioPlaybackRateProcessor.pull(buffer, offset, tmpRequestLen);
                offset += len;
                tmpRequestLen -= len;
                outLen += len;
                if (tmpRequestLen <= 0) {
                    return outLen;
                }
            } while (len != 0);

            if (mMixedBuffer == null || mMixedBuffer.length < requestLen) {
                mMixedBuffer = new byte[requestLen];
            }
            int mixLen = 0;
            if(!mRequestPause) {
                mixLen = readDataFromAudioPlayers(mMixedBuffer, requestLen);
            }
            if (mixLen > 0) {
                mAudioPlaybackRateProcessor.push(mMixedBuffer, mixLen);
            } else {
                break;
            }
        }
        return outLen;
    }

    @Override
    public int onConsumeAudioData(byte[] buffer, int requestLen, int delayMS) {
        int outLen = 0;
        Arrays.fill(buffer, (byte) 0);
        synchronized (this) {
            if (mForcePause) {
                return 0;
            }
            mNumOfDataInAudioTrackInMS = delayMS;
            long currPTS = System.currentTimeMillis();
            if (mRequestStart && mPlayState != PLAY_STATE.PLAY_STATE_PLAYING && mPlayState != PLAY_STATE.PLAY_STATE_WAIT_TO_PLAY ) {
                mPlayState = PLAY_STATE.PLAY_STATE_WAIT_TO_PLAY;
                mLastConsumePTS = currPTS;
                YYLog.info(TAG, " PLAY_STATE_WAIT_TO_PLAY");
            }

            if (mPlayState != PLAY_STATE.PLAY_STATE_WAIT_TO_PAUSE && mPlayState != PLAY_STATE.PLAY_STATE_PAUSE) {
                if (mTmpBuffer == null || mTmpBuffer.length < requestLen) {
                    mTmpBuffer = new byte[requestLen];
                }
                if (mAudioPlaybackRateProcessor != null) {
                    outLen = readDataFromProcessor(buffer, requestLen);
                } else {
                    if (!mRequestPause) {
                        outLen = readDataFromAudioPlayers(buffer, requestLen);
                    }
                }

                if (outLen > 0) {
                    mFFTProcessor.process(buffer, 0, outLen, AudioTrackWrapper.kCHANNEL_COUNT);
                }

                mPlayPositionInMS += (currPTS - mLastConsumePTS);
                mLastConsumePTS = currPTS;
            }

            if (mRequestStart && mPlayState != PLAY_STATE.PLAY_STATE_PLAYING && mPlayState == PLAY_STATE.PLAY_STATE_WAIT_TO_PLAY) {
                YYLog.info(TAG, " start delay " + mNumOfDataInAudioTrackInMS);
                mStartPlayPTS = System.currentTimeMillis() + mNumOfDataInAudioTrackInMS + AudioTrackWrapper.kSYSTEM_PLAY_DELAY_MS;
                mRequestStart = false;
            }

            if (mPlayState == PLAY_STATE.PLAY_STATE_WAIT_TO_PLAY) {
                if (currPTS >= mStartPlayPTS) {
                    if (mListener != null) {
                        mListener.onAudioPlayStart();
                        YYLog.info(TAG, " onAudioPlayStart " + mPlayerPlayPositionInMS + " >> " + delayMS);
                    }
                    mPlayState = PLAY_STATE.PLAY_STATE_PLAYING;
                    YYLog.info(TAG, " PLAY_STATE_PLAYING");
                }
            }

            if (outLen != requestLen && mRequestPause && mPlayState != PLAY_STATE.PLAY_STATE_PAUSE && mPlayState != PLAY_STATE.PLAY_STATE_WAIT_TO_PAUSE) {
                mPlayState = PLAY_STATE.PLAY_STATE_WAIT_TO_PAUSE;
                mStopPlayPTS = System.currentTimeMillis() + mNumOfDataInAudioTrackInMS + AudioTrackWrapper.kSYSTEM_PLAY_DELAY_MS;
                mRequestPause = false;
                YYLog.info(TAG, " PLAY_STATE_WAIT_TO_PAUSE %d", mNumOfDataInAudioTrackInMS);
            }
            if (mPlayState == PLAY_STATE.PLAY_STATE_WAIT_TO_PAUSE) {
                if (currPTS >= mStopPlayPTS) {
                    if (mListener != null) {
                        int numOfMSUnprocess = 0;
                        if (mAudioPlaybackRateProcessor != null) {
                            numOfMSUnprocess = mAudioPlaybackRateProcessor.numOfMSUnprocess();
                        }
                        long positionMS = getCurrentPlayPositionMS() - numOfMSUnprocess;
                        mListener.onAudioPlayStop(positionMS);
                        YYLog.info(TAG, " onAudioPlayStop ");
                    }
                    mPlayState = PLAY_STATE.PLAY_STATE_PAUSE;
                    YYLog.info(TAG, " PLAY_STATE_PAUSE");
                }
            }
            updateFFT();
        }
        return outLen;
    }

    private void updateFFT() {
        if (mPlayState == PLAY_STATE.PLAY_STATE_PAUSE && mShouldUpdateFFT && mFFTProcessor.isEnable()) {
            int tmpLen = AudioTrackWrapper.kPLAY_PERIOD_BYTES * 2;
            byte[] tmpBuffer = new byte[tmpLen];
            long orgPosition = mPlayerPlayPositionInMS;
            Arrays.fill(tmpBuffer, (byte) 0);
            readDataFromAudioPlayers(tmpBuffer, tmpLen);
            mFFTProcessor.process(tmpBuffer, 0, tmpLen, AudioTrackWrapper.kCHANNEL_COUNT);
            mShouldUpdateFFT = false;

            for (AudioPlayer audioPlayer : mAudioPlayers) {
                audioPlayer.seek(orgPosition);
            }

            mPlayerPlayPositionInMS = orgPosition;
            mPlayPositionInMS = orgPosition;
        }
    }
}
