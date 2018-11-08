package com.ycloud.mediafilters;

import android.media.MediaFormat;

import com.ycloud.api.common.SampleType;
import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.mediacodec.VideoDecodeType;
import com.ycloud.mediaprocess.MediaExportSession;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Administrator on 2018/1/2.
 */

public class VideoDecoderGroupFilter  extends AbstractYYMediaFilter
        implements MediaBufferQueue.OutputCallback<YYMediaSample>
{
    MediaFilterContext          mVideoFilterContext;
    MediaFormat                 mVideoFormat = null;
    AbstractMediaDecoderFilter  mVideoDecoder = null;
    OutputFilter                mOutputFilter = new OutputFilter();
    Yuv2RgbAsyncFilter          mYuv2RgbBufferFilter = null;
    MediaBufferQueue            mInputMediaBufferQueue = null;
    MediaExportSession          mExportSession = null;

    private int  mDecoderErrorCnt = 0;

    public static class OutputFilter extends YYMediaFilter
    {
        private long mLastDecodeOuputPts = -1;
        private int  mLastOutputSampleId = -1;
        private AtomicInteger mOutputIds = new AtomicInteger(0);

        @Override
        public boolean processMediaSample(YYMediaSample sample, Object upstream) {
            //assigne simple id.
            int sampleId = mOutputIds.getAndAdd(1);
            if(sampleId < mLastOutputSampleId) {
                //filter...
                YYLog.info(this, "[discard] video pts: " + sample.mYYPtsMillions + " mLastOutputSampleId:" + mLastOutputSampleId);
                return true;
            } else {
                mLastOutputSampleId = sampleId;
                sample.mSampleId = sampleId;

                //YYLog.debug(this, "[decode] video pts: " + sample.mYYPtsMillions + " mLastOutputSampleId:" + mLastOutputSampleId);
                if(sample.mEndOfStream) {
                    YYLog.info(this, "[decode] frameCnt=" + mLastOutputSampleId + " pts: " + mLastDecodeOuputPts);
                } else {
                    mLastDecodeOuputPts = sample.mYYPtsMillions;
                }

                return super.processMediaSample(sample, upstream);
            }
        }

        public void reset() {
            mOutputIds.set(0);
        }
    }

    public VideoDecoderGroupFilter(MediaFilterContext  videoContext, MediaExportSession exportSession) {
        //TODO[lsh] hardcode hardware decoder first.
        mVideoFilterContext = videoContext;
        mExportSession = exportSession;
    }

    public void setInputBufferQueue(MediaBufferQueue queue) {
        mInputMediaBufferQueue = queue;
        if(mVideoDecoder != null && mVideoDecoder instanceof  H264HwDecoderFilter) {
            ((H264HwDecoderFilter) mVideoDecoder).setInputBufferQueue(queue);
        } else if(mVideoDecoder != null && mVideoDecoder instanceof FfmpegDecoderFilter) {
            ((FfmpegDecoderFilter)mVideoDecoder).setInputBufferQueue(mInputMediaBufferQueue);
        }

        if(queue != null)
            queue.setOutputCallback(this);
    }

    private void initHardDecoder(MediaFormat mediaFormat) {
        mVideoDecoder = new H264HwDecoderFilter(mVideoFilterContext, true);
        ((H264HwDecoderFilter)mVideoDecoder).setInputBufferQueue(mInputMediaBufferQueue);
        ((H264HwDecoderFilter)mVideoDecoder).setVideoDecoderGroup(this);

        //create a surface and  surface texture.
        YYLog.info(this, "initHardDecoder");
        mVideoDecoder.initDecoder(mVideoFormat);
        mVideoDecoder.addDownStream(mOutputFilter);
    }

    private void initSwDecoder(MediaFormat mediaFormat) {
        mVideoDecoder = new FfmpegDecoderFilter(mVideoFilterContext);
        ((FfmpegDecoderFilter)mVideoDecoder).setInputBufferQueue(mInputMediaBufferQueue);

        mYuv2RgbBufferFilter = new Yuv2RgbAsyncFilter(mVideoFilterContext, mediaFormat);

        //mVideoDecoder.addDownStream(mYuv2RgbBufferFilter.addDownStream(mOutputFilter));
        ((FfmpegDecoderFilter)mVideoDecoder).setOutputBufferQueue(mYuv2RgbBufferFilter.getInputSampleQueue());
        ((FfmpegDecoderFilter)mVideoDecoder).setOutputFilter(mYuv2RgbBufferFilter);
        mYuv2RgbBufferFilter.addDownStream(mOutputFilter);

        YYLog.info(this, "initSwDecoder");
        mVideoDecoder.initDecoder(mVideoFormat);
    }

    private void initDecoder(MediaFormat mediaFormat, VideoDecodeType decodeType) {
        mVideoFormat = mediaFormat;

        if(decodeType == VideoDecodeType.HARD_DECODE) {
            initHardDecoder(mediaFormat);
        } else if(decodeType == VideoDecodeType.FFMPEG_DECODE) {
           initSwDecoder(mediaFormat);
        }
    }

    public void onDecodeError(VideoDecodeType decodeType) {
        YYLog.error(this, "[decoder] onDecodeError: " + decodeType);
        if(decodeType == VideoDecodeType.HARD_DECODE) {
            //switch the ffmpeg.
            if(++mDecoderErrorCnt < 2) {
                RecordDynamicParam.getInstance().setExportSwDecoder(true);
            }

            //post a seek video message to input.
            if(mExportSession != null) {
                mExportSession.restartVideoStream();
            }
        }
    }

    //should thread safe with outputMediaSample

    private void doStopDecode() {
        if (mVideoDecoder != null) {
            mVideoDecoder.releaseDecoder();

            if(mVideoDecoder instanceof FfmpegDecoderFilter) {
                mYuv2RgbBufferFilter.quit();
                mYuv2RgbBufferFilter.removeAllDownStream();
                mYuv2RgbBufferFilter = null;
            }

            mVideoDecoder.removeAllDownStream();
            this.removeDownStream(mVideoDecoder);
            mVideoDecoder = null;
            mVideoFormat = null;
        }
    }

    public void stopDecode() {
        YYLog.info(this, "stopDecode==");
        doStopDecode();
        mExportSession = null;
    }

    public AbstractYYMediaFilter getOutputFilter() {
        return mOutputFilter;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        return false;
    }

    @Override
    public void outputMediaSample(YYMediaSample sample) {
        if(sample == null || sample.mSampleType != SampleType.VIDEO)
            return;

        //begin of the stream.
        if(sample.mSampleId == 0) {
            doStopDecode();
            mOutputFilter.reset();
        }

        if(mVideoFormat == null && sample.mMediaFormat != null) {
            //hardcode...
            initDecoder(sample.mMediaFormat, mVideoFilterContext.getVideoDecodeType());
            if(sample.mBufferSize == 0) {
                mInputMediaBufferQueue.remove();
                return;
            }
        }

        mVideoFilterContext.getFilterFlowController().onVideoDecodeInput();
        if(mVideoFormat != null && mVideoDecoder != null) {
            mVideoDecoder.decodeFrame();
        }
    }
}
