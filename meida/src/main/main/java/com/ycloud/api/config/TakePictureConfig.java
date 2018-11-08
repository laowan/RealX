package com.ycloud.api.config;


import com.ycloud.api.videorecord.ITakePictureListener;

/**
 * 用于打开摄像头时设置的拍照相关参数，打开摄像头后，这些参数都无法再设置修改，除非重新打开摄像头
 * Created by Administrator on 2018/5/16.
 */

public class TakePictureConfig {
    public static final int DEFAULT_PICTURE_WIDTH = 1080;
    public static final int DEFAULT_PICTURE_HEIGHT = 1440;
    public static final int DEFAULT_JPEG_QUALITY = 100;
    /**
     * 拍照相关消息回调给业务层的回调
     */
    public ITakePictureListener mListener;

    /**
     * 业务层设置拍照分辨率的模式
     */
    public ResolutionSetType mResolutionType;

    /**
     * 业务层期望的图片宽高比，用于 mResolutionType 为 AUTO_RESOLUTION 时使用
     */
    public AspectRatioType mAspectRatio;

    /**
     *  当 mResolutionType 为SET_RESOLUTION 时，下面两个值用来设置期望的图片宽高,
     *  SDK 根据 ratio = mPictureWidth/mPictureHeight 来选择最接近的系统支持的分辨率。
     *  此时 mAspectRatio 参数将无效。
     **/
    public int mPictureWidth;
    public int mPictureHeight;

    /**
     * 拍照时，是否使用系统默认拍照音效
     */
    public boolean mUseDefaultSoundEffect;


    /**
     *  0. SET_RESOLUTION 业务层设置期望的分辨率
     *  1. AUTO_RESOLUTION 自动选择系统支持的符合业务宽高比的最高分辨率
     */
    public enum ResolutionSetType {
        SET_RESOLUTION,
        AUTO_RESOLUTION,
    }

    public TakePictureConfig() {
        this.mListener = null;
        this.mResolutionType = ResolutionSetType.SET_RESOLUTION;
        this.mAspectRatio = AspectRatioType.ASPECT_RATIO_4_3;
        this.mPictureWidth = DEFAULT_PICTURE_WIDTH;
        this.mPictureHeight = DEFAULT_PICTURE_HEIGHT;
        this.mUseDefaultSoundEffect = true;
    }
}
