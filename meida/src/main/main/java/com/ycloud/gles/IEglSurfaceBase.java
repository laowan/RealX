package com.ycloud.gles;

/**
 * Created by Administrator on 2017/1/6.
 */
public interface IEglSurfaceBase {
    public void createWindowSurface(Object surface);
    public void createOffscreenSurface(int width, int height);
    public int getWidth();
    public int getHeight();
    public void releaseEglSurface();
    public void makeCurrent();
    public void makeCurrentReadFrom(Object readSurface);
    public boolean swapBuffers();
    public void setPresentationTime(long nsecs);
}
