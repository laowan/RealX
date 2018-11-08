package com.ycloud.audio;

import android.util.Log;

import com.ycloud.utils.YYLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by Administrator on 2018/1/19.
 */

public class AudioFilePlayer extends AudioPlayer {
    static final String TAG = "AudioFilePlayer";
    static final long kMAX_PLAY_DURATION_SIZE_MS = 1000 * 60 * 10; // 10 minute
    private boolean mIsPause;
    private long mStartPlayPositionInMS;
    private long mStopPlayPositionInMS;
    private AudioFileReader mAudioFileReader;

    private String mCacheFilePath;
    private RandomAccessFile mCacheFile;
    private boolean mShouldWriteDataToCache;
    private boolean mShouldCacheFile;
    private boolean mFinishCache;

    private long mBeginReadPositionInMS;
    private long mCurReadPositionInMS;
    private long mEndReadPositionInMS;
    private long mFileLenInMS;
    private boolean mIsLoop;
    private boolean mIsStop;

    public AudioFilePlayer(int ID) {
        super(ID);
    }

    /**
     * prepare, this should be called before {@link #start(long)}
     *
     * @param path
     * @param beginReadPositionMS
     * @param endReadPositionInMS, file to end if set to -1
     */
    public void prepare(String path, long beginReadPositionMS, long endReadPositionInMS, boolean loop) {
        mBeginReadPositionInMS = beginReadPositionMS;
        mEndReadPositionInMS = endReadPositionInMS;
        mIsLoop = loop;
        mIsPause = false;
        int dotPosition = path.lastIndexOf(".");
        String suffix = path.substring(dotPosition + 1);
        if (suffix.equals("wav")) {
            mAudioFileReader = new WavFileReader();
            mShouldCacheFile = false;
        } else {
            mAudioFileReader = new EncodedAudioFileReader();
            mShouldCacheFile = true;
        }
        mAudioFileReader.setOutputFormat(AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT);
        try {
            mFileLenInMS = mAudioFileReader.open(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mBeginReadPositionInMS < 0 || mBeginReadPositionInMS >= mFileLenInMS) {
            mBeginReadPositionInMS = 0;
        }
        if (mEndReadPositionInMS <= 0 || mEndReadPositionInMS >= mFileLenInMS) {
            mEndReadPositionInMS = mFileLenInMS;
        }
        if (mEndReadPositionInMS != 0) {
            mFileLenInMS = mEndReadPositionInMS - mBeginReadPositionInMS;
        }
        mCurReadPositionInMS = mBeginReadPositionInMS;
        if (mBeginReadPositionInMS != 0) {
            mAudioFileReader.seek(mBeginReadPositionInMS);
        }

        if (mShouldCacheFile) {
            mShouldWriteDataToCache = true;
            mFinishCache = false;
            mCacheFilePath = AudioFileCacheMgr.getInstance().getCacheTmpFilePath(path, 0, 0);
            try {
                mCacheFile = new RandomAccessFile(mCacheFilePath, "rw");
            } catch (Exception e) {
                mShouldCacheFile = false;
                e.printStackTrace();
            }
        }
        YYLog.info(TAG, "prepare %d", mFileLenInMS);
    }

    @Override
    public void release() {
        mAudioFileReader.close();
        if (mShouldCacheFile) {
            try {
                mCacheFile.close();
                File cacheFile = new File(mCacheFilePath);
                cacheFile.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param startPlayPositionInMS the position offset that  relative to AudioPlayEditor play position
     */
    @Override
    public void start(long startPlayPositionInMS) {
        mShouldWriteDataToCache = true;
        mStartPlayPositionInMS = startPlayPositionInMS;
        mStopPlayPositionInMS = mStartPlayPositionInMS + kMAX_PLAY_DURATION_SIZE_MS; // init stop play position
        mCurReadPositionInMS = mBeginReadPositionInMS;
        if (mBeginReadPositionInMS != 0) {
            mAudioFileReader.seek(mBeginReadPositionInMS);
        }
    }

    /**
     *
     * @param stopPlayPositionInMS the position offset that  relative to AudioPlayEditor play position
     */
    @Override
    public void stop(long stopPlayPositionInMS) {
        if (!mIsStop) {
            mStopPlayPositionInMS = stopPlayPositionInMS;
            mIsStop = true;
            if (mShouldCacheFile && !mFinishCache && mShouldWriteDataToCache) {
                finishCache();
            }
        }
        YYLog.info(TAG, "stop");
    }

    @Override
    public void pause() {
        mIsPause = true;
        YYLog.info(TAG, "pause ");
    }

    @Override
    public void resume() {
        mIsPause = false;
        YYLog.info(TAG, "resume");
    }

    @Override
    public void seek(long positionInMS) {
        if (positionInMS < mStartPlayPositionInMS) {
            seek_file(0);
        }
        if (positionInMS >= mStartPlayPositionInMS && positionInMS < mStopPlayPositionInMS) {
            long playOffset = positionInMS - mStartPlayPositionInMS;
            if (mFileLenInMS > 0) {
                playOffset = playOffset % mFileLenInMS;
            }
            seek_file(playOffset);
        }
    }

    @Override
    public boolean isFinish(long playPositionMS) {
        return playPositionMS >= mStopPlayPositionInMS;
    }

    @Override
    public long getPlayPositionInMS() {
        return mCurReadPositionInMS;
    }

    @Override
    public int read(byte[] buffer, int requestLen, long playPositionMS) {
        int readLen = 0;
        if (mIsPause) {
            return readLen;
        }
        if (!mIsPause) {
            if (playPositionMS >= mStartPlayPositionInMS && playPositionMS < mStopPlayPositionInMS) {
                readLen = read_file(buffer, requestLen);
                if (readLen < 0) {
                    if (mIsLoop) {
                        seek_file(0);
                        readLen = read_file(buffer, requestLen);
                    } else {
                        stop(playPositionMS);
                    }
                }
            }
        }
        return readLen;
    }

    private void seek_file(long positionMS) {
        if (positionMS == mCurReadPositionInMS) {
            return;
        }
        YYLog.info(TAG, "seek %d >> %d", positionMS, mCurReadPositionInMS);
        if (mFinishCache) {
            long pos = AudioTrackWrapper.kSAMPLE_RATE * AudioTrackWrapper.kCHANNEL_COUNT * 2 * positionMS / 1000;
            int frameSizeInBytes = AudioTrackWrapper.kCHANNEL_COUNT * 2;
            pos = pos / frameSizeInBytes * frameSizeInBytes; //round for frame position
            try {
                mCacheFile.seek(pos);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCurReadPositionInMS = positionMS;
        } else {
            long readOffset = mBeginReadPositionInMS + positionMS;
            mAudioFileReader.seek(readOffset);
            mCurReadPositionInMS = readOffset;
            if (mShouldCacheFile) {
                if (positionMS != 0) {
                    mShouldWriteDataToCache = false;
                } else {
                    if (!mFinishCache) {
                        mShouldWriteDataToCache = true;
                        try {
                            mCacheFile.setLength(0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private int read_file(byte[] buffer, int requestLen) {
        int readLen = 0;
        if (mFinishCache) {
            try {
                readLen = mCacheFile.read(buffer, 0, requestLen);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                readLen = mAudioFileReader.read(buffer, requestLen);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mCurReadPositionInMS >= mEndReadPositionInMS) {
                readLen = -1;
            }
            if (readLen > 0) {
                if (mShouldCacheFile && !mFinishCache && mShouldWriteDataToCache) {
                    try {
                        mCacheFile.write(buffer, 0, readLen);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (mShouldCacheFile && !mFinishCache && mShouldWriteDataToCache) {
                    finishCache();
                }
            }
        }
        if (readLen > 0) {
            int lenMS = readLen * 1000 / (AudioTrackWrapper.kSAMPLE_RATE * AudioTrackWrapper.kCHANNEL_COUNT * 2);
            mCurReadPositionInMS += lenMS;
        }
        return readLen;
    }

    private void finishCache() {
        YYLog.info(TAG, "finishCache");
        mFinishCache = true;
        mShouldWriteDataToCache = false;
        long playDuration = mEndReadPositionInMS - mBeginReadPositionInMS;
        mBeginReadPositionInMS = 0;
        mEndReadPositionInMS = mBeginReadPositionInMS + playDuration;
        mCurReadPositionInMS = 0;
        try {
            mCacheFile.seek(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
