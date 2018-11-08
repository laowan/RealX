package com.ycloud.mediaprocess;

import com.ycloud.api.process.IMediaListener;

/**
 * Created by Administrator on 2018/1/26.
 */

public interface IMediaSnapshot {
    public void setPath(String sourcePath, String outputPath);
    public void setPicturePrefix(String prefix);
    public void setPictureQuality(int quality);
    public void setMediaListener(IMediaListener listener);
    public void setSnapshotImageSize(int width, int height);
    public void setSnapshotTime(double snapshotTime);
    public void snapshot();
    public void snapshotEx(int startTime, int duration);
    public boolean captureMultipleSnapshot(String videoPath, String outputPath, String fileType, double startTime, double frameRate, double totalTime, String filePrefix);
    public void release();
    public void cancel();
    public void multipleSnapshot(final String videoPath, final String outputPath, final String fileType, final double startTime, final double frameRate,
                                 final double totalTime, final String filePrefix);
    public void setSnapShotCnt(int snapShotCnt);
    public void setPictureListListener(IMediaSnapshotPictureListener listListener);
}
