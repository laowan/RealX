package com.ycloud.audio;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by Administrator on 2018/1/20.
 */

public abstract class AudioFileReader {
    protected int mOutSampleRate;
    protected int mOutChannels;
    private byte[] mConvertInputBuffer;
    private byte[] mConvertOutputBuffer;
    private int mConvertCacheSize;
    private int mConvertCacheOffset;
    private AudioConverter mAudioConverter;
    private boolean mHaveCheckFormat;
    private static int kAudioConvertFrameSizeInMS = 10;

    private boolean mHaveSeek = false;
    private long mSeekPositionMS = 0;

    /**
     * set output format, this should be called before {@link #open}
     * @param sampleRate
     * @param channels
     */
    public void setOutputFormat(int sampleRate, int channels) {
        mOutChannels = channels;
        mOutSampleRate = sampleRate;
    }

    public abstract long open(String path) throws Exception;

    public int getOutSampleRate() {
        return mOutSampleRate;
    }

    public int getOutChannels() {
        return mOutChannels;
    }

    public abstract int getInSampleRate() throws Exception;

    public abstract int getInChannels() throws Exception;

    public abstract long getFilePositionInMS();

    public void close() {
        if (mAudioConverter != null) {
            mAudioConverter.unint();
            mAudioConverter = null;
        }
    }

    public void seek(long positionMS) {
        //fake seek, avoid seek frequency and actually seek in read operation
        mHaveSeek = true;
        mSeekPositionMS = positionMS;
    }

    protected void seek_inner(long positionMS) throws Exception {
        if (mAudioConverter != null) {
            int inSampleRate = getInSampleRate();
            int inChannels = getInChannels();
            mAudioConverter.unint();
            mAudioConverter.init(inSampleRate, inChannels, mOutSampleRate, mOutChannels);
        }
    }

    /**
     *
     * @param reqLen  must be '10MS' times, that is reqLen  = (inSampleRate * inChannels * 2 / 100 * N)
     * @return
     */
    public int read(byte[] outputBuffer, int reqLen) throws Exception {
        int readLen = 0;
        checkFormat();
        if (mHaveSeek) {
            seek_inner(mSeekPositionMS);
            mHaveSeek = false;
        }
        if (mAudioConverter != null) {
            int convertInputLen = (int) (((long) reqLen * getInSampleRate() * getInChannels()) / (mOutChannels * mOutSampleRate));
            if (mConvertInputBuffer == null || mConvertInputBuffer.length < convertInputLen) {
                mConvertInputBuffer = new byte[convertInputLen];
            }
            int len = read_inner(mConvertInputBuffer, convertInputLen);
            if (len > 0) {
                if (len != convertInputLen) {
                    Arrays.fill(mConvertInputBuffer, len, convertInputLen - 1, (byte) 0);
                }
                readLen = mAudioConverter.process(mConvertInputBuffer, convertInputLen, outputBuffer, reqLen);
            } else {
                return -1;
            }
        } else {
            readLen = read_inner(outputBuffer, reqLen);
        }
        return readLen;
    }

    private void checkFormat() throws Exception{
        if (!mHaveCheckFormat) {
            int inSampleRate = getInSampleRate();
            int inChannels = getInChannels();

            if (mOutChannels == 0) {
                mOutChannels = inChannels;
            }
            if (mOutSampleRate == 0) {
                mOutSampleRate = inSampleRate;
            }

            if (mAudioConverter != null) {
                mAudioConverter.unint();
                mAudioConverter = null;
            }
            if (inChannels == 0 || inSampleRate == 0) {
                return;
            }

            if (mOutSampleRate != inSampleRate || mOutChannels != inChannels) {
                mAudioConverter = new AudioConverter();
                mAudioConverter.init(inSampleRate, inChannels, mOutSampleRate, mOutChannels);

                //int inputFrameSizeInByte = kAudioConvertFrameSizeInMS * mInSampleRate * mInChannels * 2 / 1000;
                //mConvertInputBuffer = new byte[inputFrameSizeInByte];

                //int outputFrameSizeInByte = kAudioConvertFrameSizeInMS * mOutSampleRate * mOutChannels * 2 / 1000;
                //mConvertOutputBuffer = new byte[outputFrameSizeInByte];
            }

            mHaveCheckFormat = true;
            //mHaveSeek = false;
            //mSeekPositionMS = 0;
        }
    }

    protected abstract int read_inner(byte[] outputBuffer, int reqLen) throws Exception;
}
