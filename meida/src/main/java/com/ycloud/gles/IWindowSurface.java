package com.ycloud.gles;

/**
 * Created by Administrator on 2017/1/6.
 */
public interface IWindowSurface extends IEglSurfaceBase
{
    public void release();
    public void recreate(Object newEglCore);
}
