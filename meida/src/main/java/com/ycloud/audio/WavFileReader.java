package com.ycloud.audio;

import com.ycloud.utils.YYLog;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by Administrator on 2018/1/4.
 */

public class WavFileReader extends AudioFileReader {
    private int mFrameSizeInBytes;
    private int mBitPerSample;
    private RandomAccessFile mFile;
    private long mAudioDataLen;
    private long mFileSize;
    private int mInChannels;
    private int mInSampleRate;
    private long mTotalDataLen;
    private int mWaveHeaderSize;
    private boolean mIsValidFormat;

    public WavFileReader() {

    }

    @Override
    public long open(String path) {
        try {
            mFile = new RandomAccessFile(path, "r");
            mFileSize = mFile.length();
            if (parseWavHeader()) {
                return mAudioDataLen * 1000 / (mInSampleRate * mInChannels * 2);
            } else {
                return 0;
            }
        } catch (Exception e) {
            YYLog.e("WavFileReader", e.toString());
        }
        return 0;
    }

    @Override
    public int getInSampleRate() {
        return mInSampleRate;
    }

    @Override
    public int getInChannels() {
        return mInChannels;
    }

    @Override
    public void close() {
        super.close();
        try {
            if (mFile != null) {
                mFile.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getFilePositionInMS() {
        try {
            long position = mFile.getFilePointer();
            position -= mWaveHeaderSize;
            if (position >= 0 ) {
               position = position / (mInSampleRate * mFrameSizeInBytes);
               return position;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    protected int read_inner(byte[] outputBuffer, int reqLen) {
        if (!mIsValidFormat) {
            return -1;
        }
        try {
            return mFile.read(outputBuffer, 0, reqLen);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void seek_inner(long positionMS) {
        try {
            super.seek_inner(positionMS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        long pos = mInSampleRate * mInChannels * 2 * positionMS / 1000;
        pos = pos / mFrameSizeInBytes * mFrameSizeInBytes; //round for frame position
        pos += mWaveHeaderSize; // skip wave header
        if (pos > mFileSize) {
            return;
        }

        try {
            if (mFile != null) {
                mFile.seek(pos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static long unsignedIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24));
    }

    private static short unsignedShortLE(byte[] buf, int offset) {
        return (short) (buf[offset] & 0xFF
                | ((buf[offset + 1] & 0xFF) << 8));
    }

    private void parseFmt(byte[] data, int offset, int len) {
        mInChannels = data[2];
        mInSampleRate = (int) unsignedIntLE(data, 4);
        mBitPerSample = data[14];
    }

    private boolean parseWavHeader() throws IOException {
        int lenRead;
        byte[] buffer = new byte[8];
        boolean foundDataChunk = false;
        mIsValidFormat = false;
        do {
            lenRead = mFile.read(buffer, 0, 4);
            if (lenRead != 4) {
                break;
            }
            String riff = new String(buffer, 0, 4);
            if (!riff.equals("RIFF")) {
                break;
            }
            lenRead = mFile.read(buffer, 0, 4);
            if (lenRead != 4) {
                break;
            }
            mTotalDataLen = unsignedIntLE(buffer, 0);
            lenRead = mFile.read(buffer, 0, 4);
            if (lenRead != 4) {
                break;
            }
            String wave = new String(buffer, 0, 4);
            if (!wave.equals("WAVE")) {
                break;
            }
            while (true) {
                lenRead = mFile.read(buffer, 0, 4);
                if (lenRead != 4) {
                    break;
                }
                String chunkID = new String(buffer, 0, 4);
                lenRead = mFile.read(buffer, 0, 4);
                if (lenRead != 4) {
                    break;
                }
                long chunkSize = unsignedIntLE(buffer, 0);

                if (chunkID.equals("data")) {
                    mAudioDataLen = chunkSize;
                    mWaveHeaderSize = (int) mFile.getFilePointer();
                    mAudioDataLen = mFileSize - mWaveHeaderSize;
                    foundDataChunk = true;
                    break;
                }

                if (chunkSize > mFileSize) {
                    break;
                }

                if (chunkID.equals("fmt ")) {
                    byte[] chunkData = new byte[(int) chunkSize];
                    lenRead = mFile.read(chunkData);
                    if (lenRead != chunkData.length) {
                        break;
                    }
                    parseFmt(chunkData, 0, (int) chunkSize);
                } else {
                    mFile.skipBytes((int) chunkSize);
                }
            }
        } while (false);
        if (!foundDataChunk || mInChannels <= 0 || mBitPerSample != 16 || mInSampleRate <= 0 || mAudioDataLen <= 0) {
            return false;
        }
        mFrameSizeInBytes = mInChannels * mBitPerSample / 8;
        mIsValidFormat = true;
        return true;
    }
}
