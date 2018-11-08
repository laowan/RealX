package com.ycloud.svplayer.surface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.ycloud.api.common.BaseImageProcesses;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gles.core.EglContextWrapper;
import com.ycloud.gles.core.EglHelperFactory;
import com.ycloud.gles.core.GLBuilder;
import com.ycloud.gles.core.IEglHelper;
import com.ycloud.gpuimagefilter.filter.FilterCenter;
import com.ycloud.gpuimagefilter.filter.FilterSession;
import com.ycloud.gpuimagefilter.filter.ImageProcessFilterGroup;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.mediarecord.InputSurface;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by liuchunyu on 2017/8/29.
 */

public class ImgProGLManager {
    public final static String TAG = ImgProGLManager.class.getSimpleName();
    private int mOutputWidth;
    private int mOutputHeight;

    private InputSurface mInputSurface;
    private SurfaceTexture mSurfaceTexture;
    private ImgGLThread mGlThread;

    private Object mInitReady = new Object();
    private Object mReleaseSyncLock = new Object();

    private ImageProcessFilterGroup mImageProcessFilterGroup;
    private FilterSession mFilterSession;
    private ImgProCallBack mImgProCallBack;

    private boolean mIsReleased = false;

    private int mFilterSessionId = -1;
    private IFaceDetectionListener mFaceDetectingListener = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private Context mContext = null;
    private ImageProcessListener mImageProcessListener = null;
    private boolean mViewMode = false;
    // for view mode only
    private GLBuilder.EGLConfigChooser mConfigChooser = null;
    private GLBuilder.EGLContextFactory mEglContextFactory = null;
    private GLBuilder.EGLWindowSurfaceFactory mEglWindowSurfaceFactory = null;
    private IEglHelper mEglHelper = null;
    private EglContextWrapper mEglContext = EglContextWrapper.EGL_NO_CONTEXT_WRAPPER;
    private Surface mOutputSurface = null;

    public ImgProGLManager() {
    }

    public void setContext(Context context) {
        mContext = context;
    }
    public void setFilterSessionId(int sessionId) {
        mFilterSessionId = sessionId;
    }
    public void setViewMode(boolean viewMode) {
        mViewMode = viewMode;
    }
    public void setOutputSurface(Surface surface) {
        mOutputSurface = surface;
    }

    public boolean inited() {
        return mInited.get();
    }

