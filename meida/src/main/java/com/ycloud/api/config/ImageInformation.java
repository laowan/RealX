package com.ycloud.api.config;

/**
 * Created by Administrator on 2018/5/22.
 */

public class ImageInformation {
    public ImageInformation(String mImagePath, float mDuration) {
        this.mDuration = mDuration;
        this.mImagePath = mImagePath;
    }

    /**
     * 当前图片显示的时间长度，单位秒
     */
    public float mDuration;
    /**
     * 当前图片的完整路径
     */
    public String mImagePath;
    /**
     * SDK 内部使用，业务层无需设置
     */
    public String mTmpPath;
}
