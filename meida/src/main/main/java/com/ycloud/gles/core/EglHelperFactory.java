package com.ycloud.gles.core;

import android.os.Build;

public class EglHelperFactory {

    public static IEglHelper create(GLBuilder.EGLConfigChooser configChooser, GLBuilder.EGLContextFactory eglContextFactory
            , GLBuilder.EGLWindowSurfaceFactory eglWindowSurfaceFactory) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return new EglHelperAPI17(configChooser, eglContextFactory, eglWindowSurfaceFactory);
        } else {
            return new EglHelper(configChooser, eglContextFactory, eglWindowSurfaceFactory);
        }
    }

}
