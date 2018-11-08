package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.ycloud.api.common.SampleType;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2018/8/22.
 * 用于超短视频(<3s)而且I帧间隔很大的视频的解码和精确截图。
 * 方案一：从头到尾顺序解码所有视频帧，然后从解码出来的视频帧中按间隔抽取需要用于截图的帧送给图片编码线程。
 * 方案二：每次Seek到目标PTS前的关键帧，然后从该关键帧开始解码直到解码到目标PTS对应的视频帧。这个看似好像会省掉
 * 不少解码时间，如果一个视频4秒，只有开始第一个是IDR帧， 那么每截取一张图片，都要从头解码所有帧，消耗
 * 的时间比第一种方案多得多！
 * 方案三：预先遍历整个视频，通过判断关键帧数量来决定采用方案一还是方案二。
 * 由于主要用于超短视频精确截图，默认使用方案一。
 */

public class AndroidDemuxDecodeFilter extends AbstractYYMediaFilter {
    private static final String TAG = "AndroidDemuxDecodeFilter";
    private int mWidth = 0;
    private int mHeight = 0;
    private int mOutputWidth = 0;
    private int mOutputHeight = 0;
    private int mSnapShotCnt = 0;
    private int mRotate = 0;
    private ByteBuffer mFrameBuffer = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private final Object mLock = new Object();
    private boolean mEndOfInputStream = false;
    private boolean mEndOfOutputStream = false;
    private final boolean mUseSeekMode = false;
    private String mCodecMIME;
    private double mVideoDuration;
    private double mSnapshotOffsetTime;
    private MediaExtractor mediaExtractor;
    private MediaFormat mVideoFormat;
    private MediaCodec mediaCodec;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;
    private MediaCodec.BufferInfo mBufferInfo;
    private Handler mDecodeHandler;
    private HandlerThread mDecodeThread;
    private int mInputFrameCount;
    private int mOutputFrameCount;
    private EventCallBack mEventCallback;
    private ExecutorService mSingleThreadExecutor;
    private long startTime;
    private static final long timeout = 5 * 1000;  // 10 ms
    private long mTargetPTS = 0;  // ms

    private static final int MSG_INIT = 0x100;
    private static final int MSG_DEINIT = 0x101;
    private static final int MSG_PROCESS = 0x102;
    private static final int MSG_SEEK = 0x103;
    private static final int EVENT_FINISH = 0x200;
    private static final int EVENT_ERROR = 0x201;

