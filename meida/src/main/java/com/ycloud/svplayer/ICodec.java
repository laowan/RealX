package com.ycloud.svplayer;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Created by DZHJ on 2017/8/26.
 */

public interface ICodec {
    void configure(MediaFormat format, Surface surface, MediaCrypto crypto, int flags);

    void start();

    void stop();

    void release();

    ByteBuffer[] getInputBuffers();

    ByteBuffer[] getOutputBuffers();

    int dequeueInputBuffer(long timeoutUs);

    void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);

    int dequeueOutputBuffer(MediaCodec.BufferInfo info, long timeoutUs);

    MediaFormat getOutputFormat();

    void releaseOutputBuffer(int index, boolean render);

    void flush();

    ByteBuffer getInputBuffer(int index);

    ByteBuffer getOutputBuffer(int index);

}
