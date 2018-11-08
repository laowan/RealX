package com.ycloud.api.process;

public class FramesExtractor {

    com.ycloud.mediacodec.VideoSnapshot mVideoSnapshot;
    public static final String TAG = FramesExtractor.class.getSimpleName();
    private String mSnapshotPath = "";
    private float mSnapshotFrequency = 0;

    public FramesExtractor() {
        mVideoSnapshot = new com.ycloud.mediacodec.VideoSnapshot(mSnapshotPath, mSnapshotFrequency);
    }

    public void execute() {
        mVideoSnapshot.transcode();
    }

    /**
     * 设置文件路径
     *
     * @param sourcePath 源文件
     * @param outputPath 输出文件
     */
    public void setPath(String sourcePath, String outputPath) {
        mVideoSnapshot.setPath(sourcePath, outputPath);
    }

    public void cancel() {
        mVideoSnapshot.cancel();
    }

    public void setRecordSnapShot(String snapShotPath) {
        this.mSnapshotPath = snapShotPath;
    }

    public void setSnapFrequency(float snapFrequency) {
        mSnapshotFrequency = snapFrequency;
    }

    /**
     * 设置进度及错误监听
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mVideoSnapshot.setMediaListener(listener);
    }

}
