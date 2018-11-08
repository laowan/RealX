package com.ycloud.mediacodec.audiocodec;

import com.ycloud.ymrmodel.YYMediaSample;

/**
 * Created by Administrator on 2018/6/2.
 */

public interface AudioEncoder {
    void setEncodeListener(AudioEncodeListener listener);
    void init() throws Exception;
    int pushToEncoder(YYMediaSample sample) throws Exception;
    void stopAudioRecord();
    void releaseEncoder();
}
