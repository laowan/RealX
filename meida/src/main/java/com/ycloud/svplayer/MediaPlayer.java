/*
 * Copyright 2014 Mario Guggenberger <mg@protyposis.net>
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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.ycloud.api.common.TransitionInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.common.GlobalConfig;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.TransitionPts;
import com.ycloud.svplayer.surface.IPlayerGLManager;
import com.ycloud.svplayer.surface.PlayerGLManager;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.TransitionTimeUtils;
import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by maguggen on 04.06.2014.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaPlayer {

    private static final String TAG = MediaPlayer.class.getSimpleName();

    private boolean mSeekInTime = true; //是否需要实时seek，默认是需要的

    public enum SeekMode {
        /**
         * Seeks to the previous sync point.
         * This seek mode equals Android MediaExtractor's {@link android.media.MediaExtractor#SEEK_TO_PREVIOUS_SYNC}.
         */
        FAST_TO_PREVIOUS_SYNC(MediaExtractor.SEEK_TO_PREVIOUS_SYNC),

        /**
         * Seeks to the next sync point.
         * This seek mode equals Android MediaExtractor's {@link android.media.MediaExtractor#SEEK_TO_NEXT_SYNC}.
         */
        FAST_TO_NEXT_SYNC(MediaExtractor.SEEK_TO_NEXT_SYNC),

        /**
         * Seeks to to the closest sync point.
         * This seek mode equals Android MediaExtractor's {@link android.media.MediaExtractor#SEEK_TO_CLOSEST_SYNC}.
         */
        FAST_TO_CLOSEST_SYNC(MediaExtractor.SEEK_TO_CLOSEST_SYNC),

        /**
         * Seeks to the exact frame if the seek time equals the frame time, else
         * to the following frame; this means that it will often seek one frame too far.
         */
        PRECISE(MediaExtractor.SEEK_TO_PREVIOUS_SYNC),

        /**
         * Default mode.
         * Always seeks to the exact frame. Can cost maximally twice the time than the PRECISE mode.
         */
        EXACT(MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        private int baseSeekMode = MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

        SeekMode(int baseSeekMode) {
            this.baseSeekMode = baseSeekMode;
        }

        public int getBaseSeekMode() {
            return baseSeekMode;
        }
    }

    public enum State {
        IDLE,
        INITIALIZED,
        PREPARING,
        PREPARED,
        STOPPED,
        RELEASING,
        RELEASED,
        ERROR
    }

    private SeekMode mSeekMode = SeekMode.FAST_TO_NEXT_SYNC;
    private Surface mSurface;
    private SurfaceHolder mSurfaceHolder;
    private int mVideoViewWidth;    // the size of the mSurface
    private int mVideoViewHeight;
    private int mViewPortWidth;
    private int mViewPortHeight;
    private int mLayoutMode = 0;
    private boolean mEnableRotate = false;
    private boolean mClockWise = false;
    private int mBackgroundColor = -1;
    private Bitmap mBackgoundBitmap = null;
    private String mPath;

    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;

    private int mVideoTrackIndex;
    private MediaFormat mVideoFormat;
    private long mVideoMinPTS;

    private int mAudioTrackIndex;
    private MediaFormat mAudioFormat;
    private long mAudioMinPTS;
    private int mAudioSessionId;
    private int mAudioStreamType;
    private float mVolumeLeft = 1, mVolumeRight = 1;

    private PlaybackThread mPlaybackThread;
    private long mCurrentDecoderPosition;
    private long mCurrentVideoDecoderPosition;
    private long mSeekTargetTime;
    private boolean mSeeking;
    private int mBufferPercentage;
    private StandaloneMediaClock mStandaloneMediaClock;

    private EventHandler mEventHandler;
    private OnPreparedListener mOnPreparedListener;
    private OnCompletionListener mOnCompletionListener;
    private OnSeekListener mOnSeekListener;
    private OnSeekCompleteListener mOnSeekCompleteListener;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;
    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnRenderStartListener mOnRenderStartListener;
    private onProgressListener mOnProgresslistener;

    private PowerManager.WakeLock mWakeLock = null;
    private boolean mScreenOnWhilePlaying;
    private boolean mStayAwake;

    private AudioPlayback mAudioPlayback;
    private Decoders mDecoders;
    private State mCurrentState;

    private boolean mRepeatRender;
    /**
     * A lock to sync release() with the actual releasing on the playback thread. This lock makes
     * sure that release() waits until everything has been released before returning to the caller,
     * and thus makes the async release look synchronized to an API caller.
     */
    private static Object mReleaseSyncLock = new Object();

    private static Object mSyncPrepareLock = new Object();

    private static final Object mStopSyncLock = new Object();

    private int mFilterSID = -1; //filter session id;

    private Context mContext;
    private List<TransitionInfo> mTransitionList;
    //当前需要转场视频在mVideosTransitionInfo中的索引
    private int mCurrentVideoIndex;
    //mCurrentTransitionIndex对应转场视频的信息
    private VideoPlayInfo mNextPlayInfo;
    private PlayerGLManager mPlayerGLManager;
    private String mFilterJson;

    private long mRenderSleepCnt = 0;

    private String mBackgroundMusicRhythmPath = null;
    private int mBackgroundMusicStart = 0;

    private boolean mUsedForRecordSession = false;
    private IPlayerGLManager mGLManger = null;
    private int mAVSyncBehavior = MediaConst.AV_SYNC_BEHAVIOR_FASTPLAY;

    public MediaPlayer(Context context) {
        mPlaybackThread = null;
        mEventHandler = new EventHandler();
        mStandaloneMediaClock = new StandaloneMediaClock();
        mCurrentState = State.IDLE;
        mAudioSessionId = 0; // AudioSystem.AUDIO_SESSION_ALLOCATE;
        mAudioStreamType = AudioManager.STREAM_MUSIC;
        mContext = context;
        STMobileFaceDetectionWrapper.enableSTMobilePlayerMode();

        mRepeatRender = false;

    }

    /**
     * 设置滤镜
     *
     * @param videoFilter 滤镜参数
     */
    public void setVideoFilter(VideoFilter videoFilter) {
        if (mPlaybackThread != null) {
            mPlaybackThread.setVideoFilter(videoFilter);
        }
    }

    public void setFilterNGSid(int sid) {
        mFilterSID = sid;
    }

    public void setFilterJson(String filterJson) {
        mFilterJson = filterJson;
    }

    /**
     * 处理封面
     *
     * @param imageBasePath
     * @param imageRate
     */
    public void processImages(String imageBasePath, int imageRate) {
        if (mPlaybackThread != null) {
            mPlaybackThread.processImages(imageBasePath, imageRate);
        }
    }

    public void setCurrentState(State state) {
        YYLog.info(this, "currentState " + mCurrentState.ordinal() + " -> " + state.ordinal());
        mCurrentState = state;
    }

    public State getCurrentState() {
        YYLog.info(this, "currentState " + mCurrentState.ordinal());
        return mCurrentState;
    }

    /**
     * Sets the media source
     *
     * @param source the media source
     * @throws IOException
     * @throws IllegalStateException
     */
    public void setDataSource(MediaSource source)
            throws IOException, IllegalStateException {
        if (mCurrentState != State.IDLE) {
            throw new IllegalStateException();
        }

        mCurrentVideoIndex = 0;
        VideoPlayInfo videoPlayInfo = getVideoPlayInfo(source, 0);
        initExtractor(videoPlayInfo);
        setCurrentState(State.INITIALIZED);
    }

    private int getTrackIndex(MediaExtractor mediaExtractor, String mimeType) {
        if (mediaExtractor == null) {
            return MediaDecoder.INDEX_NONE;
        }

        for (int i = 0; i < mediaExtractor.getTrackCount(); ++i) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            if(format != null) {
                YYLog.info(TAG, format.toString());
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(mimeType)) {
                    return i;
                }
            }
        }

        return MediaDecoder.INDEX_NONE;
    }

    /**
     * @see android.media.MediaPlayer#setDataSource(Context, Uri, Map)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException {
        setDataSource(new UriSource(context, uri, headers));
    }

    /**
     * @see android.media.MediaPlayer#setDataSource(Context, Uri)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setDataSource(Context context, Uri uri) throws IOException {
        setDataSource(context, uri, null);
    }

    private void prepareInternal() throws IOException, IllegalStateException {
        if (mCurrentState == State.RELEASING) {
            // release() has already been called, drop out of prepareAsync() (can only happen with async prepare)
            return;
        }

        mDecoders = new Decoders();

        if (mVideoTrackIndex != MediaDecoder.INDEX_NONE) {
            try {
                MediaDecoder vd;

                if (!mUsedForRecordSession) {
                    if (mPlayerGLManager == null) {
                        int width = getVideoWidth();
                        int height = getVideoHeight();
                        int rotation = getRotation();
                        if (rotation == 90 || rotation == 270) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }

                        mPlayerGLManager = new PlayerGLManager(mContext, mSurface, width, height, mFilterSID, mVideoViewWidth, mVideoViewHeight,
                                                                    mEnableRotate, mClockWise, mBackgroundColor, mBackgoundBitmap);
                        mPlayerGLManager.addFilterJson(mFilterJson);
                        mPlayerGLManager.setBackgroundMusicRhythmInfo(mBackgroundMusicRhythmPath, mBackgroundMusicStart);
                        if (mEnableRotate) {
                            mPlayerGLManager.setViewPortSize(mViewPortWidth, mViewPortHeight);
                            mPlayerGLManager.setLayoutMode(mLayoutMode);
                        }

                        if (mEventHandler != null) {
                            mPlayerGLManager.setEventHandler(mEventHandler);
                        }
                    }
                    vd = new VideoDecoderWithEGL(mVideoExtractor, mVideoTrackIndex, mPlayerGLManager);
                    mDecoders.addDecoder(vd);
                } else {
                    vd = new VideoDecoderWithEGL(mVideoExtractor, mVideoTrackIndex, mGLManger);  //: for record session
                    mDecoders.addDecoder(vd);
                }


                if (mEnableRotate) {
                    mDecoders.setEnableRotate(mEnableRotate);
                }

            } catch (Exception e) {
                YYLog.error(TAG, "cannot create video decoder: " + e.getMessage());
            }
        }
        YYLog.info(TAG, "prepareInternal, video decoder init finish .");

        if (mAudioTrackIndex != MediaDecoder.INDEX_NONE) {
            mAudioPlayback = new AudioPlayback();
            // Initialize settings in case they have already been set before the preparation
            mAudioPlayback.setAudioSessionId(mAudioSessionId);
            setVolume(mVolumeLeft, mVolumeRight); // sets the volume on mAudioPlayback

            try {
                MediaDecoder ad = new AudioDecoder(mAudioExtractor != null ? mAudioExtractor : mVideoExtractor,
                         mAudioTrackIndex,mAudioPlayback);
                mDecoders.addDecoder(ad);
            } catch (Exception e) {
                Log.e(TAG, "cannot create audio decoder: " + e.getMessage());
                mAudioPlayback = null;
            }
        }
        YYLog.info(TAG, "prepareInternal, audio decoder init finish .");

        // If no decoder could be initialized, there is nothing to play back, so we throw an exception
        if (mDecoders.getDecoders().isEmpty()) {
            throw new IOException("cannot decode any stream");
        }

        mDecoders.setMinPts(mAudioMinPTS, mVideoMinPTS);

        if (mAudioPlayback != null) {
            mAudioSessionId = mAudioPlayback.getAudioSessionId();
            mAudioStreamType = mAudioPlayback.getAudioStreamType();
        }

        // After the decoder is initialized, we know the video size
        if (mDecoders.getVideoDecoder() != null) {
            IVideoDecoder videoDecoder = (IVideoDecoder) mDecoders.getVideoDecoder();
            int width = videoDecoder.getVideoWidth();
            int height = videoDecoder.getVideoHeight();
            int rotation = getRotation();

            // Swap width/height to report correct dimensions of rotated portrait video (rotated by 90 or 270 degrees)
            if (rotation > 0 && rotation != 180) {
                int temp = width;
                width = height;
                height = temp;
            }

            mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_SET_VIDEO_SIZE, width, height));
        }

        if (mCurrentState == State.RELEASING) {
            // release() has already been called, drop out of prepareAsync()
            return;
        }

        // Decode the first frame to initialize the decoder, and seek back to the start
        // This is necessary on some platforms, else a seek directly after initialization will fail,
        // or the decoder goes into a state where it does not accept any input and does not deliver
        // any output, locking up playback (observed on N4 API22).
        // N4 API22 Test: disable this code open video, seek to end, press play to start from beginning
        //                -> results in infinite decoding loop without output
        if (true) {
            if (mDecoders.getVideoDecoder() != null) {
                FrameInfo vfi = mDecoders.decodeFrame(true);
                mDecoders.getVideoDecoder().releaseFrame(vfi);
            } else {
                mDecoders.decodeFrame(false);
            }
            /* 删除prepare时audio的pause和seek，防止播放开始时候pause中flush掉部分buffer queue中的数据
            if (mAudioPlayback != null) mAudioPlayback.pause(true);
            mDecoders.seekTo(SeekMode.FAST_TO_PREVIOUS_SYNC, 0);
            */
        }
        YYLog.info(TAG, "prepareInternal, return .");
    }

    /**
     * @see android.media.MediaPlayer#prepare()
     */
    public void prepare() throws IOException, IllegalStateException {
        if (mCurrentState != State.INITIALIZED && mCurrentState != State.STOPPED) {
            throw new IllegalStateException("prepare mCurrentState:" + mCurrentState);
        }

        setCurrentState(State.PREPARING);

        // Create the playback loop handler thread
        if (mPlaybackThread == null) {
            mPlaybackThread = new PlaybackThread();
            mPlaybackThread.start();

            synchronized (mSyncPrepareLock) {

                mPlaybackThread.prepare();
                try {
                    mSyncPrepareLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    YYLog.error(this, e.getMessage());
                    setCurrentState(State.ERROR);
                    return;
                }
                setCurrentState(State.PREPARED);
            }
        }
    }

    /**
     * @see android.media.MediaPlayer#prepareAsync()
     */
    public void prepareAsync() throws IllegalStateException {
        if (mCurrentState != State.INITIALIZED && mCurrentState != State.STOPPED) {
            throw new IllegalStateException("prepareAsync mCurrentState:" + mCurrentState);
        }

        setCurrentState(State.PREPARING);

        // Create the playback loop handler thread
        mPlaybackThread = new PlaybackThread();
        mPlaybackThread.start();

        // Execute prepare asynchronously on playback thread
        mPlaybackThread.prepare();
    }

    /**
     * @see android.media.MediaPlayer#setDisplay(SurfaceHolder)
     */
    public void setDisplay(SurfaceHolder sh) {
        mSurfaceHolder = sh;
        if (sh != null) {
            mSurface = sh.getSurface();
        } else {
            mSurface = null;
        }
        if (mPlayerGLManager != null) {
            mPlayerGLManager.asyncUpdateSurface(mSurface);
        }
    }

    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mPlayerGLManager != null) {
            mPlayerGLManager.asyncUpdateSurface(mSurface);
        }
    }

    public void setVideoViewSize(int w, int h) {
        mVideoViewWidth = w;
        mVideoViewHeight =h;
        YYLog.info(TAG, "setVideoViewSize w " + w + " h " + h);
    }

    public void setEnableRotate(boolean enable) {
        mEnableRotate = enable;
        YYLog.info(TAG, "setEnableRotate " + enable);
    }

    public void setRotateDirection(boolean clockwise) {
        mClockWise = clockwise;
    }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        mBackgoundBitmap = bitmap;
    }

    public void setVideoPath(String path){
        mPath = path;
    }

    public void setViewPortSize(int w, int h) {
        YYLog.info(TAG, "setViewPortSize w " + w + " h " + h);
        mViewPortWidth = w;
        mViewPortHeight = h;
        if (mPlayerGLManager != null) {  // 发布页-》特效编辑页，播放器未销毁，视口改变
            mPlayerGLManager.setViewPortSize(w, h);
        }
    }

    public void setLayoutMode(int mode) {
        YYLog.info(TAG, "setLayoutMode " + mode);
        mLayoutMode = mode;
        if (mPlayerGLManager != null) {     // 发布页-》特效编辑页，播放器未销毁，视频显示模式改变
            mPlayerGLManager.setLayoutMode(mode);
        }
    }

    public void start() {
        YYLog.info(this, "MediaPlayer start");
        if (mCurrentState != State.PREPARED) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            YYLog.info(this, "MediaPlayer start, currentState=" + backState + ", PREPARED State is needed, so throw a exception");
            throw new IllegalStateException("start mCurrentState:" + backState);
        }

        mPlaybackThread.play();
        stayAwake(true);
    }

    public void startRepeatRender() {
        YYLog.info(TAG,"startRepeatRender");
        mRepeatRender = true;
        if (mPlaybackThread != null) {
            mPlaybackThread.startRepeatRender();
        }
    }

    public void stopRepeatRender() {
        YYLog.info(TAG,"stopRepeatRender");
        mRepeatRender = false;
        if (mPlaybackThread != null) {
            mPlaybackThread.stopRepeatRender();
        }
    }

    public void renderLastFrame() {
        if (mPlaybackThread != null) {
            mPlaybackThread.renderLastFrame();
        }
    }

    public void pause() {
        if (mCurrentState != State.PREPARED) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            YYLog.info(this, "MediaPlayer pause, currentState=" + mCurrentState + ", PREPARED state is needed, so throw a exception!!");
            throw new IllegalStateException("pause mCurrentState:" + backState);
        }

        if (mRepeatRender) {
            mPlaybackThread.startRepeatRender();
        } else {
            mPlaybackThread.pause();
        }
        stayAwake(false);
    }

    public SeekMode getSeekMode() {
        return mSeekMode;
    }

    public void setSeekMode(SeekMode seekMode) {
        this.mSeekMode = seekMode;
    }

    public void seekTo(long usec) {
        if (mCurrentState.ordinal() < State.PREPARED.ordinal() && mCurrentState.ordinal() >= State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("seekTo mCurrentState:" + backState);
        }

        if (DeviceUtil.getPhoneModel().equals("Mi Note 2") && mSeekTargetTime == usec) {
            YYLog.info(TAG, " seek to the same time as last seek. return. ");
            return;
        }

        /* A seek needs to be performed in the decoding thread to execute commands in the correct
         * order. Otherwise it can happen that, after a seek in the media decoder, seeking procedure
         * starts, then a frame is decoded, and then the codec is flushed; the PTS of the decoded frame
         * then interferes the seeking procedure, the seek stops prematurely and a wrong waiting time
         * gets calculated. */

        YYLog.info(TAG, "seekTo " + usec + " with video sample offset " + mVideoMinPTS);

        if (mOnSeekListener != null) {
            mOnSeekListener.onSeek(MediaPlayer.this);
        }

        mSeeking = true;
        // The passed in target time is always aligned to a zero start time, while the actual video
        // can have an offset and must not necessarily start at zero. The offset can e.g. come from
        // the CTTS box SampleOffset field, and is only reported on Android 5+. In Android 4, the
        // offset is handled by the framework, not reported, and videos always start at zero.
        // By adding the offset to the seek target time, we always seek to a zero-reference time in
        // the stream.
        mSeekTargetTime = usec;
        mPlaybackThread.seekTo(mSeekTargetTime);
    }

    public void seekTo(int msec) {
        seekTo(msec * 1000L);
    }

    /**
     * Sets the playback speed. Can be used for fast forward and slow motion.
     * The speed must not be negative.
     * <p>
     * speed 0.5 = half speed / slow motion
     * speed 2.0 = double speed / fast forward
     * speed 0.0 equals to pause
     *
     * @param speed the playback speed to set
     * @throws IllegalArgumentException if the speed is negative
     */
    public void setPlaybackSpeed(float speed) {
        if (speed < 0) {
            throw new IllegalArgumentException("speed cannot be negative");
        }

        mStandaloneMediaClock.setSpeed(speed);
        mStandaloneMediaClock.startAt(mCurrentDecoderPosition);
    }

    public void setAVSyncBehavior(int behavior) {
        mAVSyncBehavior = behavior;
        YYLog.info(TAG, "setAVSyncBehavior " + behavior);
    }


    public void setVideoPlaybackSpeed(float speed) {
        if (speed < 0) {
            throw new IllegalArgumentException("speed cannot be negative");
        }

        mStandaloneMediaClock.setSpeed(speed);
        mStandaloneMediaClock.startAt(mCurrentVideoDecoderPosition);
    }

    /**
     * Gets the current playback speed. See {@link #setPlaybackSpeed(float)} for details.
     *
     * @return the current playback speed
     */
    public float getPlaybackSpeed() {
        return (float) mStandaloneMediaClock.getSpeed();
    }

    public boolean isPlaying() {
        if (mCurrentState.ordinal() >= State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            YYLog.info(this, "MediaPlayer.isPlaying, currentState=" + mCurrentState + " so throw a exception!!");
            throw new IllegalStateException("isPlaying mCurrent: "+ backState);
        }

        return mPlaybackThread != null && !mPlaybackThread.isPaused();
    }

    public void stop() {
        release();
        setCurrentState(State.STOPPED);
    }

    public void stopSync() {
        synchronized (mStopSyncLock) {
            release();
            setCurrentState(State.STOPPED);
            try {
                YYLog.info(TAG, "Wait PlayerThread EXit.");
                mStopSyncLock.wait();
            }catch (InterruptedException e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
            }
        }
    }

    public void release() {
        YYLog.info(this, "MediaPlayer.release begin,  currentState=" + mCurrentState);
        if (mCurrentState == State.RELEASING || mCurrentState == State.RELEASED) {
            return;
        }

        setCurrentState(State.RELEASING);

        HandlerThread handlerThread = new HandlerThread("mediaplayer_release");
        handlerThread.start();
        final Handler releaseHandler = new Handler(handlerThread.getLooper());

        releaseHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mPlaybackThread != null) {
                    // Create a new lock object for this release cycle
                    synchronized (mReleaseSyncLock) {
                        try {
                            // Schedule release on the playback thread
                            mPlaybackThread.release();
                            mPlaybackThread = null;

                            // Wait for the release on the playback thread to finish
                            YYLog.info(TAG, "release mReleaseSyncLock wait");
                            mReleaseSyncLock.wait();
                        } catch (InterruptedException e) {
                            YYLog.e(TAG, e.getMessage());
                        }
                    }
                }

                mOnRenderStartListener = null;

                mContext = null;

                stayAwake(false);
                STMobileFaceDetectionWrapper.enableSTMobileCameraMode();
                setCurrentState(State.RELEASED);
                YYLog.info(this, "MediaPlayer.release end");

                if (releaseHandler != null) {
                    releaseHandler.getLooper().quit();
                }

                synchronized (mStopSyncLock) {
                    YYLog.info(TAG, "notifyAll PlayerThread Exit. ");
                    mStopSyncLock.notifyAll();
                }
            }
        });
    }

    public void reset() {
        stop();
        setCurrentState(State.IDLE);
    }

    /**
     * @see android.media.MediaPlayer#setWakeMode(Context, int)
     */
    public void setWakeMode(Context context, int mode) {
        boolean washeld = false;
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                washeld = true;
                mWakeLock.release();
            }
            mWakeLock = null;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode | PowerManager.ON_AFTER_RELEASE, MediaPlayer.class.getName());
        mWakeLock.setReferenceCounted(false);
        if (washeld) {
            mWakeLock.acquire();
        }
    }

    /**
     * @see android.media.MediaPlayer#setScreenOnWhilePlaying(boolean)
     */
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (mScreenOnWhilePlaying != screenOn) {
            if (screenOn && mSurfaceHolder == null) {
                Log.w(TAG, "setScreenOnWhilePlaying(true) is ineffective without a SurfaceHolder");
            }
            mScreenOnWhilePlaying = screenOn;
            updateSurfaceScreenOn();
        }
    }

    private void stayAwake(boolean awake) {
        if (mWakeLock != null) {
            if (awake && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!awake && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        mStayAwake = awake;
        updateSurfaceScreenOn();
    }

    private void updateSurfaceScreenOn() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake);
        }
    }

    public int getDuration() {
        if (mCurrentState.ordinal() <= State.PREPARING.ordinal() && mCurrentState.ordinal() >= State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("getDuration mCurrentState:" + backState);
        }

        if (mTransitionList == null) {

            return mVideoFormat != null  && mVideoFormat.containsKey(MediaFormat.KEY_DURATION) ? (int) (mVideoFormat.getLong(MediaFormat.KEY_DURATION) / 1000) :
                    mAudioFormat != null && mAudioFormat.containsKey(MediaFormat.KEY_DURATION) ? (int) (mAudioFormat.getLong(MediaFormat.KEY_DURATION) / 1000) : 0;
        } else {
            return (int) (TransitionTimeUtils.getTotalDuration(mTransitionList) / 1000);
        }
    }

    public int getCurrentPosition() {
        if (mCurrentState.ordinal() > State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("getCurrentPosition mCurrentState:" + backState);
        }
        if (mSeeking) {
            return (int) (mSeekTargetTime / 1000);
        }

        if (mTransitionList == null) {
        /* During a seek, return the temporary seek target time; otherwise a seek bar doesn't
         * update to the selected seek position until the seek is finished (which can take a
         * while in exact mode). */
            return (int) (mCurrentDecoderPosition / 1000);
        } else {
            return (int) (TransitionTimeUtils.ptsToUnityPts(mCurrentDecoderPosition, mTransitionList, mCurrentVideoIndex) / 1000);
        }
    }

    public int getBufferPercentage() {
        return mBufferPercentage;
    }

    public int getVideoWidth() {
        if (mCurrentState.ordinal() >= State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("getVideoWidth mCurrentState:" + backState);
        }

        return mVideoFormat != null ? (int) (mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
                * mVideoFormat.getFloat(MediaExtractor.MEDIA_FORMAT_EXTENSION_KEY_DAR)) : 0;
    }


    public int getVideoHeight() {
        if (mCurrentState.ordinal() >= State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("getVideoHeight mCurrentState:" + backState);
        }

        return mVideoFormat != null ? mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT) : 0;
    }

    public void setVideoRotate(int rotateAngle) {
        if (mPlayerGLManager != null) {
            mPlayerGLManager.setVideoRotate(rotateAngle);
        }
    }

    public void setLastVideoRotate(int rotateAngle) {
        if (mPlayerGLManager != null) {
            mPlayerGLManager.setLastVideoRotate(rotateAngle);
        }
    }

    public int getRotation() {
        int rotation = 0;
        try {
            rotation = (mVideoFormat != null && mVideoFormat.containsKey("rotation-degrees") ?
                    mVideoFormat.getInteger("rotation-degrees") : 0);
        } catch (Exception e) {
            YYLog.error(TAG, "get rotation-degrees fail");
        }

        if (DeviceUtil.getPhoneModel().equals("m1 note") ||      // 魅蓝note 系统接口不识别旋转信息
                DeviceUtil.getPhoneModel().equals("Meitu M4")) {
            com.ycloud.api.process.MediaInfo info = MediaProbe.getMediaInfo(mPath,true);
            if (info != null) {
                rotation = (int)info.v_rotate;
            }
        }

        if (rotation < 0) {
            rotation += 360;
        }
        return rotation;
    }

    /**
     * @see android.media.MediaPlayer#setVolume(float, float)
     */
    public void setVolume(float leftVolume, float rightVolume) {
        mVolumeLeft = leftVolume;
        mVolumeRight = rightVolume;

        if (mAudioPlayback != null) {
            mAudioPlayback.setStereoVolume(leftVolume, rightVolume);
        }
    }

    /**
     * This API method in the Android MediaPlayer is hidden, but may be unhidden in the future. Here
     * it can already be used.
     * see android.media.MediaPlayer#setVolume(float)
     */
    public void setVolume(float volume) {
        setVolume(volume, volume);
    }

    /**
     * @see android.media.MediaPlayer#setAudioSessionId(int)
     */
    public void setAudioSessionId(int sessionId) {
        if (mCurrentState != State.IDLE) {
            throw new IllegalStateException();
        }
        mAudioSessionId = sessionId;
    }

    /**
     * @see android.media.MediaPlayer#getAudioSessionId()
     */
    public int getAudioSessionId() {
        return mAudioSessionId;
    }

    public void setAudioStreamType(int streamType) {
        // Can be set any time, no IllegalStateException is thrown, but value will be ignored if audio is already initialized
        mAudioStreamType = streamType;
    }

    /**
     * Gets the stream type of the audio playback session.
     *
     * @return the stream type
     */
    public int getAudioStreamType() {
        return mAudioStreamType;
    }

    public void setBackgroundMusicRhythmInfo(String path, int start) {
        if (mPlayerGLManager != null) {
            mPlayerGLManager.setBackgroundMusicRhythmInfo(path, start);
        }
        //avoid set rhythm info before PlayerGLManager has been created, save info and set when create PlayerGLManager
        mBackgroundMusicRhythmPath = path;
        mBackgroundMusicStart = start;
    }

    private class PlaybackThread extends HandlerThread implements Handler.Callback {
        public class ProcessImagesInfo {
            String imageBasePath;
            int imageRate;
        }

        private static final int PLAYBACK_PREPARE = 1;
        private static final int PLAYBACK_PLAY = 2;
        private static final int PLAYBACK_PAUSE = 3;
        private static final int PLAYBACK_LOOP = 4;
        private static final int PLAYBACK_SEEK = 5;
        private static final int PLAYBACK_RELEASE = 6;
        private static final int PLAYBACK_PAUSE_AUDIO = 7;
        private static final int PLAYBACK_SET_VIDEOFILTER = 8;
        private static final int PLAYBACK_PROCESS_IMAGES = 9;
        private static final int PLAYBACK_REPEAT_RENDER = 10;
        private static final int PLAYBACK_STOP_REPEAT_RENDER = 11;
        private static final int PLAYBACK_RENDER_LAST_FRAME = 12;

        private Handler mHandler;
        private boolean mPaused;
        private boolean mReleasing;
        private FrameInfo mVideoFrameInfo;
        private boolean mRenderingStarted; // Flag to know if decoding the first frame
        private double mPlaybackSpeed;
        private boolean mAVLocked;

        private boolean mPlaybackCompleted;
        private boolean mLastNeedCatchupAudioFlag = false;
        private long mLastTime = 0;
        private long mDropFrameCount = 0;

        public PlaybackThread() {
            // Give this thread a high priority for more precise event timing
            super(TAG + "#" + PlaybackThread.class.getSimpleName(), Process.THREAD_PRIORITY_AUDIO);

            // Init fields
            mPaused = true;
            mReleasing = false;
            mRenderingStarted = true;
            mAVLocked = false;
            mPlaybackCompleted = false;
        }

        /**
         * 设置滤镜
         *
         * @param videoFilter 滤镜参数
         */
        public void setVideoFilter(VideoFilter videoFilter) {
            mHandler.sendMessage(mHandler.obtainMessage(PLAYBACK_SET_VIDEOFILTER, videoFilter));
        }

        // 处理设置滤镜消息
        private void setVideoFilterInternal(VideoFilter videoFilter) throws IOException {
            MediaDecoder videoDecoder = mDecoders.getVideoDecoder();
            if (videoDecoder != null && videoDecoder instanceof VideoDecoderWithEGL) {
                ((VideoDecoderWithEGL) videoDecoder).setVideoFilter(videoFilter);
            }
            mTransitionList = videoFilter.getTransitionList();
            mCurrentVideoIndex = 0;

            int nextPlayIndex = mCurrentVideoIndex + 1;
            if (mTransitionList != null && nextPlayIndex < mTransitionList.size()) {
                mNextPlayInfo = getVideoPlayInfo(new UriSource(mContext, Uri.parse(mTransitionList.get(nextPlayIndex).mVideoPath)), (int) (mTransitionList.get(nextPlayIndex).mTransitionDuration * 1000 * 1000));
            } else {
                mNextPlayInfo = null;
            }

            if (mNextPlayInfo != null) {
                MediaDecoder transitionDecoder = new VideoDecoderWithEGL(mNextPlayInfo.mVideoExtractor, mNextPlayInfo.mVideoTrackIndex, mPlayerGLManager);
                mDecoders.addVideoDecoderForTransition(transitionDecoder);
            }
        }

        /**
         * 处理封面
         *
         * @param imageBasePath
         * @param imageRate
         */
        public void processImages(String imageBasePath, int imageRate) {
            ProcessImagesInfo info = new ProcessImagesInfo();
            info.imageBasePath = imageBasePath;
            info.imageRate = imageRate;
            YYLog.info(TAG, "processImagesMessage PLAYBACK_PROCESS_IMAGES");
            mHandler.sendMessage(mHandler.obtainMessage(PLAYBACK_PROCESS_IMAGES, info));
        }

        // 处理封面消息
        public void processImagesInternal(ProcessImagesInfo info) {

            IVideoDecoder videoDecoder = (IVideoDecoder) mDecoders.getVideoDecoder();
            if (videoDecoder != null && videoDecoder instanceof VideoDecoderWithEGL) {
                ((VideoDecoderWithEGL) videoDecoder).processImages(info.imageBasePath, info.imageRate);
            }
        }

        @Override
        public synchronized void start() {
            super.start();

            // Create the handler that will process the messages on the handler thread
            mHandler = new Handler(this.getLooper(), this);

            YYLog.info(TAG, "PlaybackThread started");
        }

        public void prepare() {
            mHandler.sendEmptyMessage(PLAYBACK_PREPARE);
        }

        public void play() {
            mPaused = false;

            //fix for合演多段不同步问题
            int playDelay = 0;
            if (getCurrentPosition() != 0) {
                playDelay = 20;
            }
            mHandler.sendEmptyMessageDelayed(PLAYBACK_PLAY, playDelay);
        }

        public void pause() {
            mPaused = true;
            mHandler.sendEmptyMessage(PLAYBACK_PAUSE);
        }

        public void startRepeatRender() {
            mPaused = true;
            mHandler.sendEmptyMessage(PLAYBACK_PAUSE);
            mHandler.sendEmptyMessage(PLAYBACK_REPEAT_RENDER);
        }

        public void stopRepeatRender() {
            mHandler.sendEmptyMessage(PLAYBACK_STOP_REPEAT_RENDER);
        }

        public void renderLastFrame() {
            mPaused = true;
            mHandler.sendEmptyMessage(PLAYBACK_RENDER_LAST_FRAME);
        }

        public boolean isPaused() {
            return mPaused;
        }

        public void seekTo(long usec) {
            // When multiple seek requests come in, e.g. when a user slides the finger on a
            // seek bar in the UI, we don't want to process all of them and can therefore remove
            // all requests from the queue and only keep the most recent one.
            mHandler.removeMessages(PLAYBACK_SEEK); // remove any previous requests
            mHandler.obtainMessage(PLAYBACK_SEEK, usec).sendToTarget();
        }

        private void release() {
            if (!isAlive()) {
                YYLog.info(TAG, "release is not in active");
                return;
            }
            YYLog.info(TAG, "release set mReleasing is true");

            mPaused = true; // Set this flag so the loop does not schedule next loop iteration
            mReleasing = true;

            // Call actual release method
            // Actually it does not matter what we schedule here, we just need to schedule
            // something so {@link #handleMessage} gets called on the handler thread, read the
            // mReleasing flag, and call {@link #releaseInternal}.
            mHandler.sendEmptyMessage(PLAYBACK_RELEASE);
        }

        @Override
        public boolean handleMessage(Message msg) {
            try {
                if (mReleasing) {
                    // When the releasing flag is set, just release without processing any more messages
                    if (PLAYBACK_PROCESS_IMAGES == msg.what) {
                        YYLog.info(TAG, "handleMessage mReleasing is true and mgs is PLAYBACK_PROCESS_IMAGES");
                        processImagesInternal((ProcessImagesInfo) msg.obj);
                    }
                    releaseInternal();
                    return true;
                }

                switch (msg.what) {
                    case PLAYBACK_PREPARE:
                        prepareInternal();
                        return true;
                    case PLAYBACK_PLAY:
                        playInternal();
                        return true;
                    case PLAYBACK_PAUSE:
                        pauseInternal();
                        return true;
                    case PLAYBACK_PAUSE_AUDIO:
                        pauseInternalAudio();
                        return true;
                    case PLAYBACK_LOOP:
                        loopInternal();
                        return true;
                    case PLAYBACK_SEEK:
                        seekInternal((Long) msg.obj);
                        return true;
                    case PLAYBACK_RELEASE:
                        releaseInternal();
                        return true;
                    case PLAYBACK_SET_VIDEOFILTER:
                        setVideoFilterInternal((VideoFilter) msg.obj);
                        return true;
                    case PLAYBACK_PROCESS_IMAGES:
                        processImagesInternal((ProcessImagesInfo) msg.obj);
                        return true;
                    case PLAYBACK_REPEAT_RENDER:
                        repeatRenderInternal();
                        return true;

                    case PLAYBACK_STOP_REPEAT_RENDER:
                        stopRepeatRenderInternal();
                        return true;

                    case PLAYBACK_RENDER_LAST_FRAME:
                        renderLastFrameInternal();
                        return true;

                    default:
                        YYLog.info(TAG, "unknown/invalid message");
                        return false;
                }
            } catch (InterruptedException e) {
                YYLog.info(TAG, "decoder interrupted" + e.toString());
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, 0));
            } catch (IllegalStateException e) {
                e.printStackTrace();
                YYLog.e(TAG, "decoder error, too many instances?" + e.getMessage());
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, 0));
            } catch (IOException e) {
                YYLog.e(TAG, "decoder error, codec can not be created" + e.getMessage());
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_IO));
            }

            // Release after an exception
            try {
                releaseInternal();
            } catch (Exception e) {
                e.printStackTrace();
                YYLog.e(TAG, "decoder error,releaseInternal fail,msg:" + msg.what + ":" + e.getMessage());
            }
            return true;
        }

        private void prepareInternal() {
            YYLog.info(TAG, "prepareInternal");
            try {
                MediaPlayer.this.prepareInternal();

                MediaPlayer.this.setCurrentState(MediaPlayer.State.PREPARED);

                // This event is only triggered after a successful async prepare (not after the sync prepare!)
                mEventHandler.sendEmptyMessage(MEDIA_PREPARED);
            } catch (IOException e) {
                YYLog.error(TAG, "prepareAsync() failed: cannot decode stream(s)", e);
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_IO));
                releaseInternal();
            } catch (IllegalStateException e) {
                YYLog.error(TAG, "prepareAsync() failed: something is in a wrong state", e);
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, 0));
                releaseInternal();
            } catch (IllegalArgumentException e) {
                YYLog.error(TAG, "prepareAsync() failed: surface might be gone", e);
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, 0));
                releaseInternal();
            }

            synchronized (mSyncPrepareLock) {
                mSyncPrepareLock.notify();
            }

        }

        private void playInternal() throws IOException, InterruptedException {
            YYLog.info(TAG, "playInternal");
            stopRepeatRenderInternal();

            if (mDecoders.getVideoDecoder() != null) {
                YYLog.info(TAG, "reset videoDecoder last extract sample time.");
                mDecoders.getVideoDecoder().mLastExtractorSampleTime = -2;
                mSeekTargetTime = -1;
            }

            if (mPlaybackCompleted == true) {
                mCurrentDecoderPosition = 0;
                mCurrentVideoDecoderPosition =0;
                mCurrentVideoIndex = 0;

                if (mTransitionList != null) {
                    TransitionPts transitionPts = new TransitionPts();
                    transitionPts.videoIndex = 0;
                    transitionPts.nextPts = 0;
                    transitionPts.currentPts = 0;
                    VideoPlayInfo videoPlayInfo = getVideoPlayInfo(new UriSource(mContext, Uri.parse(mTransitionList.get(mCurrentVideoIndex).mVideoPath)), (int) mTransitionList.get(mCurrentVideoIndex).mTransitionDuration);
                    initParamsAndSeekEx(videoPlayInfo, transitionPts);
                } else {
                    mDecoders.seekTo(SeekMode.FAST_TO_PREVIOUS_SYNC, 0);
                    //seekTo会decode some frame,所以提前在seek完毕后立即渲染会防止部分帧率过高的视频在loop里面渲染慢
                    mDecoders.renderFrames();
                }

                mPlaybackCompleted = false;

                YYLog.info(TAG, "mAudioMinPTS:" + mAudioMinPTS + ", mVideoMinPTS" + mVideoMinPTS);
            }

            // reset time (otherwise playback tries to "catch up" time after a pause)
            mStandaloneMediaClock.startAt(mDecoders.getCurrentVideoDecodingPTS());

            if (mAudioPlayback != null) {
                mHandler.removeMessages(PLAYBACK_PAUSE_AUDIO);
                mAudioPlayback.play();
            }

            mPlaybackSpeed = mStandaloneMediaClock.getSpeed();
            // Sync audio playback speed to playback speed (to account for speed changes during pause)
            if (mAudioPlayback != null) {
                if (!TimeEffectParameter.instance().IsExistTimeEffect()) {  // 时间特效，只改变视频的播放速度，音频不变
                    mAudioPlayback.setPlaybackSpeed((float) mPlaybackSpeed);
                }
            }

            mHandler.removeMessages(PLAYBACK_LOOP);
            loopInternal();
        }

        private void pauseInternal(boolean drainAudioPlayback) {
            // When playback is paused in timed API21 render mode, the remaining cached frames will
            // still be rendered, resulting in a short but noticeable pausing lag. This can be avoided
            // by switching to the old render timing mode.
            mHandler.removeMessages(PLAYBACK_LOOP); // removes remaining loop requests (required when EOS is reached)
            if (mAudioPlayback != null) {
                if (drainAudioPlayback) {
                    // Defer pausing the audio playback for the length of the playback buffer, to
                    // make sure that all audio samples have been played out.
                    mHandler.sendEmptyMessageDelayed(PLAYBACK_PAUSE_AUDIO,
                            (mAudioPlayback.getQueueBufferTimeUs() + mAudioPlayback.getPlaybackBufferTimeUs()) / 1000 + 1);
                } else {
                    mAudioPlayback.pause(false);
                }
            }
        }

        private void stopInternal() {
            // When playback is paused in timed API21 render mode, the remaining cached frames will
            // still be rendered, resulting in a short but noticeable pausing lag. This can be avoided
            // by switching to the old render timing mode.
            mHandler.removeMessages(PLAYBACK_LOOP); // removes remaining loop requests (required when EOS is reached)
            if (mAudioPlayback != null) {
                mAudioPlayback.stop();
            }
        }


        private void pauseInternal() {
            pauseInternal(false);
        }

        private void pauseInternalAudio() {
            if (mAudioPlayback != null) {
                mAudioPlayback.pause();
            }
        }

        private void repeatRenderInternal() {
            mHandler.removeMessages(PLAYBACK_REPEAT_RENDER);
            if (mPlayerGLManager != null) {
                mPlayerGLManager.repeatRenderFrame();
            }
        }

        private void stopRepeatRenderInternal() {
            mHandler.removeMessages(PLAYBACK_REPEAT_RENDER);
            if (mPlayerGLManager != null) {
                mPlayerGLManager.stopRepeatRenderFrame();
            }
        }

        private void renderLastFrameInternal() {
            mHandler.removeMessages(PLAYBACK_RENDER_LAST_FRAME);
            if (mPlayerGLManager != null) {
                mPlayerGLManager.renderLastFrame();
            }
        }

        private void loopInternal() throws IOException, InterruptedException {
            if (mDecoders.getVideoDecoder() != null && mVideoFrameInfo == null) {
                //恢复到默认值，这个值是用于连续seek时候为了节省性能跳过某些帧不解码用的，如果进入loop，说明seek已经中断
                mDecoders.getVideoDecoder().mLastExtractorSampleTime = -2;

                // This method needs a video frame to operate on. If there is no frame, we need
                // to decode one first.
                mVideoFrameInfo = mDecoders.decodeFrame(false);
                if (mVideoFrameInfo == null && !mDecoders.isEOS()) {
                    mDecoders.decodeAudioFrame();
                    // If the decoder didn't return a frame, we need to give it some processing time
                    // and come back later...
                    mHandler.sendEmptyMessageDelayed(PLAYBACK_LOOP, 10);
                    return;
                }
            }

            long startTime = SystemClock.elapsedRealtime();
            // 以音频时间戳作为同步基准，当视频落后音频时，加速视频播放追赶音频
            boolean needCatchUpAudio = false;

            //根据pts计算,如果当前decode出来的video frame距离render超过60ms,等待10ms重新进入loop
            if (mVideoFrameInfo != null && mStandaloneMediaClock.getOffsetFrom(mVideoFrameInfo.presentationTimeUs) > 60000) {
                mDecoders.decodeAudioFrame();
                mHandler.sendEmptyMessageDelayed(PLAYBACK_LOOP, 10);
                return;
            }

            // Update the current position of the player
            mCurrentDecoderPosition = mDecoders.getCurrentDecodingPTS();
            mCurrentVideoDecoderPosition = mDecoders.getCurrentVideoDecodingPTS();

            if (mDecoders.getVideoDecoder() != null && mVideoFrameInfo != null) {
                mVideoFrameInfo.unityPtsUs = TransitionTimeUtils.ptsToUnityPts(mVideoFrameInfo.presentationTimeUs, mTransitionList, mCurrentVideoIndex);

                //根据pts计算stream clock与system clock,到了video render的时间点(距离render 60ms以内),执行render frame
                needCatchUpAudio = renderVideoFrame(mVideoFrameInfo);
                if (mAVSyncBehavior == MediaConst.AV_SYNC_BEHAVIOR_DROP_FRAME) {
                    if (needCatchUpAudio) {
                        mDropFrameCount++;
                    }
                    if (!mLastNeedCatchupAudioFlag && needCatchUpAudio) {
                        mLastTime = System.currentTimeMillis();
                    }
                    /** 追赶完成 */
                    if (mLastNeedCatchupAudioFlag && !needCatchUpAudio) {
                        YYLog.info(TAG, "catch up cost time " + (System.currentTimeMillis() - mLastTime) + " mDropFrameCount " + mDropFrameCount);
                        mLastTime = 0;
                        mDropFrameCount = 0;
                    }
                    mLastNeedCatchupAudioFlag = needCatchUpAudio;
                }

                if (mOnProgresslistener != null) {
                    mOnProgresslistener.onProgress(mVideoFrameInfo.unityPtsUs / 1000);
                }

                mVideoFrameInfo = null;

                // When the first frame is rendered, video rendering has started and the event triggered
                if (mRenderingStarted) {
                    mRenderingStarted = false;
                    mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                            MEDIA_INFO_VIDEO_RENDERING_START, 0));
                }
            }

            if (mAudioPlayback != null) {
                // Sync audio playback speed to playback speed (to account for speed changes during playback)
                // Change the speed on the audio playback object only if it has really changed, to avoid runtime overhead
                if (mPlaybackSpeed != mStandaloneMediaClock.getSpeed()) {
                    mPlaybackSpeed = mStandaloneMediaClock.getSpeed();
                    if (!TimeEffectParameter.instance().IsExistTimeEffect()) {  // 时间特效，只改变视频的播放速度，音频不变
                        mAudioPlayback.setPlaybackSpeed((float) mPlaybackSpeed);
                    }
                }

                // Sync timebase to audio timebase when there is audio data available
                long currentAudioPTS = mAudioPlayback.getCurrentPresentationTimeUs();

                //存在audio track的情况下, 每次render完一帧video frame, 重置system clock和stream clock,相当于用audio当前播放的frame做一次同步
                if (currentAudioPTS > AudioPlayback.PTS_NOT_SET) {
                    if (!TimeEffectParameter.instance().IsExistTimeEffect()) {  // 时间特效，只改变视频的播放速度，音频不变
                        mStandaloneMediaClock.startAtIncrase(currentAudioPTS);
                    }
                }
            }

            // Handle EOS
            if (mDecoders.isEOS() && mNextPlayInfo != null) {
                mCurrentVideoIndex++;
                initParamsAndSeek(mNextPlayInfo);
            } else if (mDecoders.isEOS()) {
                YYLog.info(TAG, "stopInternal");
                stopInternal();
                mPaused = true;
                mEventHandler.sendEmptyMessage(MEDIA_PLAYBACK_COMPLETE);
                mPlaybackCompleted = true;
            } else {
                // Get next frame
                mVideoFrameInfo = mDecoders.decodeFrame(false);
            }

            if (!mPaused) {
                // Static delay time until the next call of the playback loop
                long delay = 10;
                // Scale delay by playback speed to avoid limiting frame rate
                delay = (long) (delay / mStandaloneMediaClock.getSpeed());
                // Calculate the duration taken for the current call
                long duration = (SystemClock.elapsedRealtime() - startTime);
                // Adjust the delay by the time taken
                delay = delay - duration;

                if (delay > 0 && !needCatchUpAudio) {
                    // Sleep for some time and then continue processing the loop
                    // This replaces the very unreliable and jittery Thread.sleep in the old decoder thread
                    mHandler.sendEmptyMessageDelayed(PLAYBACK_LOOP, delay);
                } else {
                    // The current call took too much time; there is no time left for delaying, call instantly
                    mHandler.sendEmptyMessage(PLAYBACK_LOOP);
                }
            }
        }

        private void seekInternal(long usec) throws IOException, InterruptedException {
            YYLog.info(TAG, "seekInternal:" + usec);
            mPlaybackCompleted = false;
            if (mVideoFrameInfo != null) {
                // A decoded video frame is waiting to be rendered, dismiss it
                mDecoders.getVideoDecoder().dismissFrame(mVideoFrameInfo);
                mVideoFrameInfo = null;
            }

            // Clear the audio cache
            if (mAudioPlayback != null) mAudioPlayback.pause(true);
            if (mAudioPlayback != null) mAudioPlayback.seek(usec);

            if (mTransitionList != null) {
                TransitionPts transitionPts = TransitionTimeUtils.unityPtsToPts(usec, mTransitionList);
                if (transitionPts.videoIndex == mCurrentVideoIndex) {
                    if (mDecoders.getVideoDecoderForTransition() != null) {
                        mDecoders.getVideoDecoderForTransition().seekTo(mSeekMode, transitionPts.nextPts);
                    }
                    // Seek to the target time
                    FrameInfo vfi = mDecoders.seekTo(mSeekMode, transitionPts.currentPts);
                    vfi.unityPtsUs = TransitionTimeUtils.ptsToUnityPts(vfi.presentationTimeUs, mTransitionList, mCurrentVideoIndex);
                    vfi.needDrawImage = true;
                    if (transitionPts.nextPts != -1) {
                        vfi.drawWithTwoSurface = true;
                    }
                } else {
                    mCurrentVideoIndex = transitionPts.videoIndex;
                    VideoPlayInfo videoPlayInfo = getVideoPlayInfo(new UriSource(mContext, Uri.parse(mTransitionList.get(transitionPts.videoIndex).mVideoPath)), (int) transitionPts.currentPts);
                    initParamsAndSeekEx(videoPlayInfo, transitionPts);
                }
            } else {
                if (TimeEffectParameter.instance().IsExistTimeEffect()) {
                    //如果加了时间特效，音视频分开seek
                    MediaDecoder audioDecoder = mDecoders.getAudioDecoder();
                    if (audioDecoder != null) {
                        audioDecoder.seekTo(mSeekMode, usec);
                    }
                    MediaDecoder videoDecoder = mDecoders.getVideoDecoder();
                    if (videoDecoder != null) {
                        videoDecoder.seekTo(mSeekMode, (long) TimeEffectParameter.instance().audioPtsToVideoPts(usec / 1000) * 1000);
                    }
                } else {
                    mDecoders.seekTo(mSeekMode, usec);
                }
            }

            // Reset time to keep frame rate constant
            // (otherwise it's too fast on back seeks and waits for the PTS time on fw seeks)
            mStandaloneMediaClock.startAt(mDecoders.getCurrentVideoDecodingPTS());

            // Check if another seek has been issued in the meantime
            boolean newSeekWaiting = mHandler.hasMessages(PLAYBACK_SEEK);

            // Render seek target frame (if no new seek is waiting to be processed)
            // 如果需要实时seek，需要依次响应所有seek操作
            if (newSeekWaiting && !mSeekInTime) {
                mDecoders.dismissFrames();
            } else {
                mDecoders.renderFrames();
            }

            // When there are no more seek requests in the queue, notify of finished seek operation
            if (!newSeekWaiting) {
                // Set the final seek position as the current position
                // (the final seek position may be off the initial target seek position)
                mCurrentDecoderPosition = mDecoders.getCurrentDecodingPTS();
                mCurrentVideoDecoderPosition = mDecoders.getCurrentVideoDecodingPTS();
                mSeeking = false;
                mAVLocked = false;

                mEventHandler.sendEmptyMessage(MEDIA_SEEK_COMPLETE);

                if (!mPaused) {
                    playInternal();
                }
            }
        }

        private void releaseInternal() {
            YYLog.info(TAG, "releaseInternal begin");

            if (mDecoders != null) {
                if (mVideoFrameInfo != null) {
                    mDecoders.getVideoDecoder().releaseFrame(mVideoFrameInfo);
                    mVideoFrameInfo = null;
                }
            }

            if (mDecoders != null) {
                mDecoders.release();
            }
            if (mAudioPlayback != null) mAudioPlayback.stopAndRelease();
            if (mAudioExtractor != null & mAudioExtractor != mVideoExtractor) {
                mAudioExtractor.release();
            }
            if (mVideoExtractor != null) mVideoExtractor.release();

            if (mPlayerGLManager != null) {
                mPlayerGLManager.release();
                mPlayerGLManager = null;
            }

            mSurface = null;
            mSurfaceHolder = null;

            YYLog.info(TAG, "interrupt and quit to avoid all further execution of messages in the queue and exit");

            // post interrupt to avoid all further execution of messages/events in the queue
            interrupt();
            // quit message processing and exit thread
            quit();

            YYLog.info(TAG, "PlaybackThread destroyed");

            // Notify #release() that it can now continue because #releaseInternal is finished
            synchronized (mReleaseSyncLock) {
                YYLog.info(TAG, "releaseInternal mReleaseSyncLock notify");
                mReleaseSyncLock.notify();
            }
        }


        //渲染当前video frame,frame渲染返回true,frame丢弃返回false
        private boolean renderVideoFrame(FrameInfo videoFrameInfo) throws InterruptedException {
            if (videoFrameInfo.endOfStream) {
                // The EOS frame does not contain a video frame, so we dismiss it
                mDecoders.getVideoDecoder().dismissFrame(videoFrameInfo);
                return false;
            }

            // Calculate waiting time until the next frame's PTS
            // The waiting time might be much higher that a frame's duration because timed API21
            // rendering caches multiple released output frames before actually rendering them.
            long waitingTime = mStandaloneMediaClock.getOffsetFrom(videoFrameInfo.presentationTimeUs);
            if (waitingTime < -1000) {
                // 表明根据pts计算,当前video frame已经过了render的时间点,需要catch up
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                        MEDIA_INFO_VIDEO_TRACK_LAGGING, 0));
                //如果过了render正常渲染点30ms, 采取丢帧策略
                if (mAVSyncBehavior == MediaConst.AV_SYNC_BEHAVIOR_DROP_FRAME) {
                    if (waitingTime < -30000) {
                        YYLog.info(TAG, "dismiss video frame for catch up playback");
                        mDecoders.getVideoDecoder().dismissFrame(videoFrameInfo);
                        return true;
                    }
                }
            }

            // Defer the video size changed message until the first frame of the new size is being rendered
            if (videoFrameInfo.representationChanged) {
                IVideoDecoder mediaCodecVideoDecoder = (IVideoDecoder) mDecoders.getVideoDecoder();
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_SET_VIDEO_SIZE,
                        mediaCodecVideoDecoder.getVideoWidth(), mediaCodecVideoDecoder.getVideoHeight()));
            }

            // Slow down playback, if necessary, to keep frame rate
            if (waitingTime > 5000) {
                mRenderSleepCnt++;
                Thread.sleep(waitingTime / 1000);
            }
            // Release the current frame and render it to the surface
            mDecoders.renderVideoFrame(videoFrameInfo, mTransitionList);
            return false;
        }
    }

    /**
     * Interface definition for a callback to be invoked when the media
     * source is ready for playback.
     */
    public interface OnPreparedListener {
        /**
         * Called when the media file is ready for playback.
         *
         * @param mp the MediaPlayer that is ready for playback
         */
        void onPrepared(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when the media source is ready
     * for playback.
     *
     * @param listener the callback that will be run
     */
    public void setOnPreparedListener(OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    /**
     * Interface definition for a callback to be invoked when playback of
     * a media source has completed.
     */
    public interface OnCompletionListener {
        /**
         * Called when the end of a media source is reached during playback.
         *
         * @param mp the MediaPlayer that reached the end of the file
         */
        void onCompletion(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when the end of a media source
     * has been reached during playback.
     *
     * @param listener the callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked when a seek
     * is issued.
     */
    public interface OnSeekListener {
        /**
         * Called to indicate that a seek operation has been started.
         *
         * @param mp the mediaPlayer that the seek was called on
         */
        public void onSeek(MediaPlayer mp);
    }

    /**
     * Register a calback to be invoked when a seek operation has been started.
     *
     * @param listener the callback that will be run
     */
    public void setOnSeekListener(OnSeekListener listener) {
        mOnSeekListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked indicating
     * the completion of a seek operation.
     */
    public interface OnSeekCompleteListener {
        /**
         * Called to indicate the completion of a seek operation.
         *
         * @param mp the MediaPlayer that issued the seek operation
         */
        public void onSeekComplete(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when a seek operation has been
     * completed.
     *
     * @param listener the callback that will be run
     */
    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked when the
     * video size is first known or updated
     */
    public interface OnVideoSizeChangedListener {
        /**
         * Called to indicate the video size
         * <p>
         * The video size (width and height) could be 0 if there was no video,
         * no display surface was set, or the value was not determined yet.
         *
         * @param mp     the MediaPlayer associated with this callback
         * @param width  the width of the video
         * @param height the height of the video
         */
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height);
    }

    /**
     * Register a callback to be invoked when the video size is
     * known or updated.
     *
     * @param listener the callback that will be run
     */
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked indicating buffering
     * status of a media resource being streamed over the network.
     */
    public interface OnBufferingUpdateListener {
        /**
         * Called to update status in buffering a media stream received through
         * progressive HTTP download. The received buffering percentage
         * indicates how much of the content has been buffered or played.
         * For example a buffering update of 80 percent when half the content
         * has already been played indicates that the next 30 percent of the
         * content to play has been buffered.
         *
         * @param mp      the MediaPlayer the update pertains to
         * @param percent the percentage (0-100) of the content
         *                that has been buffered or played thus far
         */
        void onBufferingUpdate(MediaPlayer mp, int percent);
    }

    /**
     * Register a callback to be invoked when the status of a network
     * stream's buffer has changed.
     *
     * @param listener the callback that will be run.
     */
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
        mOnBufferingUpdateListener = listener;
    }


    /**
     * Interface definition of a callback to be invoked indicating first frame has been rendered
     */
    public interface OnRenderStartListener {
        /**
         * called to indicate render start
         * @param mp  the MediaPlayer the update pertains to
         */
        void onRenderStart(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when render start
     * @param listener the callback that will be run.
     */
    public void setOnRenderStartListener(OnRenderStartListener listener) {
        mOnRenderStartListener = listener;
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener l) {
        if (mPlayerGLManager != null) {
            mPlayerGLManager.setMediaInfoRequireListener(l);
        }
    }

    /**
     * Unspecified media player error.
     *
     * @see MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_UNKNOWN = 1;

    /**
     * Media server died. In this case, the application must release the
     * MediaPlayer object and instantiate a new one.
     *
     * @see MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_SERVER_DIED = 100;

    /**
     * The video is streamed and its container is not valid for progressive
     * playback i.e the video's index (e.g moov atom) is not at the start of the
     * file.
     *
     * @see MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;

    /**
     * File or network related operation errors.
     */
    public static final int MEDIA_ERROR_IO = -1004;
    /**
     * Bitstream is not conforming to the related coding standard or file spec.
     */
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    /**
     * Bitstream is conforming to the related coding standard or file spec, but
     * the media framework does not support the feature.
     */
    public static final int MEDIA_ERROR_UNSUPPORTED = -1010;
    /**
     * Some operation takes too long to complete, usually more than 3-5 seconds.
     */
    public static final int MEDIA_ERROR_TIMED_OUT = -110;

    /**
     * Interface definition of a callback to be invoked when there
     * has been an error during an asynchronous operation (other errors
     * will throw exceptions at method call time).
     */
    public interface OnErrorListener {
        /**
         * Called to indicate an error.
         *
         * @param mp    the MediaPlayer the error pertains to
         * @param what  the type of error that has occurred:
         *              <ul>
         *              <li>{@link #MEDIA_ERROR_UNKNOWN}
         *              <li>{@link #MEDIA_ERROR_SERVER_DIED}
         *              </ul>
         * @param extra an extra code, specific to the error. Typically
         *              implementation dependent.
         *              <ul>
         *              <li>{@link #MEDIA_ERROR_IO}
         *              <li>{@link #MEDIA_ERROR_MALFORMED}
         *              <li>{@link #MEDIA_ERROR_UNSUPPORTED}
         *              <li>{@link #MEDIA_ERROR_TIMED_OUT}
         *              </ul>
         * @return True if the method handled the error, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the OnCompletionListener to be called.
         */
        boolean onError(MediaPlayer mp, int what, int extra);
    }

    /**
     * Register a callback to be invoked when an error has happened
     * during an asynchronous operation.
     *
     * @param listener the callback that will be run
     */
    public void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    /**
     * The player just pushed the very first video frame for rendering.
     *
     * @see MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;

    /**
     * The video is too complex for the decoder: it can't decode frames fast
     * enough. Possibly only the audio plays fine at this stage.
     *
     * @see MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;

    /**
     * MediaPlayer is temporarily pausing playback internally in order to
     * buffer more data.
     *
     * @see MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BUFFERING_START = 701;

    /**
     * MediaPlayer is resuming playback after filling buffers.
     *
     * @see MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BUFFERING_END = 702;

    /**
     * Interface definition of a callback to be invoked to communicate some
     * info and/or warning about the media or its playback.
     */
    public interface OnInfoListener {
        /**
         * Called to indicate an info or a warning.
         *
         * @param mp    the MediaPlayer the info pertains to.
         * @param what  the type of info or warning.
         *              <ul>
         *              <li>{@link #MEDIA_INFO_VIDEO_TRACK_LAGGING}
         *              <li>{@link #MEDIA_INFO_VIDEO_RENDERING_START}
         *              <li>{@link #MEDIA_INFO_BUFFERING_START}
         *              <li>{@link #MEDIA_INFO_BUFFERING_END}
         *              </ul>
         * @param extra an extra code, specific to the info. Typically
         *              implementation dependent.
         * @return True if the method handled the info, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the info to be discarded.
         */
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    /**
     * Register a callback to be invoked when an info/warning is available.
     *
     * @param listener the callback that will be run
     */
    public void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    public interface onProgressListener {
        void onProgress(long pts);
    }

    public void setOnProgresslistener(onProgressListener listener) {
        mOnProgresslistener = listener;
    }

    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;
    public  static final int MEDIA_RENDER_START = 6;

    public class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MEDIA_PREPARED:
                    YYLog.info(TAG, "onPrepared");
                    if (mOnPreparedListener != null) {
                        mOnPreparedListener.onPrepared(MediaPlayer.this);
                    }
                    return;
                case MEDIA_SEEK_COMPLETE:
                    YYLog.info(TAG, "onSeekComplete");
                    if (mOnSeekCompleteListener != null) {
                        mOnSeekCompleteListener.onSeekComplete(MediaPlayer.this);
                    }
                    return;
                case MEDIA_PLAYBACK_COMPLETE:
                    YYLog.info(TAG, "onPlaybackComplete");
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(MediaPlayer.this);
                    }
                    stayAwake(false);
                    return;
                case MEDIA_SET_VIDEO_SIZE:
                    YYLog.info(TAG, "onVideoSizeChanged");
                    if (mOnVideoSizeChangedListener != null) {
                        mOnVideoSizeChangedListener.onVideoSizeChanged(MediaPlayer.this, msg.arg1, msg.arg2);
                    }
                    return;
                case MEDIA_ERROR:
                    YYLog.error(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
                    boolean error_was_handled = false;
                    if (mOnErrorListener != null) {
                        error_was_handled = mOnErrorListener.onError(MediaPlayer.this, msg.arg1, msg.arg2);
                    }
                    if (mOnCompletionListener != null && !error_was_handled) {
                        mOnCompletionListener.onCompletion(MediaPlayer.this);
                    }
                    stayAwake(false);
                    return;
                case MEDIA_INFO:
                    //YYLog.info(TAG, "onInfo");
                    if (mOnInfoListener != null) {
                        mOnInfoListener.onInfo(MediaPlayer.this, msg.arg1, msg.arg2);
                    }
                    return;
                case MEDIA_BUFFERING_UPDATE:
                    //YYLog.info(TAG, "onBufferingUpdate");
                    if (mOnBufferingUpdateListener != null)
                        mOnBufferingUpdateListener.onBufferingUpdate(MediaPlayer.this, msg.arg1);
                    mBufferPercentage = msg.arg1;
                    return;
                case MEDIA_RENDER_START:
                    if (mOnRenderStartListener != null) {
                        mOnRenderStartListener.onRenderStart(MediaPlayer.this);
                    }
                    return;
                default:
                    // nothing
            }
        }
    }

    private VideoPlayInfo getVideoPlayInfo(MediaSource source, int seekPosition) throws IOException {
        VideoPlayInfo videoPlayInfo = new VideoPlayInfo();
        if (!mUsedForRecordSession && GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {
            videoPlayInfo.mVideoExtractor = new MediaExtractor(MediaConst.MEDIA_EXTRACTOR_TYPE_VIDEO);
            videoPlayInfo.mAudioExtractor = new MediaExtractor(MediaConst.MEDIA_EXTRACTOR_TYPE_AUDIO);
        } else {
            videoPlayInfo.mVideoExtractor = source.getVideoExtractor();
            videoPlayInfo.mAudioExtractor = source.getAudioExtractor();
        }

        if (videoPlayInfo.mVideoExtractor != null && videoPlayInfo.mAudioExtractor == null) {
            videoPlayInfo.mAudioExtractor = videoPlayInfo.mVideoExtractor;
        }

        videoPlayInfo.mVideoTrackIndex = getTrackIndex(videoPlayInfo.mVideoExtractor, "video/");
        videoPlayInfo.mAudioTrackIndex = getTrackIndex(videoPlayInfo.mAudioExtractor, "audio/");

        // Select video track
        if (videoPlayInfo.mVideoTrackIndex != MediaDecoder.INDEX_NONE) {
            videoPlayInfo.mVideoExtractor.selectTrack(videoPlayInfo.mVideoTrackIndex);
            videoPlayInfo.mVideoFormat = videoPlayInfo.mVideoExtractor.getTrackFormat(videoPlayInfo.mVideoTrackIndex);
            videoPlayInfo.mVideoMinPTS = videoPlayInfo.mVideoExtractor.getSampleTime();
            if(GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {
                videoPlayInfo.mVideoMinPTS = 0;
            }
//            YYLog.info(TAG, "selected video track #" + mVideoTrackIndex + " " + mVideoFormat.toString());
        }

        // Select audio track
        if (videoPlayInfo.mAudioTrackIndex != MediaDecoder.INDEX_NONE) {
            videoPlayInfo.mAudioExtractor.selectTrack(videoPlayInfo.mAudioTrackIndex);
            videoPlayInfo.mAudioFormat = videoPlayInfo.mAudioExtractor.getTrackFormat(videoPlayInfo.mAudioTrackIndex);
            videoPlayInfo.mAudioMinPTS = videoPlayInfo.mAudioExtractor.getSampleTime();
            if(GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {
                videoPlayInfo.mAudioMinPTS = 0;
            }
//            YYLog.info(TAG, "selected audio track #" + mAudioTrackIndex + " " + mAudioFormat.toString());
        }

        videoPlayInfo.mSeekPosition = seekPosition;

        return videoPlayInfo;
    }

    public MediaExtractor getAudioExtractor() {
        return mAudioExtractor;
    }

    private void initExtractor(VideoPlayInfo videoPlayInfo) throws IOException {

        if (mAudioExtractor != null & mAudioExtractor != mVideoExtractor) {
            mAudioExtractor.release();
            mAudioExtractor = null;
        }
        if (mVideoExtractor != null) {
            mVideoExtractor.release();
            mVideoExtractor = null;
        }

        mVideoExtractor = videoPlayInfo.mVideoExtractor;
        mAudioExtractor = videoPlayInfo.mAudioExtractor;

        if (mVideoExtractor != null && mAudioExtractor == null) {
            mAudioExtractor = mVideoExtractor;
        }

        mVideoTrackIndex = videoPlayInfo.mVideoTrackIndex;
        mAudioTrackIndex = videoPlayInfo.mAudioTrackIndex;

        // Select video track
        if (mVideoTrackIndex != MediaDecoder.INDEX_NONE) {
            mVideoExtractor.selectTrack(mVideoTrackIndex);
            mVideoFormat = videoPlayInfo.mVideoFormat;
            mVideoMinPTS = videoPlayInfo.mVideoMinPTS;
            YYLog.info(TAG, "selected video track #" + mVideoTrackIndex + " " + mVideoFormat.toString());
        }

        // Select audio track
        if (mAudioTrackIndex != MediaDecoder.INDEX_NONE) {
            mAudioExtractor.selectTrack(mAudioTrackIndex);
            mAudioFormat = videoPlayInfo.mAudioFormat;
            mAudioMinPTS = videoPlayInfo.mAudioMinPTS;
            YYLog.info(TAG, "selected audio track #" + mAudioTrackIndex + " " + mAudioFormat.toString());
        }

        if (mVideoTrackIndex == MediaDecoder.INDEX_NONE) {
            mVideoExtractor = null;
        }

        if (mAudioTrackIndex == MediaDecoder.INDEX_NONE) {
            mAudioExtractor = null;
        }

        if (mVideoTrackIndex == MediaDecoder.INDEX_NONE && mAudioTrackIndex == MediaDecoder.INDEX_NONE) {
            throw new IOException("invalid data source, no supported stream found");
        }
        if (mVideoTrackIndex != MediaDecoder.INDEX_NONE && mPlaybackThread == null && mSurface == null) {
            YYLog.info(TAG, "no video output surface specified");
        }
    }

    public static class VideoPlayInfo {
        MediaExtractor mVideoExtractor;
        int mVideoTrackIndex;
        MediaFormat mVideoFormat;
        long mVideoMinPTS;

        MediaExtractor mAudioExtractor;
        int mAudioTrackIndex;
        MediaFormat mAudioFormat;
        long mAudioMinPTS;
        int mSeekPosition;
    }

    private void initParamsAndSeek(VideoPlayInfo videoPlayInfo) throws IOException {
        int nextPlayIndex = mCurrentVideoIndex + 1;
        if (mTransitionList != null && nextPlayIndex < mTransitionList.size()) {
            mNextPlayInfo = getVideoPlayInfo(new UriSource(mContext, Uri.parse(mTransitionList.get(nextPlayIndex).mVideoPath)), (int) (mTransitionList.get(nextPlayIndex).mTransitionDuration * 1000 * 1000));
        } else {
            mNextPlayInfo = null;
        }

        mDecoders.swapVideoDecoder(mNextPlayInfo);

        initExtractor(videoPlayInfo);
        mDecoders.setMinPts(videoPlayInfo.mVideoMinPTS, videoPlayInfo.mAudioMinPTS);
        mDecoders.getAudioDecoder().reinitCodec(videoPlayInfo.mAudioExtractor, videoPlayInfo.mAudioTrackIndex);
        mDecoders.getAudioDecoder().seekTo(mSeekMode, videoPlayInfo.mSeekPosition);

        mCurrentDecoderPosition = mDecoders.getCurrentDecodingPTS();
        mCurrentVideoDecoderPosition = mDecoders.getCurrentVideoDecodingPTS();
        mStandaloneMediaClock.startAt(mDecoders.getCurrentDecodingPTS());
    }


    private void initParamsAndSeekEx(VideoPlayInfo videoPlayInfo, TransitionPts transitionPts) throws IOException {
        int nextPlayIndex = mCurrentVideoIndex + 1;
        if (mTransitionList != null && nextPlayIndex < mTransitionList.size()) {
            mNextPlayInfo = getVideoPlayInfo(new UriSource(mContext, Uri.parse(mTransitionList.get(nextPlayIndex).mVideoPath)), (int) (mTransitionList.get(nextPlayIndex).mTransitionDuration * 1000 * 1000));
        } else {
            mNextPlayInfo = null;
        }

        mDecoders.releaseVideoDecoderForTransition();

        if (mNextPlayInfo != null) {
            VideoDecoderWithEGL videoDecoderForTransition = new VideoDecoderWithEGL(mNextPlayInfo.mVideoExtractor, mNextPlayInfo.mVideoTrackIndex, mPlayerGLManager);

            if (transitionPts.nextPts != -1) {
                videoDecoderForTransition.seekTo(mSeekMode, transitionPts.nextPts);
            }

            mDecoders.addVideoDecoderForTransition(videoDecoderForTransition);
        }

        mDecoders.reinitCodec(videoPlayInfo);
        initExtractor(videoPlayInfo);
        mDecoders.setMinPts(videoPlayInfo.mVideoMinPTS, videoPlayInfo.mAudioMinPTS);
        FrameInfo vfi = mDecoders.seekTo(mSeekMode, transitionPts.currentPts);
        vfi.unityPtsUs = TransitionTimeUtils.ptsToUnityPts(vfi.presentationTimeUs, mTransitionList, mCurrentVideoIndex);
        vfi.needDrawImage = true;
        if (transitionPts.nextPts != -1) {
            vfi.drawWithTwoSurface = true;
        }

        mCurrentDecoderPosition = mDecoders.getCurrentDecodingPTS();
        mCurrentVideoDecoderPosition = mDecoders.getCurrentVideoDecodingPTS();
        mStandaloneMediaClock.startAt(mDecoders.getCurrentDecodingPTS());
    }

    public int getCurrentAudioPostion(){
        if (mCurrentState.ordinal() > State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("getCurrentPosition mCurrentState:" + backState);
        }
        if (mSeeking) {
            return (int) (mSeekTargetTime / 1000);
        }

        if (mAudioPlayback == null) {
            return -1;
        }

        long audioPosition = mAudioPlayback.getCurrentPresentationTimeUs();
        if (mTransitionList == null) {
        /* During a seek, return the temporary seek target time; otherwise a seek bar doesn't
         * update to the selected seek position until the seek is finished (which can take a
         * while in exact mode). */
            return (int) (audioPosition / 1000);
        } else {
            return (int) (TransitionTimeUtils.ptsToUnityPts(audioPosition, mTransitionList, mCurrentVideoIndex) / 1000);
        }
    }

    public int getCurrentVideoPostion(){
        if (mCurrentState.ordinal() > State.RELEASING.ordinal()) {
            State backState = mCurrentState;
            setCurrentState(State.ERROR);
            throw new IllegalStateException("getCurrentPosition mCurrentState:" + backState);
        }
        if (mSeeking) {
            return (int) (mSeekTargetTime / 1000);
        }

        if (mTransitionList == null) {
        /* During a seek, return the temporary seek target time; otherwise a seek bar doesn't
         * update to the selected seek position until the seek is finished (which can take a
         * while in exact mode). */
            return (int) (mCurrentVideoDecoderPosition / 1000);
        } else {
            return (int) (TransitionTimeUtils.ptsToUnityPts(mCurrentVideoDecoderPosition, mTransitionList, mCurrentVideoIndex) / 1000);
        }
    }

    public void StartRotate() {

        if (mPlayerGLManager != null) {
            mPlayerGLManager.StartRotate();
        }

        if (mPlaybackThread != null /*&& mPlaybackThread.isPaused()*/) {  // 播放器暂停状态下旋转
            YYLog.info(TAG, " StartRotate isPaused: true ." );
            new Thread(new Runnable() {
                @Override
                public void run() {

                    for (int i = 0; i < 9 && mPlayerGLManager != null; i++) {
                        mPlayerGLManager.RenderForRotate();
                        try {
                            Thread.sleep(30);
                        } catch (Exception e) {
                            YYLog.error(TAG, "Exception : " + e.getMessage());
                        }
                    }
                }

            }).start();
        }

    }

    public void setFlutterRotateAngel(int angle) {
        if (mPlayerGLManager != null) {
            mPlayerGLManager.setFlutterRotateAngel(angle);
        }

        if (mPlaybackThread != null /*&& mPlaybackThread.isPaused()*/) {  // 播放器暂停状态下旋转
            YYLog.info(TAG, " StartRotate isPaused: true ." );
            new Thread(new Runnable() {
                @Override
                public void run() {

                    for (int i = 0; i < 1 && mPlayerGLManager != null; i++) {
                        mPlayerGLManager.RenderForRotate();
                        try {
                            Thread.sleep(30);
                        } catch (Exception e) {
                            YYLog.error(TAG, "Exception : " + e.getMessage());
                        }
                    }
                }

            }).start();
        }
    }

    public float getCurrentRotateAngle() {
        if (mPlayerGLManager != null) {
            return mPlayerGLManager.getCurrentRotateAngle();
        }
        return 0;
    }

    public RectF getCurrentVideoRect() {
        if (mPlayerGLManager != null) {
            return mPlayerGLManager.getCurrentVideoRect();
        }
        return null;
    }


    public void setPlayerGLManager(IPlayerGLManager playerGLManager) {
        mGLManger = playerGLManager;
    }

    public void setUsedForRecord(boolean b) {
        mUsedForRecordSession = b;
    }
}
