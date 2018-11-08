package com.ycloud.gles;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * Created by Administrator on 2017/1/6.
 */
public interface IEglCore {
    public void release();
    public void makeCurrent(IEglSurfaceBase eglSfBase);
    public void makeCurrent(IEglSurfaceBase drawSurface, IEglSurfaceBase readSurface);
    public void makeNothingCurrent();
    public boolean swapBuffers(IEglSurfaceBase eglSfBase);
    public void setPresentationTime(IEglSurfaceBase eglSfBase, long nsecs);
    public boolean isCurrent(IEglSurfaceBase eglSfBase);
    public int querySurface(IEglSurfaceBase eglSfBase, int what);
    public int getGlVersion();
    public IEglSurfaceBase createSurfaceBase();
    public IWindowSurface createWindowSurface(Surface surface, boolean releaseSurface);
    public IWindowSurface createWindowSurface(SurfaceHolder holder, boolean releaseSurace);
    public IWindowSurface createWindowSurface(SurfaceTexture surfaceTexture);
}
