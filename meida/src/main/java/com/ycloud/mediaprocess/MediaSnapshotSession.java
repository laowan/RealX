package com.ycloud.mediaprocess;

import android.media.MediaFormat;
import android.os.Environment;
import com.ycloud.api.common.SampleType;
import com.ycloud.api.process.IMediaListener;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.mediafilters.FFmpegDemuxDecodeFilter;
import com.ycloud.mediafilters.IMediaSession;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediafilters.YUVClipFilter;
import com.ycloud.mediafilters.YYMediaFilterListener;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Administrator on 2018/1/26.
 * 优化从MP4文件中截图速度，替代用ffmpeg截图。
 */
public class MediaSnapshotSession implements YYMediaFilterListener, IMediaSession, IMediaSnapshot {
    private static final String TAG = "MediaSnapshotSession";
    private FFmpegDemuxDecodeFilter mVideoDecoderFilter = null;
    private YUVClipFilter mYUVClipFilter = null;
    private MediaFilterContext mVideoFilterContext = null;
    private int mFilterErrorCnt = 0;
    private int mSnapshotFrameCnt = 0;
    private AtomicBoolean mRelease = new AtomicBoolean(false);
    private Object mCancelLock = new Object();
    private String mMediaPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Movies/1.mp4";
    private String mPicturePath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/YYImage";
    private String mPictureNamePrefix = "videoSnapshot";
    private int mPictureEncodeQuality = 50;
    private int mOutputWidth = 128;
    private int mOutputHeight = 160;
    private int mSnapshotCount = 13;
    private int mStartTime = 0;
    private int mDuration = 0;
    protected AtomicReference<IMediaListener> mMediaListener = new AtomicReference<>(null);

    public MediaSnapshotSession() {
        mVideoFilterContext = new MediaFilterContext(null);
        mVideoFilterContext.getMediaStats().setBeginTimeStamp(System.currentTimeMillis());
        mVideoDecoderFilter = new FFmpegDemuxDecodeFilter();
        mYUVClipFilter = new YUVClipFilter(mVideoFilterContext);
        mVideoFilterContext.getGLManager().registerFilter(mYUVClipFilter);
        mVideoFilterContext.getGLManager().setMediaSession(this);
        mVideoDecoderFilter.addDownStream(mYUVClipFilter);
        mYUVClipFilter.setFilterListener(this);
        YYLog.info(TAG, "[tracer] MediaSnapshotSession, phone model:"+ DeviceUtil.getPhoneModel());
    }

    @Override
    public void setSnapShotCnt(int snapShotCnt)
    {
        mSnapshotCount = snapShotCnt;
    }

