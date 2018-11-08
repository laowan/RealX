package com.ycloud.mediacodec.audiocodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/6/1.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class FFmpegAacEncoder implements AudioEncoder {
    static final String TAG = "FFmpegAudioFileReader";
    private long mNativePointer;
    private native long create(int sampleRate, int channelCount, int bitRate);
    private native void destroy(long nativePointer);
    private native int inputFrameSize(long nativePointer);
    private native void pushFrame(long nativePointer, byte[] frame, int len, long pts);
    private native void pullFrame(long nativePointer, byte[] buffer, int bufferLen, long[] returnInfo);

    static int[] aac_sampling_freq = {96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000,
            0, 0, 0, 0}; /* filling */
    static int _id = 0;
    static int profile = 1;

    AudioEncodeListener mListener;
    private final MediaFormat mOutputFormat;
    private static int kMAX_AAC_FRAME_LEN = 1024;
    private byte[] mAacFrame = new byte[kMAX_AAC_FRAME_LEN];
    private long[] mReturnInfo = new long[2];
    private ByteBuffer mByteBuffer;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mHaveOutputFormat = false;

    public FFmpegAacEncoder(MediaFormat audioOutputFormat) {
        mOutputFormat = audioOutputFormat;
    }

    @Override
    public void setEncodeListener(AudioEncodeListener listener) {
        mListener = listener;
    }

    @Override
    public void init() {
        if (mNativePointer == 0) {
            int sampleRate = mOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = mOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int bitRate = mOutputFormat.getInteger(MediaFormat.KEY_BIT_RATE);
            mNativePointer = create(sampleRate, channelCount, bitRate);
            mByteBuffer = ByteBuffer.wrap(mAacFrame);
            int sampleRateIndex = getSampleRateIndex(sampleRate);
            if (sampleRateIndex > 0) {
                ByteBuffer csd = ByteBuffer.allocate(2);
                csd.put(0, (byte)( ((profile + 1) << 3) | sampleRateIndex >> 1 ));
                csd.put(1, (byte)( ((sampleRateIndex << 7) & 0x80) | channelCount << 3 ));
                csd.position(0);
                csd.limit(2);
                mOutputFormat.setByteBuffer("csd-0", csd);
            }
        }
        mHaveOutputFormat = false;
    }

    @Override
    public int pushToEncoder(YYMediaSample sample) {
        long ptsUs = sample.mAndoridPtsNanos / 1000;
        if (!mHaveOutputFormat) {
            if (mListener != null) {
                mListener.onEncoderFormatChanged(mOutputFormat);
            }
            mHaveOutputFormat = true;
        }
        pushFrame(mNativePointer, sample.mDataBytes, sample.mBufferSize, ptsUs);
        drainEncoder();
        return 0;
    }

    @Override
    public void stopAudioRecord() {
        if (mNativePointer != 0) {
            drainEncoder();
            if (mListener != null) {
                mBufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mListener.onEncodeOutputBuffer(null, mBufferInfo, mReturnInfo[1], null);
                mListener.onEndOfInputStream();
            }
            releaseEncoder();
        }
    }

    @Override
    public void releaseEncoder() {
        destroy(mNativePointer);
        mNativePointer = 0;
    }

    private void drainEncoder() {
        while (true) {
            mReturnInfo[0] = 0;
            pullFrame(mNativePointer, mAacFrame, kMAX_AAC_FRAME_LEN, mReturnInfo);
            int aacLen = (int) mReturnInfo[0];
            long pts = mReturnInfo[1];
            if (aacLen <= 0) {
                break;
            }
            mByteBuffer.position(0);
            mByteBuffer.limit(aacLen);
            mBufferInfo.set(0, aacLen, pts, 0);
            if (mListener != null) {
                if (pts < 0) {
                    pts = 0;
                }
                mListener.onEncodeOutputBuffer(mByteBuffer, mBufferInfo, pts, mOutputFormat);
            }
        }
    }


    private int getSampleRateIndex(int sampleRate) {
        for (int i = 0; i < aac_sampling_freq.length; i++) {
            if (aac_sampling_freq[i] == sampleRate) {
                return i;
            }
        }
        return -1;
    }

}
