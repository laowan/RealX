package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import com.ycloud.api.common.SampleType;
import com.ycloud.common.Constant;
import com.ycloud.mediacodec.IMediaMuxer;
import com.ycloud.mediacodec.engine.AndroidMediaMuxer;
import com.ycloud.mediacodec.engine.FfmMediaMuxer;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.mediarecord.mediacodec.MediaFormatValidator;
import com.ycloud.mediarecord.mediacodec.QueuedMuxer;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by kele on 2017/4/26.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaMuxerFilter extends AbstractYYMediaFilter
{
    private static final float RANGE_RATE = 0.2f;  //计算音频帧间隔的误差因子
    MediaFilterContext mFilterContext  = null;
    QueuedMuxer             mQueuedMuxer = null;

    boolean                 mVideoEndOfStream = false;
    boolean                 mAudioEndOfStream = false;

    AtomicBoolean           mInited     = new AtomicBoolean(false);

    MediaCodec.BufferInfo   mVideoBufferInfo = new MediaCodec.BufferInfo();
    MediaCodec.BufferInfo   mAudioBufferInfo = new MediaCodec.BufferInfo();

    private boolean mVideoAudioSync = true;
    private boolean mSingleStreamStopTriggerMode = true;

    private boolean mAndroidMuxer = true;

    public MediaMuxerFilter(MediaFilterContext filterContext, boolean androidMuxer ) {
        mAndroidMuxer = androidMuxer;
        mFilterContext = filterContext;
    }

    public void setVideoAudioSync(boolean enable) {
        mVideoAudioSync = enable;
    }

    public void setEnableAudio(boolean enableAudio) {
        mFilterContext.getRecordConfig().setEnableAudioRecord(false);
        if (mQueuedMuxer != null) {
            mQueuedMuxer.setEnableAudioRecord(enableAudio);
        }
    }

    public void setSingleStreamOfEndMode(boolean enable) {
        mSingleStreamStopTriggerMode = enable;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return false;
        }

        if(sample.mSampleType == SampleType.VIDEO) {
            processVideoSample(sample);
        } else if(sample.mSampleType == SampleType.AUDIO) {
            processAudioSample(sample);
        }
        deliverToDownStream(sample);
        return  false;
    }

    private boolean getVideoEnd() {
        return mVideoEndOfStream;
    }

    private boolean getAudioEnable() {
        if(mQueuedMuxer != null) {
            return mQueuedMuxer.getAudioEnable();
        }
        return false;
    }

    private boolean getAudioEnd() {
       return (mAudioEndOfStream || !mQueuedMuxer.getAudioEnable());
    }

    private boolean allEndOfStream() {
        return mVideoEndOfStream && (mAudioEndOfStream || !mQueuedMuxer.getAudioEnable());
    }

    private void tryNotifyEndOfStream() {
        if(!allEndOfStream()) {
            YYLog.error(TAG, "Not All end! Audio "+ mAudioEndOfStream
                                + " video " + mVideoEndOfStream + " AudioEnable " + mQueuedMuxer.getAudioEnable());
            return;
        }

        YYMediaFilterListener listener = mFilterListener.get();
        if(listener != null) {
            listener.onFilterEndOfStream(this);
        }
    }

    protected void processVideoSample(YYMediaSample sample) {
        if(!mInited.get()) {
            return;
        }

        if(sample.mMediaFormat != null && sample.mDataByteBuffer == null) {
            //output media format change.
            mQueuedMuxer.setOutputFormat(SampleType.VIDEO, sample.mMediaFormat);
            YYLog.info(this, Constant.MEDIACODE_MUXER+"video mediaformat: "+sample.mMediaFormat.toString() + " container what:" + sample.mMediaFormat.containsKey("what")
                        + " enumSize:" );

        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mQueuedMuxer.stop(SampleType.VIDEO);
            mVideoEndOfStream = true;
            YYLog.info(this, "[muxer] video end of stream");
            StateMonitor.instance().NotifyMediaMuxerEnd(MediaConst.MEDIA_TYPE_VIDEO);
            tryNotifyEndOfStream();
        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //donothing...
        } else if(sample.mDataByteBuffer != null) {

            mVideoBufferInfo.size = sample.mBufferSize;
            mVideoBufferInfo.offset = sample.mBufferOffset;
            mVideoBufferInfo.flags = sample.mBufferFlag;
            mVideoBufferInfo.presentationTimeUs = sample.mAndoridPtsNanos/1000;

            mQueuedMuxer.writeSampleData(SampleType.VIDEO, sample.mDataByteBuffer, mVideoBufferInfo, sample.mDtsMillions);
//            YYLog.info(TAG, Constant.MEDIACODE_PTS_EXPORT + "pts=" + mVideoBufferInfo.presentationTimeUs + ",dts=" + sample.mDtsMillions);

            YYMediaFilterListener listener = mFilterListener.get();
            if(listener != null) {
                listener.onFilterProcessMediaSample(this, sample.mSampleType, sample.mYYPtsMillions);
            }

            StateMonitor.instance().NotifyMeidaMuxer(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);
        }
//        YYLog.info(this, "processVideoSample end ###########");
    }

    protected void processAudioSample(YYMediaSample sample) {

        if(mAudioEndOfStream) {
            YYLog.error(this, "[muxer] processAudioSample, audio end of stream, but receive a sample again");
        }
        if(!mInited.get()) {
            YYLog.error(TAG, "[muxer] should init first .");
            return;
        }

        if(sample.mMediaFormat != null && sample.mDataByteBuffer == null) {
            //output media format change.
            mQueuedMuxer.setOutputFormat(SampleType.AUDIO, sample.mMediaFormat);
        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 && !mAudioEndOfStream ) {
            mQueuedMuxer.stop(SampleType.AUDIO);
            mAudioEndOfStream = true;
            YYLog.info(this, "[muxer] audio end of stream");
            StateMonitor.instance().NotifyMediaMuxerEnd(MediaConst.MEDIA_TYPE_AUDIO);
            tryNotifyEndOfStream();
        } else if((sample.mBufferFlag & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            //donothing...
        } else if(sample.mDataByteBuffer != null) {
            mAudioBufferInfo.size = sample.mBufferSize;
            mAudioBufferInfo.offset = sample.mBufferOffset;
            mAudioBufferInfo.flags = sample.mBufferFlag;
            mAudioBufferInfo.presentationTimeUs = sample.mAndoridPtsNanos / 1000;

            ByteBuffer outputBuffer = sample.mDataByteBuffer;
//                YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "audio pts mux:" + mAudioBufferInfo.presentationTimeUs + ",receive pts:" + currentAudioFrameReceiveStamp);
            mQueuedMuxer.writeSampleData(SampleType.AUDIO, outputBuffer, mAudioBufferInfo, mAudioBufferInfo.presentationTimeUs / 1000);

            StateMonitor.instance().NotifyMeidaMuxer(MediaConst.MEDIA_TYPE_AUDIO, sample.mYYPtsMillions);
        }
//        YYLog.info(this, "processAudioSample end=============");
    }

    @Override
    public void deInit() {
        if(!mInited.getAndSet(false)) {
            YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter deInit， but it is not initialized!!");
            return;
        }

        YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter deInit begin!!");
        if(mQueuedMuxer != null) {
            mQueuedMuxer.stop(SampleType.VIDEO);
            if(mFilterContext.getRecordConfig().getEnableAudioRecord()) {
                mQueuedMuxer.stop(SampleType.AUDIO);
            }
            mQueuedMuxer = null;
        }

        YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter deInit end!!");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void init() {
        if(mInited.get()) {
            YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter is initialized already, so just return");
            return;
        }

        YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter init begin!!");
        mVideoEndOfStream = false;
        mAudioEndOfStream = false;

        RecordConfig recordConfig = mFilterContext.getRecordConfig();
        try {
            //AndroidMediaMuxer mediaMuxer = new AndroidMediaMuxer(recordConfig.getRecordFilePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            IMediaMuxer mediaMuxer = null;
            if(mAndroidMuxer) {
                mediaMuxer = new AndroidMediaMuxer(recordConfig.getRecordFilePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
                mediaMuxer = new FfmMediaMuxer(recordConfig.getRecordFilePath());
            }
            mediaMuxer.setOrientationHint(0);
            mQueuedMuxer = new QueuedMuxer(mediaMuxer, new QueuedMuxer.Listener() {
                @Override
                public void onDetermineOutputFormat(MediaFormat videoMediaFormat, MediaFormat audioMediaFormat) {
                    YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter onDetermineOutputFormat begin");
                    MediaFormatValidator.validateVideoOutputFormat(videoMediaFormat);
                    if (mFilterContext.getRecordConfig().getEnableAudioRecord()) {
                        MediaFormatValidator.validateAudioOutputFormat(audioMediaFormat);
                    }
                    YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter onDetermineOutputFormat end");
                }
            }, recordConfig.getEnableAudioRecord() /*TODO not work 20170105*/);

            mQueuedMuxer.setSingleStreamOfEndMode(mSingleStreamStopTriggerMode);
            mQueuedMuxer.setVideoAudioSync(mSingleStreamStopTriggerMode);
            mQueuedMuxer.setEnableAudioRecord(recordConfig.getEnableAudioRecord());
            mQueuedMuxer.setRecordListener(recordConfig.getRecordListener());
        } catch (IOException e) {
            e.printStackTrace();
            mInited.set(false);
            YYLog.error(this, Constant.MEDIACODE_MUXER+"[exception] MediaMuxerFilter init exception: "+e.toString());
        }

        YYLog.info(this, Constant.MEDIACODE_MUXER+"MediaMuxerFilter init success!!");
        mInited.set(true);

        StateMonitor.instance().NotifyMediaMuxerStart(MediaConst.MEDIA_TYPE_VIDEO);
        StateMonitor.instance().NotifyMediaMuxerStart(MediaConst.MEDIA_TYPE_AUDIO);
    }
}
