package com.ycloud.svplayer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by DZHJ on 2017/8/26.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaCodecWrapper implements ICodec {
    private final MediaCodec mCodec;

    public MediaCodecWrapper(String keyMime) throws IOException {
        mCodec = MediaCodec.createDecoderByType(keyMime);
    }

    @Override
    public void configure(MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
        mCodec.configure(format,surface,crypto,flags);
    }

    @Override
    public void start() {
        mCodec.start();
    }

    @Override
    public void stop() {
        mCodec.stop();
    }

    @Override
    public void release() {
        mCodec.release();
    }

    @Override
    public ByteBuffer[] getInputBuffers() {
        return  mCodec.getInputBuffers();
    }

    @Override
    public ByteBuffer[] getOutputBuffers() {
        return mCodec.getOutputBuffers();
    }

    @Override
    public int dequeueInputBuffer(long timeoutUs) {
        return  mCodec.dequeueInputBuffer(timeoutUs);
    }

    @Override
    public void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags) {
        mCodec.queueInputBuffer(index,offset,size,presentationTimeUs,flags);
    }

    @Override
    public int dequeueOutputBuffer(MediaCodec.BufferInfo info, long timeoutUs) {
        return  mCodec.dequeueOutputBuffer(info,timeoutUs);
    }

    @Override
    public MediaFormat getOutputFormat() {
        return mCodec.getOutputFormat();
    }

    @Override
    public void releaseOutputBuffer(int index, boolean render) {
        mCodec.releaseOutputBuffer(index,render);
    }

    @Override
    public void flush() {
        mCodec.flush();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public ByteBuffer getInputBuffer(int index) {
       return mCodec.getInputBuffer(index);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public ByteBuffer getOutputBuffer(int index) {
        return mCodec.getOutputBuffer(index);
    }
}
