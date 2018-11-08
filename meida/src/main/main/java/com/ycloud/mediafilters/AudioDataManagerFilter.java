package com.ycloud.mediafilters;

import android.media.MediaCodec;
import com.ycloud.api.common.SampleType;
import com.ycloud.common.Constant;
import com.ycloud.datamanager.AudioDataManager;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.datamanager.YYAudioPacket;
import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.mediarecord.audio.AudioRecordWrapper;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2018/1/2.
 */

public class AudioDataManagerFilter extends AbstractYYMediaFilter {
    private MediaFilterContext   mFilterContext = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private long mLastAudioFrameReceiveStamp;
    private long mLastAudioFrameSetPts;
    private int mStandardAduioInterval;
    private static final float RANGE_RATE = 0.2f;  //计算音频帧间隔的误差因子

    public AudioDataManagerFilter(MediaFilterContext  filterContext) {
        mFilterContext = filterContext;
        mStandardAduioInterval = (AudioRecordConstant.SAMPLES_PER_FRAME * AudioRecordConstant.CHANNELS * 1000 * 1000) / AudioRecordConstant.SAMPLE_RATE;
        init();
    }

    public void init() {
        if(mInited.get()) {
            YYLog.info(this, "AudioDataManagerFilter is initialized already, so just return");
            return;
        }
        mLastAudioFrameReceiveStamp = 0;
        mLastAudioFrameSetPts = 0;
        YYLog.info(this, "AudioDataManagerFilter init success!!");
        mInited.set(true);
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

            AudioDataManager.instance().writeMediaFormat(sample.mMediaFormat);

            YYLog.info(this, Constant.MEDIACODE_MUXER+"Audio mediaformat: "+sample.mMediaFormat.toString() + " container what:" + sample.mMediaFormat.containsKey("what")
                    + " enumSize:" );

        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //donothing...
        } else if (sample.mDataByteBuffer != null && sample.mBufferSize >= 0) {

            ByteBuffer buffer = ByteBuffer.allocate(sample.mBufferSize);
            if (buffer.remaining() >= sample.mDataByteBuffer.remaining()) {
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
                AudioDataManager.instance().write(packet);

                sample.mDataByteBuffer.rewind();
            } else {
                YYLog.error(TAG, "buffer not enough");
            }

        }
        deliverToDownStream(sample);
        return  true;
    }

    public void startRecord() {
        if (mInited.get()) {
            AudioDataManager.instance().startRecord();
        }
    }

    public void stopRecord() {
        if (mInited.get()) {
            AudioDataManager.instance().stopRecord();
        }
    }
}
