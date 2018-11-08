package com.ycloud.mediaprocess;

import android.util.Log;

import com.ycloud.api.process.MediaInfo;
import com.ycloud.common.Constant;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;

public class MediaSnapshot extends MediaBase implements IMediaSnapshot{
    public static  final String TAG = MediaSnapshot.class.getSimpleName();
    private int mImageWidth = -1;
    private int mImageHeight = -1;
    private double mSnapshotTime = 0;
    private String mFileType = "jpg";

    private String mInputPath;
    private String mOutputPath;

    private double mInputVideoDuration = 0.0f;
    private float mInputVideoFps = 0;

    public MediaSnapshot() {
        super();
        setExcuteCmdId(MediaNative.libffmpeg_cmd_snapshot_multiple);
    }

    public void setSnapshotImageSize(int width, int height) {
        mImageWidth = width;
        mImageHeight = height;
    }

    public void setSnapshotTime(double snapshotTime) {
        mSnapshotTime = snapshotTime;
        /*if (snapshotTime > mInputVideoDuration - 1.5/mInputVideoFps){
            mSnapshotTime = mInputVideoDuration - 1.5/mInputVideoFps;
        }else {
            mSnapshotTime = snapshotTime;
        }
        YYLog.info(this, Constant.MEDIACODE_SNAPSHOT+"snapshotTime:"+mSnapshotTime+" duration:"+mInputVideoDuration+" fps:"+mInputVideoFps+" require snapshot time:"+snapshotTime+ " last frame time:"+(mInputVideoDuration-1.5/mInputVideoFps));*/
    }

    protected boolean execute() {
        FileUtils.createFile(mOutputPath);
        if (!FileUtils.checkPath(mInputPath) || !FileUtils.checkFile(mOutputPath)) {
            return false;
        }
        FileUtils.deleteFileSafely(new File(mOutputPath));
        return _captureSnapshot();
    }

    private synchronized boolean _captureSnapshot() {
        String cmd ;
        if (mImageWidth > 0 && mImageHeight > 0)
            cmd = "ffmpeg -y -ss " + mSnapshotTime + " -i \"" + mInputPath + "\" -f image2 -vframes 1 -qscale 1 -s " + mImageWidth + "x" + mImageHeight + "\"" + mOutputPath + "\"";
        else
            cmd = "ffmpeg -y -ss " + mSnapshotTime + " -i \"" + mInputPath + "\" -f image2 -vframes 1 -qscale 1 \"" + mOutputPath + "\"";

        return executeCmd(cmd);
    }

    public boolean captureMultipleSnapshot(String videoPath, String outputPath, String fileType, double startTime, double frameRate, double totalTime, String filePrefix) {
        String f = "image2";
        mOutputPath = outputPath;

        if (!FileUtils.checkPath(videoPath)) {
            return false;
        }

        if (!FileUtils.isSnapshotSupport(fileType))
            fileType = "jpg";
        mFileType = fileType;

        MediaInfo info = MediaUtils.getMediaInfo(videoPath);
        if(info == null) {
            YYLog.error(TAG, "captureMultipleSnapshot videoPath info is null, return!");
            return false;
        }

        setTotalFrame(info.total_frame);
        double duration = info.video_duration;
        if (frameRate <= 0 || startTime < 0 || startTime > duration || duration == 0) {
            Log.e(TAG, "startTime: " + startTime + " duration: " + duration);
            return false;
        }

        if (totalTime < (duration - startTime)) {
            duration = totalTime;
        } else {
            duration = duration - startTime;
        }
        YYLog.info(this, Constant.MEDIACODE_SNAPSHOT+"captureMultipleSnapshot duration:" + duration + " totalTime:" + totalTime + " startTime:" + startTime + " frameRate:" + frameRate + " info.video_duration:" + info.video_duration);
        if (!outputPath.endsWith("/"))
            outputPath = outputPath + "/" + filePrefix;
        else {
            outputPath = outputPath + filePrefix;
        }
        String cmd = "";
        if (mImageWidth > 0 && mImageHeight > 0)
            cmd = String.format("ffmpeg -y -ss %f -i \"%s\" -f %s -vf fps=%f -t %f -b:v 10000k -s %dx%d \"%s\"", startTime, videoPath, f, frameRate, duration, mImageWidth,
                    mImageHeight, outputPath + "%3d." + fileType);
        else
            cmd = String.format("ffmpeg -y -ss %f -i \"%s\" -f %s -vf fps=%f -t %f -b:v 10000k \"%s\"", startTime, videoPath, f, frameRate, duration, outputPath + "%3d." + fileType);
        return executeCmd(cmd);
    }

    public void snapshot(){
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                execute();
            }
        });
    }

    public void multipleSnapshot(final String videoPath, final String outputPath, final String fileType, final double startTime, final double frameRate,
                                              final double totalTime, final String filePrefix) {
        ExecutorUtils.getBackgroundExecutor("MultipleSnapshotAsyn").execute(new Runnable() {
            @Override
            public void run() {
                captureMultipleSnapshot(videoPath, outputPath, fileType, startTime, frameRate, totalTime,filePrefix);
            }
        });
    }

    public void setPath(String sourcePath, String outputPath) {
        mInputPath =sourcePath;
        mOutputPath =outputPath;

       /*MediaInfo mediaInfo = MediaUtils.getMediaInfo(sourcePath);
        if (mediaInfo != null) {
            setTotalFrame(mediaInfo.total_frame);
            mInputVideoDuration = mediaInfo.video_duration;
            mInputVideoFps = mediaInfo.frame_rate;
        }*/
    }

    @Override
    public void setSnapShotCnt(int snapShotCnt)
    {
        // do nothing
    }

    @Override
    public void setPicturePrefix(String prefix)
    {
        // do nothing
    }

    @Override
    public void setPictureQuality(int quality) {

    }

    @Override
    public void snapshotEx(int startTime, int duration) {

    }

    @Override
    public void setPictureListListener(IMediaSnapshotPictureListener listListener) {

    }
}
