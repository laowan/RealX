package com.ycloud.api.common;

import android.content.Context;

import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gpuimagefilter.filter.ImageFilterSessionWrapper;
import com.ycloud.imageprocess.ImageViewInternal;

/**
 * Created by Administrator on 2018/5/17.
 */

public class BaseImageProcesses implements IBaseImageView{
    private ImageViewInternal mImageViewInternal = null;

    public BaseImageProcesses(Context context) {
        mImageViewInternal = new ImageViewInternal(context);
    }

    @Override
    public boolean setImagePath(String path) {
        if(mImageViewInternal != null) {
            return mImageViewInternal.setImagePath(path);
        }
        return false;
    }

    @Override
    public boolean setImagePath(String path, int hash) {
        if(mImageViewInternal != null) {
            return mImageViewInternal.setImagePath(path,hash);
        }
        return false;
    }

    @Override
    public void setPreMultiplyAlpha(boolean preMultiplyAlpha) {
        if(mImageViewInternal != null) {
            mImageViewInternal.setPreMultiplyAlpha(preMultiplyAlpha);
        }
    }

    @Override
    public void clearTaskQueue() {
        if(mImageViewInternal != null) {
            mImageViewInternal.clearTaskQueue();
        }
    }

    @Override
    public void startProcess() {
        if(mImageViewInternal != null) {
            mImageViewInternal.startProcess();
        }
    }

    @Override
    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        if(mImageViewInternal != null) {
            mImageViewInternal.setFaceDetectionListener(listener);
        }
    }

    @Override
    public void setImageProcessListener(ImageProcessListener listener) {
        if(mImageViewInternal != null) {
            mImageViewInternal.setImageProcessListener(listener);
        }
    }

    @Override
    public ImageFilterSessionWrapper getImageFilterSessionWrapper() {
        if (mImageViewInternal != null) {
            return mImageViewInternal.getImageFilterSessionWrapper();
        }
        return null;
    }

    @Override
    public void release() {
        if (mImageViewInternal != null) {
            mImageViewInternal.release();
        }
    }

    @Override
    public boolean refreshView() {
        throw new RuntimeException("BaseImageProcess do not support refresh operation.");
    }

    @Override
    public boolean setLayoutMode(int mode) {
        throw new RuntimeException("BaseImageProcess do not support setLayoutMode operation.");
    }
}
