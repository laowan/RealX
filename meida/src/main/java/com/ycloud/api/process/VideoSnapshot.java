package com.ycloud.api.process;

import com.ycloud.mediaprocess.IMediaSnapshot;
import com.ycloud.mediaprocess.IMediaSnapshotPictureListener;
import com.ycloud.mediaprocess.MediaSnapshot;
import com.ycloud.mediaprocess.MediaSnapshotSession;

/**
 * 视频截图
 */
public class VideoSnapshot {
    private static final String VideoSnapshot = VideoSnapshot.class.getSimpleName();
    IMediaSnapshot mMediaSnapshot;
    private String mSourcePath = "";
    private String mOutputPath = "";
    private boolean mUseMediaSnapshotSession = true;

    public VideoSnapshot() {
        if (mUseMediaSnapshotSession) {
            mMediaSnapshot = new MediaSnapshotSession();
        } else {
            mMediaSnapshot = new MediaSnapshot();
        }
    }

    /**
     * 设置文件路径
     * @param captureCount 需要从MP4文件中截图的数量，一般13张
     */
    public void setCaptureCount(int captureCount){
        mMediaSnapshot.setSnapShotCnt(captureCount);
    }

    /**
     * 设置编码JPEG图片的质量 0 ~ 100
     *
     * @param quality 质量 0 ~ 100
     */
    public void setPictureQuality(int quality) {
        mMediaSnapshot.setPictureQuality(quality);
    }

    /**
     * 设置文件路径
     *
     * @param sourcePath 源文件
     * @param outputPath 输出路径 (单张截图为文件路径，批量截图为文件夹路径)
     */
    public void setPath(String sourcePath, String outputPath) {
        mSourcePath = sourcePath;
        mOutputPath = outputPath;
        mMediaSnapshot.setPath(sourcePath, outputPath);
    }

    public void setPicturePrefix(String prefix)
    {
        mMediaSnapshot.setPicturePrefix(prefix);
    }
    /**
     * 设置进度及错误监听
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mMediaSnapshot.setMediaListener(listener);
    }

    /**
     * 截图图片文件路径链表回调监听接口
     * @param listListener
     */
    public void setPictureListListener(IMediaSnapshotPictureListener listListener) {
        mMediaSnapshot.setPictureListListener(listListener);
    }

    /**
     * 设置截图的分辨率
     * @param width
     * @param height
     */
    public void setSnapshotImageSize(int width, int height) {
        mMediaSnapshot.setSnapshotImageSize(width,height);
    }

    /**
     * 获取视频截图
     *
     * @param snapshotTime 截图时间（单位s）
     */
    public void captureSnapshot(double snapshotTime) {
        mMediaSnapshot.setSnapshotTime(snapshotTime);
        mMediaSnapshot.snapshot();
    }

    /**
     * 从视频中某一段时间（单位：秒）内获取视频截图
     *
     * @param startTime 截图起始时间（单位s）
     * @param duration 持续时间长度， 截取范围为startTime ~ startTime + duration
     */
    public void captureSnapshotEx(int startTime, int duration) {
        mMediaSnapshot.snapshotEx(startTime, duration);
    }

    /**
     * 批量获取视频截图
     * @param startTime 截图开始时间时间（单位s）
     * @param duration  截图持续时间段
     * @param fileType  图片类型："bmp", "jpg", "jpeg", "png"
     * @param frameRate 截图频率（平均1s内截图数量）
     * @param filePrefix 批量生成截图文件名的前缀
     */
    public void captureMultipleSnapshot(double startTime, double duration, String fileType, double frameRate,String filePrefix) {
        mMediaSnapshot.multipleSnapshot(mSourcePath, mOutputPath, fileType, startTime, frameRate, duration,filePrefix);
    }

    /**
     * 释放
     */
    public void release() {
        mMediaSnapshot.release();
    }

    /**
     * 增加取消接口
     */
    public void cancel(){
        mMediaSnapshot.cancel();
    }

}
