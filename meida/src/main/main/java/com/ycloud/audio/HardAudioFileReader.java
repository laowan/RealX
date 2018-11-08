package com.ycloud.audio;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.ycloud.mediacodec.compat.MediaCodecBufferCompatWrapper;
import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class HardAudioFileReader extends AudioFileReader {
    MediaExtractor mMediaExtractor;
    MediaCodecBufferCompatWrapper mMediaCodecBufferCompatWrapper;
    private  MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    static final String TAG = "HardAudioFileReader";
    MediaCodec mDecoder;
    boolean mSawInputEOS = false;
    boolean mSawOutputEOS = false;
    byte[] mCacheBuffer;
    int mCacheSize;
    int mCacheOffset;
    private int mInChannels;
    private int mInSampleRate;
    private MediaFormat mFormat;
    private boolean mBeginOutputData;

    @Override
    public long open(String path) throws IOException {
        mCacheSize = 0;
        mCacheBuffer = null;
        mMediaExtractor = new MediaExtractor();
        mMediaExtractor.setDataSource(path);
        for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
            mFormat = mMediaExtractor.getTrackFormat(i);
            String mime = mFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                mMediaExtractor.selectTrack(i);
                break;
            }
        }
        if (mFormat == null) {
            return 0;
        }
        String mediaMime = mFormat.getString(MediaFormat.KEY_MIME);
        long duration = mFormat.getLong(MediaFormat.KEY_DURATION) / 1000;
        mDecoder = MediaCodec.createDecoderByType(mediaMime);
        mDecoder.configure(mFormat, null, null, 0);
        mDecoder.start();
        mMediaCodecBufferCompatWrapper = new MediaCodecBufferCompatWrapper(mDecoder);
        mInChannels = 0;
        mInSampleRate = 0;

        return duration;
    }

    @Override
    public void close() {
        super.close();
        if (mDecoder != null) {
            mDecoder.release();
            mDecoder = null;
        }
        if (mMediaExtractor != null) {
            mMediaExtractor.release();
            mMediaExtractor = null;
        }
    }

    @Override
    public int getInSampleRate() throws Exception {
        if (mInSampleRate == 0) {
            getRealFormat();
        }
        return mInSampleRate;
    }

    @Override
    public int getInChannels() throws Exception {
        if (mInChannels == 0) {
            getRealFormat();
        }
        return mInChannels;
    }

    @Override
    public long getFilePositionInMS() {
        if (mMediaExtractor != null) {
            long filePositionMS = mMediaExtractor.getSampleTime() / 1000;
            if (mCacheSize != 0 && mInChannels != 0 && mInSampleRate != 0) {
                filePositionMS += ((long)mCacheSize * 1000 / (mInSampleRate * mInChannels * 2));
            }
            return filePositionMS;
        }
        return 0;
    }

    @Override
    public void seek_inner(long positionMS) throws Exception{
        super.seek_inner(positionMS);
        if (mMediaExtractor != null && mDecoder != null) {
            int mode = MediaExtractor.SEEK_TO_NEXT_SYNC;
            mCacheSize = 0;
            mMediaExtractor.seekTo(positionMS * 1000, mode);
            if (mCacheBuffer == null) {
                mCacheBuffer = new byte[1024];
            }
            if (mSawOutputEOS) {
                mSawOutputEOS = false;
                mDecoder.configure(mFormat, null, null, 0);
                mDecoder.start();
                mMediaCodecBufferCompatWrapper = new MediaCodecBufferCompatWrapper(mDecoder);
                YYLog.info(TAG, " seek_inner reconfigure codec" );
            }else {
                //Clear decoder cache
                if (!mSawOutputEOS) {
                    try {
                        mDecoder.flush();
                    }catch (Exception e) {
                        YYLog.info(TAG, " flush %s ", e.toString());
                    }
                }
                while (drainDecoder(mCacheBuffer, 0, mCacheBuffer.length) > 0) {
                    ;
                }
            }
            mCacheSize = 0;
            if (mSawInputEOS) {
                mSawInputEOS = false;
            }
        }
    }

    @Override
    protected int read_inner(byte[] outputBuffer, int reqLen) throws Exception {
        int offset = 0;
        int readLen = 0;
        if (mDecoder == null) {
            return -1;
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
        if (mSawOutputEOS) {
            return -1;
        }
        while (reqLen > 0 && !mSawOutputEOS) {
            if (!mSawInputEOS) {
                int inputBufferIndex;
                int dequeueCount = 100;
                do {
                    inputBufferIndex = mDecoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        break;
                    }
                } while (dequeueCount-- > 0);
                if (inputBufferIndex < 0) {
                    break;
                }
                ByteBuffer dstBuf = mMediaCodecBufferCompatWrapper.getInputBuffer(inputBufferIndex);
                if (dstBuf != null) {
                    int sampleSize = mMediaExtractor.readSampleData(dstBuf, 0);
                    if (sampleSize < 0) {
                        mSawInputEOS = true;
                        mDecoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        long presentationTimeUs = 0;//mMediaExtractor.getSampleTime();
                        mDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                        mMediaExtractor.advance();
                    }
                }
            }

            int frameRead = drainDecoder(outputBuffer, offset, reqLen);
            readLen += frameRead;
            reqLen -= frameRead;
            offset += frameRead;
        }
        if (mSawOutputEOS && readLen == 0) {
            return -1;
        }
        return readLen;
    }

    private void getRealFormat() throws Exception {
        if (mDecoder == null) {
            return;
        }

        int tryCount = 5;
        while (tryCount > 0 && !mSawOutputEOS) {
            YYLog.info(TAG, " getRealFormat " + tryCount);
            if (!mSawInputEOS) {
                int inputBufferIndex;
                int dequeueCount = 100;
                do {
                    inputBufferIndex = mDecoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        break;
                    }
                } while (dequeueCount-- > 0);

                ByteBuffer dstBuf = mMediaCodecBufferCompatWrapper.getInputBuffer(inputBufferIndex);
                if (dstBuf != null) {
                    int sampleSize = mMediaExtractor.readSampleData(dstBuf, 0);
                    if (sampleSize < 0) {
                        mSawInputEOS = true;
                        mDecoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        long presentationTimeUs = mMediaExtractor.getSampleTime();
                        mDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                        mMediaExtractor.advance();
                    }
                }
            }

            drainDecoder(null, 0, 0);
            if (mInSampleRate != 0 && mInChannels != 0) {
                break;
            }
            tryCount--;
        }
    }

    private int drainDecoder(byte[] outputBuffer, int offset, int reqLen) throws Exception{
        int readLen = 0;
        while (!mSawOutputEOS) {
            int index = mDecoder.dequeueOutputBuffer(mBufferInfo,0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                /* get output format from codec and pass them to muxer*/
                MediaFormat format = mDecoder.getOutputFormat();
                if (format == null) {/*这个可能性应该没有*/
                    Log.e(TAG, "audio decoder actual output format is null");
                    throw new RuntimeException("Could not determine actual output format.");
                }
                int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                mInChannels = channels;
                mInSampleRate = sampleRate;

                YYLog.info(TAG, " input format " + sampleRate + ":" + channels);
                break;
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mMediaCodecBufferCompatWrapper = new MediaCodecBufferCompatWrapper(mDecoder);
            } else {
                if (!mBeginOutputData) {
                    YYLog.info(TAG, " begin output data ");
                    mBeginOutputData = true;
                }
                ByteBuffer decodedData = mMediaCodecBufferCompatWrapper.getOutputBuffer(index);
                if (decodedData == null) {
                    throw new RuntimeException("audio decoder " + index + " was null");
                }

                int count = mBufferInfo.size;
                int copyLen = count > reqLen ? reqLen : count;
                int leftLen = count - copyLen;
                if (outputBuffer != null ) {
                    decodedData.get(outputBuffer, offset, copyLen);
                }
                if (leftLen > 0 ) {
                    if (mCacheBuffer == null || leftLen > mCacheBuffer.length) {
                        mCacheBuffer = new byte[leftLen];
                    }
                    decodedData.get(mCacheBuffer, 0, leftLen);
                    mCacheSize = leftLen;
                    mCacheOffset = 0;
                }

                reqLen -= copyLen;
                readLen += copyLen;
                offset += copyLen;
                decodedData.clear();

                mDecoder.releaseOutputBuffer(index, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mSawOutputEOS = true;
                    mDecoder.stop();
                    break;      // out of while
                }
                if (reqLen == 0) {
                    break;
                }
            }
        }
        return readLen;
    }
}
