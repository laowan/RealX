package com.ycloud.mediafilters;

/**
 * Created by Administrator on 2018/1/2.
 */

public class AbstractInputFilter extends AbstractYYMediaFilter {

    protected IMediaSession         mMediaSession = null;
    protected MediaBufferQueue      mVideoOutputBufferQueue = null;
    protected MediaBufferQueue      mAudioOutputBufferQueue = null;

    public void setMediaSession(IMediaSession session)
    {
        mMediaSession = session;
    }

    public  void setVideoOutputQueue(MediaBufferQueue queue) {
        mVideoOutputBufferQueue = queue;
    }

    public void setAudioOutputQueue(MediaBufferQueue queue) {
        mAudioOutputBufferQueue = queue;
    }

    public void start() {
    }
    public void stop() {
    }

    public void videoSeekTo(long timeUs) {
    }
}
