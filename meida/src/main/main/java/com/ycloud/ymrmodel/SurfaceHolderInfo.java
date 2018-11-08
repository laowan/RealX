package com.ycloud.ymrmodel;


import android.view.SurfaceHolder;

/**
 * Android4.2以下的版本，不支持通过Surface来创建eglCreateWindowSurface.
 */
public class SurfaceHolderInfo extends AbstractSurfaceInfo
{
    public SurfaceHolder mSurfaceHolder = null;
    public SurfaceHolderInfo(SurfaceHolder sh, int width, int height) {
        super(width, height);
        mSurfaceHolder = sh;
    }
}
