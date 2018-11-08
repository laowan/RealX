package com.ycloud.gles.core;

import com.ycloud.utils.YYLog;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class EglHelper implements IEglHelper {
    private static final String TAG = EglHelper.class.getSimpleName();

    private GLBuilder.EGLConfigChooser eglConfigChooser;
    private GLBuilder.EGLContextFactory eglContextFactory;
    private GLBuilder.EGLWindowSurfaceFactory eglWindowSurfaceFactory;
    private EGL10 mEgl;
    private EGLDisplay mEglDisplay;
    private EGLSurface mEglSurface;
    private EGLConfig mEglConfig;
    private EGLContext mEglContext;


    public EglHelper(GLBuilder.EGLConfigChooser configChooser, GLBuilder.EGLContextFactory eglContextFactory
            , GLBuilder.EGLWindowSurfaceFactory eglWindowSurfaceFactory) {
        this.eglConfigChooser = configChooser;
        this.eglContextFactory = eglContextFactory;
        this.eglWindowSurfaceFactory = eglWindowSurfaceFactory;
    }

    /**
     * Initialize EGL for a given configuration spec.
     *
     * @param eglContext
     */
    @Override
    public EglContextWrapper start(EglContextWrapper eglContext) {
        YYLog.w(TAG, "start() tid=" + Thread.currentThread().getId());
        /*
         * Get an EGL instance
         */
        mEgl = (EGL10) EGLContext.getEGL();

        /*
         * Get to the default display.
         */
        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
            throwEglException("eglGetDisplay failed");
        }

        /*
         * We can now initialize EGL for that display
         */
        int[] version = new int[2];
        if (!mEgl.eglInitialize(mEglDisplay, version)) {
            throwEglException("eglInitialize failed");
        }
        mEglConfig = eglConfigChooser.chooseConfig(mEgl, mEglDisplay);

        /*
         * Create an EGL context. We want to do this as rarely as we can, because an
         * EGL context is a somewhat heavy object.
         */
        if (mEglConfig != null) {
            mEglContext = eglContextFactory.createContext(mEgl, mEglDisplay, mEglConfig, eglContext.getEglContextOld());
        }
        if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) {
            mEglContext = null;
            throwEglException("createContext");
        }
        YYLog.w(TAG, "createContext " + mEglContext + " tid=" + Thread.currentThread().getId());

        mEglSurface = null;

        EglContextWrapper eglContextWrapper = new EglContextWrapper();
        eglContextWrapper.setEglContextOld(mEglContext);
        return eglContextWrapper;
    }

    /**
     * Create an egl surface for the current SurfaceHolder surface. If a surface
     * already exists, destroy it before creating the new surface.
     *
     * @return true if the surface was created successfully.
     */
    @Override
    public boolean createSurface(Object surface) {
        YYLog.w(TAG, "createSurface()  tid=" + Thread.currentThread().getId());
        /*
         * Check preconditions.
         */
        if (mEgl == null) {
            YYLog.e(TAG, "egl not initialized");
            return false;
        }
        if (mEglDisplay == null) {
            YYLog.e(TAG, "eglDisplay not initialized");
            return false;
        }
        if (mEglConfig == null) {
            YYLog.e(TAG, "mEglConfig not initialized");
            return false;
        }

        /*
         *  The window size has changed, so we need to create a new
         *  surface.
         */
        destroySurfaceImp();

        /*
         * Create an EGL surface we can render into.
         */
        mEglSurface = eglWindowSurfaceFactory.createWindowSurface(mEgl,
                mEglDisplay, mEglConfig, surface);

        if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
            int error = mEgl.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                YYLog.e(TAG, "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
            }
            return false;
        }

        /*
         * Before we can issue GL commands, we need to make sure
         * the context is current and bound to a surface.
         */
        if (mEglContext != null && !mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            /*
             * Could not make the context current, probably because the underlying
             * SurfaceView surface has been destroyed.
             */
            logEglErrorAsWarning(TAG, "eglMakeCurrent", mEgl.eglGetError());
            return false;
        }

        return true;
    }

    /**
     * Display the current render surface.
     *
     * @return the EGL error code from eglSwapBuffers.
     */
    @Override
    public int swap() {
        if (!mEgl.eglSwapBuffers(mEglDisplay, mEglSurface)) {
            return mEgl.eglGetError();
        }
        return EGL10.EGL_SUCCESS;
    }

    @Override
    public void makeCurrent() {
        mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
    }

    @Override
    public void makeUnCurrent() {
        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
    }

    @Override
    public void makeNoSurface() {
        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, mEglContext);
    }

    @Override
    public void destroySurface() {
        YYLog.w(TAG, "destroySurface()  tid=" + Thread.currentThread().getId());

        destroySurfaceImp();
    }

    private void destroySurfaceImp() {
        if (mEglSurface != null && mEglSurface != EGL10.EGL_NO_SURFACE) {
            mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT);
            eglWindowSurfaceFactory.destroySurface(mEgl, mEglDisplay, mEglSurface);
            mEglSurface = null;
        }
    }

    @Override
    public void finish() {
        YYLog.w(TAG, "finish() tid=" + Thread.currentThread().getId());

        if (mEglContext != null) {
            eglContextFactory.destroyContext(mEgl, mEglDisplay, mEglContext);
            mEglContext = null;
        }
        if (mEglDisplay != null) {
            mEgl.eglTerminate(mEglDisplay);
            mEglDisplay = null;
        }
    }

    @Override
    public void setPresentationTime(long nsecs) {
    }

    @Override
    public int getWidth() {
        int[] value = new int[1];
        mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_WIDTH, value);
        return value[0];
    }

    @Override
    public int getHeight() {
        int[] value = new int[1];
        mEgl.eglQuerySurface(mEglDisplay, mEglSurface, EGL10.EGL_HEIGHT, value);
        return value[0];
    }

    @Override
    public int queryContext() {
        return 2;
    }

    @Override
    public Object getSurface() {
        return null;
    }

    private void throwEglException(String function) {
        throwEglException(function, mEgl.eglGetError());
    }

    public static void throwEglException(String function, int error) {
        String message = formatEglError(function, error);
        YYLog.e(TAG, "throwEglException tid=" + Thread.currentThread().getId() + " " + message);
    }

    public static void logEglErrorAsWarning(String tag, String function, int error) {
        YYLog.w(tag, formatEglError(function, error));
    }

    public static String formatEglError(String function, int error) {
        return function + " failed";
    }

}
