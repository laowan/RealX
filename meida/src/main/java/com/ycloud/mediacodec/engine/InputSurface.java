/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// from: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/InputSurface.java
// blob: 157ed88d143229e4edb6889daf18fb73aa2fc5a5
package com.ycloud.mediacodec.engine;

import android.annotation.TargetApi;
import android.opengl.EGL14;
import android.os.Build;
import android.view.Surface;

import com.ycloud.gles.core.EglContextWrapper;
import com.ycloud.gles.core.EglHelperFactory;
import com.ycloud.gles.core.GLBuilder;
import com.ycloud.gles.core.IEglHelper;

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 * <p>
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
 * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
 * to the video encoder.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class InputSurface {
    private static final String TAG = "OpenglContext";
    private GLBuilder.EGLConfigChooser mConfigChooser = null;
    private GLBuilder.EGLContextFactory mEglContextFactory = null;
    private GLBuilder.EGLWindowSurfaceFactory mEglWindowSurfaceFactory = null;
    private IEglHelper mEglHelper = null;
    private EglContextWrapper mEglContext = EglContextWrapper.EGL_NO_CONTEXT_WRAPPER;
    private Surface mSurface;
    /**
     * Creates an OpenglContext from a Surface.
     */
    public InputSurface(Surface surface) {
        if (surface == null) {
            throw new NullPointerException();
        }
        mSurface = surface;
        eglSetup();
    }
    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
     */
    private void eglSetup() {
        if (mConfigChooser == null) {
            mConfigChooser = new GLBuilder.SimpleEGLConfigChooser(true, 2);
        }
        if (mEglContextFactory == null) {
            mEglContextFactory = new GLBuilder.DefaultContextFactory(2);
        }
        if (mEglWindowSurfaceFactory == null) {
            mEglWindowSurfaceFactory = new GLBuilder.DefaultWindowSurfaceFactory();
        }

        mEglHelper = EglHelperFactory.create(mConfigChooser, mEglContextFactory, mEglWindowSurfaceFactory);
        mEglContext = mEglHelper.start(mEglContext);
        mEglHelper.createSurface(mSurface);
    }
    /**
     * Discard all resources held by this class, notably the EGL context.  Also releases the
     * Surface that was passed to our constructor.
     */
    public void release() {
        mEglHelper.destroySurface();
        mEglHelper.finish();
        mSurface.release();
        mSurface = null;
    }
    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        if (mEglHelper != null) {
            mEglHelper.makeCurrent();
        }
    }

    public void makeUnCurrent() {
        if (mEglHelper != null) {
            mEglHelper.makeUnCurrent();
        }
    }
    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     */
    public boolean swapBuffers() {
        mEglHelper.swap();
        return true;
    }
    /**
     * Returns the Surface that the MediaCodec receives buffers from.
     */
    public Surface getSurface() {
        return mSurface;
    }
    /**
     * Queries the surface's width.
     */
    public int getWidth() {
        return mEglHelper.getWidth();
    }
    /**
     * Queries the surface's height.
     */
    public int getHeight() {
        return mEglHelper.getHeight();
    }
    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        mEglHelper.setPresentationTime(nsecs);
    }
    /**
     * Checks for EGL errors.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
