package com.ycloud.gles;

import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import javax.microedition.khronos.egl.EGL10;

/**
 * Created by Administrator on 2017/1/6.
 */
public class EglSurfaceBaseKhronos implements IEglSurfaceBase
{
    protected static final String TAG = OpenGlUtils.TAG;

    // EglCore object we're associated with. It may be associated with multiple
    // surfaces.
    protected EglCoreKhronos mEglCore;

    protected javax.microedition.khronos.egl.EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;
    private int mWidth = -1;
    private int mHeight = -1;

    protected EglSurfaceBaseKhronos(EglCoreKhronos eglCore) {
        mEglCore = eglCore;
    }

    /**
     * Creates a window surface.
     * <p>
     *
     * @param surface
     *            May be a Surface or SurfaceTexture.
     */
    public void createWindowSurface(Object surface) {
        if (mEGLSurface != EGL10.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mEglCore.createWindowSurface(surface);

        // Don't cache width/height here, because the size of the underlying
        // surface can change
        // out from under us (see e.g. HardwareScalerActivity).
        // mWidth = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
        // mHeight = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
    }

    /**
     * Creates an off-screen surface.
     */
    public void createOffscreenSurface(int width, int height) {
        if (mEGLSurface != EGL10.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mEglCore.createOffscreenSurface(width, height);
        mWidth = width;
        mHeight = height;
    }

    /**
     * Returns the surface's width, in pixels.
     * <p>
     * If this is called on a window surface, and the underlying surface is in
     * the process of changing size, we may not see the new size right away
     * (e.g. in the "surfaceChanged" callback). The size should match after the
     * next buffer swap.
     */
    public int getWidth() {
        if (mWidth < 0) {
            return mEglCore._querySurface(mEGLSurface, EGL10.EGL_WIDTH);
        } else {
            return mWidth;
        }
    }

    /**
     * Returns the surface's height, in pixels.
     */
    public int getHeight() {
        if (mHeight < 0) {
            return mEglCore._querySurface(mEGLSurface, EGL10.EGL_HEIGHT);
        } else {
            return mHeight;
        }
    }

    /**
     * Release the EGL surface.
     */
    public void releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface);
        mEGLSurface = EGL10.EGL_NO_SURFACE;
        mWidth = mHeight = -1;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        mEglCore._makeCurrent(mEGLSurface);
    }

    @Override
    public void makeCurrentReadFrom(Object readSurface) {
        if(readSurface instanceof  EglSurfaceBaseKhronos) {
            mEglCore._makeCurrent(mEGLSurface, ((EglSurfaceBaseKhronos)readSurface).mEGLSurface);
        } else {
            throw new RuntimeException("makeCurrentReadFrom readSurface is not getInstance of  EglSurfaceBaseKhronos" );
        }
    }

    /**
     * Makes our EGL context and surface current for drawing, using the supplied
     * surface for reading.
     */
    private void makeCurrentReadFrom(EglSurfaceBaseKhronos readSurface) {
        mEglCore._makeCurrent(mEGLSurface, readSurface.mEGLSurface);
    }

    /**
     * Calls eglSwapBuffers. Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    public boolean swapBuffers() {
        boolean result = mEglCore._swapBuffers(mEGLSurface);
        if (!result) {
            YYLog.debug(TAG, "WARNING: swapBuffers() failed");
        }
        return result;
    }

    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs
     *            Timestamp, in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        //not support pts.
        //mEglCore.setPresentationTime(mEGLSurface, nsecs);
    }

    public javax.microedition.khronos.egl.EGLSurface getEGLSurface() {
        return mEGLSurface;
    }

}
