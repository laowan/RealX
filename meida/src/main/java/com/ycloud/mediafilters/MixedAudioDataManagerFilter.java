package com.ycloud.mediafilters;

import android.media.MediaCodec;

import com.ycloud.api.common.SampleType;
import com.ycloud.common.Constant;
import com.ycloud.datamanager.YYAudioPacket;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2018/1/2.
 */

public class MixedAudioDataManagerFilter extends AbstractYYMediaFilter {
    private AtomicBoolean mInited = new AtomicBoolean(false);

    public MixedAudioDataManagerFilter() {
        init();
    }

    public void init() {
        if(mInited.get()) {
            YYLog.info(this, "AudioDataManagerFilter is initialized already, so just return");
            return;
        }
        YYLog.info(this, "AudioDataManagerFilter init success!!");
        mInited.set(true);
        MixedAudioDataManager.instance().reset();
    }

    @Override
    public void deInit() {
        super.deInit();
        if(!mInited.getAndSet(false)) {
            YYLog.info(this, "[tracer] AudioDataManagerFilter deinit, but it is not initialized state!!!");
            return;
        }

        mInited.set(false);
        YYLog.info(this, "[tracer] AudioDataManagerFilter deinit success!!!");
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {

        if (!mInited.get() || sample.mSampleType != SampleType.AUDIO) {
            return false;
        }

        if(sample.mMediaFormat != null && sample.mDataByteBuffer == null) {  // SPS PPS

            MixedAudioDataManager.instance().writeMediaFormat(sample.mMediaFormat);

            YYLog.info(this, Constant.MEDIACODE_MUXER+"Audio mediaformat: "+sample.mMediaFormat.toString() + " container what:" + sample.mMediaFormat.containsKey("what")
                    + " enumSize:" );

        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //donothing...
        } else if (sample.mDataByteBuffer != null && sample.mBufferSize >= 0) {

            ByteBuffer buffer = ByteBuffer.allocate(sample.mBufferSize);
            buffer.put(sample.mDataByteBuffer);
            buffer.asReadOnlyBuffer();
            buffer.position(sample.mBufferOffset);
            buffer.limit(sample.mBufferSize);

            YYAudioPacket packet = new YYAudioPacket();
            packet.mDataByteBuffer = buffer;
            packet.mBufferFlag = sample.mBufferFlag;
            packet.mBufferOffset = sample.mBufferOffset;
            packet.mBufferSize = sample.mBufferSize;
            packet.pts = sample.mAndoridPtsNanos / 1000;

//              YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "audio pts mux:" + packet.pts + ",receive pts:" + currentAudioFrameReceiveStamp);
            MixedAudioDataManager.instance().write(packet);

            sample.mDataByteBuffer.rewind();
        }
        deliverToDownStream(sample);
        return  true;
    }

    public void startRecord() {
        if (mInited.get()) {
            MixedAudioDataManager.instance().startRecord();
        }
    }

    public void stopRecord() {
        if (mInited.get()) {
            MixedAudioDataManager.instance().stopRecord();
        }
    }
}

