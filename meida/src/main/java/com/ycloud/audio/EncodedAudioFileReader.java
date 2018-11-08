package com.ycloud.audio;

import android.annotation.TargetApi;
import android.os.Build;

import com.ycloud.utils.YYLog;

/**
 * Created by Administrator on 2017/12/30.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class EncodedAudioFileReader extends AudioFileReader {
    private AudioFileReader mAudioFileReader;
    private String mFilePath;
    private long mPositionInMS;

    private static final String TAG = "EncodedAudioFileReader";

    public EncodedAudioFileReader() {
        mAudioFileReader = new HardAudioFileReader();
        //mAudioFileReader = new FFmpegAudioFileReader();
    }

    @Override
    public long open(String path) {
        mFilePath = path;
        mPositionInMS = 0;
        try {
            mAudioFileReader.setOutputFormat(mOutSampleRate, mOutChannels);
            long lenMS = mAudioFileReader.open(path);
            if (mOutSampleRate == 0) {
                mOutSampleRate = mAudioFileReader.getInSampleRate();
            }
            if (mOutChannels == 0) {
                mOutChannels = mAudioFileReader.getInChannels();
            }
            return lenMS;
        } catch (Exception e) {
            YYLog.e(TAG, " open: " + e.toString());
            return switchReader();
        }
    }

    @Override
    public void close() {
        super.close();
        mAudioFileReader.close();
        mAudioFileReader = null;
    }

    @Override
    protected void seek_inner(long positionMS) {
        mPositionInMS = positionMS;
        try {
            super.seek_inner(positionMS);
            mAudioFileReader.seek(positionMS);
        } catch (Exception e) {
            e.printStackTrace();
            switchReader();
            try {
                mAudioFileReader.seek(positionMS);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    protected int read_inner(byte[] outputBuffer, int reqLen) {
        try {
            int retLen;
            retLen = mAudioFileReader.read(outputBuffer, reqLen);
            if (retLen > 0) {
                long lenMS = retLen * 1000 / (getOutSampleRate() * getOutChannels() * 2);
                mPositionInMS += lenMS;
            }
            return  retLen;
        } catch (Exception e) {
            YYLog.e(TAG, " read_inner" + e.toString());
            switchReader();
            return read_inner(outputBuffer, reqLen);
        }
    }

    @Override
    public int getInSampleRate() {
        return mOutSampleRate;
    }

    @Override
    public int getInChannels() {
        return mOutChannels;
    }

    @Override
    public long getFilePositionInMS() {
        return mAudioFileReader.getFilePositionInMS();
    }

    private long switchReader() {
        mAudioFileReader.close();
        mAudioFileReader = new FFmpegAudioFileReader();
        mAudioFileReader.setOutputFormat(mOutSampleRate, mOutChannels);
        long lenMS = 0;
        YYLog.info(TAG, " switch reader: " + mPositionInMS);
        try {
            lenMS = mAudioFileReader.open(mFilePath);
            if (mOutSampleRate == 0) {
                mOutSampleRate = mAudioFileReader.getInSampleRate();
            }
            if (mOutChannels == 0) {
                mOutChannels = mAudioFileReader.getInChannels();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mPositionInMS != 0) {
            mAudioFileReader.seek(mPositionInMS);
        }
        return lenMS;
    }
}
