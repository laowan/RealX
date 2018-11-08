package com.ycloud.mediafilters;

import com.ycloud.api.common.SampleType;
import com.ycloud.audio.AudioPlaybackRateProcessor;
import com.ycloud.mediarecord.audio.AudioRecordWrapper;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

/**
 * Created by Administrator on 2018/2/27.
 */

public class AudioSpeedFilter extends AbstractYYMediaFilter {
    private AudioPlaybackRateProcessor mAudioPlaybackRateProcessor = null;
    private static final String TAG = "AudioSpeedFilter";
    public void init(int sampleRate, int channels) {
        deInit();
        mAudioPlaybackRateProcessor = new AudioPlaybackRateProcessor();
        mAudioPlaybackRateProcessor.init(sampleRate, channels, true);
    }

    public void setRate(float rate) {
        if (mAudioPlaybackRateProcessor != null) {
            YYLog.info(TAG, " setRate " + rate);
            mAudioPlaybackRateProcessor.setRate(rate);
        }
    }

    @Override
    public void deInit() {
        if (mAudioPlaybackRateProcessor != null) {
            mAudioPlaybackRateProcessor.flush();
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mDeliverToEncoder = true;
            sample.mSampleType = SampleType.AUDIO;
            sample.mDataBytes = new byte[AudioRecordWrapper.SAMPLES_PER_FRAME * 4];
            sample.mBufferSize = AudioRecordWrapper.SAMPLES_PER_FRAME * 4;
            int requestSize = sample.mBufferSize;
            while (mAudioPlaybackRateProcessor != null) {
                int bytes = mAudioPlaybackRateProcessor.pull(sample.mDataBytes, 0, requestSize);
                sample.mBufferSize = bytes;
                if (bytes > 0) {
                    deliverToDownStream(sample);
                } else {
                    break;
                }
            }
            sample.decRef();
            mAudioPlaybackRateProcessor.unint();
            mAudioPlaybackRateProcessor = null;
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (mAudioPlaybackRateProcessor != null) {
            mAudioPlaybackRateProcessor.push(sample.mDataBytes, sample.mBufferSize);
            int requestSize = sample.mBufferSize;
            while (mAudioPlaybackRateProcessor != null) {
                int numBytes = mAudioPlaybackRateProcessor.numOfBytesAvailable();
                if (numBytes >= requestSize) {
                    int bytes = mAudioPlaybackRateProcessor.pull(sample.mDataBytes, 0, requestSize);
                    sample.mBufferSize = bytes;
                    if (bytes > 0) {
                        if (bytes == requestSize) {
                            deliverToDownStream(sample);
                        }else {
                            YYLog.w(TAG, " not in frame size ");
                        }
                    }
                } else {
                    break;
                }
            }
        }else {
            deliverToDownStream(sample);
        }
        return true;
    }
}
