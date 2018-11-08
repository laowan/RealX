package com.ycloud.mediafilters;

import android.media.MediaCodec;

import com.ycloud.api.common.SampleType;
import com.ycloud.api.videorecord.IVideoRecordListener;
import com.ycloud.common.Constant;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.datamanager.YYVideoPacket;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2017/12/30.
 */

public class VideoDataManagerFilter extends AbstractYYMediaFilter {
    private MediaFilterContext   mFilterContext = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private IVideoRecordListener mRecordListener;
    private final int EVENT_ONPROGRESS = 0;
    private final int EVENT_START = 1;
    private final int EVENT_STOP = 2;

    public VideoDataManagerFilter(MediaFilterContext  filterContext) {
        mFilterContext = filterContext;
        init();
    }

    public void init() {
        if(mInited.get()) {
            YYLog.info(this, "VideoDataManagerFilter is initialized already, so just return");
            return;
        }
        YYLog.info(this, "VideoDataManagerFilter init success!!");
        mInited.set(true);
    }

    @Override
    public void deInit() {
        super.deInit();
        if(!mInited.getAndSet(false)) {
            YYLog.info(this, "[tracer] VideoDataManagerFilter deinit, but it is not initialized state!!!");
            return;
        }

        mInited.set(false);
        YYLog.info(this, "[tracer] VideoDataManagerFilter deinit success!!!");
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {

        if (!mInited.get() || sample.mSampleType != SampleType.VIDEO) {
            return false;
        }

        if(sample.mMediaFormat != null && sample.mDataByteBuffer == null) {  // SPS PPS

            VideoDataManager.instance().writeMediaFormat(sample.mMediaFormat);

            YYLog.info(this, Constant.MEDIACODE_MUXER+"video mediaformat: "+sample.mMediaFormat.toString() + " container what:" + sample.mMediaFormat.containsKey("what")
                    + " enumSize:" );

        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //donothing...
        } else if (sample.mDataByteBuffer != null && sample.mBufferSize >= 0) {

            ByteBuffer buffer = ByteBuffer.allocate(sample.mBufferSize);
            buffer.put(sample.mDataByteBuffer);
            buffer.asReadOnlyBuffer();
            buffer.position(sample.mBufferOffset);
            buffer.limit(sample.mBufferSize);

            YYVideoPacket packet = new YYVideoPacket();
            packet.mDataByteBuffer = buffer;
            packet.mBufferFlag = sample.mBufferFlag;
            packet.mBufferOffset = sample.mBufferOffset;
            packet.mBufferSize = sample.mBufferSize;
            packet.pts = sample.mAndoridPtsNanos/1000;
            packet.mFrameType = sample.mFrameType;
            packet.mBodyFrameDataArr = sample.bodyFrameDataArrClone();
            packet.mFaceFrameDataArr = sample.faceFrameDataArrClone();

            VideoDataManager.instance().write(packet);  // 多段录制时，内部会调整pts值和上一段保持pts连贯，让mediaplayer当做是一个视频文件

            sample.mDataByteBuffer.rewind();
        }
        deliverToDownStream(sample);
        return  true;
    }

    private void recordCallback(int event, float seconds) {
        if (mRecordListener == null) {
            RecordConfig recordConfig = mFilterContext.getRecordConfig();
            if (recordConfig != null) {
                mRecordListener = recordConfig.getRecordListener();
            }
        }
        if (mRecordListener != null) {
            if(event == EVENT_ONPROGRESS) {
                mRecordListener.onProgress(seconds);
            } else if (event == EVENT_START) {
                mRecordListener.onStart(true);
            } else if (event == EVENT_STOP) {
                mRecordListener.onStop(true);
            }
        }
    }

    public void startRecord() {
        if (mInited.get()) {
            VideoDataManager.instance().startRecord();
        }
        recordCallback(EVENT_START, 0);
    }

    public void stopRecord() {
        if (mInited.get()) {
            VideoDataManager.instance().stopRecord();
        }
        recordCallback(EVENT_STOP, 0);
    }
}
