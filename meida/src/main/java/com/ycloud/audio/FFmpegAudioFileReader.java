package com.ycloud.audio;

/**
 * Created by Administrator on 2018/5/30.
 */

public class FFmpegAudioFileReader extends AudioFileReader {
    static final String TAG = "FFmpegAudioFileReader";
    private long mNativePointer;
    private native long create(int outSampleRate, int outChannelCount);
    private native void destroy(long nativePointer);
    private native void open(long nativePointer, String path);
    private native int getSampleRate(long nativePointer);
    private native int getChannelCount(long nativePointer);
    private native long lenInMS(long nativePointer);
    private native void seek(long nativePointer, long ms);
    private native long currentPosition(long nativePointer);
    private native long readFrame(long nativePointer, byte[] buffer, int offset, int bufferLen);

    byte[] mCacheBuffer;
    int mCacheSize;
    int mCacheOffset;
    public FFmpegAudioFileReader() {

    }

    @Override
    public long open(String path) {
        mNativePointer = create(mOutSampleRate, mOutChannels);
        if (mNativePointer == 0) {
            return 0;
        }
        open(mNativePointer, path);
        if (mOutSampleRate == 0) {
            mOutSampleRate = getSampleRate(mNativePointer);
        }
        if (mOutChannels == 0) {
            mOutChannels = getChannelCount(mNativePointer);
        }
        mCacheSize = 0;
        mCacheBuffer = null;
        return lenInMS(mNativePointer);
    }

    @Override
    public void close() {
        super.close();
        destroy(mNativePointer);
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
        return currentPosition(mNativePointer);
    }

    @Override
    protected int read_inner(byte[] outputBuffer, int reqLen) {
        int offset = 0;
        int readLen = 0;
        if (mNativePointer == 0) {
            return -1;
        }
        if (mCacheBuffer == null) {
            mCacheBuffer = new byte[mOutSampleRate * mOutChannels * 2 / 10];
        }
        if (mCacheBuffer != null && mCacheSize > 0) {
            int copyLen = reqLen > mCacheSize ? mCacheSize : reqLen;
            System.arraycopy(mCacheBuffer, mCacheOffset, outputBuffer, 0, copyLen);
            mCacheSize -= copyLen;
            reqLen -= copyLen;
            offset += copyLen;
            readLen += copyLen;
            mCacheOffset += copyLen;
            if (reqLen == 0) {
                return readLen;
            }
        }

        while (reqLen > 0) {
            mCacheOffset = 0;
            long frameLen = readFrame(mNativePointer, mCacheBuffer, 0, mCacheBuffer.length);
            if (frameLen <= 0) {
                break;
            }
            mCacheSize = (int) frameLen;
            int copyLen = reqLen > mCacheSize ? mCacheSize : reqLen;
            System.arraycopy(mCacheBuffer, mCacheOffset, outputBuffer, offset, copyLen);
            mCacheSize -= copyLen;
            reqLen -= copyLen;
            offset += copyLen;
            readLen += copyLen;
            mCacheOffset += copyLen;
            if (reqLen == 0) {
                return readLen;
            }
        }
        return 0;
    }

    @Override
    public void seek_inner(long positionMS) {
        try {
            super.seek_inner(positionMS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mCacheSize = 0;
        mCacheOffset = 0;
        seek(mNativePointer, positionMS);
    }
}
