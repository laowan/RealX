package com.ycloud.imageprocess;

import android.content.Context;
import android.view.SurfaceHolder;

import com.ycloud.api.common.BaseImageView;
import com.ycloud.api.common.IBaseImageView;
import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gpuimagefilter.filter.ImageFilterSessionWrapper;
import com.ycloud.svplayer.surface.ImgProGLManager;
import com.ycloud.utils.YYLog;

import java.io.File;

/**
 * Created by Administrator on 2018/5/17.
 */

public class ImageViewInternal implements IBaseImageView, SurfaceHolder.Callback {
    private static final String TAG = "ImageViewInternal";
    private Context mContext = null;
    private String mImagePath = null;
    private int mHash = 0;
    private ImgProGLManager mImgProGLManager = null;
    private ImageFilterSessionWrapper mImageFilterSessionWrapper = null;
    private BaseImageView mBaseImageView = null;
    private boolean mViewMode = false;  // view 模式提供实时显示特效效果，用于图片特效编辑页。 非view模式用于后台图片加特效处理
    private SurfaceHolder mSurfaceHolder = null;
    private boolean mPreMultiplyAlpha = false;

    // for BaseImageProcess
    public ImageViewInternal(Context context) {
        mContext = context.getApplicationContext();
        mImageFilterSessionWrapper = new ImageFilterSessionWrapper();
        mImgProGLManager = new ImgProGLManager();
        mImgProGLManager.setContext(mContext);
        mImgProGLManager.setFilterSessionId(mImageFilterSessionWrapper.getSessionID());
        YYLog.info(TAG, "Construct ImageViewInternal for Process mode .");
    }

    // for BaseImageView
    public ImageViewInternal(BaseImageView view, Context context) {
        mContext = context;
        mBaseImageView = view;
        mViewMode = true;
        mImageFilterSessionWrapper = new ImageFilterSessionWrapper();
        mImgProGLManager = new ImgProGLManager();
        mImgProGLManager.setContext(mContext);
        mImgProGLManager.setFilterSessionId(mImageFilterSessionWrapper.getSessionID());
        mImgProGLManager.setViewMode(mViewMode);
        mBaseImageView.getHolder().addCallback(this);
        YYLog.info(TAG, "Construct ImageViewInternal for view mode .");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        YYLog.info(TAG, "surfaceCreated .");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        YYLog.info(TAG, "surfaceChanged .");
        mSurfaceHolder = holder;
        mImgProGLManager.setOutputSurface(mSurfaceHolder.getSurface());
        mImgProGLManager.init(width, height, mContext);
        if (mImgProGLManager != null && mImagePath != null && !mImagePath.isEmpty()) {
            mImgProGLManager.processImage(mImagePath, mHash, mPreMultiplyAlpha);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        YYLog.info(TAG, "surfaceDestroyed .");
    }

    @Override
    public boolean refreshView() {
        if (mImgProGLManager != null && mImgProGLManager.inited() && mImagePath != null) {
            mImgProGLManager.processImage(mImagePath, mHash, mPreMultiplyAlpha);
            return true;
        }
        return false;
    }


    @Override
    public boolean setImagePath(String path) {
        YYLog.info(TAG, "setImagePath " + path);
        File f = new File(path);
        if (!f.exists()) {
            YYLog.error(TAG, "File : " + path + " not exist !");
            return false;
        }

        mImagePath = path;
        return true;
    }

    @Override
    public boolean setImagePath(String path, int hash) {
        YYLog.info(TAG, "setImagePath " + path + " hash " + hash);
        File f = new File(path);
        if (!f.exists()) {
            YYLog.error(TAG, "File : " + path + " not exist !");
            return false;
        }

        mImagePath = path;
        mHash = hash;
        return true;
    }

    @Override
    public void setPreMultiplyAlpha(boolean preMultiplyAlpha) {
        mPreMultiplyAlpha  = preMultiplyAlpha;
    }

    public void clearTaskQueue(){
        if (mImgProGLManager != null && !mViewMode) {
            mImgProGLManager.clearTaskQueue();
        }
    }

    public void startProcess() {
        if (mImgProGLManager != null && !mViewMode && mImagePath != null) {
            mImgProGLManager.processImage(mImagePath, mHash, mPreMultiplyAlpha);
        }
    }

    @Override
    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        if (mImgProGLManager != null) {
            mImgProGLManager.setFaceDetectionListener(listener);
        }
    }

    @Override
    public void setImageProcessListener(ImageProcessListener listener) {
        if (mImgProGLManager != null) {
            mImgProGLManager.setImageProcessListener(listener);
        }
    }

    @Override
    public ImageFilterSessionWrapper getImageFilterSessionWrapper() {
        return mImageFilterSessionWrapper;
    }

    @Override
    public void release() {
        if (mImgProGLManager != null) {
            mImgProGLManager.unInit();
            mImgProGLManager = null;
        }
        if (mImageFilterSessionWrapper != null) {
            mImageFilterSessionWrapper = null;
        }
    }

    @Override
    public boolean setLayoutMode(int mode) {
        return false;
    }
}
