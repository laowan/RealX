package com.ycloud.gles.core;

import android.annotation.TargetApi;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.os.Build;
import android.util.Log;

import com.ycloud.utils.YYLog;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class GLBuilder {
    private static final String TAG = GLBuilder.class.getSimpleName();

    public interface EGLConfigChooser {
        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display);

        android.opengl.EGLConfig chooseConfig(android.opengl.EGLDisplay display, boolean recordable);
    }

    private static abstract class BaseConfigChooser
            implements EGLConfigChooser {


        private static final int EGL_RECORDABLE_ANDROID = 0x3142;
        protected int[] mConfigSpec;
        private int mContextClientVersion;

        public BaseConfigChooser(int[] configSpec, int contextClientVersion) {
            mContextClientVersion = contextClientVersion;
            mConfigSpec = filterConfigSpec(configSpec);
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] num_config = new int[1];
            if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config)) {
                YYLog.e(TAG, "eglChooseConfig failed");
                return null;
            }

            int numConfigs = num_config[0];

            if (numConfigs <= 0) {
                YYLog.e(TAG, "No configs match configSpec");
                return null;
            }

            YYLog.i(TAG, "eglChooseConfig numConfigs = " + numConfigs);

            EGLConfig[] configs = new EGLConfig[numConfigs];
            if (!egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs, num_config)) {
                YYLog.e(TAG, "eglChooseConfig#2 failed");
                return null;
            }
            EGLConfig config = chooseConfig(egl, display, configs);
            if (config == null) {
                YYLog.e(TAG, "No config chosen");
            }
            return config;
        }

        abstract EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs);


        private int[] filterConfigSpec(int[] configSpec) {
            if (mContextClientVersion != 2 && mContextClientVersion != 3) {
                return configSpec;
            }
            /* We know none of the subclasses define EGL_RENDERABLE_TYPE.
             * And we know the configSpec is well formed.
             */
            int len = configSpec.length;
            int[] newConfigSpec = new int[len + 2];
            System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1);
            newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE;
            if (mContextClientVersion == 2) {
                newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT;  /* EGL_OPENGL_ES2_BIT */
            } else {
                newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR; /* EGL_OPENGL_ES3_BIT_KHR */
            }
            newConfigSpec[len + 1] = EGL10.EGL_NONE;
            return newConfigSpec;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public android.opengl.EGLConfig chooseConfig(android.opengl.EGLDisplay display, boolean recordable) {
            int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
            if (mContextClientVersion >= 3) {
                renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
            }

            // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
            // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
            // when reading into a GL_RGBA buffer.
            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_STENCIL_SIZE, 0,
                    EGL14.EGL_RENDERABLE_TYPE, renderableType,
                    EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                    EGL14.EGL_NONE
            };
            if (recordable) {
                attribList[attribList.length - 3] = EGL_RECORDABLE_ANDROID;
                attribList[attribList.length - 2] = 1;
            }
            android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, configs.length,
                    numConfigs, 0)) {
                YYLog.w(TAG, "unable to find RGB8888 / " + mContextClientVersion + " EGLConfig");
                return null;
            }
            return configs[0];
        }
    }

    /**
     * Choose a configuration with exactly the specified r,g,b,a sizes,
     * and at least the specified depth and stencil sizes.
     */
    private static class ComponentSizeChooser extends BaseConfigChooser {
        public ComponentSizeChooser(int redSize, int greenSize, int blueSize,
                                    int alphaSize, int depthSize, int stencilSize, int contextClientVersion) {
            super(new int[]{
                    EGL10.EGL_RED_SIZE, redSize,
                    EGL10.EGL_GREEN_SIZE, greenSize,
                    EGL10.EGL_BLUE_SIZE, blueSize,
                    EGL10.EGL_ALPHA_SIZE, alphaSize,
                    EGL10.EGL_DEPTH_SIZE, depthSize,
                    EGL10.EGL_STENCIL_SIZE, stencilSize,
                    EGL10.EGL_NONE}, contextClientVersion);
            mValue = new int[1];
            mRedSize = redSize;
            mGreenSize = greenSize;
            mBlueSize = blueSize;
            mAlphaSize = alphaSize;
            mDepthSize = depthSize;
            mStencilSize = stencilSize;
        }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) {
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                if ((d >= mDepthSize) && (s >= mStencilSize)) {
                    int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                    int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                    int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                    int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
                    if ((r == mRedSize) && (g == mGreenSize) && (b == mBlueSize) && (a == mAlphaSize)) {
                        return config;
                    }
                }
            }
            return null;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display,
                                     EGLConfig config, int attribute, int defaultValue) {
            if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
                return mValue[0];
            }

            YYLog.w(TAG, "unable to find attribute + " + attribute);
            return defaultValue;
        }

        private int[] mValue;
        // Subclasses can adjust these values:
        protected int mRedSize;
        protected int mGreenSize;
        protected int mBlueSize;
        protected int mAlphaSize;
        protected int mDepthSize;
        protected int mStencilSize;
    }

    /**
     * This class will choose a RGB_888 surface with
     * or without a depth buffer.
     */
    public static class SimpleEGLConfigChooser extends ComponentSizeChooser {
        public SimpleEGLConfigChooser(boolean withDepthBuffer, int contextClientVersion) {
            super(8, 8, 8, 8, withDepthBuffer ? 16 : 0, 0, contextClientVersion);
        }
    }


    public interface EGLContextFactory {
        EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig, EGLContext eglContext);

        void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context);


        android.opengl.EGLContext createContextAPI17(android.opengl.EGLDisplay display, android.opengl.EGLConfig eglConfig, android.opengl.EGLContext eglContext);


        void destroyContext(android.opengl.EGLDisplay display, android.opengl.EGLContext context);
    }

    public static class DefaultContextFactory implements EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        private int contextClientVersion;

        public DefaultContextFactory(int contextClientVersion) {
            this.contextClientVersion = contextClientVersion;
        }

        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config, EGLContext eglContext) {
            int[] attrib_list = {
                    EGL_CONTEXT_CLIENT_VERSION, contextClientVersion,
                    EGL10.EGL_NONE};

            return egl.eglCreateContext(display, config, eglContext, contextClientVersion != 0 ? attrib_list : null);
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display,
                                   EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
                YYLog.e("DefaultContextFactory", "display:" + display + " context: " + context);
                EglHelper.throwEglException("eglDestroyContext", egl.eglGetError());
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public android.opengl.EGLContext createContextAPI17(android.opengl.EGLDisplay display, android.opengl.EGLConfig eglConfig, android.opengl.EGLContext sharedContext) {
            int[] attrib_list = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, contextClientVersion,
                    EGL14.EGL_NONE};
            return EGL14.eglCreateContext(display, eglConfig, sharedContext, attrib_list, 0);
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void destroyContext(android.opengl.EGLDisplay display, android.opengl.EGLContext context) {
            if (!EGL14.eglDestroyContext(display, context)) {
                YYLog.e("DefaultContextFactory", "display:" + display + " context: " + context);
                EglHelper.throwEglException("eglDestroyContext", EGL14.eglGetError());
            }
        }
    }


    public interface EGLWindowSurfaceFactory {
        /**
         * @return null if the surface cannot be constructed.
         */
        EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config,
                                       Object nativeWindow);

        void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface);

        android.opengl.EGLSurface createWindowSurface(android.opengl.EGLDisplay display, android.opengl.EGLConfig config,
                                                      Object nativeWindow);

        void destroySurface(android.opengl.EGLDisplay display, android.opengl.EGLSurface surface);
    }

    public static class DefaultWindowSurfaceFactory implements EGLWindowSurfaceFactory {

        @Override
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display,
                                              EGLConfig config, Object nativeWindow) {

            int[] surfaceAttribs = {
                    EGL10.EGL_NONE
            };
            EGLSurface result = null;
            try {
                result = egl.eglCreateWindowSurface(display, config, nativeWindow, surfaceAttribs);
            } catch (IllegalArgumentException e) {
                // This exception indicates that the surface flinger surface
                // is not valid. This can happen if the surface flinger surface has
                // been torn down, but the application has not yet been
                // notified via SurfaceHolder.Callback.surfaceDestroyed.
                // In theory the application should be notified first,
                // but in practice sometimes it is not. See b/4588890
                Log.e("DefaultWindow", "eglCreateWindowSurface", e);
            }
            return result;
        }

        @Override
        public void destroySurface(EGL10 egl, EGLDisplay display,
                                   EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public android.opengl.EGLSurface createWindowSurface(android.opengl.EGLDisplay display, android.opengl.EGLConfig config, Object nativeWindow) {
            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };
            android.opengl.EGLSurface result = null;
            try {
                result = EGL14.eglCreateWindowSurface(display, config, nativeWindow, surfaceAttribs, 0);
            } catch (IllegalArgumentException e) {
                // This exception indicates that the surface flinger surface
                // is not valid. This can happen if the surface flinger surface has
                // been torn down, but the application has not yet been
                // notified via SurfaceHolder.Callback.surfaceDestroyed.
                // In theory the application should be notified first,
                // but in practice sometimes it is not. See b/4588890
                Log.e("DefaultWindow", "eglCreateWindowSurface", e);
            }
            return result;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void destroySurface(android.opengl.EGLDisplay display, android.opengl.EGLSurface surface) {
            EGL14.eglDestroySurface(display, surface);
        }
    }
}