    public void init(int outputWidth, int outputHeight, Context context) {
        if (mInited.get()) {
            return;
        }
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;

        mGlThread = new ImgGLThread(TAG);
        mGlThread.start();
        if (SDKCommonCfg.getRecordModePicture()) {
            mFilterSession = FilterCenter.getInstance().createFilterSession(mFilterSessionId);
        } else {
            mFilterSession = FilterCenter.getInstance().createFilterSession();
        }
        mImageProcessFilterGroup = new ImageProcessFilterGroup(context, mFilterSession.getSessionID(), mGlThread.getLooper(), mViewMode);
        mImageProcessFilterGroup.setFaceDetectionListener(mFaceDetectingListener);
        mImageProcessFilterGroup.setImageProcessListener(mImageProcessListener);
        mGlThread.init();

        synchronized (mInitReady) {
            try {
                mInitReady.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mInited.set(true);
        Log.e(TAG, "ImgProGLManager ready");
    }

    public void unInit() {
        if (mGlThread != null) {
            synchronized (mReleaseSyncLock) {

                try {
                    if (mGlThread != null) {
                        // Schedule release on the playback thread
                        mGlThread.unInit();
                        mGlThread = null;
                        // Wait for the release on the playback thread to finish
                        mReleaseSyncLock.wait();
                    }
                } catch (InterruptedException e) {
                    // nothing to do here
                }
            }
        }
        mInited.set(false);
    }

    public boolean isReleased() {
        return mIsReleased;
    }

    public FilterSession getFilterSession() {
        return mFilterSession;
    }

    public void processImages(String imageBasePath, int imageRate, ImgProCallBack imgProCallBack) {
        mImgProCallBack = imgProCallBack;
        mGlThread.processImages(imageBasePath, imageRate);
    }

    public void clearTaskQueue() {
        if (mGlThread != null) {
            mGlThread.clearTaskQueue();
        }
    }

    public void processImage(String path, int hash, boolean preMultipyAlpha) {
        if (SDKCommonCfg.getRecordModePicture()) {
            if (!mInited.get()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                init(options.outWidth, options.outHeight, mContext);
            }
        }
        if (mGlThread != null) {
            mGlThread.processImage(path, hash, preMultipyAlpha);
        }
    }

    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        mFaceDetectingListener = listener;
    }

    public void setImageProcessListener(ImageProcessListener listener) {
        mImageProcessListener = listener;
    }

    private class ImgGLThread extends HandlerThread implements Handler.Callback {
        private static final int GL_INIT = 1;
        private static final int GL_RELEASE = 2;
        private static final int GL_PROCESS_IMG = 3;
        private static final int GL_PROCESS_IMG_EX = 4;


        private static final String IMAGE_BASE_PATH = "imageBasePath";
        private static final String IMAGE_RATE = "imageRate";
        private static final String IMAGE_HASH = "imageHash";
        private static final String IMAGE_PREMULTI_ALPHA = "imagePreMultiplyAlpha";

        private Handler mHandler;

        public ImgGLThread(String name) {
            super(name);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case GL_INIT:
                    if (SDKCommonCfg.getRecordModePicture() && mViewMode) {
                        initInternalEx();
                    } else {
                        initInternal();
                    }
                    break;
                case GL_RELEASE:
                    interrupt();
                    quit();
                    if (SDKCommonCfg.getRecordModePicture() && mViewMode) {
                        unInitInternalEx();
                    } else {
                        unInitInternal();
                    }
                    break;
                case GL_PROCESS_IMG:
                    Bundle bundle = msg.getData();
                    String imageBasePath = bundle.getString(IMAGE_BASE_PATH);
                    int imageRate = bundle.getInt(IMAGE_RATE);
                    processImagesInternal(imageBasePath, imageRate);
                    break;
                case GL_PROCESS_IMG_EX:
                    Bundle b = msg.getData();
                    String imagePath = b.getString(IMAGE_BASE_PATH);
                    int hash = b.getInt(IMAGE_HASH, 0);
                    boolean preMultiplyAlpha = b.getBoolean(IMAGE_PREMULTI_ALPHA);
                    processImageInternal(imagePath, hash, preMultiplyAlpha);
                    break;
                default:
                    break;
            }

            return false;
        }

        @Override
        public synchronized void start() {
            super.start();
            // Create the handler that will process the messages on the handler thread
            mHandler = new Handler(this.getLooper(), this);
        }

        public void init() {
            Message msg = Message.obtain();
            msg.what = GL_INIT;
            mHandler.sendMessage(msg);
        }

        public void unInit() {
            mHandler.sendEmptyMessage(GL_RELEASE);
        }

        public void processImages(String imageBasePath, int imageRate) {
            Message msg = Message.obtain();
            msg.what = GL_PROCESS_IMG;
            Bundle bundle = new Bundle();
            bundle.putString(IMAGE_BASE_PATH, imageBasePath);
            bundle.putInt(IMAGE_RATE, imageRate);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

        public void clearTaskQueue() {
            if (mHandler != null) {
                mHandler.removeMessages(GL_PROCESS_IMG_EX);
            }
        }

        public void processImage(String path, int hash, boolean preMultipyAlpha) {
            Message msg = Message.obtain();
            msg.what = GL_PROCESS_IMG_EX;
            Bundle bundle = new Bundle();
            bundle.putString(IMAGE_BASE_PATH, path);
            bundle.putInt(IMAGE_HASH, hash);
            bundle.putBoolean(IMAGE_PREMULTI_ALPHA, preMultipyAlpha);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    private void initInternal() {
        OpenGlUtils.checkGlError("initInternal begin");
        YYLog.info(TAG, "initInternal");
        // 创建环境
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        mSurfaceTexture = new SurfaceTexture(texture[0]);
        Surface surface = new Surface(mSurfaceTexture);
        mInputSurface = new InputSurface(surface);
        mInputSurface.makeCurrent();

        mImageProcessFilterGroup.init(mOutputWidth, mOutputHeight);
        mImageProcessFilterGroup.startListen();

        synchronized(mInitReady) {
            mInitReady.notify();
        }
        OpenGlUtils.checkGlError("initInternal end");
    }

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
        mEglHelper.createSurface(mOutputSurface);
        mEglHelper.makeCurrent();
    }

    private void initInternalEx() {
        OpenGlUtils.checkGlError("initInternalEx begin");
        YYLog.info(TAG, "initInternalEx");
        eglSetup();
        mImageProcessFilterGroup.init(mOutputWidth, mOutputHeight);
        mImageProcessFilterGroup.startListen();

        synchronized(mInitReady) {
            mInitReady.notify();
        }
        OpenGlUtils.checkGlError("initInternal end");
    }

    private void unInitInternalEx() {

        if (mImageProcessFilterGroup != null) {
            mImageProcessFilterGroup.destroy();
            FilterCenter.getInstance().removeFilterObserver(mImageProcessFilterGroup, mFilterSession.getSessionID());
            mImageProcessFilterGroup = null;
        }
        mFilterSession = null;

        mEglHelper.destroySurface();
        mEglHelper.finish();
        YYLog.info(TAG, "releaseInternal mEglHelper destroySurface and finish");
        mOutputSurface = null;
        if (mReleaseSyncLock != null) {
            synchronized (mReleaseSyncLock) {
                if (mReleaseSyncLock != null) {
                    mReleaseSyncLock.notify();
                }
            }
        }

        mIsReleased = true;
        mInited.set(false);
        YYLog.info(TAG, "unInitInternalEx");
    }


    public void unInitInternal() {
        if (mImageProcessFilterGroup != null) {
            mImageProcessFilterGroup.destroy();
            FilterCenter.getInstance().removeFilterObserver(mImageProcessFilterGroup, mFilterSession.getSessionID());
            mImageProcessFilterGroup = null;
        }

        mFilterSession = null;

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }

        if (mReleaseSyncLock != null) {
            synchronized (mReleaseSyncLock) {
                if (mReleaseSyncLock != null) {
                    mReleaseSyncLock.notify();
                }
            }
        }

        mIsReleased = true;
        mInited.set(false);
        YYLog.info(TAG, "unInitInternal");
    }

    private void processImagesInternal(String imageBasePath, int imageRate) {
        OpenGlUtils.checkGlError("processImagesInternal begin");
        mImageProcessFilterGroup.processImages(imageBasePath, imageRate);
        mImgProCallBack.onImgProCallBack();
        OpenGlUtils.checkGlError("processImagesInternal end");
    }

    private void processImageInternal(String path, int hash, boolean preMultiplyAlpha) {
        mImageProcessFilterGroup.processImage(path, hash, preMultiplyAlpha);
        if (mViewMode && mEglHelper != null) {
            mEglHelper.swap();
        }
    }
}
