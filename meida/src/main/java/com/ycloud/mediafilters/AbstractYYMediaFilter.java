package com.ycloud.mediafilters;

import com.ycloud.ymrmodel.YYMediaSample;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by kele on 2016/11/17.
 */

public abstract  class AbstractYYMediaFilter implements IMediaFilter
{
    protected ArrayList<IMediaFilter> mDownStreamList = new ArrayList<IMediaFilter>();
    protected int     mImageWidth = 0;
    protected int     mImageHeight = 0;

    protected int       mOutputWidth = 0;
    protected int       mOutputHeight = 0;

    protected AtomicReference<YYMediaFilterListener>  mFilterListener = new AtomicReference<>(null);

    public void setFilterListener(YYMediaFilterListener listener) {
        mFilterListener = new AtomicReference<>(listener);
    }

    public void setImageSize(int imageWidth, int imageHeight) {
        mImageHeight = imageHeight;
        mImageWidth = imageWidth;
    }

    public boolean checkImageSizeUpdated(int imageWidth, int imageHeight, boolean update) {
        if(imageWidth != mImageWidth || imageHeight != mImageHeight) {
            if(update) {
                mImageWidth = imageWidth;
                mImageHeight = imageHeight;
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean checkOuptuSizeUpdate(int outputWidth, int outputHeight, boolean update) {
        if(outputWidth != mOutputWidth || outputHeight != mOutputHeight) {
            if(update) {
                mOutputWidth = outputWidth;
                mOutputHeight = outputHeight;
            }
            return true;
        } else {
            return false;
        }
    }

    public void setOutputSize(int width, int height) {
        mOutputWidth = width;
        mOutputHeight = height;
    }

    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        //do nothing.
        return false;
    }

    public AbstractYYMediaFilter addDownStream(IMediaFilter downStream) {
        //keep unique
        if(mDownStreamList.indexOf(downStream) < 0) {
            mDownStreamList.add(downStream);
        }
        return this;
    }

    public void removeDownStream(IMediaFilter downStrean) {
        mDownStreamList.remove(downStrean);
    }

    public void removeAllDownStream() {
        mDownStreamList.clear();
    }

    public void deliverToDownStream(YYMediaSample sample) {
        for(IMediaFilter  filter : mDownStreamList) {
            filter.processMediaSample(sample, this);
        }
    }

    public void deInit(){
    }
}
