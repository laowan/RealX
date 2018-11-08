package com.ycloud.svplayer.surface;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.view.Surface;

import com.ycloud.mediacodec.compat.MediaCodecBufferCompatWrapper;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.svplayer.MediaExtractor;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import java.io.IOException;

/**
 * Created by DZHJ on 2017/7/26.
 * 该类输出textureId给外部类使用，
 * 目前仅用于用于转场
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediacodecVideoDecoderForTransition {

    private static final String TAG = MediacodecVideoDecoderForTransition.class.getSimpleName();

    private MediaExtractor mVideoExtractor;
    private MediaCodec mVideoDecoder;
    private int mVideoTrackIndex;
    private Surface mSurface;
    private int mSurfaceTextureId;
    private SurfaceTexture mSurfaceTexture;
    MediaCodec.BufferInfo mBufferInfo;
    private boolean mIsExtractorEOS;
    private boolean mIsDecoderEOS;
    private long mCurrentPtsUs = -1;
    MediaCodecBufferCompatWrapper mBufferWrapper;
    private String mVideoPath;


    public MediacodecVideoDecoderForTransition(String videPath) throws IOException {
        mVideoPath = videPath;
        mVideoExtractor = new MediaExtractor(MediaConst.MEDIA_EXTRACTOR_TYPE_VIDEO);
        mVideoExtractor.setDataSource(videPath);

        mVideoTrackIndex = getTrackIndex(mVideoExtractor, "video/");
        mVideoExtractor.selectTrack(mVideoTrackIndex);
        mSurfaceTextureId = OpenGlUtils.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        mSurfaceTexture = new SurfaceTexture(mSurfaceTextureId);
        mSurface = new Surface(mSurfaceTexture);
        MediaFormat format = mVideoExtractor.getTrackFormat(mVideoTrackIndex);
        mVideoDecoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        mVideoDecoder.configure(format, mSurface, null, 0);
        mVideoDecoder.start();
        mBufferWrapper = new MediaCodecBufferCompatWrapper(mVideoDecoder);
        mBufferInfo = new MediaCodec.BufferInfo();
        decodeFrame();

    }

    /**
     * 该函数用于精确seek到指定pts，因此可能有些耗时
     * @param seekTimeUs
     * @throws IOException
     */
    public boolean seekTo(long seekTimeUs) throws IOException {

        long presentationTimeMs;
        long seekTargetTimeMs = seekTimeUs / 1000;
        int frameSkipCount = 0;

        mVideoDecoder.flush();
        mVideoExtractor.seekTo(seekTimeUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        presentationTimeMs  = decodeFrame()/1000;

        while (presentationTimeMs < seekTargetTimeMs) {
            if (frameSkipCount == 0) {
                YYLog.info(TAG, "skipping frames...");
            }
            frameSkipCount++;
            presentationTimeMs = decodeFrame() / 1000;
            if(mIsDecoderEOS == true) {
                break;
            }
        }

        YYLog.info(TAG, "seeking finished, skipped " + frameSkipCount + " frames");

        if (presentationTimeMs == seekTargetTimeMs) {
            YYLog.info(TAG, "exact seek match!");
        }

        if(presentationTimeMs>=seekTargetTimeMs){
            return  true;
        }else {
            return false;
        }

    }

    private int getTrackIndex(MediaExtractor mediaExtractor, String mimeType) {
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            YYLog.info(TAG, format.toString());
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(mimeType)) {
                return i;
            }
        }
        return -1;
    }

    private void drainExtractor(long timeoutUs) {
        do {
            int trackIndex = mVideoExtractor.getSampleTrackIndex();
            if (trackIndex >= 0 && trackIndex != mVideoTrackIndex) {
                continue;
            }
            int result = mVideoDecoder.dequeueInputBuffer(timeoutUs);
            if (result < 0)
                break;
            if (trackIndex < 0) {
                mVideoDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                mIsExtractorEOS = true;
                break;
            }
            int sampleSize = mVideoExtractor.readSampleData(mBufferWrapper.getInputBuffer(result), 0);
            boolean isKeyFrame = (mVideoExtractor.getSampleFlags() & android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
            mVideoDecoder.queueInputBuffer(result, 0, sampleSize, mVideoExtractor.getSampleTime(), isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0);
            mVideoExtractor.advance();
        } while (mIsExtractorEOS == false);
    }

    public int getTextureId() {
        return mSurfaceTextureId;
    }

    public long getCurrentTimeUs() {
        return mCurrentPtsUs;
    }

    public void getTransformMatrix(float[] maxtrix) {
        mSurfaceTexture.getTransformMatrix(maxtrix);
    }

    public void release() {
        if (mVideoExtractor != null) {
            mVideoExtractor.release();
            mVideoExtractor = null;
        }
        if (mVideoDecoder != null) {
            mVideoDecoder.stop();
            mVideoDecoder.release();
            mVideoDecoder = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }

        mBufferWrapper = null;
    }

    public long decodeFrame() {
        int result = -1;
        while (result < 0) {
            result = mVideoDecoder.dequeueOutputBuffer(mBufferInfo, 0);
            if (result > 0) {

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mIsDecoderEOS = true;
                    mBufferInfo.size = 0;
                }
                boolean doRender = (mBufferInfo.size > 0);

                mVideoDecoder.releaseOutputBuffer(result, doRender);
                if (doRender) {
                    mSurfaceTexture.updateTexImage();
                    mCurrentPtsUs = mBufferInfo.presentationTimeUs;
                }
            } else {
                switch (result) {
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        mBufferWrapper = new MediaCodecBufferCompatWrapper(mVideoDecoder);
                        break;
                }
            }
            drainExtractor(0);
        }

        return mCurrentPtsUs;
    }

    public void resetPosition() throws IOException {

        mIsExtractorEOS =false;
        mIsDecoderEOS =false;
        mCurrentPtsUs = -1;

        mVideoDecoder.flush();
        mVideoExtractor.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        decodeFrame();
    }

    public boolean isDecoderEOS(){
        return  mIsDecoderEOS;
    }
}
