package com.ycloud.svplayer;

import android.annotation.TargetApi;
import android.os.Build;

import java.nio.ByteBuffer;

/**
 * Created by DZHJ on 2017/8/26.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class CodecBufferCompatWrapper {

    final ICodec mCodec;
    final ByteBuffer[] mInputBuffers;
    final ByteBuffer[] mOutputBuffers;

    public CodecBufferCompatWrapper(ICodec mediaCodec) {
        mCodec = mediaCodec;

        if (mCodec instanceof  MediaCodecWrapper && Build.VERSION.SDK_INT < 21) {
            mInputBuffers = mediaCodec.getInputBuffers();
            mOutputBuffers = mediaCodec.getOutputBuffers();
        } else {
            mInputBuffers = mOutputBuffers = null;
        }
    }

    public ByteBuffer getInputBuffer(final int index) {
        if (mCodec instanceof  MediaCodecWrapper && Build.VERSION.SDK_INT < 21) {
            return mInputBuffers[index];
        }

        return mCodec.getInputBuffer(index);
    }

    public ByteBuffer getOutputBuffer(final int index) {
        if (mCodec instanceof MediaCodecWrapper && Build.VERSION.SDK_INT < 21) {
            return mOutputBuffers[index];
        }

        return mCodec.getOutputBuffer(index);
    }
}
