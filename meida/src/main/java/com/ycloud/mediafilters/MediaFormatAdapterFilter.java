package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.api.common.SampleType;
import com.ycloud.mediacodec.MediaFormatExtraConstants;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.utils.StringUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Created by Kele on 2017/5/18.
 *
 * 软编码要用android平台的MediaMuxer，需要把sps,pps等转化成对应的media format.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaFormatAdapterFilter extends AbstractYYMediaFilter
{
    private MediaFormat mVideoMediaFormat = null;
    private MediaFilterContext   mFilterContext = null;
    private int mFrameCnt = 0;
    private final static int NAL_THREE = 3;
    private byte[] mStartCodeTest = new byte[NAL_THREE];
    private boolean mIFrameMode = false;

    private byte[] mNAL3StartCode = {(byte)0x00, (byte)0x00, (byte)0x01};
    private byte[] m00Byte =  {(byte)0x00};

    private boolean mNal3ToNal4 = false;

    private long mLastVideoFrameReceiveStamp;
    private long mLastVideoFrameSetPts;

    public MediaFormatAdapterFilter(MediaFilterContext filterContext) {
        mFilterContext = filterContext;
    }

    public void init() {
        mLastVideoFrameReceiveStamp = 0;
        mLastVideoFrameSetPts = 0;
        StateMonitor.instance().NotifyFormatAdapterStart(MediaConst.MEDIA_TYPE_VIDEO);
        StateMonitor.instance().NotifyFormatAdapterStart(MediaConst.MEDIA_TYPE_AUDIO);
    }

    public void setmIFrameMode(boolean iframeMode) {
        mIFrameMode =iframeMode;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        //check for the sps, pps..
        if(sample.mEncoderType == VideoEncoderType.SOFT_ENCODER_X264 && sample.mSampleType == SampleType.VIDEO) {
            return processH264MediaSample(sample);
        } else {
            deliverToDownStream2(sample);
            return false;
        }
    }

    private MediaFormat getVideoMediaFormat(YYMediaSample sample) {
        if(mVideoMediaFormat == null) {
            YYLog.info(this, "[mediaformat]createVideoFormat, codec=264 width="+sample.mWidth + " height" + sample.mHeight);
            mVideoMediaFormat = MediaFormat.createVideoFormat(VideoConstant.H264_MIME, sample.mWidth, sample.mHeight);
        }
        return mVideoMediaFormat;
    }

    //**Android system MediaMuxer should make sure h264 startcode with 4 byte 00000001
    public void setNAL3ValidNAL4(boolean enable) {
        mNal3ToNal4 = enable;
    }

    private boolean isStartWithNAL3Length(YYMediaSample sample)
    {
        boolean ret = false;
        int len = sample.mBufferSize < NAL_THREE ? sample.mBufferSize : NAL_THREE;
        if(len <NAL_THREE || sample.mDataByteBuffer == null)
            return ret;

        int pos = sample.mDataByteBuffer.position();
        sample.mDataByteBuffer.get(mStartCodeTest, 0, len);
        if(Arrays.equals(mStartCodeTest, mNAL3StartCode)) {
            ret = true;
        }
        sample.mDataByteBuffer.position(pos);
        return ret;
    }

    private boolean processH264MediaSample(YYMediaSample sample) {
        if(sample.mEndOfStream) {
            sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }

        if(isStartWithNAL3Length(sample) && mNal3ToNal4 && sample.mSampleType == SampleType.VIDEO) {
            //replace with
            sample.mBufferSize++;
            ByteBuffer buffer = ByteBuffer.allocateDirect(sample.mBufferSize);
            buffer.order(ByteOrder.nativeOrder());
            buffer.put(m00Byte);
            buffer.put(sample.mDataByteBuffer);
            buffer.flip();
            sample.mDataByteBuffer = buffer;
            sample.mBufferOffset = 0;
            YYLog.info(this, "[mediaformat]replace the nal 3 with nal 4 =================");
        }

        if(sample.mFrameType == VideoConstant.VideoFrameType.kVideoPPSFrame) {
            MediaFormat mediaFormat = getVideoMediaFormat(sample);
            ByteBuffer pps = ByteBuffer.allocateDirect(sample.mBufferSize);
            pps.order(ByteOrder.nativeOrder());

            if(sample.mDataByteBuffer != null) {
                int pos = sample.mDataByteBuffer.position();
                sample.mDataByteBuffer.position(sample.mBufferOffset);
                pps.put(sample.mDataByteBuffer);
                pps.flip();
                sample.mDataByteBuffer.position(pos);
            }
            mediaFormat.setByteBuffer(MediaFormatExtraConstants.KEY_AVC_PPS, pps);
            YYLog.info(this, "[mediaformat]processH264MediaSample set pps to MediaFormat pps.size="+pps.remaining() + "pps.limit=" +pps.limit());

        } else if(sample.mFrameType == VideoConstant.VideoFrameType.kVideoSPSFrame) {
            MediaFormat mediaFormat = getVideoMediaFormat(sample);
            ByteBuffer sps = ByteBuffer.allocateDirect(sample.mBufferSize);
            sps.order(ByteOrder.nativeOrder());

            if(sample.mDataByteBuffer != null) {
                int pos = sample.mDataByteBuffer.position();
                sample.mDataByteBuffer.position(sample.mBufferOffset);
                sps.put(sample.mDataByteBuffer);
                sps.flip();
                sample.mDataByteBuffer.position(pos);
            }
            mediaFormat.setByteBuffer(MediaFormatExtraConstants.KEY_AVC_SPS, sps);
            YYLog.info(this, "[mediaformat]processH264MediaSample set sps to MediaFormat, sps.size="+sps.remaining() + "sps.limit="+sps.limit());
        } else {
            if(mVideoMediaFormat != null) {
                YYMediaSample formatSample = YYMediaSampleAlloc.instance().alloc();
                formatSample.mSampleType = SampleType.VIDEO;
                formatSample.mMediaFormat = mVideoMediaFormat;

                YYLog.info(this, "[mediaformat]processH264MediaSample deliver MediaFormat to muxer begin");
                deliverToDownStream2(formatSample);
                YYLog.info(this, "[mediaformat]processH264MediaSample deliver MediaFormat to muxer end");
                formatSample.decRef();
                mVideoMediaFormat = null;
            }

            sample.mAndoridPtsNanos = sample.mYYPtsMillions*1000*1000;

            if(sample.mFrameType == VideoConstant.VideoFrameType.kVideoIDRFrame  || sample.mFrameType == VideoConstant.VideoFrameType.kVideoIFrame )
            {
                //MPEG4Write need this.
                sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            } else if(mIFrameMode){
                YYLog.info(this, "[mediaformat]processH264MediaSample got no IFrame----");
            }

            if(++mFrameCnt % 30 ==0) {
                YYLog.info(this, "[mediaformat]processH264MediaSample deliver to muxer, frame cnt:"+mFrameCnt);
            }

            if(!sample.mEndOfStream) {
                StateMonitor.instance().NotifyFormatAdapter(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);
            }
            deliverToDownStream2(sample);
        }
        return false;
    }

    private void deliverToDownStream2(YYMediaSample sample) {
        if(sample.mMediaFormat != null && sample.mDataByteBuffer == null) {  // SPS PPS
            //donothing...
        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //donothing...
        } else if (sample.mDataByteBuffer != null && sample.mBufferSize >= 0) {
            float recordSpeed = mFilterContext.getRecordConfig().getRecordSpeed();
            long currentFrameReceiveStamp = sample.mAndoridPtsNanos;

            /* 录制倍率不为1的情况下,需要改变video的pts，实现快慢速播放效果
             * 合成时候，不存在倍率变化，recordSpeed都是1,调试时候发现，如果用浮点数1.0计算，计算结果与使用整型计算有误差，导致pts可能小于dts，导致合成丢帧
             * 所以这里特殊处理，如果recordSpeed为1，就不用浮点数计算了，使用整数计算*/
            if (Math.abs(recordSpeed - 1) < 0.000001) {
                sample.mAndoridPtsNanos = (currentFrameReceiveStamp - mLastVideoFrameReceiveStamp) + mLastVideoFrameSetPts;
            } else {
                sample.mAndoridPtsNanos = (long) ((currentFrameReceiveStamp - mLastVideoFrameReceiveStamp) / recordSpeed) + mLastVideoFrameSetPts;
            }

//            YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "video pts mux:" + mVideoBufferInfo.presentationTimeUs + ",receive pts:" + currentFrameReceiveStamp);

            mLastVideoFrameSetPts = sample.mAndoridPtsNanos;
            mLastVideoFrameReceiveStamp = currentFrameReceiveStamp;
        }
        deliverToDownStream(sample);
    }
}
