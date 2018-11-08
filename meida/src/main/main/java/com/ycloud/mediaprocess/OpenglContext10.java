package com.ycloud.mediaprocess;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class OpenglContext10 implements OpenglContext{
    private static final String TAG = "OpenglContext10";
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;
    private SurfaceTexture mSurfaceTexture;
    private EGL10 mEGL = null;
    public OpenglContext10() {
    }

    public void init(SurfaceTexture surfaceTexture) {
        mSurfaceTexture = surfaceTexture;
        eglSetup();
        makeCurrent();
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
     */
    private void eglSetup() {
        mEGL = (EGL10) EGLContext.getEGL();;
        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL10 display");
        }
        int[] version = new int[2];
        if (!mEGL.eglInitialize(mEGLDisplay, version)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL10");
        }
        // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
        // to minimize artifacts from possible YUV conversion.
        int[] attribList = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL_RECORDABLE_ANDROID, 1,
                EGL10.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!mEGL.eglChooseConfig(mEGLDisplay, attribList, configs, configs.length, numConfigs)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }
        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                0x3098, 2,
                EGL10.EGL_NONE
        };
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, configs[0], mEGL.EGL_NO_CONTEXT, attrib_list);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }
   /*     // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL10.EGL_NONE
        };*/
        mEGLSurface = mEGL.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurfaceTexture, null);
        checkEglError("eglCreateWindowSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }
    }

    /**
     * Discard all resources held by this class, notably the EGL context.  Also releases the
     * Surface that was passed to our constructor.
     */
    public void release() {

        if (mEGLDisplay != mEGL.EGL_NO_DISPLAY) {
            mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEGL.eglTerminate(mEGLDisplay);
        }

        if (null != mSurfaceTexture)
            mSurfaceTexture.release();

        mSurfaceTexture= null;

        mEGLDisplay = mEGL.EGL_NO_DISPLAY;
        mEGLContext = mEGL.EGL_NO_CONTEXT;
        mEGLSurface = mEGL.EGL_NO_SURFACE;
    }

    /**
     * Makes our EGL context and surface current.
     */
    private void makeCurrent() {
        if (!mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Checks for EGL errors.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = mEGL.eglGetError()) != mEGL.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
