package com.ycloud.mediafilters;


import com.ycloud.common.Constant;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.utils.YYLog;

/**
 * Created by kele on 2016/12/12.
 */

public class AbstractEncoderFilter extends IEncodeFilter {
    private long mlastCountTime = System.currentTimeMillis();

    private  int mInputByteSize = 0;
    private int mOutputByteSize = 0;

    private int mFrameCnt = 0;
    private int mBitRate = 0;
    private int mFrameRate = 0;

    private long lastCapCountTime = 0;
    private int capFrameRate;

    private long encodeTime = 0;
    private IEncoderListener mEncoderListener = null;

    VideoEncoderConfig mEncoderConfig = null;
    protected int mRetryCnt = 0;

    private boolean mbFirstFrame = true;
    private boolean mHasBFrame = false;

    public String mEncodeParam = null;  // 用来显示到界面的

    @Override
    public void setEncoderListener(IEncoderListener listener) {
        mEncoderListener = listener;
    }

    public void handleEncodedFrameStats(long outputFrameSize, int inputFrameSize, int frameType) {
        if(mbFirstFrame && mEncoderListener != null) {
            mEncoderListener.onEncodeFirstFrame();
        }
        mbFirstFrame = false;

        if (frameType == VideoConstant.VideoFrameType.kVideoBFrame){
            if (!mHasBFrame) {
                YYLog.info(TAG, "handleEncodedFrameStats B frame enable");
                mHasBFrame = true;
                if (mEncoderListener != null)
                    mEncoderListener.onEncodeEncParam(mEncodeParam + ":haveBFrame:true");
            }
        }

        mFrameCnt++;
        mOutputByteSize += outputFrameSize;
        mInputByteSize += inputFrameSize;

        if (mlastCountTime == 0) {
            mlastCountTime = System.currentTimeMillis();
        }


        float interval = (System.currentTimeMillis() - mlastCountTime) / 1000.0f;
        if (interval >= 3) {
            mBitRate = (int)(8 * mOutputByteSize / interval);
            mFrameRate = (int)(mFrameCnt / interval);
            YYLog.info(this, Constant.MEDIACODE_ENCODER+"encoded bitRate:" + mBitRate + ", mFrameCnt:" + mFrameRate + " input_video_size="+mInputByteSize + " output_video_size="+mOutputByteSize);

            if (mEncoderListener != null) {
                mEncoderListener.onEncodeStat(mBitRate, mFrameRate);
            }

            mlastCountTime = System.currentTimeMillis();
            mOutputByteSize = 0;
            mInputByteSize = 0;
            mFrameCnt=0;
        }

        //有数据输出，则认为解码器是创建成功的，把retry cnt清0.
        mRetryCnt = 0;
    }

    public void handleEncodeResolution(int width, int height) {
        if (mEncoderListener != null) {
            YYLog.info(this,Constant.MEDIACODE_ENCODER+"handleEncodeResolution:"+width+"x"+height);
            mEncoderListener.onEncodeResolution(width, height);
        }
    }

    public void handleCaptureFrameStats() {
        //stat
        if (System.currentTimeMillis() - lastCapCountTime >= 3 * 1000) {
            capFrameRate = capFrameRate / 3;
            YYLog.info(this, Constant.MEDIACODE_ENCODER+"encoded capture mFrameCnt:" + capFrameRate);
            lastCapCountTime = System.currentTimeMillis();
            capFrameRate = 0;
        }
        capFrameRate++;
    }

    public void setEncodeCfg(VideoEncoderConfig cfg) {
        YYLog.info(this, Constant.MEDIACODE_ENCODER+"setEncodeCfg "+cfg.toString());
        if(mEncoderConfig == null) {
            mEncoderConfig = new VideoEncoderConfig(cfg);
        } else {
            mEncoderConfig.assign(cfg);
        }
    }

    public void notifyEncoderParam(String param) {
        if( mEncoderListener != null) {
            mEncoderListener.onEncodeEncParam(param);
        }
    }

    public boolean checkEncodeUpdate(int width, int height, boolean bLowDelay, int frameRate, int bitRate, String encodeParameter) {
        boolean ret = false;
        if(mEncoderConfig.getEncodeHeight() != height || mEncoderConfig.getEncodeWidth() != width) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "checkEncodeUpdate from " + mEncoderConfig.getEncodeHeight() + "x" +
                    mEncoderConfig.getEncodeWidth() + ", to " + width + "x" + height);

            mEncoderConfig.setEncodeSize(width, height);
            ret = true;
        }

        if(mEncoderConfig.mLowDelay != bLowDelay) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "checkEncodeUpdate lowDelay from " + mEncoderConfig.mLowDelay + ", to " + bLowDelay);
            mEncoderConfig.mLowDelay = bLowDelay;
            ret = true;
        }

        if(mEncoderConfig.mFrameRate != frameRate) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "checkEncodeUpdate frameRate from " + mEncoderConfig.mFrameRate + ", to " + frameRate);
            mEncoderConfig.mFrameRate = frameRate;
            ret = true;
        }

        //实际码率和配置码率不相等，不需要重启编码器
        /*
        if (mEncoderConfig.mBitRate != bitRate) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "checkEncodeUpdate biteRate from " + mEncoderConfig.mBitRate + ", to " + bitRate);
            mEncoderConfig.mBitRate = bitRate;
            ret = true;
        }
        */

        if ((!mEncoderConfig.mLowDelay || mEncoderConfig.mEncodeType != VideoEncoderType.SOFT_ENCODER_X264)
                &&mEncoderConfig != null
                && ((mEncoderConfig.mEncodeParameter == null && encodeParameter != null) || (mEncoderConfig.mEncodeParameter!= null && !mEncoderConfig.mEncodeParameter.equals(encodeParameter)))) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "checkEncodeUpdate param from " + mEncoderConfig.mEncodeParameter + ", to " + encodeParameter);
            mEncoderConfig.mEncodeParameter = encodeParameter;
            ret = true;
        }

        return ret;
    }
}
