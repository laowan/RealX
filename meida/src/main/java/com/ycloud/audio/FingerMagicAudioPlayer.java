package com.ycloud.audio;

import com.ycloud.utils.YYLog;

/**
 * Created by Administrator on 2017/12/30.
 */

public class FingerMagicAudioPlayer extends AudioPlayer {
    static final String TAG = "FingerMagicAudioPlayer";
    private boolean mIsPause;
    private long mPlayDurationMS = -1;

    private String mBeginFilePathToPlay;
    private FingerMagicAudioFileReader mBeginFileDecoder;
    private long mBeginFileDurationMS;
    private long mBeginFileStartPlayPositionMS;

    private String mMainFilePathToPlay;
    private FingerMagicAudioFileReader mMainFileDecoder;
    private long mMainFileStartPlayPositionMS;
    private long mMainFileDurationMS;
    private int mMainFileLoopCount;
    private int mMainFilePlayingCount;

    private String mEndFilePathToPlay;
    private FingerMagicAudioFileReader mEndFileDecoder;
    private long mEndFileStartPlayPositionMS;
    private long mEndFileDurationMS;

    private int mFilePlaying; // -1 for not play or finish play,  0 for begin, 1 for main, 2 for end
    protected PLAY_STATE mState;
    private boolean mIsEditing;

    enum PLAY_STATE {
        PLAY_STATE_WAIT_TO_PLAY,
        PLAY_STATE_PLAYING,
        PLAY_STATE_FINISH,
    }

    public FingerMagicAudioPlayer(int ID) {
        super(ID);
    }

    public int prepare(String[] paths) {
        mBeginFilePathToPlay = paths[0];
        mMainFilePathToPlay = paths[1];
        mEndFilePathToPlay = paths[2];

        mBeginFileDecoder = new FingerMagicAudioFileReader();
        mBeginFileDecoder.setOutputFormat(AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT);
        mBeginFileDurationMS = mBeginFileDecoder.open(mBeginFilePathToPlay);

        mMainFileStartPlayPositionMS = mBeginFileDurationMS;
        mMainFileDecoder = new FingerMagicAudioFileReader();
        mMainFileDecoder.setOutputFormat(AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT);
        mMainFileDurationMS = mMainFileDecoder.open(mMainFilePathToPlay);

        mEndFileDecoder = new FingerMagicAudioFileReader();
        mEndFileDecoder.setOutputFormat(AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT);
        mEndFileDurationMS = mEndFileDecoder.open(mEndFilePathToPlay);
        mIsEditing = false;

        mPlayDurationMS = -1;
        return 0;
    }

    @Override
    public void release() {
        if (mBeginFileDecoder != null) {
            mBeginFileDecoder.close();
            mBeginFileDecoder = null;
        }
        if (mMainFileDecoder != null) {
            mMainFileDecoder.close();
            mMainFileDecoder = null;
        }
        if (mEndFileDecoder != null) {
            mEndFileDecoder.close();
            mEndFileDecoder = null;
        }
    }

    @Override
    public void start(long startPlayPositionInMS) {
        if (!mIsEditing) {
            mBeginFileStartPlayPositionMS = startPlayPositionInMS - mBeginFileDurationMS;
            mMainFileStartPlayPositionMS = startPlayPositionInMS;
            mState = PLAY_STATE.PLAY_STATE_PLAYING;
            mFilePlaying = 1;
            mMainFileLoopCount = 99;
            mMainFilePlayingCount = 0;
            mIsEditing = true;
            YYLog.info(TAG, "begin edit " + startPlayPositionInMS);
        }
    }

    @Override
    public void stop(long stopPlayPositionInMS) {
        if (mIsEditing) {
            if (mFilePlaying == 1) {
                mMainFileLoopCount = mMainFilePlayingCount + 1;
            } else {
                mMainFileLoopCount = 0;
            }
            if (mEndFileDurationMS > 0) {
                mEndFileStartPlayPositionMS = mMainFileStartPlayPositionMS + mMainFileLoopCount * mMainFileDurationMS;
            }else {
                mEndFileStartPlayPositionMS = stopPlayPositionInMS;
            }
            mPlayDurationMS = mEndFileStartPlayPositionMS + mEndFileDurationMS;
            YYLog.info(TAG, " endEdit " + mBeginFileStartPlayPositionMS + " : " + mMainFileStartPlayPositionMS + " : " + mEndFileStartPlayPositionMS + " >> " + mPlayDurationMS);
            mIsEditing = false;
            mState = PLAY_STATE.PLAY_STATE_FINISH;
        }
    }

