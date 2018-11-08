package com.ycloud.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/1/11.
 */

public interface IMediaMuxer {
    public void start();
    public void stop();
    public void release();
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, long dtsMs);
    public int addTrack(MediaFormat format);
    public void setOrientationHint(int orientationHint);
}
