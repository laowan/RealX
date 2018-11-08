package com.ycloud.svplayer;

import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2017/12/30.
 */

public interface MemExtractor {
    public MediaFormat getTrackFormat(int index);
    public void selectTrack(int index);
    public void unselectTrack(int index);
    public void seekTo(long timeUs, int mode);
    public boolean advance();
    // return the data size read from mediaExtractor.
    public int readSampleData(ByteBuffer byteBuf, int offset);
    public int getSampleTrackIndex();
    public long getSampleTime();
    public long getCachedDuration();
    public int getSampleFlags();
    public void setUseType(int type);
}
