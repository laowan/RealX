package com.ycloud.mediacodec.videocodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.ycloud.common.Constant;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Hui on 2015/11/13.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public abstract class HardDecRender {
    private static final String TAG = "HardDecRender";
    protected MediaCodec mDecoder;
    protected Surface mSurface;
    protected MediaFormat mFormat;
    protected boolean mNeedConfig = true;
    protected int mWidth = 720;
    protected int mHeight = 1280;
    protected boolean mInitialized = false;
    protected ByteBuffer[] mInputBuffers;
    protected MediaCodec.BufferInfo mInfo;
    protected AtomicBoolean mSecondTsWriten;
    protected String mCrashTsFirst;
    protected String mCrashTsSecond;
    protected int mNoFrameCnt = 0;
    protected boolean mIsExceptionOccured = false;
    protected long mStreamId = 0;

    protected HardDecRender() {
    }

    public boolean IsNeedConfig() {
        return mNeedConfig;
    }

    public void ConfigDone() {
        synchronized (this) {
            mNeedConfig = false;
        }
    }

    public void EndofStream() {
        mNoFrameCnt = 0;
        try {
//            int inIndex = mDecoder.dequeueInputBuffer(200000);
//            mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//            mDecoder.flush();
//            UnBlockingForceFlush();
//            mInputBuffers = mDecoder.getInputBuffers();
        }
        catch (Throwable t) {
            YYLog.error(this, Constant.MEDIACODE_DECODER +"HardDecRender EndofStream throwable "+ t.getMessage());
        }
    }

    public boolean GetAndClearExceptionFlag() {
        boolean occured = mIsExceptionOccured;
        mIsExceptionOccured = false;
        return occured;
    }

    protected static boolean IsAvailable(String codecName) {
        if (Build.VERSION.SDK_INT < 17) {
            return false;
        }
        return null != codecName;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected long PushFrame(Surface surface, String codecName, String codecType, byte[] bf, long pts, boolean isHeader) {
        synchronized (this) {
            if (mNeedConfig && !isHeader) {
                mIsExceptionOccured = true;
                return 0;
            }
            mSurface = surface;
            if (!mInitialized) {
                reset(mSurface, codecName, codecType, mWidth, mHeight);
                mIsExceptionOccured = true;
            }
            long outPts = 0;
            int outIndex = 0;
            try {
                while (true) {
                    outIndex = mDecoder.dequeueOutputBuffer(mInfo, 0);
                    if (outIndex >= 0) {
                        mDecoder.releaseOutputBuffer(outIndex, true);
                        outPts = mInfo.presentationTimeUs / 1000;
                        break;
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        YYLog.debug(this, Constant.MEDIACODE_DECODER+"HardDecRender PushFrame INFO_OUTPUT_BUFFERS_CHANGED");
                        //mDecoder.getOutputBuffers();
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        mFormat = mDecoder.getOutputFormat();
                        mWidth = mFormat.getInteger(MediaFormat.KEY_WIDTH);
                        mHeight = mFormat.getInteger(MediaFormat.KEY_HEIGHT);
                        //mYv.OnPicSizeChange(mWidth, mHeight);
                        YYLog.debug(this, Constant.MEDIACODE_DECODER+"HardDecRender PushFrame INFO_OUTPUT_FORMAT_CHANGED format "
                                + mDecoder.getOutputFormat()+" width "+mWidth+" height "+mHeight);
                    } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        ++mNoFrameCnt;
                        if ((mNoFrameCnt % 150) == 0) {
                            YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender PushFrame noFrameCnt "+mNoFrameCnt);
                            reset(mSurface, codecName, codecType, mWidth, mHeight);
                            mIsExceptionOccured = true;
                        }
                        YYLog.debug(this, Constant.MEDIACODE_DECODER+"HardDecRender PushFrame INFO_TRY_AGAIN_LATER, no frame count:" + mNoFrameCnt);
                        break;
                    } else {
                        break;
                    }
                }

                int inIndex = 0;
                do {
                    inIndex = mDecoder.dequeueInputBuffer(200000);
                }while(inIndex<0);

                ByteBuffer buffer = mInputBuffers[inIndex];
                buffer.clear();
                buffer.put(bf);
                if (isHeader) {
                    YYLog.info(this, Constant.MEDIACODE_DECODER + "HardDecRender PushFrame config video");
                    mDecoder.queueInputBuffer(inIndex, 0, bf.length, pts * 1000, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                } else {
                    mDecoder.queueInputBuffer(inIndex, 0, bf.length, pts * 1000, 0);
                }

            } catch (Exception e) {
                ++mNoFrameCnt;
                mIsExceptionOccured = true;
                //TODO.
//                ViewLiveStatManager.getOrCreateViewLiveStatMgr(mStreamId).reportDecException(e.toString());
                YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender PushFrame exception "+e.getMessage());
                reset(mSurface, codecName, codecType, mWidth, mHeight);
            }
            return outPts;
        }
    }

    private void touchCrashTsFirst() {
        try {
            YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender touchCrashTsFirst "+ mCrashTsFirst);
            //TODO[sv]
            //HwCodecConfig.setRunTimeStamp(mCrashTsFirst, System.currentTimeMillis());
            mSecondTsWriten.set(false);
        } catch (Exception e) {
            YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender touchCrashTsFirst exception "+e.getMessage());
        }
    }

    private void touchCrashTsSecondInstant() {
        try {
            YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender touchCrashTsSecondInstant "+mCrashTsSecond);
            //TODO[sv]
            //HwCodecConfig.setRunTimeStamp(mCrashTsSecond, System.currentTimeMillis());
            mSecondTsWriten.set(true);
        } catch (Exception e) {
            YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender touchCrashTsSecondInstant exception "+e.getMessage());
        }
    }

    private void touchCrashTsSecond(boolean instantly) {
        if (!instantly) {
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(10000);
                        if (!mSecondTsWriten.get()) {
                            touchCrashTsSecondInstant();
                        }
                    } catch (Exception e) {
                        YYLog.error(this,  Constant.MEDIACODE_DECODER+"HardDecRender touchCrashTsSecond exception "+e.getMessage());
                    }
                }
            });
            th.start();
        }
        else {
            touchCrashTsSecondInstant();
        }
    }


    protected int reset(Surface surface, String codecName, String codecType, int width, int height) {
        synchronized (this) {
            try {
                if (!IsAvailable(codecName)) {
                    YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender reset codecType "+codecType
                            +"codecName"+codecName+" hardware decoder is not available");
                    return -1;
                }
                release();
                YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender reset");
                touchCrashTsFirst();
                mWidth = width;
                mHeight = height;
                mSurface = surface;

                mFormat = MediaFormat.createVideoFormat(codecType, mWidth, mHeight);
                mDecoder = MediaCodec.createByCodecName(codecName);
                //mDecoder = MediaCodec.createDecoderByType(codecType);
                mDecoder.configure(mFormat, mSurface, null, 0);
                mDecoder.start();
                mInputBuffers = mDecoder.getInputBuffers();
                mInfo = new MediaCodec.BufferInfo();
                mInitialized = true;
                mNeedConfig = true;
                YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender reset create codec=" + codecName + " start success.");
            } catch (Exception e) {
                YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender reset codec "+codecName+" exception "+e.getMessage());
            }
            touchCrashTsSecond(false);
            if (!mInitialized) {
                return -1;
            }
            return 0;
        }
    }

    protected int reset(Surface surface, String codecName,MediaFormat inputFormat) {
        synchronized (this) {
            try {
                if (!IsAvailable(codecName)) {
                    YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender reset "
                            +"codecName"+codecName+" hardware decoder is not available");
                    return -1;
                }
                release();
                YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender reset");
                touchCrashTsFirst();
                mSurface = surface;

                mFormat = inputFormat;
                mDecoder = MediaCodec.createByCodecName(codecName);
                //mDecoder = MediaCodec.createDecoderByType(codecType);
                mDecoder.configure(mFormat, mSurface, null, 0);
                mDecoder.start();
                mInputBuffers = mDecoder.getInputBuffers();
                mInfo = new MediaCodec.BufferInfo();
                mInitialized = true;
                mNeedConfig = false;
                YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender reset create codec=" + codecName + " start success.");
            } catch (Exception e) {
                YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender reset codec "+codecName+" exception "+e.getMessage());
            }
            touchCrashTsSecond(false);
            if (!mInitialized) {
                return -1;
            }
            return 0;
        }
    }

    final Object mForceFlushLock = new Object();
    private void UnBlockingForceFlush() {
        Thread tmp = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mDecoder.flush();       // maybe blocked
                    YYLog.info(this, Constant.MEDIACODE_DECODER+"HardDecRender UnBlockingForceFlush flushed normally");
                }
                catch (Throwable t) {
                    YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender UnBlockingForceFlush flushed with errors, maybe blocked, throwable "+t.getMessage());
                }
                finally {
                    synchronized (mForceFlushLock) {
                        mForceFlushLock.notifyAll();
                    }
                }
            }
        });
        try {
            synchronized (mForceFlushLock) {
                tmp.start();
                mForceFlushLock.wait(1000);
                tmp.interrupt();
            }
        }
        catch (Throwable t) {
            YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender UnBlockingForceFlush thread start throwable"+t.getMessage());
        }
    }

    public void release() {
        synchronized (this) {
            try {
                if (mDecoder != null) {
                    mInitialized = false;
                    //mDecoder.flush();
//                    UnBlockingForceStop();
                    mDecoder.stop();
                    mInputBuffers = null;
                }
            } catch (Throwable e) {
                YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender decoder stop throwable "+ e.getMessage());
            }
            finally {
                if (mDecoder != null) {
                    try {
                        mDecoder.release();
                        mDecoder = null;
                    } catch (Exception e1) {
                        YYLog.error(this, Constant.MEDIACODE_DECODER+"HardDecRender decoder release exception "+e1.getMessage());
                    }
                }
                touchCrashTsSecond(true);
            }
        }
    }

    public abstract int reset();

    public abstract int reset(Surface surface, int width, int height);

    public abstract int reset(Surface surface,MediaFormat inputFormat);

    public abstract long PushFrame(Surface surface, byte[] bf, long pts, boolean isHeader);

    protected static String findCodecName(final String mime, String[] supportedPrefixes,
                                          String[] unSupportedPrefixes, boolean isIgnoreCodecWhiteList) {
        YYLog.info(TAG, Constant.MEDIACODE_DECODER+"HardDecRender findCodecName mime "+mime+" supportedPrefixes "+ Arrays.toString(supportedPrefixes)
                +" unSupportedPrefixes "+Arrays.toString(unSupportedPrefixes)+" isIgnoreCodecWhiteList "+isIgnoreCodecWhiteList);
        if (Build.VERSION.SDK_INT < 16) {
            YYLog.error(TAG, Constant.MEDIACODE_DECODER+"HardDecRender findCodecName failed!! SDK version "+Build.VERSION.SDK_INT);
            return null;
        }
        List<String> codecNames = new ArrayList<String>();
        for (int i = MediaCodecList.getCodecCount() - 1; i >= 0; i--) {
            MediaCodecInfo mInfo = MediaCodecList.getCodecInfoAt(i);
            if (mInfo.isEncoder()) {
                continue;
            }
            if (!isSupportMime(mInfo, mime)) {
                continue;
            }
            if (isDisabledCodec(mInfo.getName())) {
                continue;
            }
            codecNames.add(mInfo.getName());
        }
        for (String cname: codecNames) {
            int i;
            for (i = 0; i < unSupportedPrefixes.length; ++i) {
                if (cname.startsWith(unSupportedPrefixes[i])) {
                    break;
                }
            }
            if (i < unSupportedPrefixes.length) {
                continue;
            }
            for (String supportedPrefix : supportedPrefixes) {
                if (cname.startsWith(supportedPrefix)) {
                    YYLog.info(TAG, Constant.MEDIACODE_DECODER+"HardDecRender findCodecName codecName="+cname);
                    return cname;
                }
            }
        }
        if (!isIgnoreCodecWhiteList) {
            return null;
        }
        if (codecNames.size() == 0) {
            YYLog.error(TAG, Constant.MEDIACODE_DECODER+"HardDecRender findCodecName failed!! codecNames empty!");
            return null;
        }
        String cname = codecNames.get(codecNames.size() - 1);
        YYLog.info(TAG, Constant.MEDIACODE_DECODER+"HardDecRender findCodecName codecName="+cname);
        return cname;
    }

    private static boolean isDisabledCodec(String name) {
        if (name.startsWith("OMX.google.")) {
            return true;
        }
        // packet video
        if (name.startsWith("OMX.PV.")) {
            return true;
        }
        if (name.startsWith("OMX.ittiam")) {
            return true;
        }
        if (name.endsWith(".sw.dec")) {
            return true;
        }
        return !name.startsWith("OMX.");
    }

    private static boolean isSupportMime(MediaCodecInfo mInfo, String mime) {
        String[] types = mInfo.getSupportedTypes();
        for (int j = 0; j < types.length; j++) {
            if (mime.equalsIgnoreCase(types[j])) {
                return true;
            }
        }
        return false;
    }

    public void setStreamId(long streamId) {
        mStreamId = streamId;
    }
}
