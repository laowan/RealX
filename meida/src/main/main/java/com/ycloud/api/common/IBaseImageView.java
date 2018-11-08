package com.ycloud.api.common;

import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gpuimagefilter.filter.ImageFilterSessionWrapper;

/**
 * Created by Administrator on 2018/5/17.
 */

public interface IBaseImageView {
    // for both
    boolean setImagePath(String path);
    /** 对同一张照片做一系列不同的特效处理并返回位图，hash用来区分不同结果的位图, 默认值0*/
    boolean setImagePath(String path, int hash);
    void setFaceDetectionListener(IFaceDetectionListener listener);
    void setImageProcessListener(ImageProcessListener listener);
    ImageFilterSessionWrapper getImageFilterSessionWrapper();
    void release();
    /** 清除阻塞在任务队列中还未开始执行的特效处理任务，用于触摸调节滤镜参数场景，触摸滑动速度快，任务消息非常多 */
    void clearTaskQueue();
    // for BaseImageProcess
    void startProcess();

    /** 设置是否预乘Alpha通道， 对于与透明度相关的特效需要设置为 true */
    void setPreMultiplyAlpha(boolean preMultiplyAlpha);

    // for BaseImageView
    boolean refreshView();
    boolean setLayoutMode(int mode);
}
