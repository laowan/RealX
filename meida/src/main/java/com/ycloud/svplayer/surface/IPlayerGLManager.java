package com.ycloud.svplayer.surface;

import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.svplayer.FrameInfo;

/**
 *
 * Created by Administrator on 2018/3/30.
 */

public interface IPlayerGLManager {
    public PlayerGLManager.SurfaceWrapper getInputSurface();
    public void returnSurface(int surfaceIndex);
    public void renderFrame(FrameInfo frameInfo, int surfaceIndex);
    public void processImages(String imageBasePath, int imageRate);
    public void setVideoFilter(VideoFilter videoFilter);
}
