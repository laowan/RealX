package com.ycloud.mediacodec.videocodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;

import com.ycloud.VideoProcessTracer;
import com.ycloud.common.Constant;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.statistics.UploadStatManager;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by lookatmeyou on 2016/4/21.
 */

//TODO. 同步服务器下发的配置.
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class HardSurfaceEncoder {
    private final static String TAG = HardSurfaceEncoder.class.getSimpleName();
    private static  MediaCodec mEncoder;
    private static Surface mInputSurface;

    MediaCodec.BufferInfo mBufferInfo;

    int mWidth, mHeight, mFps, mBps, mGopSize, mBitRateModel;

    private String mCodecName = null;
    private LinkedList<Long> mCachedPtsList = new LinkedList<Long>();

    private static boolean mInitialized = false;
    HardEncodeListner mListener;
    private AtomicLong mEncodeId = new AtomicLong(-1);

    int mLevel;
    int mBaseLineLevel;
    int mProfile;
    String mMime = "";

    private int mRequestSyncCnt = 0;

    private int mFrameCnt = 0;

    private String mStrFormat = "";   // 用于输出调试信息，显示在界面上

    static MediaFormat mMediaFormat;
    VideoEncoderConfig mVideoEncoderConfig;

    HardSurfaceEncoder(String tag, String mime, long eid) {
        mMime = mime;
        mEncodeId.set(eid);
    }

    public static boolean IsAvailable() {
        return Build.VERSION.SDK_INT >= 18;
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public boolean init(VideoEncoderConfig config, HardEncodeListner listener) {
        mVideoEncoderConfig = config;
        synchronized (this) {
            try {
                if (!IsAvailable()) {
                    YYLog.error(TAG, "hardware encoder is not available");
                    return false;
                }

                YYLog.info(this, Constant.MEDIACODE_ENCODER + "[procedure] encoder init, configure： " + config.toString());
                this.mWidth = config.getEncodeWidth();
                this.mHeight = config.getEncodeHeight();
                this.mFps = config.mFrameRate;
                this.mBps = config.mBitRate;
                this.mGopSize = config.mGopSize;
                this.mBitRateModel = config.mBitRateModel;

                if (!mInitialized) {
                    initEncoder();
                }

                mBufferInfo = new MediaCodec.BufferInfo();
                mListener = listener;
                YYLog.info(this,Constant.MEDIACODE_ENCODER+"MediaCodec format:"+mStrFormat);
            } catch (Throwable t) {
                YYLog.error(TAG, Constant.MEDIACODE_ENCODER + "[exception]" + t.toString());
                UploadStatManager.getInstance().reportEncException(t.toString());
            }

            return mInitialized;
        }
    }

    private void initEncoder() {
        try {
            if (mEncoder == null) {

                mEncoder = MediaCodec.createEncoderByType(mMime);

                mCodecName = mEncoder.getName();
                mMediaFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
                MediaCodecInfo.CodecProfileLevel[] pr = mEncoder.getCodecInfo().getCapabilitiesForType(mMime).profileLevels;
                mLevel = 0;
                mProfile = 0;
                if (mMime.equals("video/hevc")) {
                    for (MediaCodecInfo.CodecProfileLevel aPr : pr) {
                        if (mProfile == aPr.profile && mLevel <= aPr.level) {
                            mProfile = aPr.profile;
                            mLevel = aPr.level;
                        }
                    }
                } else if (!mVideoEncoderConfig.mLowDelay && mVideoEncoderConfig.mEncodeType == VideoEncoderType.HARD_ENCODER_H264) {
                    //find baseline level
                    for (MediaCodecInfo.CodecProfileLevel aPr : pr) {
                        if (aPr.profile <= MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444) {
                            if (mProfile < aPr.profile) {
                                mProfile = aPr.profile;
                                mLevel = aPr.level;
                            } else if (mProfile == aPr.profile && mLevel < aPr.level) {
                                mProfile = aPr.profile;
                                mLevel = aPr.level;
                            }
                        }

                        if (aPr.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline) {
                            if (mBaseLineLevel < aPr.level) {
                                mBaseLineLevel = aPr.level;
                            }
                        }
                    }
                    if (mProfile > 0) {
                        mLevel = mLevel > MediaCodecInfo.CodecProfileLevel.AVCLevel42 ?
                                MediaCodecInfo.CodecProfileLevel.AVCLevel42 : mLevel;     // avoid crash
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mMediaFormat.setInteger(MediaFormat.KEY_PROFILE, mProfile);
                        }

                        YYLog.info(this, "mediaFormat.level:" + mLevel);
                        mMediaFormat.setInteger("level", mLevel);
                    }

                    mBaseLineLevel = mBaseLineLevel > MediaCodecInfo.CodecProfileLevel.AVCLevel42 ?
                            MediaCodecInfo.CodecProfileLevel.AVCLevel42 : mBaseLineLevel;     // avoid crash

                    mMediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                    YYLog.info(this, "mediaFormat.Baseline level:" + mBaseLineLevel);
                    mMediaFormat.setInteger("level", mBaseLineLevel);

                }
                mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBps);
//                mediaFormat.setInteger("bitrate-mode", 0);                // 0:BITRATE_MODE_CQ, 1:BITRATE_MODE_VBR, 2:BITRATE_MODE_CBR
                mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);

                mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mGopSize);

                mMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, mBitRateModel);


                //do config from server
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec params:" + mVideoEncoderConfig.mEncodeParameter);
                try {
                    if (!mVideoEncoderConfig.encodeParameterEmpty()) {
                        String itemDelim = ":";
                        String[] tokens = mVideoEncoderConfig.mEncodeParameter.split(itemDelim);
                        String valueDelim = "=";
                        for (int i = 0; i < tokens.length; i++) {
                            YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec parse:" + tokens[i]);
                            String[] keyValue = tokens[i].split(valueDelim);
                            if (keyValue.length == 2) {
                                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec param item: name " + keyValue[0] + ", value " + keyValue[1]);
                                setEncoderParams(mMediaFormat, keyValue[0], keyValue[1]);
                            } else {
                                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec invalid param item:" + Arrays.toString(keyValue));
                            }
                        }
                    }
                } catch (Exception e) {
                    YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec parse error:" + e);
                }

                mStrFormat = mMediaFormat.toString();
                YYLog.info(this, Constant.MEDIACODE_ENCODER + "before configure, MediaCodec format-----:" + mStrFormat);

                //更新视频comment中的分辨率信息
                String resolution = mMediaFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" + mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                VideoProcessTracer.getInstace().setResolution(resolution);
                mVideoEncoderConfig.mInterfaceWidth = mMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                mVideoEncoderConfig.mInterfaceHeight = mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            }

            mEncoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            mEncoder.start();
            mInitialized = true;

        } catch (Exception e) {
            YYLog.error(TAG,"video initEncoder error," + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deInit() {
        synchronized (this) {
            try {

                try {
                    if (mEncoder != null) {
                        mEncoder.stop();
                        mInitialized = false;
                        YYLog.info(this, Constant.MEDIACODE_ENCODER + " mEncoder.stop");
                    }
                } catch (Throwable e) {
                    YYLog.error(TAG, "[exception]" + e.getMessage());
                } finally {
                    if (mEncoder != null) {
                        mEncoder.release();
                        YYLog.info(this, Constant.MEDIACODE_ENCODER + " mEncoder.release");
                    }
                    mEncoder = null;
                }

                initEncoder();

            } catch (Throwable e) {
                YYLog.error(TAG, "[exception]" + e.getMessage());
            }
        }
    }

    public static void releaseEncoder() {
        try {
            if (mEncoder != null) {
                mEncoder.stop();
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + " mEncoder.stop");
            }
        } catch (Throwable e) {
            YYLog.error(TAG, "[exception]" + e.getMessage());
        } finally {
            if (mEncoder != null) {
                mEncoder.release();
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + " mEncoder.release");
            }
            mEncoder = null;
        }

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if(mMediaFormat != null) {
            mMediaFormat = null;
        }
        mInitialized = false;
    }

    public void drainEncoder(YYMediaSample sample, boolean endOfStream) {

        YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder begin");
        try {
            if (!mInitialized) {
                YYLog.info(this, Constant.MEDIACODE_ENCODER +"drainEncoder but encoder not started, just return!");
                return;
            }
            //没有创建成功，则不调用drainEncoder, 不然这里也会有问题， 譬如说这次又失败了，无法保证mEncoder的有效性.
            //仍然会崩溃.
//            if (!mInitialized) {
//                init(mWidth, mHeight, mFps, mBps, mListener);
//            }
            final int TIMEOUT_USEC = 10000;

            if (endOfStream) {
                mCachedPtsList.clear();
                mEncoder.signalEndOfInputStream();
                YYLog.info(this, Constant.MEDIACODE_ENCODER +"drainEncoder and siganl that end the encoder!!!! ");
            }

            long pts = (sample == null ? 0 : sample.mAndoridPtsNanos);
            mCachedPtsList.add(pts);

            ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
            while (true) {
                int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (!endOfStream) {
                        break;      // out of while
                    }
                    YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder INFO_TRY_AGAIN_LATER ");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = mEncoder.getOutputBuffers();
                    YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder INFO_OUTPUT_BUFFERS_CHANGED ");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    if (mListener != null) {
                        mListener.onEncoderFormatChanged(newFormat);
                    }
                    YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder INFO_OUTPUT_FORMAT_CHANGED ");
                } else if (encoderStatus < 0) {
                    YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder error!! ");
                    UploadStatManager.getInstance().reportEncError(encoderStatus);
                } else {
                    YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder get a frame!! , frameCnt="+(++mFrameCnt));
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    UploadStatManager.getInstance().endEncode((int) (mBufferInfo.presentationTimeUs / 1000));

                    long realPts = mBufferInfo.presentationTimeUs / 1000; //TODO: why add 3000
//                    YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "video pts after encode:" + realPts);
                    if (realPts < 0) {
                        YYLog.info(TAG, "error pts get from encode:" + realPts + ",just return");
                        return;
                    }

                    long dts = 0;
                    if (mCachedPtsList.size() > 0) {
                        dts = mCachedPtsList.pop();
                    }

                    mListener.onEncodeOutputBuffer(encodedData, mBufferInfo, dts, realPts, mEncoder.getOutputFormat(), sample);
                    mEncoder.releaseOutputBuffer(encoderStatus, false);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;      // out of while
                    }
                }
            }

            if (endOfStream) {
                mListener.onEndOfInputStream();
            }
        } catch (Throwable e) {
            YYLog.error(TAG, Constant.MEDIACODE_ENCODER +"[exception]" + e.toString());
            e.printStackTrace();
            deInit();
            UploadStatManager.getInstance().reportEncException(e.toString());
            mListener.onError(mEncodeId.get(), e.toString()); //notify error.
        }

        YYLog.debug(this, Constant.MEDIACODE_ENCODER +"drainEncoder end");
    }


    public void setEncoderParams(MediaFormat format, String name, String value) {
        switch (name) {
            case "bitrate-mode":
                /*
                 * Constant quality mode *
                public static final int BITRATE_MODE_CQ = 0;
                 * Variable bitrate mode *
                public static final int BITRATE_MODE_VBR = 1;
                 * Constant bitrate mode *
                public static final int BITRATE_MODE_CBR = 2;
                 */
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set bitrate-mode: " + value);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    format.setInteger(MediaFormat.KEY_BITRATE_MODE, Integer.parseInt(value));
                }
                break;
            case "color-range":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set color-range: " + value);
                //format.setInteger(MediaFormat.KEY_COLOR_RANGE, Integer.parseInt(value));//use this line when use Android API level 24
                format.setInteger("color-range", Integer.parseInt(value));
                break;
            case "color-standard":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set color-standard: " + value);
                //format.setInteger(MediaFormat.KEY_COLOR_STANDARD, Integer.parseInt(value));//use this line when use Android API level 24
                format.setInteger("color-standard", Integer.parseInt(value));
                break;
            case "color-transfer":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set color-transfer: " + value);
                //format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, Integer.parseInt(value));//use this line when use Android API level 24
                format.setInteger("color-transfer", Integer.parseInt(value));
                break;
            case "complexity":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set complexity: " + value);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    format.setInteger(MediaFormat.KEY_COMPLEXITY, Integer.parseInt(value));
                }
                break;
            case "gop_duration":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set i-frame-interval: " + value);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, Integer.parseInt(value));
                break;
            case "intra-refresh-period":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set i-frame-interval: " + value);
                //format.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, Integer.parseInt(value));//use this line when use Android API level 24
                format.setInteger("intra-refresh-period", Integer.parseInt(value));
                break;
            case "profile":
                switch (value.toLowerCase()) {
                    case "baseline":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: Baseline");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                        }
                        break;
                    case "main":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: Main");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain);
                        }
                        break;
                    case "extended":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: Extended");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileExtended);
                        }
                        break;
                    case "high":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: High");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
                        }
                        break;
                    case "high10":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: High10");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10);
                        }
                        break;
                    case "high422":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: High422");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422);
                        }
                        break;
                    case "high444":
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: High444");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444);
                        }
                        break;
                    default:
                        YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set profile: error keyword");
                        break;
                }
                break;
            case "level":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set level: " + value);
                //format.setInteger(MediaFormat.KEY_LEVEL, Integer.parseInt(value));//use this line when use Android API level 23
                format.setInteger("level", Integer.parseInt(value));
                break;
            case "priority":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set priority: " + value);
                //format.setInteger(MediaFormat.KEY_PRIORITY, Integer.parseInt(value)); //use this line when use Android API level 23
                format.setInteger("priority", Integer.parseInt(value));
                break;
            case "repeat-previous-frame-after":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set repeat-previous-frame-after: " + value);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, Integer.parseInt(value));
                }
                break;

            case "width":
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "MediaCodec set width: " + value);
                //format.setInteger(MediaFormat.KEY_WIDTH,Integer.parseInt(value));
                break;
            case "pack_16":
                int width = format.getInteger(MediaFormat.KEY_WIDTH);
                int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                if (Integer.parseInt(value)==1 && width>0 && height>0) {
                    int wDivide = width / 16;
                    int wRemainder = width % 16;
                    if (wRemainder > 0){
                        format.setInteger(MediaFormat.KEY_WIDTH,wDivide * 16 + 16);
                    }
                    int hDivide = height / 16;
                    int hRemainder = height % 16;
                    if (hRemainder > 0){
                        format.setInteger(MediaFormat.KEY_HEIGHT,hDivide * 16 + 16);
                    }
                }
                break;
            default:
                YYLog.info(TAG, "unsupported params:" + name);
                break;
        }
    }

    /**
     * Change a video encoder's target bitrate on the fly. The value is an
     * Integer object containing the new bitrate in bps.
     */
    public void adjustBitRate(int bitRateInKbps) {
        if (mEncoder == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                int bitRateInBps = bitRateInKbps * 1024;
                Bundle bundle = new Bundle();
                bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitRateInBps);
                mEncoder.setParameters(bundle);
                YYLog.info(this, Constant.MEDIACODE_ENCODER + "succeed to adjustBitRate " + bitRateInBps);
            } catch (Throwable t) {
                YYLog.error(this, Constant.MEDIACODE_ENCODER + "[exception] adjustBitRate. " + t.toString());
            }
        } else {
            YYLog.error(this, Constant.MEDIACODE_ENCODER + "adjustBitRate is only available on Android API 19+");
        }
    }

    /**
     * Request that the encoder produce a sync frame "soon".
     */
    public void requestSyncFrame() {
        if (mEncoder == null || !mInitialized) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "build version is:" + Build.VERSION.SDK_INT + ",can set PARAMETER_KEY_REQUEST_SYNC_FRAME.");
            try {
                Bundle bundle = new Bundle();
                bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                mEncoder.setParameters(bundle);
                if(mRequestSyncCnt++ % 30 == 0) {
                    YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "requestSyncFrame, cnt="+mRequestSyncCnt);
                }
            } catch (Throwable t) {
                YYLog.error(this, Constant.MEDIACODE_ENCODER + "[exception] requestSyncFrame: " + t.toString());
            }
        }
    }

    public String getFormat() {
        return mStrFormat;
    }
}
