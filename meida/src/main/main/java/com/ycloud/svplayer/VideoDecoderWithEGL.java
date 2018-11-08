package com.ycloud.svplayer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.svplayer.surface.IPlayerGLManager;
import com.ycloud.svplayer.surface.PlayerGLManager;
import com.ycloud.utils.YYLog;

import java.io.IOException;

/**
 * Created by dzhj on 17/7/18.
 */

/**
 * 用于编辑页播放器贴纸，滤镜等的实时预览
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class VideoDecoderWithEGL extends MediaDecoder implements IVideoDecoder {

    private IPlayerGLManager mPlayerGLManager;
    PlayerGLManager.SurfaceWrapper mSurfaceWrapper;

    public VideoDecoderWithEGL(MediaExtractor extractor, int trackIndex, IPlayerGLManager playerGLManager)
            throws IOException {
        super(extractor,trackIndex,CodecType.VIDEO);
        mPlayerGLManager = playerGLManager;
        mSurfaceWrapper = mPlayerGLManager.getInputSurface();
        reinitCodec();
    }


    @Override
    protected void configureCodec(ICodec codec, MediaFormat format) {
        YYLog.info(TAG,"configureCodec:" + format.toString());

        int width = getVideoWidth();
        int height =getVideoHeight();
        int rotation =getVideoRotation();

        // Swap width/height to report correct dimensions of rotated portrait video (rotated by 90 or 270 degrees)
        if (rotation ==90 || rotation == 270) {
            int temp = width;
            width = height;
            height = temp;
        }

        mSurfaceWrapper.mSurfaceTexture.setDefaultBufferSize(width,height);

        codec.configure(format, mSurfaceWrapper.mSurface, null, 0);
    }

    public int getVideoWidth() {
        MediaFormat format = getFormat();
        return format != null ? (int)(format.getInteger(MediaFormat.KEY_HEIGHT)
                * format.getFloat(MediaExtractor.MEDIA_FORMAT_EXTENSION_KEY_DAR)) : 0;
    }

    public int getVideoHeight() {
        MediaFormat format = getFormat();
        return format != null ? format.getInteger(MediaFormat.KEY_HEIGHT) : 0;
    }

    /**
     * Returns the rotation of the video in degree.
     * Only works on API21+, else it always returns 0.
     * @return the rotation of the video in degrees
     */
    public int getVideoRotation() {
        try {
            MediaFormat format = getFormat();
            // rotation-degrees is available from API21, officially supported from API23 (KEY_ROTATION)
            return format != null && format.containsKey("rotation-degrees") ?
                format.getInteger("rotation-degrees") : 0;
        } catch ( Exception e) {
            YYLog.error(TAG,"get rotation-degrees fail");
            return  0;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void renderFrame(FrameInfo frameInfo) {
        //YYLog.info(TAG, "renderFrame: " + frameInfo);
        releaseFrame(frameInfo, true);
        mPlayerGLManager.renderFrame(frameInfo,mSurfaceWrapper.mSurfaceIndex);
        releaseFrameInfo(frameInfo);
    }

    public void processImages(String imageBasePath, int imageRate) {
        if (mPlayerGLManager != null) {
            YYLog.info(TAG, "processImages imageBasePath=" + imageBasePath + " imageRate=" + imageRate);
            mPlayerGLManager.processImages(imageBasePath, imageRate);
        }
        else {
            YYLog.info(TAG, "processImages mPlayerGLManager is null");
        }
    }

    /**
     * Releases all data belonging to a frame and optionally renders it to the configured surface.
     */
    public void releaseFrame(FrameInfo frameInfo, boolean render) {
        getCodec().releaseOutputBuffer(frameInfo.bufferIndex, render); // render picture
//        releaseFrameInfo(frameInfo);
    }

    @Override
    protected FrameInfo seekTo(MediaPlayer.SeekMode seekMode, long seekTargetTimeUs,
                               MediaExtractor extractor, ICodec codec) throws IOException {
        /* Android API compatibility:
         * Use millisecond precision to stay compatible with VideoView API that works
         * in millisecond precision only. Else, exact seek matches are missed if frames
         * are positioned at fractions of a millisecond. */
        long presentationTimeMs = -1;
        long seekTargetTimeMs = seekTargetTimeUs / 1000;

        FrameInfo frameInfo = super.seekTo(seekMode, seekTargetTimeUs, extractor, codec);

        if(frameInfo == null) {
            return null;
        }

        if (seekMode == MediaPlayer.SeekMode.FAST_TO_CLOSEST_SYNC
                || seekMode == MediaPlayer.SeekMode.FAST_TO_PREVIOUS_SYNC
                || seekMode == MediaPlayer.SeekMode.FAST_TO_NEXT_SYNC) {
            YYLog.info(TAG, "fast seek to " + seekTargetTimeUs + " arrived at " + frameInfo.presentationTimeUs);
        }else if (seekMode == MediaPlayer.SeekMode.PRECISE || seekMode == MediaPlayer.SeekMode.EXACT) {
            /* NOTE
             * This code seeks one frame too far, except if the seek time equals the
             * frame PTS:
             * (F1.....)(F2.....)(F3.....) ... (Fn.....)
             * A frame is shown for an interval, e.g. (1/fps seconds). Now if the seek
             * target time is somewhere in frame 2's interval, we end up with frame 3
             * because we need to decode it to know if the seek target time lies in
             * frame 2's interval (because we don't know the frame rate of the video,
             * and neither if it's a fixed frame rate or a variable one - even when
             * deriving it from the PTS series we cannot be sure about it). This means
             * we always end up one frame too far, because the MediaCodec does not allow
             * to go back, except when starting at a sync frame.
             *
             * Solution for fixed frame rate could be to subtract the frame interval
             * time (1/fps secs) from the seek target time.
             *
             * Solution for variable frame rate and unknown frame rate: go back to sync
             * frame and re-seek to the now known exact PTS of the desired frame.
             * See EXACT mode handling below.
             */
            int frameSkipCount = 0;
            long lastPTS = -1;

            presentationTimeMs = frameInfo.presentationTimeUs / 1000;

            while (presentationTimeMs < seekTargetTimeMs) {
                if (frameSkipCount == 0) {
                    YYLog.info(TAG, "skipping frames...");
                }
                frameSkipCount++;

                if (isOutputEos()) {
                    /* When the end of stream is reached while seeking, the seek target
                     * time is set to the last frame's PTS, else the seek skips the last
                     * frame which then does not get rendered, and it might end up in a
                     * loop trying to reach the unreachable target time. */
                    seekTargetTimeUs = frameInfo.presentationTimeUs;
                    seekTargetTimeMs = seekTargetTimeUs / 1000;
                }

                if (frameInfo.endOfStream) {
                    YYLog.info(TAG, "end of stream reached, seeking to last frame");
                    releaseFrame(frameInfo, false);
                    if(lastPTS !=-1) {
                        return seekTo(seekMode, lastPTS, extractor, codec);
                    } else {
                        return seekTo(seekMode, seekTargetTimeUs, extractor, codec);
                    }
                }

                lastPTS = frameInfo.presentationTimeUs;
                releaseFrame(frameInfo, false);

                frameInfo = decodeFrame(true, true);
                presentationTimeMs = frameInfo.presentationTimeUs / 1000;
            }

            YYLog.info(TAG, "frame new position:         " + frameInfo.presentationTimeUs);
            YYLog.info(TAG, "seeking finished, skipped " + frameSkipCount + " frames");

            if (seekMode == MediaPlayer.SeekMode.EXACT && presentationTimeMs > seekTargetTimeMs) {
                if (frameSkipCount == 0) {
                    // In a single stream, the initiating seek always seeks before or directly
                    // to the requested frame, and this case never happens. With DASH, when the seek
                    // target is very near a segment border, it can happen that a wrong segment
                    // (the following one) is determined as target seek segment, which means the
                    // target of the initiating seek is too far, and we cannot go back either because
                    // it is the first frame of the segment
                    // TODO avoid this case by fixing DASH seek (fix segment calculation or reissue
                    // seek to previous segment when this case is detected)
                    Log.w(TAG, "this should never happen");
                } else {
                    /* If the current frame's PTS it after the seek target time, we're
                     * one frame too far into the stream. This is because we do not know
                     * the frame rate of the video and therefore can't decide for a frame
                     * if its interval covers the seek target time of if there's already
                     * another frame coming. We know after the next frame has been
                     * decoded though if we're too far into the stream, and if so, and if
                     * EXACT mode is desired, we need to take the previous frame's PTS
                     * and repeat the seek with that PTS to arrive at the desired frame.
                     */
                    YYLog.info(TAG, "exact seek: repeat seek for previous frame at " + lastPTS);
                    releaseFrame(frameInfo, false);
                    return seekTo(seekMode, lastPTS, extractor, codec);
                }
            }
        }

        if (presentationTimeMs == seekTargetTimeMs) {
            YYLog.info(TAG, "exact seek match!");
        }

        frameInfo.needDrawImage = true;

        return frameInfo;
    }

    public void setVideoFilter(VideoFilter videoFilter) {
        if(mPlayerGLManager != null) {
            mPlayerGLManager.setVideoFilter(videoFilter);
        }
    }


    @Override
    public void decodeFrame() {
        decodeFrame(false,true);
    }

    @Override
    public long getCurrentTimeUs() {
        return mCurrentFrameInfo.presentationTimeUs;
    }

    @Override
    public void release() {
        super.release();
        if(mPlayerGLManager != null) {
            mPlayerGLManager.returnSurface(mSurfaceWrapper.mSurfaceIndex);
        }
    }
}
