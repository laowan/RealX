package com.ycloud.mediafilters;

import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.mediarecord.audio.AudioVoiceChangerToolbox;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by jinyongqing on 2017/6/26.
 */

public class AudioProcessFilter extends  AbstractYYMediaFilter {

    private AudioFilterContext   mFilterContext = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private AudioVoiceChangerToolbox mAudioVoiceChangerToolbox = null;

    public AudioProcessFilter(AudioFilterContext  filterContext) {
        mFilterContext = filterContext;
    }

    public void init() {
        YYLog.info(this, "[tracer] AudioProcessFilter init begin!!!");
        if(mInited.get()) {
            YYLog.info(this, "AudioProcessFilter is initialized already, so just return");
            return;
        }

        if(mFilterContext.getRecordConfig().getVoiceChangeMode() != AudioVoiceChangerToolbox.VeoNone) {
            mAudioVoiceChangerToolbox = AudioVoiceChangerToolbox.getInstance();
            mAudioVoiceChangerToolbox.initWithSampleRate(AudioRecordConstant.SAMPLE_RATE, AudioRecordConstant.CHANNELS);
            mAudioVoiceChangerToolbox.setEffectMode(mFilterContext.getRecordConfig().getVoiceChangeMode());
        }

        YYLog.info(this, "AudioProcessFilter init success!!");
        mInited.set(true);
    }

    @Override
    public void deInit() {
        YYLog.info(this, "[tracer] AudioProcessFilter deinit begin!!!");
        super.deInit();
        if(!mInited.getAndSet(false)) {
            YYLog.info(this, "[tracer] AudioProcessFilter deinit, but it is not initialized state!!!");
            return;
        }

        synchronized (this) {
            if (mAudioVoiceChangerToolbox != null) {
                mAudioVoiceChangerToolbox.deInit();
                mAudioVoiceChangerToolbox = null;
            }
        }
        YYLog.info(this, "[tracer] AudioProcessFilter deinit success!!!");
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if(mAudioVoiceChangerToolbox != null) {
            mAudioVoiceChangerToolbox.audioEngineProcess(sample.mDataBytes);
            deliverToDownStream(sample);
            return  true;
        }
        deliverToDownStream(sample);
        return  false;
    }
}
