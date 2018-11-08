package com.ycloud.audio;

import android.text.TextUtils;

import com.ycloud.utils.YYLog;
import java.io.File;

/**
 * Created by Administrator on 2018/1/4.
 */

public class FingerMagicAudioFileReader extends AudioFileReader {
    static final String TAG = "FingerMagicAudioFileReader";
    AudioFileReader mAudioFileReader;
    WavFileWriter mCacheFileWriter;
    boolean mShouldWriteToCacheTmp;
    boolean mFinishCacheTmp;
    String mCacheFilePath;
    String mCacheTmpFilePath;
    long mDurationMS;

    public FingerMagicAudioFileReader() {

    }

    @Override
    public long open(String path) {
        mCacheFilePath = AudioFileCacheMgr.getInstance().getCacheFilePath(path, getOutSampleRate(), getOutChannels());
        if (mCacheFilePath == null) {
            return 0;
        }
        try {


            if (!new File(mCacheFilePath).exists()) {
                mAudioFileReader = new EncodedAudioFileReader();
                mAudioFileReader.setOutputFormat(getOutSampleRate(), getOutChannels());
                mDurationMS = mAudioFileReader.open(path);
                mCacheTmpFilePath = AudioFileCacheMgr.getInstance().getCacheTmpFilePath(path, getOutSampleRate(), getOutChannels());
                mCacheFileWriter = new WavFileWriter();
                mCacheFileWriter.open(mCacheTmpFilePath, mAudioFileReader.getOutSampleRate(), mAudioFileReader.getOutChannels());
                mShouldWriteToCacheTmp = true;
                mFinishCacheTmp = false;
                YYLog.info(TAG, " use orig file " + path);
            } else {
                mAudioFileReader = new WavFileReader();
                mAudioFileReader.setOutputFormat(getOutSampleRate(), getOutChannels());
                mDurationMS = mAudioFileReader.open(mCacheFilePath);
                if (mDurationMS == 0) {
                    // try orig file
                    mAudioFileReader.close();
                    mAudioFileReader = null;
                    mAudioFileReader = new EncodedAudioFileReader();
                    mAudioFileReader.setOutputFormat(getOutSampleRate(), getOutChannels());
                    mDurationMS = mAudioFileReader.open(path);
                }
                mShouldWriteToCacheTmp = false;
                mFinishCacheTmp = true;
                YYLog.info(TAG, " use cache file " + mCacheFilePath);
            }
            if (mDurationMS <= 0) {
                close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return mDurationMS;
    }

    @Override
    public int getInSampleRate() {
        if (mAudioFileReader != null) {
            try {
                mAudioFileReader.getInSampleRate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    @Override
    public int getInChannels() {
        if (mAudioFileReader != null) {
            try {
                return mAudioFileReader.getInChannels();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    @Override
    public long getFilePositionInMS() {
        if (mAudioFileReader != null) {
            mAudioFileReader.getFilePositionInMS();
        }
        return 0;
    }

    @Override
    public void close() {
        super.close();
        if (mAudioFileReader != null) {
            mAudioFileReader.close();
            mAudioFileReader = null;
        }

        if (mCacheFileWriter != null) {
            mCacheFileWriter.close();
            mCacheFileWriter = null;
        }

        if (mCacheTmpFilePath != null) {
            new File(mCacheTmpFilePath).delete();
        }
    }

    @Override
    protected int read_inner(byte[] outputBuffer, int reqLen) {
        int readLen = -1;
        if (mAudioFileReader != null) {
            try {
                readLen = mAudioFileReader.read(outputBuffer, reqLen);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mShouldWriteToCacheTmp) {
                writeDataToCacheFile(outputBuffer, readLen);
            }
        }
        return readLen;
    }

    private void writeDataToCacheFile(byte[]data, int len) {
        if (len > 0) {
            if (mShouldWriteToCacheTmp && mCacheFileWriter != null) {
                mCacheFileWriter.write(data, 0, len);
            }
        } else {
            // Read file to end, so finish cache
            if (mShouldWriteToCacheTmp && mCacheFileWriter != null ) {
                long sizeMS = mCacheFileWriter.sizeMS();
                if (Math.abs(mDurationMS - sizeMS) < 500) {
                    mShouldWriteToCacheTmp = false;
                    mCacheFileWriter.close();
                    mCacheFileWriter = null;
                    AudioFileCacheMgr.getInstance().finishCache(mCacheTmpFilePath);
                    switchToCacheFile(mCacheFilePath);
                }else {
                    mShouldWriteToCacheTmp = false;
                    mCacheFileWriter.resetData();
                }
            }
        }
    }

    @Override
    protected void seek_inner(long positionMS) {
        try {
            super.seek_inner(positionMS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mAudioFileReader != null) {
            mAudioFileReader.seek(positionMS);
        }
        if (positionMS == 0) {
            //Try cache when seek to 0
            if (!mFinishCacheTmp && !switchToCacheFile(mCacheFilePath)) {
                mShouldWriteToCacheTmp = true;
                if (mCacheFileWriter != null) {
                    mCacheFileWriter.resetData();
                }
            }
        } else {
            //Don't cache when seek to insure cache integrity
            mShouldWriteToCacheTmp = false;
        }
    }

    private boolean switchToCacheFile(String path) {
        if (!TextUtils.isEmpty(path) && new File(path).exists()) {
            AudioFileReader cacheFileReader = new WavFileReader();
            long lenMS = 0;
            try {
                lenMS = cacheFileReader.open(path);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (lenMS == 0) {
                cacheFileReader.close();
            } else {
                if (mAudioFileReader != null) {
                    mAudioFileReader.close();
                    mFinishCacheTmp = true;
                    mShouldWriteToCacheTmp = false;
                    mAudioFileReader = cacheFileReader;
                    return true;
                }
            }
        }
        return false;
    }
}
