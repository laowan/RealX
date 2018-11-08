package com.ycloud.mediarecord.audio;

/**
 * Created by zhangbin on 2016/12/23.
 */

public interface IPcmFrameListener {
    void onGetPcmFrame(byte[] pcmBuffer,int size);
}
