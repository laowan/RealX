package com.ycloud.gles.core;

public interface IEglHelper {
    EglContextWrapper start(EglContextWrapper eglContext);

    boolean createSurface(Object surface);

    int swap();

    void makeCurrent();

    void makeUnCurrent();

    void makeNoSurface();

    void destroySurface();

    void finish();

    void setPresentationTime(long nsecs);

    int getWidth();

    int getHeight();

    int queryContext();

    Object getSurface();
}
