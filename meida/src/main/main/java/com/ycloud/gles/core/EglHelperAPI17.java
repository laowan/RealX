package com.ycloud.gles.core;

import android.annotation.TargetApi;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;

import com.ycloud.utils.YYLog;

import static com.ycloud.gles.core.EglHelper.formatEglError;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class EglHelperAPI17 implements IEglHelper {
    private static final String TAG = EglHelperAPI17.class.getSimpleName();

    private GLBuilder.EGLConfigChooser eglConfigChooser;
    private GLBuilder.EGLContextFactory eglContextFactory;
    private GLBuilder.EGLWindowSurfaceFactory eglWindowSurfaceFactory;
    private EGLDisplay mEglDisplay;
    private EGLConfig mEglConfig;
    private EGLContext mEglContext;
    private EGLSurface mEglSurface;

    public EglHelperAPI17(GLBuilder.EGLConfigChooser configChooser, GLBuilder.EGLContextFactory eglContextFactory
            , GLBuilder.EGLWindowSurfaceFactory eglWindowSurfaceFactory) {
        this.eglConfigChooser = configChooser;
        this.eglContextFactory = eglContextFactory;
        this.eglWindowSurfaceFactory = eglWindowSurfaceFactory;
    }

    @Override
    public EglContextWrapper start(EglContextWrapper eglContext) {
        YYLog.w(TAG, "start() tid=" + Thread.currentThread().getId());
        /*
         * Get an EGL instance
         */

        /*
         * Get to the default display.
         */
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);

        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throwEglException("eglGetDisplay failed");
        }

        /*
         * We can now initialize EGL for that display
         */
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            mEglDisplay = null;
            throwEglException("eglInitialize failed");
        }
        mEglConfig = eglConfigChooser.chooseConfig(mEglDisplay, false);

        /*
         * Create an EGL context. We want to do this as rarely as we can, because an
         * EGL context is a somewhat heavy object.
         */
        if (mEglConfig != null) {
            mEglContext = eglContextFactory.createContextAPI17(mEglDisplay, mEglConfig, eglContext.getEglContext());
        }
        if (mEglContext == null || mEglContext == EGL14.EGL_NO_CONTEXT) {
            mEglContext = null;
            throwEglException("createContext");
        }
        YYLog.w(TAG, "createContext " + mEglContext + " tid=" + Thread.currentThread().getId());

        mEglSurface = null;

        EglContextWrapper eglContextWrapper = new EglContextWrapper();
        eglContextWrapper.setEglContext(mEglContext);
        return eglContextWrapper;
    }

    @Override
    public boolean createSurface(Object surface) {
        YYLog.w(TAG, "createSurface()  tid=" + Thread.currentThread().getId());

        if (mEglDisplay == null) {
            YYLog.e(TAG,"eglDisplay not initialized");
            return false;
        }
        if (mEglConfig == null) {
            YYLog.e(TAG,"mEglConfig not initialized");
            return false;
        }

        destroySurfaceImp();
        /*
         * Create an EGL surface we can render into.
         */
        mEglSurface = eglWindowSurfaceFactory.createWindowSurface(mEglDisplay, mEglConfig, surface);

        if (mEglSurface == null || mEglSurface == EGL14.EGL_NO_SURFACE) {
            int error = EGL14.eglGetError();
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                YYLog.e(TAG, "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
            }
            return false;
        }

        /*
         * Before we can issue GL commands, we need to make sure
         * the context is current and bound to a surface.
         */
        if (mEglContext != null && !EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            /*
             * Could not make the context current, probably because the underlying
             * SurfaceView surface has been destroyed.
             */
            logEglErrorAsWarning(TAG, "eglMakeCurrent", EGL14.eglGetError());
            return false;
        }

        return true;
    }


    @Override
    public int swap() {
        if (!EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)) {
            int error = EGL14.eglGetError();
            YYLog.error(TAG, "swap: start get error:" + error);
            return error;
        }
        return EGL14.EGL_SUCCESS;
    }

    @Override
    public void makeCurrent() {
        EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
    }

    @Override
    public void makeUnCurrent() {
        EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    @Override
    public void makeNoSurface() {
        EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, mEglContext);
    }

    @Override
    public void destroySurface() {
        YYLog.w(TAG, "destroySurface()  tid=" + Thread.currentThread().getId());
        destroySurfaceImp();
    }

    private void destroySurfaceImp() {
        if (mEglSurface != null && mEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            eglWindowSurfaceFactory.destroySurface(mEglDisplay, mEglSurface);
            mEglSurface = null;
        }
    }

    @Override
    public void finish() {
        YYLog.w(TAG, "finish() tid=" + Thread.currentThread().getId());
        if (mEglContext != null) {
            eglContextFactory.destroyContext(mEglDisplay, mEglContext);
            mEglContext = null;
        }
        if (mEglDisplay != null) {
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEglDisplay);
            mEglDisplay = null;
        }
    }


    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs Timestamp, in nanoseconds.
     */
    @Override
    public void setPresentationTime(long nsecs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && nsecs != 0) {
            EGLExt.eglPresentationTimeANDROID(mEglDisplay, mEglSurface, nsecs);
        }
    }

    @Override
    public int getWidth() {
        int[] value = new int[1];
        EGL14.eglQuerySurface(mEglDisplay, mEglSurface, EGL14.EGL_WIDTH, value, 0);
        return value[0];
    }

    @Override
    public int getHeight() {
        int[] value = new int[1];
        EGL14.eglQuerySurface(mEglDisplay, mEglSurface, EGL14.EGL_HEIGHT, value, 0);
        return value[0];
    }

    @Override
    public int queryContext() {
        int[] value = new int[1];
        EGL14.eglQueryContext(mEglDisplay, mEglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, value, 0);
        return value[0];
    }

    @Override
    public Object getSurface() {
        return mEglSurface;
    }

    public static void logEglErrorAsWarning(String tag, String function, int error) {
        YYLog.w(tag, formatEglError(function, error));
    }

    private void throwEglException(String function) {
        throwEglException(function, EGL14.eglGetError());
    }

    public static void throwEglException(String function, int error) {
        String message = formatEglError(function, error);
        YYLog.e(TAG, "throwEglException tid=" + Thread.currentThread().getId() + " " + message);
    }
}
