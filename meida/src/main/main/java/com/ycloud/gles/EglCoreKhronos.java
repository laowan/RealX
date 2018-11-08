package com.ycloud.gles;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.ycloud.utils.YYLog;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.opengles.GL;

/**
 * Created by Administrator on 2017/1/6.
 */
public class EglCoreKhronos implements IEglCore {
    private static final String TAG = "EglCore";
    private EGL10 mEgl;
    private javax.microedition.khronos.egl.EGLContext mEGLContext;
    private javax.microedition.khronos.egl.EGLDisplay mEGLDisplay;
    private javax.microedition.khronos.egl.EGLConfig mEGLConfig;

    protected EglCoreKhronos() {
        this(null);
    }

    protected EglCoreKhronos(javax.microedition.khronos.egl.EGLConfig config) {
        mEgl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();

        mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }

        int[] version = new int[2];
        if (!mEgl.eglInitialize(mEGLDisplay, version)) {
            throw new RuntimeException("eglInitialize failed");
        }
        mEGLConfig = chooseConfig(mEgl, mEGLDisplay);
    }

    private int[] filterConfigSpec(int[] configSpec) {
        // if (EGLContextClientVersion != 2) {
        // return configSpec;
        // }
		/*
		 * We know none of the subclasses define EGL_RENDERABLE_TYPE. And we
		 * know the configSpec is well formed.
		 */
        int len = configSpec.length;
        int[] newConfigSpec = new int[len + 2];
        System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1);
        newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE;
        newConfigSpec[len] = 4; /* EGL_OPENGL_ES2_BIT */
        newConfigSpec[len + 1] = EGL10.EGL_NONE;
        return newConfigSpec;
    }

    public javax.microedition.khronos.egl.EGLConfig chooseConfig(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display) {
        int[] mConfigSpec;
        mConfigSpec = filterConfigSpec(new int[] { EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                // EGL10.EGL_DEPTH_SIZE, depthSize,
                // EGL10.EGL_STENCIL_SIZE, stencilSize,
                EGL10.EGL_NONE });

        int[] num_config = new int[1];
        if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config)) {
            throw new IllegalArgumentException("eglChooseConfig failed");
        }

        int numConfigs = num_config[0];

        if (numConfigs <= 0) {
            throw new IllegalArgumentException("No configs match configSpec");
        }

        javax.microedition.khronos.egl.EGLConfig[] configs = new javax.microedition.khronos.egl.EGLConfig[numConfigs];
        if (!egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs,
                num_config)) {
            throw new IllegalArgumentException("eglChooseConfig#2 failed");
        }
        javax.microedition.khronos.egl.EGLConfig config = doChooseConfig(egl, display, configs);
        if (config == null) {
            throw new IllegalArgumentException("No config chosen");
        }

        int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        int mEGLContextClientVersion = 2;
        int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION,
                mEGLContextClientVersion, EGL10.EGL_NONE };

        mEGLContext = egl.eglCreateContext(display, config,
                EGL10.EGL_NO_CONTEXT,
                mEGLContextClientVersion != 0 ? attrib_list : null);

        return config;
    }

    public javax.microedition.khronos.egl.EGLConfig doChooseConfig(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                                                                   javax.microedition.khronos.egl.EGLConfig[] configs) {
        for (javax.microedition.khronos.egl.EGLConfig config : configs) {
            int d = findConfigAttrib(egl, display, config,
                    EGL10.EGL_DEPTH_SIZE, 0);
            int s = findConfigAttrib(egl, display, config,
                    EGL10.EGL_STENCIL_SIZE, 0);
            // if ((d >= mDepthSize) && (s >= mStencilSize)) {
            int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE,
                    0);
            int g = findConfigAttrib(egl, display, config,
                    EGL10.EGL_GREEN_SIZE, 0);
            int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE,
                    0);
            int a = findConfigAttrib(egl, display, config,
                    EGL10.EGL_ALPHA_SIZE, 0);
            if ((r == 8) && (g == 8) && (b == 8) && (a == 8)) {
                return config;
            }
            // }
        }
        return null;
    }

    private int findConfigAttrib(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                                 javax.microedition.khronos.egl.EGLConfig config, int attribute, int defaultValue) {
        int[] value = new int[1];
        if (egl.eglGetConfigAttrib(display, config, attribute, value)) {
            return value[0];
        }
        return defaultValue;
    }

    public GL getGL() {
        return mEgl.eglGetCurrentContext().getGL();
    }

    /**
     * Destroys the specified surface. Note the EGLSurface won't actually be
     * destroyed if it's still current in a context.
     */
    public void releaseSurface(javax.microedition.khronos.egl.EGLSurface eglSurface) {
        mEgl.eglDestroySurface(mEgl.eglGetCurrentDisplay(), eglSurface);
    }

    /**
     * Creates an EGL surface associated with a Surface.
     * <p>
     * If this is destined for MediaCodec, the EGLConfig should have the
     * "recordable" attribute.
     */
    public javax.microedition.khronos.egl.EGLSurface createWindowSurface(Object surface) {
        if (!(surface instanceof Surface)
                && !(surface instanceof SurfaceTexture) && !(surface instanceof SurfaceHolder)) {
            throw new RuntimeException("invalid surface: " + surface);
        }

        // Create a window surface, and attach it to the Surface we received.
        // FIXME: useless object created, comment added by Huangchengzong
        int[] surfaceAttribs = { EGL10.EGL_NONE };
        javax.microedition.khronos.egl.EGLSurface eglSurface = mEgl.eglCreateWindowSurface(
                mEGLDisplay, mEGLConfig, surface,
                null);
        checkEglError("eglCreateWindowSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    public javax.microedition.khronos.egl.EGLSurface createOffscreenSurface(int width, int height) {
        int[] surfaceAttribs = { EGL11.EGL_WIDTH, width, EGL11.EGL_HEIGHT,
                height, EGL11.EGL_NONE };
        javax.microedition.khronos.egl.EGLSurface eglSurface = mEgl.eglCreatePbufferSurface(
                mEGLDisplay, mEGLConfig, surfaceAttribs);
        checkEglError("eglCreatePbufferSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * Makes our EGL context current, using the supplied surface for both "draw"
     * and "read".
     */
    public void _makeCurrent(javax.microedition.khronos.egl.EGLSurface eglSurface) {
        //EGLDisplay display = mEgl.eglGetCurrentDisplay();
        if (mEGLDisplay == EGL11.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            YYLog.debug(this, "NOTE: makeCurrent w/o display");
        }
        if (!mEgl.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface,
                mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Makes our EGL context current, using the supplied "draw" and "read"
     * surfaces.
     */
    public void _makeCurrent(javax.microedition.khronos.egl.EGLSurface drawSurface, javax.microedition.khronos.egl.EGLSurface readSurface) {
        javax.microedition.khronos.egl.EGLDisplay display = mEgl.eglGetCurrentDisplay();
        if (display == EGL11.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            YYLog.debug(this, "NOTE: makeCurrent w/o display");
        }
        if (!mEgl.eglMakeCurrent(display, drawSurface, readSurface,
                mEgl.eglGetCurrentContext())) {
            throw new RuntimeException("eglMakeCurrent(draw,read) failed");
        }
    }





    /**
     * Calls eglSwapBuffers. Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    protected boolean _swapBuffers(javax.microedition.khronos.egl.EGLSurface eglSurface) {
        return mEgl.eglSwapBuffers(mEgl.eglGetCurrentDisplay(), eglSurface);
    }

    /**
     * Sends the presentation time stamp to EGL. Time is expressed in
     * nanoseconds.
     */
    protected void _setPresentationTime(javax.microedition.khronos.egl.EGLSurface eglSurface, long nsecs) {
        // EGLExt.eglPresentationTimeANDROID(mEGLDisplay, eglSurface, nsecs);
    }

    /**
     * Returns true if our context and the specified surface are current.
     */
    protected boolean _isCurrent(javax.microedition.khronos.egl.EGLSurface eglSurface) {
        return mEGLContext.equals(mEgl.eglGetCurrentContext())
                && eglSurface.equals(mEgl.eglGetCurrentSurface(EGL11.EGL_DRAW));
    }

    /**
     * Performs a simple surface query.
     */
    protected int _querySurface(javax.microedition.khronos.egl.EGLSurface eglSurface, int what) {
        int[] value = new int[1];
        mEgl.eglQuerySurface(mEgl.eglGetCurrentDisplay(), eglSurface, what,
                value);
        return value[0];
    }

    /**
     * Queries a string value.
     */
    public String queryString(int what) {
        return mEgl.eglQueryString(mEgl.eglGetCurrentDisplay(), what);
    }

    /**
     * Returns the GLES version this context is configured for (currently 2 or
     * 3).
     */
    public int getGlVersion() {
        return 2;
    }

    @Override
    public IEglSurfaceBase createSurfaceBase() {
        return new EglSurfaceBaseKhronos(this);
    }

    @Override
    public IWindowSurface createWindowSurface(Surface surface, boolean releaseSurface) {
        throw new RuntimeException("EGL version error,  eglCreateWindowSurface forbidden call with surface this android version...");
    }

    @Override
    public IWindowSurface createWindowSurface(SurfaceHolder holder, boolean releaseSurace) {
        return new WindowSurfaceKhronos(this, holder, releaseSurace);
    }

    @Override
    public IWindowSurface createWindowSurface(SurfaceTexture surfaceTexture) {
        return new WindowSurfaceKhronos(this, surfaceTexture);
    }

    /**
     * Writes the current display, context, and surface to the log.
     */
    public void logCurrent(String msg) {
        javax.microedition.khronos.egl.EGLDisplay display;
        javax.microedition.khronos.egl.EGLContext context;
        javax.microedition.khronos.egl.EGLSurface surface;

        display = mEgl.eglGetCurrentDisplay();
        context = mEgl.eglGetCurrentContext();
        surface = mEgl.eglGetCurrentSurface(EGL11.EGL_DRAW);
        YYLog.info(this, "Current EGL (" + msg + "): display=" + display
                + ", context=" + context + ", surface=" + surface);
    }

    private javax.microedition.khronos.egl.EGLSurface getCurrentSurface() {
        return mEgl.eglGetCurrentSurface(EGL11.EGL_DRAW);
    }

    /**
     * Checks for EGL errors. Throws an exception if an error has been raised.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = mEgl.eglGetError()) != EGL11.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x"
                    + Integer.toHexString(error));
        }
    }

    @Override
    public void release() {
        // if (!mEgl.eglDestroyContext(display, context))
        if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
            // Android is unusual in that it uses a reference-counted
            // EGLDisplay. So for
            // every eglInitialize() we need an eglTerminate().
            // mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE,
            // EGL10.EGL_NO_SURFACE,
            // EGL14.EGL_NO_CONTEXT);
            mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEgl.eglTerminate(mEGLDisplay);
        }

        mEGLDisplay = EGL10.EGL_NO_DISPLAY;
        mEGLContext = EGL10.EGL_NO_CONTEXT;
        mEGLConfig = null;
    }

    @Override
    public void makeCurrent(IEglSurfaceBase eglSurfaceBase) {
        if(eglSurfaceBase == null)
            return;

        if(eglSurfaceBase instanceof EglSurfaceBaseKhronos) {
            eglSurfaceBase.makeCurrent();
        } else {
            throw new RuntimeException("EGL version error,  eglSurfaceBase is not EglSurfaceBaseKhronos ");
        }
    }

    @Override
    public void makeCurrent(IEglSurfaceBase drawSurface, IEglSurfaceBase readSurface) {
        javax.microedition.khronos.egl.EGLSurface dSf = null;
        javax.microedition.khronos.egl.EGLSurface rSf = null;
        if(drawSurface != null && (drawSurface instanceof  EglSurfaceBaseKhronos)) {
            dSf = ((EglSurfaceBaseKhronos)drawSurface).getEGLSurface();
        } else if(drawSurface != null){
            throw new RuntimeException("EGL version error,  drawSurface is not getInstance of  EglSurfaceBaseKhronos");
        }

        if(readSurface != null && (readSurface instanceof  EglSurfaceBaseKhronos)) {
            rSf = ((EglSurfaceBaseKhronos)readSurface).getEGLSurface();
        } else if(readSurface != null){
            throw new RuntimeException("EGL version error,  readSurface is not getInstance of  EglSurfaceBaseKhronos");
        }

        javax.microedition.khronos.egl.EGLDisplay display = mEgl.eglGetCurrentDisplay();
        if (display == EGL11.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            YYLog.debug(TAG, "NOTE: makeCurrent w/o display");
        }
        if (!mEgl.eglMakeCurrent(display, dSf, rSf, mEgl.eglGetCurrentContext())) {
            throw new RuntimeException("eglMakeCurrent(draw,read) failed");
        }
    }

    /**
     * Makes no context current.
     */
    @Override
    public void makeNothingCurrent() {
        if (!mEgl.eglMakeCurrent(mEgl.eglGetCurrentDisplay(),
                EGL11.EGL_NO_SURFACE, EGL11.EGL_NO_SURFACE,
                EGL11.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    @Override
    public boolean swapBuffers(IEglSurfaceBase eglSfBase) {
        if(eglSfBase != null && eglSfBase instanceof EglSurfaceBaseKhronos) {
            return _swapBuffers(((EglSurfaceBaseKhronos)eglSfBase).getEGLSurface());
        } else if(eglSfBase != null){
            throw new RuntimeException("EGL version error,  eglSurfaceBase is not EglSurfaceBaseKhronos ");
        }
        return false;
    }

    @Override
    public void setPresentationTime(IEglSurfaceBase eglSfBase, long nsecs) {
        if(eglSfBase != null && eglSfBase instanceof EglSurfaceBaseKhronos) {
            _setPresentationTime(((EglSurfaceBaseKhronos)eglSfBase).getEGLSurface(), nsecs);
        } else if(eglSfBase != null){
            throw new RuntimeException("EGL version error,  eglSurfaceBase is not EglSurfaceBaseKhronos ");
        }
    }

    @Override
    public boolean isCurrent(IEglSurfaceBase eglSfBase) {
        if(eglSfBase != null && eglSfBase instanceof EglSurfaceBaseKhronos) {
            _isCurrent(((EglSurfaceBaseKhronos)eglSfBase).getEGLSurface());
        } else if(eglSfBase != null){
            throw new RuntimeException("EGL version error,  eglSurfaceBase is not EglSurfaceBaseKhronos ");
        }
        return false;
    }

    @Override
    public int querySurface(IEglSurfaceBase eglSfBase, int what) {
        if(eglSfBase != null && eglSfBase instanceof EglSurfaceBaseKhronos) {
            _querySurface(((EglSurfaceBaseKhronos)eglSfBase).getEGLSurface(), what);
        } else if(eglSfBase != null){
            throw new RuntimeException("EGL version error,  eglSurfaceBase is not EglSurfaceBaseKhronos ");
        }
        return -1;
    }
}
