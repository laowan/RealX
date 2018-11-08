/*
 * Copyright 2016 Mario Guggenberger <mg@protyposis.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.svplayer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;

import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

/**
 * Created by Mario on 13.09.2015.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public abstract class MediaDecoder {
    protected String TAG = MediaDecoder.class.getSimpleName();

    public static final long PTS_NONE = Long.MIN_VALUE;
    public static final long PTS_EOS = Long.MAX_VALUE;

    private static final long TIMEOUT_US = 0;
    public static final int INDEX_NONE = -1;

    private MediaExtractor mExtractor;
    private int mTrackIndex;
    private MediaFormat mFormat;
    private ICodec mCodec;
    private boolean mEnableRotate = false;

    public enum CodecType{
        VIDEO,AUDIO
    }

    CodecBufferCompatWrapper mCodecBufferCompatWrapper;
    private MediaCodec.BufferInfo mBufferInfo;
    private boolean mInputEos;
    private boolean mOutputEos;

    /* Flag notifying that the representation has changed in the extractor and needs to be passed
     * to the decoder. This transition state is only needed in playback, not when seeking. */
    private boolean mRepresentationChanging;
    /* Flag notifying that the decoder has changed to a new representation, post-actions need to
     * be carried out. */
    private boolean mRepresentationChanged;

    private long mDecodingPTS;

    protected FrameInfo mCurrentFrameInfo;
    private FrameInfoPool mFrameInfoPool;

    //保存上一次extract的sample time
    protected long mLastExtractorSampleTime;

    public MediaDecoder(MediaExtractor extractor, int trackIndex,CodecType codecType)
            throws IllegalStateException, IOException
    {
        // Apply the name of the concrete class that extends this base class to the logging tag
        // THis is really not a nice solution but there's no better one: http://stackoverflow.com/a/936724
        YYLog.info(TAG, "MediaDecoder construct begin:trackIndex=" + trackIndex + ",codecType=" + codecType);

        if(extractor == null || trackIndex == INDEX_NONE) {
            throw new IllegalArgumentException("no track specified");
        }

        mExtractor = extractor;
        mTrackIndex = trackIndex;
        mFormat = extractor.getTrackFormat(mTrackIndex);

        mCodec = DecoderFactory.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME),codecType);
        //MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));

        mDecodingPTS = PTS_NONE;
        mFrameInfoPool = new FrameInfoPool();

        mLastExtractorSampleTime = -2;
        YYLog.info(TAG, "MediaDecoder construct end");
    }

    public void setEnableRotate(boolean enable) {
        mEnableRotate = enable;
    }

    public  void reinitCodec(MediaExtractor extractor,int trackIndex){
        if(extractor == null || trackIndex == INDEX_NONE) {
            throw new IllegalArgumentException("no track specified");
        }

        mExtractor = extractor;
        mTrackIndex = trackIndex;
        reinitCodec();
    }

    protected final MediaFormat getFormat() {
        return mFormat;
    }

    protected final ICodec getCodec() {
        return mCodec;
    }

    protected final boolean isInputEos() {
        return mInputEos;
    }

    protected final boolean isOutputEos() {
        return mOutputEos;
    }

    /**
     * Starts or restarts the codec with a new format, e.g. after a representation change.
     */
    protected final void reinitCodec() {
        try {
            long t1 = SystemClock.elapsedRealtime();

            // Get new format and restart codec with this format
            YYLog.info(TAG, "reinitCodec getTrackFormat:" + mTrackIndex);
            mFormat = mExtractor.getTrackFormat(mTrackIndex);

            mCodec.stop();
            configureCodec(mCodec, mFormat);
            mCodec.start(); // TODO speedup, but how? this takes a long time and introduces lags when switching DASH representations (AVC codec)
            mCodecBufferCompatWrapper = new CodecBufferCompatWrapper(mCodec);
            mBufferInfo = new MediaCodec.BufferInfo();
            mInputEos = false;
            mOutputEos = false;
            YYLog.info(TAG, "reinitCodec " + (SystemClock.elapsedRealtime() - t1) + "ms");
        } catch (IllegalArgumentException e) {
            mCodec.release(); // Release failed codec to not leak a codec thread (MediaCodec_looper)
            YYLog.error(TAG, "reinitCodec: invalid surface or format:" + e.getMessage());
            throw e;
        } catch (IllegalStateException e) {
            mCodec.release(); // Release failed codec to not leak a codec thread (MediaCodec_looper)
            YYLog.error(TAG, "reinitCodec: illegal state:" + e.getMessage());
            throw e;
        }
    }

    /**
     * Configures the codec during initialization. Should be overwritten by subclasses that require
     * a more specific configuration.
     *
     * @param codec the codec to configure
     * @param format the format to configure the codec with
     */
    protected void configureCodec(ICodec codec, MediaFormat format) {
        codec.configure(format, null, null, 0);
    }

    /**
     * Skips to the next sample of this decoder's track by skipping all samples belonging to other decoders.
     */
    public final void skipToNextSample() {
        int trackIndex;
        while ((trackIndex = mExtractor.getSampleTrackIndex()) != -1 && trackIndex != mTrackIndex && !mInputEos) {
            mExtractor.advance();
        }
    }

    /**
     * Checks any constraints if it is a good idea to decode another frame. Returns true by default,
     * and is meant to be overwritten by subclasses with special behavior, e.g. an audio track might
     * limit filling of the playback buffer.
     *
     * @return value telling if another frame should be decoded
     */
    protected boolean shouldDecodeAnotherFrame() {
        return true;
    }

    /**
     * Queues a sample from the MediaExtractor to the input of the MediaCodec. The return value
     * signals if the operation was successful and can be tried another time (return true), or if
     * there are no more input buffers available, the next sample does not belong to this decoder
     * (if skip is false) or the input EOS is reached (return false).
     *
     * @param skip if true, samples belonging to foreign tracks are skipped
     * @return true if the operation can be repeated for another sample, false if it's another
     * decoder's turn or the EOS
     */
    public final boolean queueSampleToCodec(boolean skip) {
        if(mInputEos || !shouldDecodeAnotherFrame()) return false;

        // If we are not at the EOS and the current extractor track is not the this track, we
        // return false because it is some other decoder's turn now.
        // If we are at the EOS, the following code will issue a BUFFER_FLAG_END_OF_STREAM.
        if(mExtractor.getSampleTrackIndex() != -1 && mExtractor.getSampleTrackIndex() != mTrackIndex) {
            if(skip) return mExtractor.advance();
            return false;
        }

        boolean sampleQueued = false;
        int inputBufIndex = mCodec.dequeueInputBuffer(TIMEOUT_US);
        if (inputBufIndex >= 0) {
            ByteBuffer inputBuffer =mCodecBufferCompatWrapper.getInputBuffer(inputBufIndex);

            if(mExtractor.hasTrackFormatChanged()) {
                /* The mRepresentationChanging flag and BUFFER_FLAG_END_OF_STREAM flag together
                 * notify the decoding loop that the representation changes and the codec
                 * needs to be reconfigured.
                 */
                mRepresentationChanging = true;
                mCodec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            } else {
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                long presentationTimeUs = 0;

                if (sampleSize < 0 || mExtractor.getSampleTime() == -1) {
                    YYLog.info(TAG, "EOS input");
                    mInputEos = true;
                    sampleSize = 0;
                } else {
                    presentationTimeUs = mExtractor.getSampleTime();
                    sampleQueued = true;
                }

                mCodec.queueInputBuffer(
                        inputBufIndex,
                        0,
                        sampleSize,
                        presentationTimeUs,
                        mInputEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                //YYLog.info(TAG, "queued PTS " + presentationTimeUs);

                if (!mInputEos) {
                    mExtractor.advance();
                }
            }
        }
        return sampleQueued;
    }

    /**
     * Consumes a decoded frame from the decoder output and returns information about it.
     *
     * @return a FrameInfo if a frame was available; NULL if the decoder needs more input
     * samples/decoding time or if the output EOS has been reached
     */
    public final FrameInfo dequeueDecodedFrame() {
        if(mOutputEos) return null;

        int res = mCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
        mOutputEos = res >= 0 && (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

        if(mOutputEos && mRepresentationChanging) {
            /* Here, the output is not really at its end, it's just the end of the
             * current representation segment, and the codec needs to be reconfigured to
             * the following representation format to carry on.
             */

            reinitCodec();

            mOutputEos = false;
            mRepresentationChanging = false;
            mRepresentationChanged = true;
        }
        else if (res >= 0) {
            // Frame decoded. Fill frame info object and return to caller...

            // Adjust buffer: http://bigflake.com/mediacodec/#q11
            // This is done on audio buffers only, video decoder does not return actual buffers
            ByteBuffer data = mCodecBufferCompatWrapper.getOutputBuffer(res);
            if (data != null && mBufferInfo.size != 0) {
                data.position(mBufferInfo.offset);
                data.limit(mBufferInfo.offset + mBufferInfo.size);
                //YYLog.info(TAG, "raw data bytes: " + mBufferInfo.size);
            }

            FrameInfo fi = mFrameInfoPool.newFrameInfo();
            fi.bufferIndex = res;
            fi.data = data;
            fi.presentationTimeUs = mBufferInfo.presentationTimeUs;
            fi.unityPtsUs = mBufferInfo.presentationTimeUs;
            fi.endOfStream = mOutputEos;
            fi.drawWithTwoSurface = false;
            fi.needDrawImage = false;

            if(mRepresentationChanged) {
                mRepresentationChanged = false;
                fi.representationChanged = true;
            }
            if(fi.endOfStream) {
                YYLog.info(TAG, "EOS output");
            } else {
                mDecodingPTS = fi.presentationTimeUs;
            }

            //YYLog.info(TAG, "decoded PTS " + fi.presentationTimeUs);

            return fi;
        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            mCodecBufferCompatWrapper = new CodecBufferCompatWrapper(mCodec);
            YYLog.info(TAG, "output buffers have changed.");
        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // NOTE: this is the format of the raw output, not the format as specified by the container
            MediaFormat format = mCodec.getOutputFormat();
            YYLog.info(TAG, "output format has changed to " + format);
            onOutputFormatChanged(format);
        } else if (res == MediaCodec.INFO_TRY_AGAIN_LATER) {
            //YYLog.info(TAG, "dequeueOutputBuffer timed out");
        }

        //YYLog.info(TAG, "EOS NULL");
        return null; // EOS already reached, no frame left to return
    }

    /**
     * Returns the PTS of the current, that is, the most recently decoded frame.
     * @return the PTS of the most recent frame
     */
    public long getCurrentDecodingPTS() {
        return mDecodingPTS;
    }

    /**
     * Renders a frame at the specified offset time to some output (e.g. video frame to screen,
     * audio frame to audio track).
     * @param frameInfo the frame info holding the frame buffer
     */
    public void renderFrame(FrameInfo frameInfo) {
        releaseFrame(frameInfo);
    }

    /**
     * Renders the current frame instantly.
     * This only works if the decoder holds a current frame, e.g. after a seek.
     * @see #renderFrame(FrameInfo)
     */
    public void renderFrame() {
        if (mCurrentFrameInfo != null) {
            /*if (this instanceof IVideoDecoder) {
                YYLog.info(TAG, "renderFrame video");
            } else {
                YYLog.info(TAG, "renderFrame audio");
            }*/

            renderFrame(mCurrentFrameInfo);
        }
    }

    /**
     * Dismisses a frame without rendering it.
     * @param frameInfo the frame info holding the frame buffer to dismiss
     */
    public void dismissFrame(FrameInfo frameInfo) {
        releaseFrame(frameInfo);
    }

    /**
     * Dismisses the current frame.
     * This only works if the decoder holds a current frame, e.g. after a seek.
     */
    public void dismissFrame() {
        if(mCurrentFrameInfo != null) dismissFrame(mCurrentFrameInfo);
    }

    /**
     * Releases a frame and all its associated resources.
     * When overwritten, this method must release the output buffer through
     * {@link MediaCodec#releaseOutputBuffer(int, boolean)} or {@link MediaCodec#releaseOutputBuffer(int, long)},
     * and then release the frame info through {@link #releaseFrameInfo(FrameInfo)}.
     *
     * @param frameInfo information about the current frame
     */
    public void releaseFrame(FrameInfo frameInfo) {
        mCodec.releaseOutputBuffer(frameInfo.bufferIndex, false);
        releaseFrameInfo(frameInfo);
    }

    /**
     * Releases the frame info back into the decoder for later reuse. This method must always be
     * called after handling a frame.
     *
     * @param frameInfo information about a frame
     */
    public final void releaseFrameInfo(FrameInfo frameInfo) {
        frameInfo.clear();
        mFrameInfoPool.freeByteBuffer(frameInfo);
    }

    /**
     * Overwrite in subclass to handle a change of the output format.
     * @param format the new media format
     */
    protected void onOutputFormatChanged(MediaFormat format) {
        // nothing to do here
    }

    /**
     * Runs the decoder loop, optionally until a new frame is available.
     * The returned FrameInfo object keeps metadata of the decoded frame. To release its data,
     * call {@link #releaseFrame(FrameInfo)}.
     *
     * @param skip skip frames of other tracks
     * @param force force decoding in a loop until a frame becomes available or the EOS is reached
     * @return a FrameInfo object holding metadata of a decoded frame or NULL if no frame has been decoded
     */
    public final FrameInfo decodeFrame(boolean skip, boolean force) {
        //YYLog.info(TAG, "decodeFrame");
        while(!mOutputEos) {
            // Dequeue decoded frames
            FrameInfo frameInfo = dequeueDecodedFrame();

            // Enqueue encoded buffers into decoders
            while (queueSampleToCodec(skip)) {}

            if(frameInfo != null) {
                // If a frame has been decoded, return it
                mCurrentFrameInfo = frameInfo;
                return frameInfo;
            }

            if(!force) {
                // If we have not decoded a frame and we're not forcing decoding until a frame becomes available, return null
                return null;
            }
        }

        YYLog.info(TAG, "EOS NULL");
        return null; // EOS already reached, no frame left to return
    }

    /**
     * Seeks to the specified target PTS with the specified seek mode. After the seek, the decoder
     * holds the frame from the target position which must either be rendered through {@link #renderFrame()}
     * or dismissed through {@link #dismissFrame()}.
     *
     * @param seekMode the mode how the seek should be carried out
     * @param seekTargetTimeUs the target PTS to seek to
     * @throws IOException
     */
    public final FrameInfo seekTo(MediaPlayer.SeekMode seekMode, long seekTargetTimeUs) throws IOException {
        /**
         * seek有可能只extract sample,并没有decode,这种情况下不需要重置decodingPts
         * mDecodingPTS = PTS_NONE;
         */

        mCurrentFrameInfo = seekTo(seekMode, seekTargetTimeUs, mExtractor, mCodec);
        return  mCurrentFrameInfo;
    }

    /**
     * This method implements the actual seeking and can be overwritten by subclasses to implement
     * custom seeking methods.
     *
     * @see #seekTo(MediaPlayer.SeekMode, long)
     */
    protected FrameInfo seekTo(MediaPlayer.SeekMode seekMode, long seekTargetTimeUs,
                               MediaExtractor extractor, ICodec codec) throws IOException {

        YYLog.info(TAG, "seeking to:                 " + seekTargetTimeUs);
        YYLog.info(TAG, "extractor current position: " + extractor.getSampleTime());
        if (mEnableRotate) {
            extractor.seekTo(seekTargetTimeUs, SEEK_TO_PREVIOUS_SYNC);   // fix SDANDROID-2319
        } else {
            extractor.seekTo(seekTargetTimeUs, seekMode.getBaseSeekMode());
        }

        YYLog.info(TAG, "extractor new position:     " + extractor.getSampleTime());

        // TODO add seek cancellation possibility
        // e.g. by returning an object with a cancel method and checking the flag at fitting places within this method

        mInputEos = false;
        mOutputEos = false;
        codec.flush();

        if(extractor.hasTrackFormatChanged()) {
            reinitCodec();
            mRepresentationChanged = true;
        }

        //音频seek不用解码播放，只需要extract sample即可
        if (this instanceof AudioDecoder) {
            return null;
        }

        //seek extract相同的帧直接返回null,不进入解码
        if (extractor.getSampleTime() == mLastExtractorSampleTime) {
            YYLog.warn(TAG, "extract sample is same as before:" + mLastExtractorSampleTime);
            return null;
        }
        mLastExtractorSampleTime = extractor.getSampleTime();

        return decodeFrame(true, true);
    }

    /**
     * Releases the codec and its resources. Must be called when the decoder is no longer in use.
     */
    public void release() {
        mCodec.stop();
        mCodec.release();
        YYLog.info(TAG, "decoder released");
    }

    private class FrameInfoPool {

        private ConcurrentLinkedQueue<FrameInfo> mEmptyFrameInfos;

        public FrameInfoPool() {
            mEmptyFrameInfos = new ConcurrentLinkedQueue<FrameInfo>();
        }

        public FrameInfo newFrameInfo() {
            if(!mEmptyFrameInfos.isEmpty()) {
                return mEmptyFrameInfos.poll();
            } else  {
                return  new FrameInfo();
            }
        }

        public void freeByteBuffer(FrameInfo frameInfo) {
            if (frameInfo != null) {
                frameInfo.clear();
                mEmptyFrameInfos.offer(frameInfo);
            }
        }
    }
}
