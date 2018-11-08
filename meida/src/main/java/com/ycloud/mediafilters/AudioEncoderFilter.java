package com.ycloud.mediafilters;

/**
 * Created by kele on 2017/4/27.
 */


import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.api.common.SampleType;
import com.ycloud.common.Constant;
import com.ycloud.mediacodec.audiocodec.AudioEncoder;
import com.ycloud.mediacodec.audiocodec.FFmpegAacEncoder;
import com.ycloud.mediacodec.audiocodec.HardAudioEncoder;
import com.ycloud.mediacodec.audiocodec.AudioEncodeListener;
import com.ycloud.mediacodec.format.MediaFormatExtraConstants;
import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** 对Audio encoder简单的封装*/
public class AudioEncoderFilter extends AbstractYYMediaFilter implements AudioEncodeListener
{
    AudioEncoder mAudioEncoder = null;
    AudioFilterContext      mFilterContext = null;

    // audio
    private int mAudioBitrate = AudioRecordConstant.AUDIO_BITRATE;
    private int mAudioChannels = AudioRecordConstant.CHANNELS;
    private int mAudioSampleRate = AudioRecordConstant.SAMPLE_RATE;

    AtomicBoolean mInited     = new AtomicBoolean(false);
    AtomicBoolean mRecording = new AtomicBoolean(false);
    private long mSamplesHaveReceived = 0;
    private long mLastEncodedPts = 0;
    private boolean mHaveOutputFormat = false;
    //private AacFileWriter mFileDmp = new AacFileWriter();

    public AudioEncoderFilter(AudioFilterContext filterContext) {
        mFilterContext = filterContext;
    }

    public void init() {
        if(mInited.get()) {
            YYLog.info(this, "AudioEncoderFilter is initialized already, so just return");
            return;
        }
        //mFileDmp.open("/sdcard/r.aac", mAudioSampleRate, mAudioChannels );
        initAudioEncoder(false);
        YYLog.info(this, "AudioEncoderFilter init success!!!");
        mInited.set(true);
    }

    public void init(int samplerate, int channels) {
        if(mInited.get()) {
            YYLog.info(this, "AudioEncoderFilter is initialized already, so just return");
            return;
        }
        mAudioChannels = channels;
        mAudioSampleRate = samplerate;
        initAudioEncoder(false);
        YYLog.info(this, "AudioEncoderFilter init success!!!");
        mInited.set(true);
    }

    private synchronized  void initAudioEncoder(boolean initAudio) {
        if(mAudioEncoder == null) {
            mAudioEncoder = new HardAudioEncoder(getAudioFormat());
            //mAudioEncoder = new FFmpegAacEncoder(getAudioFormat());
            mAudioEncoder.setEncodeListener(this);
        }

        if(initAudio) {
            try {
                mAudioEncoder.init();
                mHaveOutputFormat = false;
            } catch (Exception e) {
                YYLog.info(this, " init hard audio encoder error: " + e.toString());
                switchEncoder();
            }
        }
    }

    public boolean isRecording() {
        return mRecording.get();
    }


    public void startAudioEncode() {
        if(mInited.get() && mAudioEncoder != null) {
            initAudioEncoder(true);
            mRecording.set(true);
            mSamplesHaveReceived = 0;
            mLastEncodedPts = 0;
            YYLog.info(this, "AudioEncoderFilter startAudioEncode!!!");
        }
    }

    public void stopAudioEncode(boolean releaseAudioEncoder) {
        if(mInited.get() && mAudioEncoder != null && mRecording.get()) {
            mAudioEncoder.stopAudioRecord();
            mAudioEncoder = null;
            //mFileDmp.close();
        }
        mRecording.set(false);

        if(!releaseAudioEncoder) {
            initAudioEncoder(true);
        }
        YYLog.info(this, "AudioEncoderFilter stopAudioEncode!!!");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private MediaFormat getAudioFormat() {
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC, mAudioSampleRate, mAudioChannels/*1*/);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);
        /*https://github.com/OnlyInAmerica/HWEncoderExperiments/blob/audioonly/HWEncoderExperiments/src/main/java/net/openwatch/hwencoderexperiments/AudioEncoder.java*/
/*        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);*/
        if (mAudioChannels == 1) {
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        }
        if (mAudioChannels == 2) {
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        }

        return audioFormat;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if(sample.mSampleType == SampleType.AUDIO  && mRecording.get() && sample.mDeliverToEncoder) {
            long pts = (long) (((float) mSamplesHaveReceived) / mAudioSampleRate * 1000000000);
            sample.mAndoridPtsNanos = pts;
            try {
                mAudioEncoder.pushToEncoder(sample);
            } catch (Exception e) {
                YYLog.info(this, " mAudioEncoder push to encoder error " + e.toString());
                switchEncoder();
                try {
                    mAudioEncoder.pushToEncoder(sample);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            mSamplesHaveReceived += (sample.mBufferSize / 2);
        }
      //donot deliver...
        return false;
    }

    @Override
    public void deInit() {
        super.deInit();
        if(!mInited.getAndSet(false)) {
            return;
        }

        YYLog.info(this, "AudioEncoderFilter deinit begin.........");
        if(mAudioEncoder != null) {
            mAudioEncoder.releaseEncoder();
            mAudioEncoder = null;
        }

        YYLog.info(this, "AudioEncoderFilter deinit end.........");
    }

    //byte[] tmpBuffer = new byte[2048];

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onEncodeOutputBuffer(ByteBuffer buffer, MediaCodec.BufferInfo buffInfo, long ptsUs, MediaFormat mediaFormat) {
        if (mLastEncodedPts > ptsUs) {
            return;
        }

        mLastEncodedPts = ptsUs;
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();

        sample.mSampleType = SampleType.AUDIO;
        sample.mDataByteBuffer = buffer;
        sample.mBufferOffset = buffInfo.offset;
        sample.mBufferSize = buffInfo.size;
        sample.mBufferFlag = buffInfo.flags;
        sample.mMediaFormat = mediaFormat;

        sample.mAndoridPtsNanos = ptsUs*1000;
        sample.mYYPtsMillions = ptsUs/1000;
        sample.mDtsMillions = 0;
        /*
        if(buffer != null) {
            int p = buffer.position();
            int l = buffer.limit();
            buffer.get(tmpBuffer, 0, buffInfo.size);
            mFileDmp.write(tmpBuffer, buffInfo.size);
            buffer.position(p);
            buffer.limit(l);
        }
        */

        deliverToDownStream(sample);
        sample.decRef();
    }

    @Override
    public void onEncoderFormatChanged(MediaFormat mediaFormat) {
        if (!mHaveOutputFormat) {
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mSampleType = SampleType.AUDIO;
            sample.mMediaFormat = mediaFormat;
            deliverToDownStream(sample);
            sample.decRef();
            mHaveOutputFormat = true;
        }
    }

    @Override
    public void onEndOfInputStream() {
        YYLog.info(this, Constant.MEDIACODE_ENCODER+"AudioEncoderFilter onEndOfInputStream");
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
        sample.mSampleType = SampleType.AUDIO;
        sample.mEndOfStream = true;
        sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        deliverToDownStream(sample);
        sample.decRef();
    }

    @Override
    public void onError(long eid, String errMsg) {
        //TODO.
        YYLog.error(this, Constant.MEDIACODE_ENCODER+"AudioEncoderFilter error: "+errMsg);
    }

    private void switchEncoder() {
        mAudioEncoder.releaseEncoder();
        mAudioEncoder = new FFmpegAacEncoder(getAudioFormat());
        mAudioEncoder.setEncodeListener(this);
        try {
            mAudioEncoder.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
