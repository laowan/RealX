package com.ycloud.mediacodec.engine;

import com.ycloud.api.process.IMediaListener;

/**
 * Created by DZHJ on 2017/3/14.
 */

public interface IMediaTranscoder {

    /**
     * 设置文件路径
     *
     * @param sourcePath 源文件
     * @param outputPath 输出文件
     */
    void setPath(String sourcePath, String outputPath) ;

    /**
     * 设置转码时的视频旋转角度
     *
     * @param angle 视频旋转角度， 顺时针方向
     */
    void setForceRotateAngle(float angle);

    /**
     * 设置输出视频宽高
     *
     * @param width 宽（单位px）
     * @param height 高（单位px）
     */
    void setVideoSize(int width, int height);

    /**
     * 设置裁剪时间段
     *
     * @param startTime 起始时间（单位s）
     * @param totalTime 总时间段（单位s）
     */
    void setMediaTime(float startTime, float totalTime);

    /**
     * 设置帧率
     *
     * @param frameRate
     */
    void setFrameRate(int frameRate);

    /**
     * 设置码率
     *
     * @param bitrate
     */
    void setBitrate(int bitrate);

    /**
     * 设置码率范围
     *
     * @param minRate 最小码率
     * @param maxtRate 最大码率
     * @param bufsize 缓冲区大小
     */
    void setBitrateRange(int minRate, int maxtRate, int bufsize);

    /**
     * 设置gop值
     *
     * @param gop
     */
    void setGop(int gop);

    /**
     * 设置crf值
     *
     * @param crf 建议范围18~23
     */
    void setCrf(int crf);

    /**
     * 执行转码
     */
    void transcode();

    /**
     * 释放
     */
    void release();

    /**
     * 设置进度及错误监听
     *
     * @param listener
     */
     void setMediaListener(IMediaListener listener);

    /**
     * 取消转码
     */
    void cancel();

    /**
     * 设置裁剪区域
     */
    void setCropField(int width, int height, int offsetX, int offsetY);

    /**
     * 设置转码过程中的截图路径
     * 仅仅对边转码边截图的场景使用
     */
    void setSnapshotPath(String snapshotPath);

    /**
     * 设置转码过程中的截图频率
     * 仅仅对边转码边截图对场景使用
     */
    void setSnapshotFrequency(float frequency);

    /**
     * 设置截图保存的图片格式
     * @param fileType
     */
    void setSnapshotFileType(String fileType);

    /**
     * 设置截图保存图片文件名的前缀
     * @param prefix
     */
    void setSnapshotPrefix(String prefix);
}