    @Override
    public void pause() {
        mIsPause = true;
    }

    @Override
    public void resume() {
        mIsPause = false;
        YYLog.info(TAG, "resume");
    }

    @Override
    public void seek(long curPlayPosition) {
        if (mPlayDurationMS != -1 && curPlayPosition > mPlayDurationMS) {
            mState = PLAY_STATE.PLAY_STATE_FINISH;
            return;
        }
        long seekPosition;
        mFilePlaying = -1;
        mMainFilePlayingCount = 0;
        mState = PLAY_STATE.PLAY_STATE_WAIT_TO_PLAY;
        mBeginFileDecoder.seek(0);
        mMainFileDecoder.seek(0);
        mEndFileDecoder.seek(0);
        if (curPlayPosition >= mBeginFileStartPlayPositionMS && curPlayPosition < mMainFileStartPlayPositionMS) {
            // seek in begin file
            if (mBeginFileStartPlayPositionMS >= 0) {
                seekPosition = curPlayPosition - mBeginFileStartPlayPositionMS;
                mBeginFileDecoder.seek(seekPosition);
            }
            mFilePlaying = 0;
            mState = PLAY_STATE.PLAY_STATE_PLAYING;
        } else if (curPlayPosition >= mMainFileStartPlayPositionMS && curPlayPosition < mEndFileStartPlayPositionMS) {
            // seek in main file
            seekPosition = curPlayPosition - mMainFileStartPlayPositionMS;
            if (mMainFileDurationMS > 0) {
                mMainFilePlayingCount = (int) (seekPosition / mMainFileDurationMS);
                seekPosition = seekPosition % mMainFileDurationMS;
            }
            mMainFileDecoder.seek(seekPosition);
            mFilePlaying = 1;
            mState = PLAY_STATE.PLAY_STATE_PLAYING;
        } else if (curPlayPosition >= mEndFileStartPlayPositionMS && curPlayPosition < mPlayDurationMS) {
            // seek in end file
            seekPosition = curPlayPosition - mEndFileStartPlayPositionMS;
            mEndFileDecoder.seek(seekPosition);
            mFilePlaying = 2;
            mState = PLAY_STATE.PLAY_STATE_PLAYING;
        }
    }

    @Override
    public boolean isFinish(long positionMS) {
        return mState == PLAY_STATE.PLAY_STATE_FINISH;
    }

    @Override
    public int read(byte[] buffer, int reqLen, long curPlayPosition) {
        int readLen = 0;
        try {


            if (mState == PLAY_STATE.PLAY_STATE_FINISH) {
                return readLen;
            }
            if (mPlayDurationMS != -1 && curPlayPosition >= mPlayDurationMS) {
                mState = PLAY_STATE.PLAY_STATE_FINISH;
                return readLen;
            }
            if (mState == PLAY_STATE.PLAY_STATE_WAIT_TO_PLAY) {
                if (curPlayPosition >= mBeginFileStartPlayPositionMS) {
                    mState = PLAY_STATE.PLAY_STATE_PLAYING;
                    mFilePlaying = 0;
                } else {
                    return readLen;
                }
            }

            if (mFilePlaying == 0) {
                if (mBeginFileStartPlayPositionMS >= 0) {
                    readLen = mBeginFileDecoder.read(buffer, reqLen);
                } else {
                    readLen = -1;
                }
                if (readLen <= 0) {
                    if (mMainFileLoopCount > 0) {
                        mFilePlaying = 1;
                    } else {
                        mFilePlaying = 2;
                    }
                }
            }

            if (mFilePlaying == 1) {
                readLen = mMainFileDecoder.read(buffer, reqLen);
                if (readLen <= 0) {
                    mMainFilePlayingCount++;
                    if (mMainFilePlayingCount < mMainFileLoopCount) {
                        mMainFileDecoder.seek(0);
                        readLen = mMainFileDecoder.read(buffer, reqLen);
                    } else {
                        mFilePlaying = 2;
                    }
                }
            }

            if (mFilePlaying == 2) {
                readLen = mEndFileDecoder.read(buffer, reqLen);
                if (readLen <= 0) {
                    mFilePlaying = -1;
                    mState = PLAY_STATE.PLAY_STATE_FINISH;
                    YYLog.info(TAG, " finish play magic audio ");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return readLen;
    }
}
