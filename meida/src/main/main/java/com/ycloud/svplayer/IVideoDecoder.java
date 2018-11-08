package com.ycloud.svplayer;

/**
 * Created by dzhj on 17/7/18.
 */

public interface IVideoDecoder {
    int getVideoWidth();
    int getVideoHeight();
    int getVideoRotation();
    void dismissFrame(FrameInfo frameInfo);
    void releaseFrame(FrameInfo frameInfo);
    void renderFrame(FrameInfo frameInfo);
    void release();

    void decodeFrame();
    void renderFrame();
    long getCurrentTimeUs();
    void reinitCodec(MediaExtractor extractor,int trackIndex);


}
