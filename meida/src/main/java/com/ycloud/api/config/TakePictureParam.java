package com.ycloud.api.config;

/**
 * 每次调拍照接口拍照时，可以改变的拍照相关参数
 * Created by Administrator on 2018/5/16.
 */

public class TakePictureParam {
    /**
     * JPEG 编码的质量(0~100) 值越高越清晰，编码耗时更长
     */
    public int mQuality;

    /**
     * 拍照获取图片的全路径，如:/sdcard/yoyi/album/xxx.jpg 每次拍照时传入
     */
    public String mImagePath;

    /**
     * 缩略图的全路径，如:/sdcard/yoyi/album/xxx.jpg 每次拍照时传入
     */
    public String mThumbnailPath;

    /**
     *  缩略图编码质量
     */
    public int mThumbnailQuality;

    /**
     * 生成缩略图的分辨率
     */
    public int mThumbnailWidth;
    public int mthumbnailHeight;

    /**
     * 生成缩略图的格式
     */
    public PictureCodingType mThumbnailCodeType;

    /**
     * 拍照后是否实时识别当前照片的人脸信息并回调给业务层
     */
    public boolean mDoFaceDetect;

    /**
     * 生成图片的宽高比，如果相机出来的原始视频不是该宽高比，SDK裁剪成对应的宽高比
     */
    public AspectRatioType mAspect;

    /**
     * 拍照的图片是否做水平镜像
     */
    public boolean mFlipX;

    /**
     * 图片编码格式 jpeg / png, 缩略图用png, 照片用jpeg
     * Attention: PNG compression is much much much slower than JPEG compression on Android, especially when your bitmap size is high.
	 * SDK hard code JPEG, because png cost too much time
     */
    public enum PictureCodingType {
        PICTURE_CODING_TYPE_JPEG,
        PICTURE_CODING_TYPE_PNG,
    }

    public TakePictureParam() {
        this.mQuality = 100;
        this.mImagePath = null;
        this.mThumbnailCodeType = PictureCodingType.PICTURE_CODING_TYPE_PNG;
    }
}
