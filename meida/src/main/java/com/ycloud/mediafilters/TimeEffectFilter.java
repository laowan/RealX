package com.ycloud.mediafilters;

import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

/**
 * Created by Administrator on 2018/4/25.
 */

public class TimeEffectFilter extends AbstractYYMediaFilter {
    public static final String TAG = "TimeEffectFilter";
    private long mLastVideoFrameReceiveStamp;
    private long mLastVideoFrameSetPts;
    private float mVideoSpeed = 1.0f;

    private long mLastVideoFrameReceiveDts;
    private long mLastVideoFrameSetDts;

    public void init(){
        mLastVideoFrameReceiveStamp = 0;
        mLastVideoFrameSetPts = 0;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (TimeEffectParameter.instance().IsExistTimeEffect()) {
            float speed = TimeEffectParameter.instance().getCurrentSpeed(sample.mYYPtsMillions);
            if (mVideoSpeed != speed && sample.mFrameType != VideoConstant.VideoFrameType.kVideoBFrame) {
                YYLog.info(TAG, " speed change from " + mVideoSpeed + " to " + speed);
                mVideoSpeed = speed;
            }
            long currentFrameReceiveStamp = sample.mAndoridPtsNanos;

            /*播放倍率不为1的情况下,需要改变video的pts，实现快慢速播放效果*/
            sample.mAndoridPtsNanos = (long) ((currentFrameReceiveStamp - mLastVideoFrameReceiveStamp) / mVideoSpeed) + mLastVideoFrameSetPts;
            mLastVideoFrameSetPts = sample.mAndoridPtsNanos;
            mLastVideoFrameReceiveStamp = currentFrameReceiveStamp;


            /*播放倍率不为1的情况下,需要改变video的dts*/
            long currentFrameReceiveDts = sample.mDtsMillions;
            sample.mDtsMillions = (long) ((currentFrameReceiveDts - mLastVideoFrameReceiveDts) / mVideoSpeed) + mLastVideoFrameSetDts;
            mLastVideoFrameSetDts = sample.mDtsMillions;
            mLastVideoFrameReceiveDts = currentFrameReceiveDts;

//            YYLog.info(TAG, "jyq test receiveDts:" + mLastVideoFrameReceiveDts + ",setDts:" + mLastVideoFrameSetDts + ",videoSeed:" + mVideoSpeed);
            //YYLog.info(TAG, " change pts form " + currentFrameReceiveStamp + " to " + mLastVideoFrameSetPts);
        }
        deliverToDownStream(sample);
        return false;
    }
}
