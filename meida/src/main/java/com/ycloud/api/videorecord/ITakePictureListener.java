package com.ycloud.api.videorecord;

import com.ycloud.facedetection.STMobileFaceDetectionWrapper;

/**
 * Created by Administrator on 2018/5/14.
 */

public interface ITakePictureListener {
    /**
     * 拍照图片回调
     * @param result 0 : success , -1 : failed
     * @param path   picture path
     */
    void onTakenPicture(int result, String path);

    /**
     * 生成缩略图回调， TakePictureParam.mThumbnailPath != null 才生效
     * @param result 0 : success , -1 : failed
     * @param path  Thumbnail picture path
     */
    void onTakenThumbnailPicture(int result, String path);

    /**
     * 回调当前图片的人脸肢体等信息， TakePictureParam.mDoFaceDetect = true 才生效
     * @param pointInfo
     */
    void onTakenFacePoint(STMobileFaceDetectionWrapper.FacePointInfo pointInfo);
}
