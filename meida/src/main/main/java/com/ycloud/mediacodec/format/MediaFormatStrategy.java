package com.ycloud.mediacodec.format;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;
import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.mediacodec.VideoEncoderConfig;

/**
 * Created by DZHJ on 2017/3/15.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaFormatStrategy implements IMediaFormatStrategy {

    private  int mVideoBitrate;
    private  int mAudioBitrate;
    private int mIFrameInternal=30;
    private int mFrameRate =30;
    VideoEncoderConfig mVideoEncoderConfig;


    public MediaFormatStrategy() {
        mVideoEncoderConfig= new VideoEncoderConfig();
        mVideoEncoderConfig.mEncodeParameter = RecordDynamicParam.getInstance().getHardEncodeParameters();
    }
    @Override
    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
        MediaFormat format = YYMediaFormatStrategy.getVideoFormatForEncoder(mVideoEncoderConfig,"video/avc");
/*        MediaFormat format = MediaFormat.createVideoFormat("video/avc", mVideoWidth, mVideoHeight);
        // From Nexus 4 Camera in 720p
        format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInternal);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);*/
        return format;
    }

    @Override
    public MediaFormat createAudioOutputFormat(MediaFormat inputFormat) {
/*        if (mAudioBitrate == AUDIO_BITRATE_AS_IS || mAudioChannels == AUDIO_CHANNELS_AS_IS) return null;

        // Use original sample rate, as resampling is not supported yet.
        final MediaFormat format = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC,
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), mAudioChannels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);*/
        return inputFormat;
    }

    @Override
    public void setBitrate(int bitrate) {
        mVideoEncoderConfig.setBitRate(bitrate);

    }

    @Override
    public void setDemension(int width, int height) {
        mVideoEncoderConfig.setEncodeSize(width,height);
    }

    @Override
    public void setFrameRate(int frameRate) {
        mVideoEncoderConfig.setFrameRate(frameRate);
    }

    @Override
    public void setIFrameInternal(int iFrameInternal) {
    }
}
