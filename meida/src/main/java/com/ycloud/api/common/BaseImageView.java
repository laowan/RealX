package com.ycloud.api.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gpuimagefilter.filter.ImageFilterSessionWrapper;
import com.ycloud.imageprocess.ImageViewInternal;

/**
 * Created by Administrator on 2018/6/8.
 *
 */

public class BaseImageView extends SurfaceView implements IBaseImageView {
    private Context mContext = null;
    private ImageViewInternal mImageViewInternal = null;

    public BaseImageView(Context context) {
        super(context);
        mContext = context;
        mImageViewInternal = new ImageViewInternal(this, context);
    }

    public BaseImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mImageViewInternal = new ImageViewInternal(this, context);
    }

    public BaseImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        mImageViewInternal = new ImageViewInternal(this, context);
    }

    @Override
    public boolean setImagePath(String path) {
        if(mImageViewInternal != null) {
            return mImageViewInternal.setImagePath(path);
        }
        return false;
    }

    @Override
    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        if(mImageViewInternal != null) {
            mImageViewInternal.setFaceDetectionListener(listener);
        }
    }

    @Override
    public boolean refreshView() {
        if (mImageViewInternal != null) {
            return mImageViewInternal.refreshView();
        }
        return false;
    }

    // TODO. implement differernt mode
    @Override
    public boolean setLayoutMode(int mode) {
        if (mImageViewInternal != null) {
            mImageViewInternal.setLayoutMode(mode);
        }
        return false;
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
    public void setImageProcessListener(ImageProcessListener listener) {

    }

    @Override
    public void clearTaskQueue() {

    }

    @Override
    public void startProcess() {
        throw new RuntimeException("BaseImageView do not support startProcess operation.");
    }

    @Override
    public boolean setImagePath(String path, int hash) {
        return false;
    }

    @Override
    public void setPreMultiplyAlpha(boolean preMultiplyAlpha) {

    }
}
