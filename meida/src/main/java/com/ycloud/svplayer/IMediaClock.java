package com.ycloud.svplayer;

/**
 * Created by DZHJ on 2017/7/10.
 */

public interface IMediaClock {
    long getOffsetFrom(long from);
    void startAtIncrase(long mediaTime);
    void startAt(long mediaTime);
}
