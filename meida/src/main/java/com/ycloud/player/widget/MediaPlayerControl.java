package com.ycloud.player.widget;

import com.ycloud.mediaprocess.VideoFilter;


public interface MediaPlayerControl {

	void start();

    void pause();

    int getDuration();

    int getCurrentPosition();

    void seekTo(int msec);

    boolean isPlaying();

    int getBufferPercentage();
    
    void setVFilters(VideoFilter mVideoFilter);
    
    void setVideoVolume(float volume);
    
    void setBackgroundMusicVolume(float volume);
}
