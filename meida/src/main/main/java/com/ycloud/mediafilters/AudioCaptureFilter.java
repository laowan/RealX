package com.ycloud.mediafilters;

import com.ycloud.api.common.SampleType;
import com.ycloud.api.videorecord.MediaRecordErrorListener;
import com.ycloud.audio.FFTProcessor;
import com.ycloud.common.Constant;
import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.mediarecord.audio.AudioRecordWrapper;
import com.ycloud.mediarecord.audio.IPcmFrameListener;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by kele on 2017/4/27.
 */

/** 对AudioCapture简单的封装 */
public class AudioCaptureFilter extends  AbstractYYMediaFilter
{
    private AudioRecordWrapper mAudioRecordWrapper = null;

    private AudioFilterContext   mFilterContext = null;

    AtomicBoolean mInited     = new AtomicBoolean(false);
    AtomicBoolean mEncodeEnable = new AtomicBoolean(false);

    private long mRecordStartTime = 0;
    private FFTProcessor mFFTProcessor = new FFTProcessor();

    public AudioCaptureFilter(AudioFilterContext  filterContext) {
        mFilterContext = filterContext;
    }

    /**设置是否采集数据,送入编码器的标识。开始录制应该立即设为true，停止录制应该立即设为false*/
    public void setEncodeEnable(boolean encodeEnable) {
        mEncodeEnable.set(encodeEnable);
        mRecordStartTime = System.nanoTime();
        mFFTProcessor.flush();
    }

    private IPcmFrameListener mPcmListener = new IPcmFrameListener() {
        @Override
        public void onGetPcmFrame(byte[] pcmBuffer, int size) {
            mFFTProcessor.process(pcmBuffer, 0, size, AudioRecordConstant.CHANNELS);

            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mSampleType = SampleType.AUDIO;
            sample.mDataBytes = pcmBuffer;
            sample.mBufferSize = size;
            sample.mAndoridPtsNanos = System.nanoTime() - mRecordStartTime;
//            YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "audio pts capture:" + sample.mAndoridPtsNanos / 1000000);
            sample.mDeliverToEncoder = mEncodeEnable.get();
            if (sample.mDeliverToEncoder) {
                deliverToDownStream(sample);
            }
            sample.decRef();
        }
    };

    /**
     * 启动音频采集线程
     */
    public void startCapture() {
        YYLog.info(this, "AudioCaptureFilter startCapture");
        if (mAudioRecordWrapper != null) {
            mAudioRecordWrapper.startAudioCapture();
        }
    }

    /**
     * 停止音频采集线程
     */
    public void stopCapture() {
        YYLog.info(this, "AudioCaptureFilter stopCapture");
        if (mAudioRecordWrapper != null) {
            mAudioRecordWrapper.stopAudioCapture();
        }
    }

    public void init() {
        if (mInited.get()) {
            YYLog.info(this, "AudioCaptureFilter is initialized already, so just return");
            return;
        }
        synchronized (this) {
            if (mInited.get()) {
                return;
            }

            mFFTProcessor.init(1024);

            if (mFilterContext.getRecordConfig().getEnableAudioRecord()) {
                mAudioRecordWrapper = new AudioRecordWrapper();
                mAudioRecordWrapper.setAudioInfoErrorListerner(new MediaRecordErrorListener() {

                    public void onVideoRecordError(int what, String message) {
                        YYLog.error(this, "audio record error!!!!");
                    }
                });
                mAudioRecordWrapper.setAudioDataListener(mPcmListener);
                mAudioRecordWrapper.setAudioRecordListener(
                        mFilterContext.getRecordConfig().getAudioRecordListener());
            }

            YYLog.info(this, "AudioCaptureFilter init success!!");
            mInited.set(true);
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        return super.processMediaSample(sample, upstream);
    }



    /**
     *  线程安全， 在最后退出小视频录制的时候， 需要确保关掉了音频采集设置，
     *  不然第2次打开音频设置失败.
     * */
    @Override
    public void deInit() {
        YYLog.info(this, "[tracer] AudioCaptureFilter deinit begin!!!");
        super.deInit();
        if(!mInited.getAndSet(false)) {
            YYLog.info(this, "[tracer] AudioCaptureFilter deinit, but it is not initialized state!!!");
            return;
        }

        synchronized (this) {
        /*释放音频采集*/
            if (mAudioRecordWrapper != null) {
                mAudioRecordWrapper.release();
                mAudioRecordWrapper = null;/*置空*/
            }
        }
        mFFTProcessor.deinit();

        YYLog.info(this, "[tracer] AudioCaptureFilter deinit success!!!");
    }

    public void enableAudioFrequencyCalculate(boolean enable) {
        mFFTProcessor.setEnable(enable);
        mFFTProcessor.flush();
    }

    public int audioFrequencyData(float[] buffer, int len) {
        if (mEncodeEnable.get()) {
            return mFFTProcessor.frequencyData(buffer, len);
        }
        return 0;
    }
}