    private void startSnapshot() {
        if(mRelease.get()) {
            YYLog.info(TAG, "MediaSnapshotSession is released");
            return;
        }
        YYLog.info(TAG, "MediaSnapshotSession startSnapshot .");
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                OpenGlUtils.checkGlError("MediaSnapshotSession.start. ");
                mYUVClipFilter.init(mOutputWidth, mOutputHeight, mPicturePath,mPictureNamePrefix,mPictureEncodeQuality);
                OpenGlUtils.checkGlError("MediaSnapshotSession.start end");

                if (mVideoDecoderFilter != null) {
                    mVideoDecoderFilter.init(mMediaPath, mOutputWidth, mOutputHeight, mSnapshotCount);
                    mVideoDecoderFilter.setSnapshotRange(mStartTime, mDuration);
                    mVideoDecoderFilter.start();
                }
            }
        });


    }

    private void stopSnapshot() {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                OpenGlUtils.checkGlError("MediaSnapshotSession.stop begin");
                mVideoDecoderFilter.deInit();
                OpenGlUtils.checkGlError("MediaSnapshotSession.stop end");
            }
        });
    }

    @Override
    public void setPath(String sourcePath, String outputPath)
    {
        mMediaPath = sourcePath;
        mPicturePath = outputPath;
    }

    @Override
    public void setPicturePrefix(String prefix)
    {
        mPictureNamePrefix = prefix;
    }

    /**
     * 截图图片文件路径链表回调监听接口
     * @param listListener
     */
    public void setPictureListListener(IMediaSnapshotPictureListener listListener) {
        mYUVClipFilter.setPictureListListener(listListener);
    }

    @Override
    public void setMediaListener(IMediaListener listener) {
        mMediaListener = new AtomicReference<>(listener);
    }

    @Override
    public void setSnapshotImageSize(int width, int height) {
        if (width > 0 && height > 0) {
            mOutputWidth = width;
            mOutputHeight = height;
        }
    }

    @Override
    public void setPictureQuality(int quality) {
        mPictureEncodeQuality = quality;
    }

    @Override
    public void setSnapshotTime(double snapshotTime) {

    }

    @Override
    public void snapshot() {
        startSnapshot();
    }

    @Override
    public void snapshotEx(int startTime, int duration) {
        YYLog.info(TAG, "snapshotEx startTime " +startTime + " duration "+ duration);
        mStartTime = startTime;
        mDuration = duration;
        startSnapshot();
    }

    @Override
    public boolean captureMultipleSnapshot(String videoPath, String outputPath, String fileType, double startTime, double frameRate, double totalTime, String filePrefix) {
        return false;
    }

    @Override
    public void multipleSnapshot(String videoPath, String outputPath, String fileType, double startTime, double frameRate, double totalTime, String filePrefix) {

    }

    public void cancel() {
        YYLog.info(TAG, "Cancel start.");
        release();
    }


    @Override
    public void glMgrCleanup() {
        synchronized (mCancelLock) {
            if (mRelease.get()) {
                mVideoFilterContext = null;
                mVideoDecoderFilter = null;
                mCancelLock.notify();
                YYLog.info(TAG, "MediaSnapshotSession glMgrCleanup");
            }
        }
    }

    @Override
    public void audioMgrCleanup() {
        if(mRelease.get()) {
            YYLog.info(TAG, "MediaSnapshotSession audioMgrCleanup");
        }
    }

    @Override
    public void setInputVideoFormat(final MediaFormat mediaFromat) {

    }

    @Override
    public void setInputAudioFormat(MediaFormat mediaFormat) {

    }

    public void release() {
        synchronized (mCancelLock) {
            if (mRelease.getAndSet(true)) {
                YYLog.info(TAG, "[tracer] release already!!");
                return;
            }
            YYLog.info(TAG, "[tracer] MediaSnapshotSession release begin");

            stopSnapshot();
            if (mVideoFilterContext != null) {
                mVideoFilterContext.getGLManager().quit();
                mVideoFilterContext = null;
            }

            try {
                YYLog.info(TAG, "mCancelLock.wait()");
                mCancelLock.wait();
            } catch (Exception e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
            }
        }

        mMediaListener = null;

        YYLog.info(TAG, "[tracer] MediaSnapshotSession release end !!");
    }

    @Override
    public void onFilterInit(AbstractYYMediaFilter filter) {

    }

    @Override
    public void onFilterDeInit(AbstractYYMediaFilter filter) {

    }

    @Override
    public void onFilterEndOfStream(AbstractYYMediaFilter filter) {
        //if(filter instanceof YUVClipFilter) {
            if (mVideoFilterContext != null) {
                mVideoFilterContext.getMediaStats().setmEndTimeStamp(System.currentTimeMillis());
                mVideoFilterContext.getMediaStats().dump();
            }
            YYLog.info(TAG, "MediaSnapshotSession finished!!!");
            if (mMediaListener != null) {
                IMediaListener listener = mMediaListener.get();
                if (listener != null) {
                    listener.onEnd();
                }
            }
        //}
    }

    @Override
    public void onFilterProcessMediaSample(AbstractYYMediaFilter filter, SampleType sampleType, long ptsMs) {
        if( sampleType != SampleType.VIDEO)
            return;

        if(filter instanceof YUVClipFilter) {
            //notify every 1 video frame snapshot..
            mSnapshotFrameCnt++;
            float percent = (float)mSnapshotFrameCnt/mSnapshotCount;
            percent = (percent>=1.0)? 1.0f : percent;
            YYLog.info(TAG, "========================percent:"+percent);
            if(mMediaListener != null) {
                IMediaListener listener = mMediaListener.get();
                if (listener != null) {
                    listener.onProgress(percent);
                }
            }
        }
    }

    @Override
    public void onFilterError(AbstractYYMediaFilter filter, String errMsg) {
        if(mFilterErrorCnt != 0) {
            mFilterErrorCnt = 0;
            if (mMediaListener != null) {
                IMediaListener listener = mMediaListener.get();
                if (listener != null) {
                    //TODO. error Type
                    listener.onError(-1, errMsg);
                }
            }
        }
    }

}
