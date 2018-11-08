package com.ycloud.svplayer;

import com.ycloud.api.common.TransitionInfo;
import com.ycloud.player.TransitionPts;
import com.ycloud.utils.TransitionTimeUtils;
import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class Decoders {

    private static final String TAG = Decoders.class.getSimpleName();

    private List<MediaDecoder> mDecoders;
    private MediaDecoder mVideoDecoder;
    private AudioDecoder mAudioDecoder;
    private long mAudioMinPTS;
    private long mVideoMinPTS;

    private MediaDecoder mVideoDecoderForTransition;

    public Decoders() {
        mDecoders = new ArrayList<>();
    }

    public void addDecoder(MediaDecoder decoder) {
        mDecoders.add(decoder);

        if (decoder instanceof IVideoDecoder) {
            mVideoDecoder = decoder;
        } else if (decoder instanceof AudioDecoder) {
            mAudioDecoder = (AudioDecoder) decoder;
        }
    }

    public void setEnableRotate(boolean enable) {
        if (mVideoDecoder != null) {
            mVideoDecoder.setEnableRotate(enable);
        }
    }

    public void addVideoDecoderForTransition(MediaDecoder videoDecoderForTransition){
        mVideoDecoderForTransition = videoDecoderForTransition;
    }

    public void releaseVideoDecoderForTransition(){
        if(mVideoDecoderForTransition != null) {
            mVideoDecoderForTransition.release();
            mVideoDecoderForTransition = null;
        }
    }


    public List<MediaDecoder> getDecoders() {
        return mDecoders;
    }

    public MediaDecoder getVideoDecoder() {
        return mVideoDecoder;
    }

    public MediaDecoder getVideoDecoderForTransition(){
        return mVideoDecoderForTransition;
    }

    public AudioDecoder getAudioDecoder() {
        return mAudioDecoder;
    }

    /**
     * Runs the audio/video decoder loop, optionally until a new frame is available.
     * The returned frameInfo object keeps metadata of the decoded frame.
     *
     * @param force force decoding in a loop until a frame becomes available or the EOS is reached
     * @return a VideoFrameInfo object holding metadata of a decoded video frame or NULL if no frame has been decoded
     */
    public FrameInfo decodeFrame(boolean force) {
        //YYLog.info(TAG, "decodeFrame");
        boolean outputEos = false;

        while(!outputEos) {
            int outputEosCount = 0;
            FrameInfo fi;
            FrameInfo vfi = null;

            for (MediaDecoder decoder : mDecoders) {
                while((fi = decoder.dequeueDecodedFrame()) != null) {

                    if(decoder == mVideoDecoder) {
                        vfi = fi;
                        break;
                    } else {
                        decoder.renderFrame(fi);
                    }
                }

                while (decoder.queueSampleToCodec(false)) {}

                if(decoder.isOutputEos()) {
                    outputEosCount++;
                }
            }

            if(vfi != null) {
                // If a video frame has been decoded, return it
                return vfi;
            }

            if(!force) {
                // If we have not decoded a video frame and we're not forcing decoding until a frame
                // becomes available, return null.
                return null;
            }

            outputEos = (outputEosCount == mDecoders.size());
        }

        YYLog.info(TAG, "EOS NULL");
        return null; // EOS already reached, no video frame left to return
    }

    public void decodeAudioFrame() {
        if(mAudioDecoder != null) {
            FrameInfo fi;
            while ((fi = mAudioDecoder.dequeueDecodedFrame()) != null) {
                mAudioDecoder.renderFrame(fi);
            }

            while (mAudioDecoder.queueSampleToCodec(false)) {
            }
        }
    }

    /**
     * Releases all decoders. This must be called to free decoder resources when this object is no longer in use.
     */
    public void release() {
        YYLog.info(TAG, "Decoders.release begin!");
        for (MediaDecoder decoder : mDecoders) {
            // Catch decoder.release() exceptions to avoid breaking the release loop on the first
            // exception and leaking unreleased decoders.
            try {
                decoder.release();
            } catch (Exception e) {
                YYLog.error(TAG, "[exception] release failed"+e.toString());
            }
        }

        mDecoders.clear();

        if(mVideoDecoderForTransition != null) {
            try {
                mVideoDecoderForTransition.release();
            } catch ( Exception e) {
                YYLog.error(TAG, "[exception]mVideoDecoderForTransition release failed"+e.toString());
            }
        }

        YYLog.info(TAG, "Decoders.release done!!");
    }

    public FrameInfo seekTo(MediaPlayer.SeekMode seekMode, long seekTargetTimeUs) throws IOException {
        FrameInfo vfi = null;
        for (MediaDecoder decoder : mDecoders) {
            if(decoder instanceof AudioDecoder) {
                decoder.seekTo(seekMode, seekTargetTimeUs + mAudioMinPTS);
            } else if(decoder instanceof IVideoDecoder){
                if (seekTargetTimeUs != 0) {
                    seekTargetTimeUs += mVideoMinPTS;
                }
                vfi =  decoder.seekTo(seekMode, seekTargetTimeUs);
            } else {
                decoder.seekTo(seekMode, seekTargetTimeUs);
            }
        }
        return  vfi;
    }

    public void renderFrames() {
        if(mVideoDecoderForTransition != null) {
            mVideoDecoderForTransition.renderFrame();
        }
        for (MediaDecoder decoder : mDecoders) {
            decoder.renderFrame();
        }
    }

    public void dismissFrames() {
        for (MediaDecoder decoder : mDecoders) {
            decoder.dismissFrame();
        }
    }

    public long getCurrentDecodingPTS() {
        long minPTS = Long.MAX_VALUE;
        for (MediaDecoder decoder : mDecoders) {
            long pts = decoder.getCurrentDecodingPTS();
            if(pts != MediaDecoder.PTS_NONE && minPTS > pts) {
                minPTS = pts;
            }
        }
        return minPTS;
    }
    public long getCurrentVideoDecodingPTS() {
        long videoPTS = 0;
        for (MediaDecoder decoder : mDecoders) {
            long pts = decoder.getCurrentDecodingPTS();
            if(decoder instanceof  VideoDecoderWithEGL) {
                videoPTS = pts;
                break;
            }
        }
        return videoPTS;
    }


    public boolean isEOS() {
        //return getCurrentDecodingPTS() == MediaDecoder.PTS_EOS;
        int eosCount = 0;
        for (MediaDecoder decoder : mDecoders) {
            if(decoder.isOutputEos()) {
                eosCount++;
            }
        }
        return eosCount == mDecoders.size();
    }

    public void setMinPts(long audioMinPTS,long videoMinPTS){
        mAudioMinPTS = audioMinPTS;
        mVideoMinPTS = videoMinPTS;
    }


    public void reinitCodec(MediaPlayer.VideoPlayInfo currentPlayInfo) {
        for (MediaDecoder decoder : mDecoders) {
            if (decoder instanceof AudioDecoder) {
                decoder.reinitCodec(currentPlayInfo.mAudioExtractor,currentPlayInfo.mAudioTrackIndex);
            } else if (decoder instanceof IVideoDecoder) {
                decoder.reinitCodec(currentPlayInfo.mVideoExtractor,currentPlayInfo.mVideoTrackIndex);
            }
        }
    }

    public void swapVideoDecoder(MediaPlayer.VideoPlayInfo nextVideoPlayInfo) {
        if (mVideoDecoderForTransition != null) {
            MediaDecoder tempVideoDecoder = mVideoDecoderForTransition;
            mDecoders.remove(mVideoDecoder);
            mVideoDecoderForTransition = mVideoDecoder;
            mVideoDecoder = tempVideoDecoder;
            mDecoders.add(mVideoDecoder);


            if (nextVideoPlayInfo != null) {
                mVideoDecoderForTransition.reinitCodec(nextVideoPlayInfo.mVideoExtractor, nextVideoPlayInfo.mVideoTrackIndex);
            } else {
                mVideoDecoderForTransition.release();
                mVideoDecoderForTransition = null;
            }
        }
    }

    public void renderVideoFrame(FrameInfo frameInfo,List<TransitionInfo> mTransitionList){
        if(mTransitionList != null && mVideoDecoderForTransition != null) {
            TransitionPts transitionPts = TransitionTimeUtils.unityPtsToPts(frameInfo.unityPtsUs, mTransitionList);
            if(transitionPts.nextPts != -1) {
                while (mVideoDecoderForTransition.getCurrentDecodingPTS() < transitionPts.nextPts) {
                    mVideoDecoderForTransition.decodeFrame(false, true);
                    mVideoDecoderForTransition.renderFrame();
                }
                frameInfo.needDrawImage =true;
                frameInfo.drawWithTwoSurface = true;
                mVideoDecoder.renderFrame(frameInfo);
            } else {
                frameInfo.needDrawImage = true;
                frameInfo.drawWithTwoSurface = false;
                mVideoDecoder.renderFrame(frameInfo);
            }
        } else {
            frameInfo.needDrawImage = true;
            frameInfo.drawWithTwoSurface = false;
            mVideoDecoder.renderFrame(frameInfo);
        }
    }
}
