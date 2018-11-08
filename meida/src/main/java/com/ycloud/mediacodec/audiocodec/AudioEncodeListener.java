package com.ycloud.mediacodec.audiocodec;

/**
 * Created by kele on 2017/4/27.
 */

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**平台硬编码回调*/
public interface AudioEncodeListener {
    void onEncodeOutputBuffer(ByteBuffer buffer, MediaCodec.BufferInfo buffInfo, long ptsUs, MediaFormat mediaFormat);
    void onEncoderFormatChanged(MediaFormat mediaFormat);
    void onEndOfInputStream();
    void onError(long eid, String errMsg); //硬编码出错.
}