    public AndroidDemuxDecodeFilter() {
        mDecodeThread = new HandlerThread("AndroidDemuxDecode");
        mDecodeThread.start();
        mDecodeHandler = new Handler(mDecodeThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INIT:
                        InitParam param = (InitParam) msg.obj;
                        initInternal(param.path, param.outputWidth, param.outputHeight, param.snapshotCount);
                        break;
                    case MSG_DEINIT:
                        deinitInternal();
                        break;
                    case MSG_PROCESS:
                        DecodeVideoFrame();
                        break;
                    case MSG_SEEK:
                        doSeekToTargetPTS((long) msg.obj);
                        break;
                    default:
                        break;
                }
            }
        };
        mSingleThreadExecutor = Executors.newSingleThreadExecutor();
        YYLog.info(TAG, "mDecodeThread " + mDecodeThread.getId() + " start success.");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean mediaExtractorInit(String path) {
        if (mediaExtractor == null) {
            mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(path);
            } catch (IOException e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
                return false;
            }
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video")) {
                    mediaExtractor.selectTrack(i);
                    mVideoFormat = format;
                    mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                    mCodecMIME = mime;
                    MediaInfo info = MediaProbe.getMediaInfo(path, true);
                    float mFrameRate = 0;
                    if (info != null) {
                        mRotate = (int) info.v_rotate;
                        mFrameRate = info.frame_rate;
                        mVideoDuration = info.duration;
                    }
                    YYLog.info(TAG, "Extractor path :" + path + " width " + mWidth + " height "
                            + mHeight + " MIME " + mime + " rotate " + mRotate + " mFrameRate " + mFrameRate
                            + " duration " + mVideoDuration);
                    return true;
                }
            }
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean MediaCodecInit() {
        if (mediaCodec == null) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    mediaCodec = MediaCodec.createDecoderByType(mCodecMIME);
                } else {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                        mVideoFormat.setString(MediaFormat.KEY_FRAME_RATE, null);
                    }
                    MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                    String mCodecName = codecList.findDecoderForFormat(mVideoFormat);
                    mediaCodec = MediaCodec.createByCodecName(mCodecName);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    YYLog.info(TAG, "Create MIME " + mCodecMIME + ", Decoder : " + mediaCodec.getName());
                } else {
                    YYLog.info(TAG, "Create MIME " + mCodecMIME + ", Decoder : " + mediaCodec.toString());
                }

                // codec specific data such as sps pps vps,  if you configure the codec with a
                // MediaFormat containing these keys, they will be automatically submitted by
                // MediaCodec directly after start.Therefore, the use of BUFFER_FLAG_CODEC_CONFIG
                // flag is discouraged and is recommended only for advanced users.
                mediaCodec.configure(mVideoFormat, null, null, 0);
                mediaCodec.start();
                mInputBuffers = mediaCodec.getInputBuffers();
                mOutputBuffers = mediaCodec.getOutputBuffers();
                mBufferInfo = new MediaCodec.BufferInfo();
                return true;
            } catch (Exception e) {
                YYLog.error(TAG, "Exception :" + e.getMessage());
            }
        }
        return false;
    }


    private void continueProcess() {
        if (mDecodeHandler == null) {
            YYLog.error(TAG, "continueProcess mDecodeHandler == null. ");
            return;
        }
        Message msg = mDecodeHandler.obtainMessage();
        msg.what = MSG_PROCESS;
        mDecodeHandler.sendMessage(msg);
    }

    private void seekToNextTargetPTS(long pts) {
        if (mDecodeHandler == null) {
            YYLog.error(TAG, "continueProcess mDecodeHandler == null. ");
            return;
        }
        Message msg = mDecodeHandler.obtainMessage();
        msg.what = MSG_SEEK;
        msg.obj = pts;
        mDecodeHandler.sendMessage(msg);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void doSeekToTargetPTS(long pts) {
        if (mediaExtractor != null) {
            mediaExtractor.seekTo(pts * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
    }

    public boolean init(String path, int width, int height, int SnapShotCnt) {
        if (mDecodeHandler == null) {
            YYLog.error(TAG, "init mDecodeHandler == null. ");
            return false;
        }
        if (mInited.get()) {
            YYLog.warn(TAG, "already inited yet.");
            return true;
        }
        if (!FileUtils.checkPath(path) || width <= 0 || height <= 0 || SnapShotCnt <= 0) {
            YYLog.error(TAG, "Parameter Invalid !");
            return false;
        }

        InitParam param = new InitParam(path, width, height, SnapShotCnt);
        Message msg = mDecodeHandler.obtainMessage();
        msg.what = MSG_INIT;
        msg.obj = param;
        mDecodeHandler.sendMessage(msg);
        YYLog.info(TAG, "init start.");
        synchronized (mLock) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                YYLog.error(TAG, "Exception:" + e.getMessage());
            }
        }
        YYLog.info(TAG, "init end, " + mInited.get());
        return mInited.get();
    }

    private void initInternal(String path, int width, int height, int snapShotCnt) {
        YYLog.info(TAG, "initInternal start.");
        if (!mediaExtractorInit(path)) {
            YYLog.error(TAG, "mediaExtractorInit failed.");
            synchronized (mLock) {
                mLock.notifyAll();
            }
            return;
        }

        if (!MediaCodecInit()) {
            YYLog.error(TAG, "MediaCodecInit failed.");
            synchronized (mLock) {
                mLock.notifyAll();
            }
            return;
        }

        mOutputWidth = width;
        mOutputHeight = height;
        mSnapShotCnt = snapShotCnt;
        mFrameBuffer = ByteBuffer.allocate(mWidth * mHeight * 4);
        mInited.set(true);
        synchronized (mLock) {
            mLock.notifyAll();
        }
        YYLog.info(TAG, "initInternal end.");
    }

    // unit second
    public void setSnapshotRange(int startTime, int duration) {
        long mStartTime = startTime;
        long mSnapshotDuration = duration;
        // snapshot picture in a time range, not the whole video file.
        if (mStartTime >= 0 && mSnapshotDuration > 0 && mStartTime < mVideoDuration) {  // unit: seconds

            mStartTime = mStartTime * 1000;
            mSnapshotDuration = mSnapshotDuration * 1000;
            if (mStartTime + mSnapshotDuration > mVideoDuration * 1000) {
                mSnapshotDuration = (long) mVideoDuration * 1000 - mStartTime;
            }
            mSnapshotOffsetTime = mSnapshotDuration / (mSnapShotCnt);
        }
        mTargetPTS = mStartTime;  // unit millisecond
        YYLog.info(TAG, "setSnapshotRange [" + startTime + "," + (startTime + duration) + "]" +
                " timeOffset " + mSnapshotOffsetTime);
    }

    public void start() {
        if (mDecodeHandler == null) {
            YYLog.error(TAG, "start mDecodeHandler == null || mInited false. ");
            return;
        }
        Message msg = mDecodeHandler.obtainMessage();
        msg.what = MSG_PROCESS;
        mDecodeHandler.sendMessage(msg);
        YYLog.info(TAG, " start.");
        startTime = System.currentTimeMillis();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void processVideoFrame(ByteBuffer buffer, int size, long pts, int flags) {
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
        if (size > 0 && ((flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0)) {
            sample.mSampleType = SampleType.VIDEO;
            sample.mYYPtsMillions = pts;
            sample.mAndoridPtsNanos = pts * 1000000;
            sample.mWidth = mWidth;
            sample.mHeight = mHeight;
            sample.mDisplayRotation = mRotate;
            sample.mEncodeWidth = mOutputWidth;
            sample.mEncodeHeight = mOutputHeight;
            sample.mDeliverToEncoder = false;
            sample.mDataByteBuffer = buffer;
            sample.mBufferSize = size;
            sample.mBufferFlag = flags;
        } else {
            YYLog.info(TAG, " video end of stream");
            sample.mEndOfStream = true;
            sample.mDeliverToEncoder = false;
            sample.mDataByteBuffer = null;
            sample.mBufferOffset = 0;
            sample.mBufferSize = 0;
            sample.mWidth = mWidth;
            sample.mHeight = mHeight;
            sample.mEncodeWidth = mOutputWidth;
            sample.mEncodeHeight = mOutputHeight;
            sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }

        deliverToDownStream(sample);
        sample.decRef();
        YYLog.info(TAG, "processVideoFrame pts :" + pts);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void DecodeVideoFrame() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Not inited yet.");
            return;
        }

        boolean mGetTargetFrame = false;
        boolean mDecoderError = false;
        try {
            /* 1. input encoded video frame */
            if (!mEndOfInputStream) {
                int inIndex = mediaCodec.dequeueInputBuffer(timeout);
                if (inIndex >= 0) {         // input buffer available
                    int sampleSize = mediaExtractor.readSampleData(mFrameBuffer, 0);
                    long presentationTimeUs = mediaExtractor.getSampleTime();
                    int flags = 0;
                    if (sampleSize < 0) {   // end of input stream
                        flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        sampleSize = 0;
                        mEndOfInputStream = true;
                        YYLog.info(TAG, " mEndOfInputStream true.");
                    } else {
                        mInputBuffers[inIndex].clear();
                        mInputBuffers[inIndex].put(mFrameBuffer.array(), 0, sampleSize);
                        mInputFrameCount++;
                    }
                    mediaCodec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, flags);
                    mediaExtractor.advance();
                }
            }

            /* 2. output decoded video frame */
            int outputIndex = mediaCodec.dequeueOutputBuffer(mBufferInfo, timeout);
            if (outputIndex >= 0) {
                long pts = mBufferInfo.presentationTimeUs;
                int size = mBufferInfo.size;
                int flags = mBufferInfo.flags;
                int offset = mBufferInfo.offset;
                if ((flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mEndOfOutputStream = true;
                    YYLog.info(TAG, " mEndOfOutputStream true. ");
                }
                if (pts >= mTargetPTS * 1000 && !mEndOfOutputStream) {  // 目标PTS之后的第一帧，作为截图帧
                    ByteBuffer outBuffer = mOutputBuffers[outputIndex];
                    outBuffer.position(offset);
                    outBuffer.limit(offset + size);

                    ByteBuffer buf = ByteBuffer.allocate(size);
                    buf.clear();
                    buf.put(outBuffer);
                    buf.rewind();
                    processVideoFrame(buf, size, pts, flags);
                    mGetTargetFrame = true;
                    mSnapShotCnt--;
                }

                if (!mEndOfOutputStream) {
                    mOutputFrameCount++;
                }
                mediaCodec.releaseOutputBuffer(outputIndex, false);

            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mediaCodec.getOutputFormat();
                mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
                    mWidth = format.getInteger("crop-right") + 1 - format.getInteger("crop-left");
                }
                mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
                    mHeight = format.getInteger("crop-bottom") + 1 - format.getInteger("crop-top");
                }
                YYLog.info(TAG, "INFO_OUTPUT_FORMAT_CHANGED . width " + mWidth + " height " + mHeight);
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                YYLog.info(TAG, "INFO_OUTPUT_BUFFERS_CHANGED .");
                mOutputBuffers = mediaCodec.getOutputBuffers();
            }
        } catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e + ", Message " + e.getMessage());
            mDecoderError = true;
            HandleCallback(EVENT_ERROR, "Exception:" + e + "," + e.getMessage());
        }

        if (!mEndOfOutputStream && !mDecoderError && mSnapShotCnt > 0) {
            if (mGetTargetFrame) {
                mTargetPTS += (long) mSnapshotOffsetTime;
                YYLog.info(TAG, "Get the target frame, next Target is " + mTargetPTS + " mSnapshotOffsetTime" +
                        mSnapshotOffsetTime + " mSnapShotCnt " + mSnapShotCnt);
                if (mUseSeekMode) { // seek to a sync sample at or before the specified time
                    seekToNextTargetPTS(mTargetPTS);
                }
            }
            continueProcess();          // continue to decode
        } else {
            long endTime = System.currentTimeMillis();
            YYLog.info(TAG, "DecodeVideoFrame Finish, input " + mInputFrameCount +
                    " output " + mOutputFrameCount + " Cost " + (endTime - startTime));
            HandleCallback(EVENT_FINISH, null);
        }

    }

    public void deInit() {
        if (!mInited.get()) {
            YYLog.warn(TAG, "Not inited yet!");
            return;
        }

        if (mDecodeHandler != null) {
            Message msg = mDecodeHandler.obtainMessage();
            msg.what = MSG_DEINIT;
            mDecodeHandler.sendMessage(msg);
        }
        YYLog.info(TAG, " deInit start .");

        synchronized (mLock) {
            try {
                mLock.wait();
                YYLog.info(TAG, " wait deinitInternal OK.");
            } catch (InterruptedException e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
            }
        }

        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
            mDecodeHandler = null;
        }

        if (mDecodeThread != null) {
            long threadID = mDecodeThread.getId();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mDecodeThread.quit();
            } else {
                mDecodeThread.quitSafely();
            }

            try {
                mDecodeThread.join();
                YYLog.info(TAG, "Decode Thread " + threadID + " Exit .");
                mDecodeThread = null;
            } catch (InterruptedException e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
            }
        }

        if (mSingleThreadExecutor != null) {
            mSingleThreadExecutor.shutdown();
            mSingleThreadExecutor = null;
        }

        YYLog.info(TAG, " deInit end .");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void deinitInternal() {
        YYLog.info(TAG, "deinitInternal start .");
        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
            } catch (IllegalStateException e) {
                YYLog.error(TAG, "Exception :" + e.getMessage());
            } finally {
                mediaCodec.release();
                mediaCodec = null;
            }
        }
        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }
        mDecodeHandler.removeMessages(MSG_PROCESS);
        mBufferInfo = null;
        mInputBuffers = null;
        mOutputBuffers = null;
        mFrameBuffer = null;
        mInited.set(false);
        synchronized (mLock) {
            mLock.notifyAll();
        }
        YYLog.info(TAG, " deinitInternal end. ");
    }

    public void setEventCallback(EventCallBack callback) {
        mEventCallback = callback;
    }

    private void HandleCallback(final int event, final String param) {
        if (mEventCallback != null && mSingleThreadExecutor != null) {
            mSingleThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    switch (event) {
                        case EVENT_FINISH:
                            mEventCallback.onFinished();
                            break;
                        case EVENT_ERROR:
                            mEventCallback.onError(param);
                            break;
                        default:
                            break;
                    }
                }
            });
        }
    }

    private class InitParam {
        private InitParam(String path, int width, int height, int snapshotCount) {
            this.path = path;
            this.outputWidth = width;
            this.outputHeight = height;
            this.snapshotCount = snapshotCount;
        }

        private String path;
        private int outputWidth;
        private int outputHeight;
        private int snapshotCount;
    }

    public interface EventCallBack {
        void onFinished();

        void onError(String ErrMsg);
    }
}
