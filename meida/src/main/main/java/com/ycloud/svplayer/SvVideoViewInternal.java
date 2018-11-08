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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;

import com.ycloud.api.common.BaseVideoView;
import com.ycloud.api.common.IBaseVideoView;
import com.ycloud.api.common.IVideoViewInternal;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.audio.AudioFileCacheMgr;
import com.ycloud.audio.AudioPlayEditor;
import com.ycloud.common.GlobalConfig;
import com.ycloud.gpuimagefilter.filter.PlayerFilterSessionWrapper;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.gpuimagefilter.utils.FilterIDManager;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.widget.MediaPlayerListener;
import com.ycloud.svplayer.surface.ImgProCallBack;
import com.ycloud.svplayer.surface.ImgProGLManager;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.io.IOException;

/**
 * Created by maguggen on 04.06.2014.
 */
public class SvVideoViewInternal implements IVideoViewInternal, MediaController.MediaPlayerControl {

    private static final String TAG = SvVideoViewInternal.class.getSimpleName();

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    public static final int LAYOUT_ORIGINAL = 0;        //原始布局
    public static final int LAYOUT_SCALE_FIT = 1;       //原始比例缩放自适应布局,上下或者左右加黑边
    public static final int LAYOUT_SCALE_FILL = 2;      //原始比例缩放填充布局,超出可见区域裁剪
    public static final int LAYOUT_STRETCH_FILL = 3;    //按照布局拉伸或者缩放视频
    // 本地导入的竖屏视频，在VIVO X20 /  三星S8+等长屏手机中铺满，上下无黑边， 横屏视频上下加黑边，恶心的需求
    public static final int LAYOUT_SCALE_FILL_FOR_LOCAL_VIDEO = 4;

    // 国际版，无预览页，无法知道视频大小，先特殊处理
    public static final int LAYOUT_SCALE_FILL_FOR_LOCAL_VIDEO_INTERNATIONAL = 5;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private MediaSource mSource;
    private MediaPlayer mPlayer;
    private SurfaceHolder mSurfaceHolder;
    private Surface mSurface;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mWindowWidth = 0;
    private int mWindowHeight = 0;
    private int mSeekWhenPrepared;
    private int mLastSeekPosition = 0;
    private float mPlaybackSpeedWhenPrepared;

    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnSeekListener mOnSeekListener;
    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnInfoListener mOnInfoListener;
    private MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener;
    private MediaPlayer.OnRenderStartListener mOnRenderStartListener;

    android.media.MediaPlayer mAudioPlayer = null;
    private float mBackgroundMusicVolume = 1.0f;
    private int mAudioPlayerState = STATE_IDLE;

    private VideoFilter mVideoFilter = null;

    private MediaPlayerListener mMediaPlayerListener = null;

    private float mVideoVolume = -1;

    private int mInitWidth = 0;
    private int mInitHeight = 0;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private int mVideoLayout = LAYOUT_SCALE_FIT;

    private IBaseVideoView mBaseVideoView;

    private boolean mIsVideoReversed;
    private String mCacheDir;
    private static final String mTempReverseFileName = "reverse.mp4";
    private String mVideoPath;

    private PlayerFilterSessionWrapper mPlayerFilterSessionWrapper = null;

    private ImgProGLManager mImgProGLManager;
    private boolean mSourceChanged = false;
    private AudioPlayEditor mAudioPlayEditor;

    private String mBackgroundMusicRhythmPath = null;
    private int mBackgroundMusicStart = 0;
    private int mLayout = 0;
    private boolean mEnableRotate = false;
    private boolean mClockWise = false;
    private int mBackgroundColor = -1;
    private Bitmap mBackgoundBitmap = null;

    private int mBackgroundMusicID = -1;
    //private String mBackgroundMusicPath = null;
    private float mNormalVideoSpeed = 1.0f;
    private boolean mEnableAudioFrequencyCalculate;

    private int mLastRotateAngle = 0;

    public SvVideoViewInternal(IBaseVideoView baseVideoView) {
        mBaseVideoView = baseVideoView;
        mPlayerFilterSessionWrapper = new PlayerFilterSessionWrapper();
    }

    public void enableRotate(boolean rotate) {
        mEnableRotate = rotate;
    }

    public void setRotateDirection(boolean clockwise) {
        mClockWise = clockwise;
    }

    public void setBackGroundColor(int color) {
        mBackgroundColor = color;
    }

    public void setBackGroundBitmap(Bitmap bitmap) {
        mBackgoundBitmap = bitmap;
    }


    public boolean getEnableRotate() {
        return mEnableRotate;
    }

    //将filter设置到player上面
    public void setVFilters(VideoFilter videoFilter) {
        mVideoFilter = videoFilter;
        if (mVideoFilter == null) {
            return;
        }

        if (mPlayer != null) {
            mPlayer.setVideoFilter(mVideoFilter);
        }

        setBackgroundMusicPath(mVideoFilter.mBackgroundMusicFilePath, mVideoFilter.mBackgroundMusicStart);
        //setBackgroundMusicPath(mVideoFilter.mMixedAudioPath, mVideoFilter.mBackgroundMusicStart);
        setVideoVolume(mVideoFilter.getmVideoVolume());
        setBackgroundMusicVolume(mVideoFilter.getMusicVolume());

        //设置节奏识别信息
        setBackgroundMusicRhythmInfo(mVideoFilter.mBackgroundMusicRhythmPath, mVideoFilter.mBackgroundMusicStart);
    }

    @Override
    public PlayerFilterSessionWrapper getPlayerFilterSessionWrapper() {
        return mPlayerFilterSessionWrapper;
    }

    /**
     * 处理封面
     *
     * @param imageBasePath  图片根目录
     * @param imageRate      图片每秒截图数量
     * @param imgProCallBack 完成回调
     */
    public void processImages(String imageBasePath, int imageRate, ImgProCallBack imgProCallBack) {
        if (mImgProGLManager == null) {
            mImgProGLManager = new ImgProGLManager();
            mImgProGLManager.init(mPlayer.getVideoWidth(), mPlayer.getVideoHeight(), mBaseVideoView.getContext());
        }
        String filterJson = mPlayerFilterSessionWrapper.getFilterConfig();
        if (filterJson != null && !filterJson.isEmpty()) {
            // 移除所有处理封面filterGroup中的filter后，用播放器中的json初始化
            mImgProGLManager.getFilterSession().removeAllFilter();
            mImgProGLManager.getFilterSession().addFilter(filterJson, true);
        }

        mImgProGLManager.processImages(imageBasePath, imageRate, imgProCallBack);
    }

    @Override
    public void setVideoVolume(float volume) {
        if (volume >= 0)
            mVideoVolume = volume;

        if (null != mPlayer) {
            mPlayer.setVolume(volume, volume);
        }

        if (null != mVideoFilter)
            mVideoFilter.setVideoVolume(volume);

    }

    public void initVideoView(Context context) {
        if (mBaseVideoView instanceof BaseVideoView) {
            ((BaseVideoView) mBaseVideoView).getHolder().addCallback(this);
        }

        mCacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
        AudioFileCacheMgr.getInstance().init(mCacheDir);
    }

    /**
     * @param source the media source
     */
    public void setVideoSource(MediaSource source) {
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        mSource = source;
        mSourceChanged = true;
        mSeekWhenPrepared = mLastSeekPosition = 0;
        mPlaybackSpeedWhenPrepared = 1;
        openVideo();
        if (mBaseVideoView instanceof BaseVideoView) {
            ((BaseVideoView) mBaseVideoView).requestLayout();
            ((BaseVideoView) mBaseVideoView).invalidate();
        }
    }

    public void setVideoSourceMemory() {
        mCurrentState = STATE_IDLE;
//        mTargetState = STATE_PLAYING;
        mSourceChanged = true;
        mSeekWhenPrepared = mLastSeekPosition = 0;
        mPlaybackSpeedWhenPrepared = 1;
        if (mBaseVideoView instanceof BaseVideoView) {
            ((BaseVideoView) mBaseVideoView).requestLayout();
            ((BaseVideoView) mBaseVideoView).invalidate();
        }
    }


    /**
     * @param path
     * @see android.widget.VideoView#setVideoPath(String)
     */
    public void setVideoPath(String path) {
        YYLog.info(TAG, "setVideoPath:" + path);
        mVideoPath = path;
        setVideoSource(new UriSource(mBaseVideoView.getContext(), Uri.parse(path)));
    }

    @Override
    public void setMediaPlayerListener(MediaPlayerListener mediaPlayerListener) {
        mMediaPlayerListener = mediaPlayerListener;
    }

    private void openVideo() {
        if ((GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 0 && mSource == null) || (mSurfaceHolder == null && mSurface == null)) {
            // not ready for playback yet, will be called again later
            return;
        }

        if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1 && mVideoPath == null) {
            setVideoSourceMemory();
        }

        if (mPlayer != null) {
            mPlayer.setDisplay(null);
            mPlayer.setOnPreparedListener(null);
            mPlayer.setOnSeekListener(null);
            mPlayer.setOnSeekCompleteListener(null);
            mPlayer.setOnCompletionListener(null);
            mPlayer.setOnVideoSizeChangedListener(null);
            mPlayer.setOnErrorListener(null);
            mPlayer.setOnInfoListener(null);
            mPlayer.setOnBufferingUpdateListener(null);
//            mPlayer.stop();
            mPlayer.stopSync();

            mPlayer = null;
        }

        mPlayer = new MediaPlayer(mBaseVideoView.getContext());
        //设置背景音乐的节奏
        mPlayer.setBackgroundMusicRhythmInfo(mBackgroundMusicRhythmPath, mBackgroundMusicStart);
        if (mSurfaceHolder != null) {
            mPlayer.setDisplay(mSurfaceHolder);
        } else if (mSurface != null) {
            mPlayer.setSurface(mSurface);
        }

        if (mEnableRotate) {
            mPlayer.setVideoPath(mVideoPath);
            mPlayer.setEnableRotate(mEnableRotate);
            mPlayer.setRotateDirection(mClockWise);
            mPlayer.setVideoViewSize(mBaseVideoView.getWidth(), mBaseVideoView.getHeight());
            if (mBackgroundColor != -1) {
                mPlayer.setBackgroundColor(mBackgroundColor);
            }
            if (mBackgoundBitmap != null) {
                mPlayer.setBackgroundBitmap(mBackgoundBitmap);
            }
            if (mWindowWidth > 0 && mWindowHeight > 0) {
                mPlayer.setViewPortSize(mWindowWidth, mWindowHeight);
            } else {
                mPlayer.setViewPortSize(mBaseVideoView.getWidth(), mBaseVideoView.getHeight());
            }
            mPlayer.setLayoutMode(mLayout);
        }
        mPlayer.setScreenOnWhilePlaying(true);
        mPlayer.setOnPreparedListener(mPreparedListener);
        mPlayer.setOnSeekListener(mSeekListener);
        mPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
        mPlayer.setOnCompletionListener(mCompletionListener);
        mPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
        mPlayer.setOnErrorListener(mErrorListener);
        mPlayer.setOnInfoListener(mInfoListener);
        mPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
        mPlayer.setOnRenderStartListener(mRenderStartListener);

        // Create a handler for the error message in case an exceptions happens in the following thread
        final Handler exceptionHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                mCurrentState = STATE_ERROR;
                mTargetState = STATE_ERROR;
                mErrorListener.onError(mPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                return true;
            }
        });

        try {
            mCurrentState = STATE_PREPARING;
            mPlayer.setDataSource(mSource);
            mPlayer.setFilterNGSid(mPlayerFilterSessionWrapper.getSessionID());
            mPlayer.prepareAsync();

            if (mVideoFilter != null) {
                mPlayer.setVideoFilter(mVideoFilter);
            }
            YYLog.info(TAG, "video opened");
        } catch (IOException e) {
            YYLog.error(TAG, "video open failed! " + e.toString());

            // Send message to the handler that an error occurred
            // (we don't need a message id as the handler only handles this single message)
            exceptionHandler.sendEmptyMessage(0);
        } catch (NullPointerException e) {
            YYLog.error(TAG, "player released while preparing" + e.toString());
        }

        if (mImgProGLManager != null) {
            mImgProGLManager.unInit();
            mImgProGLManager.init(mPlayer.getVideoWidth(), mPlayer.getVideoHeight(), mBaseVideoView.getContext());
        }
        mSourceChanged = false;
    }

    /**
     * Resizes the video view according to the video size to keep aspect ratio.
     * Code copied from {@link android.widget.VideoView#onMeasure(int, int)}.
     */
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure(" + MeasureSpec.toString(widthMeasureSpec) + ", "
        //        + MeasureSpec.toString(heightMeasureSpec) + ")");
        if (mBaseVideoView instanceof BaseVideoView) {
            int width = ((BaseVideoView) mBaseVideoView).getDefaultSize(mVideoWidth, widthMeasureSpec);
            int height = ((BaseVideoView) mBaseVideoView).getDefaultSize(mVideoHeight, heightMeasureSpec);
            if (mVideoWidth > 0 && mVideoHeight > 0) {

                int widthSpecMode = View.MeasureSpec.getMode(widthMeasureSpec);
                int widthSpecSize = View.MeasureSpec.getSize(widthMeasureSpec);
                int heightSpecMode = View.MeasureSpec.getMode(heightMeasureSpec);
                int heightSpecSize = View.MeasureSpec.getSize(heightMeasureSpec);

                if (widthSpecMode == View.MeasureSpec.EXACTLY && heightSpecMode == View.MeasureSpec.EXACTLY) {
                    // the size is fixed
                    width = widthSpecSize;
                    height = heightSpecSize;

                    // for compatibility, we adjust size based on aspect ratio
                    if (mVideoWidth * height < width * mVideoHeight) {
                        //Log.i("@@@", "image too wide, correcting");
                        width = height * mVideoWidth / mVideoHeight;
                    } else if (mVideoWidth * height > width * mVideoHeight) {
                        //Log.i("@@@", "image too tall, correcting");
                        height = width * mVideoHeight / mVideoWidth;
                    }
                } else if (widthSpecMode == View.MeasureSpec.EXACTLY) {
                    // only the width is fixed, adjust the height to match aspect ratio if possible
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                    if (heightSpecMode == View.MeasureSpec.AT_MOST && height > heightSpecSize) {
                        // couldn't match aspect ratio within the constraints
                        height = heightSpecSize;
                    }
                } else if (heightSpecMode == View.MeasureSpec.EXACTLY) {
                    // only the height is fixed, adjust the width to match aspect ratio if possible
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                    if (widthSpecMode == View.MeasureSpec.AT_MOST && width > widthSpecSize) {
                        // couldn't match aspect ratio within the constraints
                        width = widthSpecSize;
                    }
                } else {
                    // neither the width nor the height are fixed, try to use actual video size
                    width = mVideoWidth;
                    height = mVideoHeight;
                    if (heightSpecMode == View.MeasureSpec.AT_MOST && height > heightSpecSize) {
                        // too tall, decrease both width and height
                        height = heightSpecSize;
                        width = height * mVideoWidth / mVideoHeight;
                    }
                    if (widthSpecMode == View.MeasureSpec.AT_MOST && width > widthSpecSize) {
                        // too wide, decrease both width and height
                        width = widthSpecSize;
                        height = width * mVideoHeight / mVideoWidth;
                    }
                }
            } else {
                // no size yet, just adopt the given spec sizes
            }
            YYLog.info(TAG, "SvVideoViewInternal.onMeasure:width=" + width + ",height=" + height);
            ((BaseVideoView) mBaseVideoView).callSetMeasuredDimension(width, height);
        }
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        this.mOnPreparedListener = l;
    }

    public void setOnSeekListener(MediaPlayer.OnSeekListener l) {
        this.mOnSeekListener = l;
    }

    public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener l) {
        this.mOnSeekCompleteListener = l;
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l) {
        this.mOnCompletionListener = l;
    }

    public void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener l) {
        this.mOnBufferingUpdateListener = l;
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {
        this.mOnErrorListener = l;
    }

    public void setOnInfoListener(MediaPlayer.OnInfoListener l) {
        this.mOnInfoListener = l;
    }

    public void setOnRenderStartListener(MediaPlayer.OnRenderStartListener l) {
        this.mOnRenderStartListener = l;
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener l) {
        mPlayer.setMediaInfoRequireListener(l);
    }

    @Override
    public void start() {
        YYLog.info(TAG, "start");

        if (isInPlaybackState() && (mSurfaceHolder != null || mSurface != null)) {
            mPlayer.start();
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.start();
            }

            YYLog.info(TAG, "VideoPlayer start:");
            if (isInAudioPlaybackState()) {
                YYLog.info(TAG, "AudioPalyer start mAudioPlayerState:" + mAudioPlayerState);
                mAudioPlayer.start();
                mAudioPlayerState = STATE_PLAYING;
            }
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void startRotate() {
        if (mPlayer != null) {
            mPlayer.StartRotate();
        }
    }

    @Override
    public void setFlutterRotateAngel(int angle) {
        if (mPlayer != null) {
            mPlayer.setFlutterRotateAngel(angle);
        }
    }

    @Override
    public float getCurrentRotateAngle() {
        if (mPlayer != null) {
            return mPlayer.getCurrentRotateAngle();
        }

        return 0;
    }

    @Override
    public void setLastRotateAngle(int angle) {
        mLastRotateAngle = angle;
        YYLog.info(TAG, "setLastRotateAngle " + angle);
        if (mPlayer != null) {
            mPlayer.setLastVideoRotate(angle);
        }
    }

    @Override
    public RectF getCurrentVideoRect() {
        if (mPlayer != null) {
            return mPlayer.getCurrentVideoRect();
        }

        return null;
    }

    @Override
    public void pause() {
        YYLog.info(TAG, "pause");

        if (isInPlaybackState()) {
            mPlayer.pause();
        }

        if (mAudioPlayer != null && mAudioPlayerState > STATE_PREPARED) {
            mAudioPlayer.pause();
        }
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.pause();
        }
        mTargetState = STATE_PAUSED;
    }

    public void stopPlayback() {
        YYLog.info(TAG, "stopPlayback");

        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer = null;
        }

        if (mAudioPlayer != null) {
            mAudioPlayer.stop();
            mAudioPlayer.release();
            mAudioPlayer = null;
        }

        if (mImgProGLManager != null) {
            mImgProGLManager.unInit();
            mImgProGLManager = null;
        }

        if (mPlayerFilterSessionWrapper != null) {
            mPlayerFilterSessionWrapper.setFilterInfoListener(null);
            mPlayerFilterSessionWrapper = null;
        }

        if (mSource != null) {
            mSource = null;
        }

        if (mSurfaceHolder != null) {
            mSurfaceHolder = null;
        }

        if(mSurface != null) {
            mSurface = null;
        }

        if (mBaseVideoView != null) {
            mBaseVideoView = null;
        }

        mMediaPlayerListener = null;
        mOnRenderStartListener = null;

        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.stop();
            mAudioPlayEditor.release();
            mAudioPlayEditor = null;
        }
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

        YYLog.info(TAG, " setPlaybackSpeed " + speed);

        if (isInPlaybackState()) {
            // 如果存在时间特效，先记录设置的倍率，在每个PTS动态调整整个播放速度，
            // 整个播放速度 = 时间特效的速度 * 普通设置的速度
            if (TimeEffectParameter.instance().IsExistTimeEffect()) {
                YYLog.info(TAG, " setPlaybackSpeed mNormalVideoSpeed : " + mNormalVideoSpeed);
                mNormalVideoSpeed = speed;
            } else {
                mPlayer.setPlaybackSpeed(speed);
            }
        }
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.setPlaybackRate(speed);
        }
        mPlaybackSpeedWhenPrepared = speed;
    }

    @Override
    public void setAVSyncBehavior(int behavior) {
        if (behavior != MediaConst.AV_SYNC_BEHAVIOR_FASTPLAY
                && behavior != MediaConst.AV_SYNC_BEHAVIOR_DROP_FRAME) {
            return;
        }
        if (mPlayer != null) {
            mPlayer.setAVSyncBehavior(behavior);
        }
    }

    /**
     * Gets the current playback speed. See {@link #setPlaybackSpeed(float)} for details.
     *
     * @return the current playback speed
     */
    public float getPlaybackSpeed() {
        if (isInPlaybackState()) {
            return mPlayer.getPlaybackSpeed();
        } else {
            return mPlaybackSpeedWhenPrepared;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        YYLog.info(TAG, "surfaceCreated called!");
        mSurfaceHolder = holder;
        if (mPlayer == null || mSourceChanged == true) {
            openVideo();
        } else {
            if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1 || mSource != null) {
                mPlayer.setDisplay(mSurfaceHolder);
            }
        }

        if (mTargetState == STATE_PLAYING) {
            start();
        } else if (mTargetState == STATE_PAUSED) {
            YYLog.info(TAG, "surfaceCreated startRepeatRender when in pause state");
            startRepeatRender();
        }
    }

    public void surfaceCreated(Surface surface) {
        YYLog.info(TAG, "surfaceCreated called with surface!");
        mSurface = surface;
        if (mPlayer == null || mSourceChanged == true) {
            openVideo();
        } else {
            if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1 || mSource != null) {
                mPlayer.setSurface(surface);
            }
        }

        if (mTargetState == STATE_PLAYING) {
            start();
        } else if (mTargetState == STATE_PAUSED) {
            YYLog.info(TAG, "surfaceCreated startRepeatRender when in pause state");
            startRepeatRender();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        /*
        YYLog.info(TAG,"surfaceChanged");
        mSurfaceHolder = holder;
        if (mPlayer != null) {
            mPlayer.setDisplay(mSurfaceHolder);
        }
        */
    }

    public void surfaceChanged(Surface surface, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        YYLog.info(TAG, "surfaceDestroyed called!");
        try {
            if (mPlayer != null) {
                mPlayer.setSurface(null);
                YYLog.info(TAG, "set svPlayer display to null for surfaceDestroyed");
                mLastSeekPosition = mPlayer.getCurrentPosition();
            }

            YYLog.info(TAG, "mTargetState:" + mTargetState + ", mCurrentState:" + mCurrentState + ", mLastSeekPosition:" + mLastSeekPosition);
            if (mAudioPlayer != null) {
                mAudioPlayer.pause();
            }
            mTargetState = STATE_PAUSED;
        } catch (Exception e) {
            e.getMessage();
            YYLog.e(TAG, "surfaceDestroyed :" + e.getMessage());
        }

        mSurfaceHolder = null;

    }

    public void surfaceDestroyed() {
        YYLog.info(TAG, "surfaceDestroyed called!");
        try {
            if (mPlayer != null) {
                mPlayer.setDisplay(null);
                YYLog.info(TAG, "set svPlayer display to null for surfaceDestroyed");
                mLastSeekPosition = mPlayer.getCurrentPosition();
            }

            YYLog.info(TAG, "mTargetState:" + mTargetState + ", mCurrentState:" + mCurrentState + ", mLastSeekPosition:" + mLastSeekPosition);
            if (mAudioPlayer != null) {
                mAudioPlayer.pause();
            }
            mTargetState = STATE_PAUSED;
        } catch (Exception e) {
            e.getMessage();
            YYLog.e(TAG, "surfaceDestroyed :" + e.getMessage());
        }
    }

    @Override
    public int getDuration() {
        return mPlayer != null ? mPlayer.getDuration() : 0;
    }

    @Override
    public float getBackgroundMusicVolume() {
        return mBackgroundMusicVolume;
    }

    @Override
    public float getVideoVolume(float volume) {

        if (-1 == mVideoVolume)
            return 1.0f;
        return mVideoVolume;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public int getCurrentVideoPostion() {
        if (isInPlaybackState()) {
            return mPlayer.getCurrentVideoPostion();
        }
        return 0;
    }

    @Override
    public int getCurrentAudioPosition() {
        if (isInPlaybackState()) {
            int position = mPlayer.getCurrentAudioPostion();
            if (position < 0 && mAudioPlayEditor != null) {
                position = (int) mAudioPlayEditor.getCurrentPlayPositionMS();
            }
            return position;
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        YYLog.info(TAG, "seekTo:" + msec);

        if (isInPlaybackState()) {
            mPlayer.seekTo(msec);
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.seek(msec);
            }

            if (isInAudioPlaybackState()) {
                mAudioPlayer.seekTo(msec);
                if (mPlayer.isPlaying() && !mAudioPlayer.isPlaying()) {
                    mAudioPlayer.start();
                }
            } else {
                YYLog.error(TAG, "AudioPlayer no in playback state");
            }

            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    public MediaPlayer.SeekMode getSeekMode() {
        return mPlayer.getSeekMode();
    }

    public void setSeekMode(MediaPlayer.SeekMode seekMode) {
        mPlayer.setSeekMode(seekMode);
    }

    private boolean isInPlaybackState() {
        return mPlayer != null && mCurrentState >= STATE_PREPARED;
    }

    @Override
    public boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return mPlayer != null ? mPlayer.getBufferPercentage() : 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return mPlayer != null ? mPlayer.getAudioSessionId() : 0;
    }

    private MediaPlayer.OnPreparedListener mPreparedListener =
            new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mCurrentState = STATE_PREPARED;
                    if (null != mPlayer && mVideoVolume >= 0)
                        mPlayer.setVolume(mVideoVolume, mVideoVolume);

                    setPlaybackSpeed(mPlaybackSpeedWhenPrepared);

                    if (mOnPreparedListener != null) {
                        mOnPreparedListener.onPrepared(mp);
                    }

                    if (mMediaPlayerListener != null) {
                        Message msg = new Message();
                        msg.what = MediaPlayerListener.MSG_PLAY_PREPARED;
                        mMediaPlayerListener.notify(msg);
                    }

                    int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
                    if (seekToPosition != 0) {
                        seekTo(seekToPosition);
                    }

                    try {
                        mVideoWidth = mp.getVideoWidth();
                        mVideoHeight = mp.getVideoHeight();
                        int rotation = mp.getRotation();
                        YYLog.info(TAG, " rotation " + rotation + " mLastRotateAngle " + mLastRotateAngle);
                        if (90 == rotation || 270 == rotation) {
                            int tmp = mVideoWidth;
                            mVideoWidth = mVideoHeight;
                            mVideoHeight = tmp;

                            if (mEnableRotate && mPlayer != null) {
                                mPlayer.setVideoRotate(rotation);
                            }
                        }

                        if (mEnableRotate && mPlayer != null) {
                            mPlayer.setLastVideoRotate(mLastRotateAngle);
                        }

                        setVideoLayout(mVideoLayout);

                        if (mTargetState == STATE_PLAYING) {
                            start();
                        }
                    } catch (IllegalStateException e) {
                        YYLog.error(TAG, e.getMessage());
                    }
                }
            };

    private MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    mVideoWidth = width;
                    mVideoHeight = height;
                    setVideoLayout(mVideoLayout);
                }
            };

    private MediaPlayer.OnSeekListener mSeekListener = new MediaPlayer.OnSeekListener() {
        @Override
        public void onSeek(MediaPlayer mp) {
            if (mOnSeekListener != null) {
                mOnSeekListener.onSeek(mp);
            }
        }
    };

    private MediaPlayer.OnSeekCompleteListener mSeekCompleteListener =
            new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    if (mOnSeekCompleteListener != null) {
                        mOnSeekCompleteListener.onSeekComplete(mp);
                    }

                    if (mMediaPlayerListener != null) {
                        Message msg = new Message();
                        msg.what = MediaPlayerListener.MSG_PLAY_SEEK_COMPLETED;
                        mMediaPlayerListener.notify(msg);
                    }
                }
            };

    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {

                    if (isInAudioPlaybackState()) {
                        YYLog.info(TAG, "AudioPalyer VideoOnCompletion pause mAudioPlayerState:" + mAudioPlayerState + " AudioPalyer:" + mAudioPlayer);
                        mAudioPlayerState = STATE_PAUSED;
                        try {
                            mAudioPlayer.pause();
                            mAudioPlayer.seekTo(0);

                        } catch (IllegalStateException e) {
                            YYLog.error(TAG, "audio player error when video player complete" + e.getMessage());
                        }
                    }

                    if (mAudioPlayEditor != null) {
                        mAudioPlayEditor.stopPlayAllPlayer(-1);
                        mAudioPlayEditor.pause();
                        mAudioPlayEditor.seek(0);
                    }

                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;

                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mp);
                    }

                    YYLog.info(TAG, " onComplete ");

                    if (mMediaPlayerListener != null) {
                        Message msg = new Message();
                        msg.what = MediaPlayerListener.MSG_PLAY_COMPLETED;
                        mMediaPlayerListener.notify(msg);
                    }
                }
            };

    private MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;
                    if (mOnErrorListener != null) {
                        return mOnErrorListener.onError(mp, what, extra);
                    }

                    if (mBaseVideoView != null) {
                        YYLog.error(TAG, "Cannot play the video");
                    }

                    return true;
                }
            };

    private MediaPlayer.OnInfoListener mInfoListener =
            new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    if (mOnInfoListener != null) {
                        return mOnInfoListener.onInfo(mp, what, extra);
                    }
                    return true;
                }
            };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    if (mOnBufferingUpdateListener != null) {
                        mOnBufferingUpdateListener.onBufferingUpdate(mp, percent);
                    }
                }
            };


    private MediaPlayer.OnRenderStartListener mRenderStartListener =
            new MediaPlayer.OnRenderStartListener() {
                @Override
                public void onRenderStart(MediaPlayer mp) {
                    YYLog.info(TAG, "render start for first video frame");

                    if (mOnRenderStartListener != null) {
                        mOnRenderStartListener.onRenderStart(mp);
                    }
                }
            };


    private android.media.MediaPlayer.OnCompletionListener mBackgroundMusicCompletionListener = new android.media.MediaPlayer.OnCompletionListener() {

        @Override
        public void onCompletion(android.media.MediaPlayer mp) {
            YYLog.info(TAG, "backgroundMuscic onCompletion");
            mAudioPlayerState = STATE_PLAYBACK_COMPLETED;
        }
    };

    private android.media.MediaPlayer.OnPreparedListener mBackgroundMusicOnPreparedListener = new android.media.MediaPlayer.OnPreparedListener() {

        @Override
        public void onPrepared(android.media.MediaPlayer mp) {
            mAudioPlayerState = STATE_PREPARED;
            if (null != mAudioPlayer) {
                if (null != mVideoFilter) {
                    float audioVolume = mVideoFilter.getMusicVolume();
                    mAudioPlayer.setVolume(audioVolume, audioVolume);
                }
                try {
                    int position = getCurrentPosition();
                    int duration = getDuration();
                    if (duration - position > 1000) {
                        mAudioPlayer.seekTo(position);
                        YYLog.info(TAG, "onPrepared audio player seekTo:" + position + ",duration:" + duration);
                    }

                    if (mTargetState == STATE_PLAYING && mPlayer != null && mPlayer.isPlaying()) {
                        mAudioPlayer.start();
                        YYLog.info(TAG, "onPrepared audio player start");
                        mAudioPlayerState = STATE_PLAYING;
                    }
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private android.media.MediaPlayer.OnErrorListener mBackgroundMusicOnErrorListener = new android.media.MediaPlayer.OnErrorListener() {

        @Override
        public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
            YYLog.error(TAG, "audio mediaplayer error:what" + what + " extra:" + extra);
//			if (mAudioPlayerState == STATE_PLAYING)
//			{
//
//				setBackgroundMusicPath(mAudioPath);
//			}

            mAudioPlayerState = STATE_ERROR;
            return false;
        }

    };

    private MediaPlayer.onProgressListener mOnProgresslistener = new MediaPlayer.onProgressListener() {
        @Override
        public void onProgress(long pts) {
            if (TimeEffectParameter.instance().IsExistTimeEffect()) {
                float videoSpeed = TimeEffectParameter.instance().getCurrentSpeed(pts) * mNormalVideoSpeed;
                if (mPlayer != null) {
                    float currentSpeed = mPlayer.getPlaybackSpeed();
                    if (currentSpeed != videoSpeed) {
                        YYLog.info(TAG, " change video speed from " + currentSpeed + " to " + videoSpeed + ". PTS:" + pts);
                        mPlayer.setVideoPlaybackSpeed(videoSpeed);
                    }
                }
            }
        }
    };

    private void setBackgroundMusicPath(String path, double startPosition) {
        //将最终生成的bgm(混合了音效，配乐等)设置到filter中,供export时使用
        if (mVideoFilter != null) {
            //mVideoFilter.setExportBgm(path);
        }

        mAudioPlayerState = STATE_IDLE;

        if (mAudioPlayer != null && mAudioPlayer.isPlaying()) {
            try {
                mAudioPlayer.stop();
            } catch (IllegalStateException e) {
                YYLog.error(TAG, "setBackgroundMusicPath error:" + e.getMessage());
            }
        }

        if (TextUtils.isEmpty(path)) {
            return;
        }

        try {
            YYLog.info(TAG, "init AudioPlayer");
            if (mAudioPlayer == null) {
                //mAudioPlayer = new android.media.MediaPlayer();
            }
            if (mAudioPlayer != null) {
                mAudioPlayer.reset();
                mAudioPlayer.setDataSource(path);
                mAudioPlayer.setOnCompletionListener(mBackgroundMusicCompletionListener);
                mAudioPlayer.setOnPreparedListener(mBackgroundMusicOnPreparedListener);
                mAudioPlayer.setOnErrorListener(mBackgroundMusicOnErrorListener);
                mAudioPlayer.prepareAsync();
                mAudioPlayerState = STATE_PREPARING;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (path != null && mAudioPlayEditor == null) {
            mAudioPlayEditor = new AudioPlayEditor();
        }
        if (mAudioPlayEditor != null) {
            if (mBackgroundMusicID != -1) {
                mAudioPlayEditor.removePlayer(mBackgroundMusicID);
                mBackgroundMusicID = -1;
            }
            if (path != null) {
                mBackgroundMusicID = mAudioPlayEditor.addPlayer(path, (long) startPosition, -1, true, 0);
            }
        }
    }

    protected boolean isInAudioPlaybackState() {
//        return (mAudioPlayer != null && mAudioPlayerState != STATE_ERROR && mAudioPlayerState != STATE_IDLE && mAudioPlayerState != STATE_PREPARING);
        return (mAudioPlayer != null && mAudioPlayerState >= STATE_PREPARED);
    }

    public void setBackgroundMusicVolume(float volume) {
        if (mAudioPlayEditor != null && mBackgroundMusicID != -1) {
            mAudioPlayEditor.setPlayerVolume(mBackgroundMusicID, volume);
        }

        if (null != mAudioPlayer) {
            mBackgroundMusicVolume = volume;
            mAudioPlayer.setVolume(volume, volume);
        }
        if (null != mVideoFilter)
            mVideoFilter.setMusicVolume(volume);

    }

    public void setBackgroundMusicRhythmInfo(String filePath, int start) {
        if (mPlayer != null) {
            mPlayer.setBackgroundMusicRhythmInfo(filePath, start);
        }

        //avoid set rhythm info before player has been created, save info and set when create player
        mBackgroundMusicRhythmPath = filePath;
        mBackgroundMusicStart = start;
    }

    @Override
    public void updateVideoLayout(int fitMode, int windowWidth, int windowHeight) {
        if (mBaseVideoView instanceof BaseVideoView) {
            YYLog.info(TAG, "updateVideoLayout:fitMode=" + fitMode + ",width=" + windowWidth + ",height=" + windowHeight);
            if (mBaseVideoView == null) {
                return;
            }

            if (mEnableRotate) {
                YYLog.info(TAG, " current surface w " + mBaseVideoView.getWidth());
                YYLog.info(TAG, " current surface H " + mBaseVideoView.getHeight());
                mWindowWidth = windowWidth;
                mWindowHeight = windowHeight;
                mLayout = fitMode;

                mSurfaceWidth = windowWidth;
                mSurfaceHeight = windowHeight;
                //return;
            }

            ViewGroup.LayoutParams lp = ((BaseVideoView) mBaseVideoView).getLayoutParams();

            float windowRation = windowWidth / (float) windowHeight;
            float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;

            switch (fitMode) {
                case LAYOUT_SCALE_FIT:
                    if (videoRatio > windowRation) {
                        lp.width = windowWidth;
                        lp.height = (int) (windowWidth / videoRatio);
                    } else {
                        lp.height = windowHeight;
                        lp.width = (int) (windowHeight * videoRatio);
                    }
                    break;
                case LAYOUT_ORIGINAL:
                    //TODO
                    break;
                case LAYOUT_SCALE_FILL:
                    if (videoRatio > windowRation) {
                        lp.height = windowHeight;
                        lp.width = (int) (videoRatio * windowHeight);
                    } else {
                        lp.height = (int) (windowWidth / videoRatio);
                        lp.width = windowWidth;
                    }
                    break;
            }

            if (mEnableRotate) {
                lp.width = windowWidth;
                lp.height = windowHeight;
            }

            ((BaseVideoView) mBaseVideoView).setLayoutParams(lp);
            ((BaseVideoView) mBaseVideoView).getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
            ((BaseVideoView) mBaseVideoView).invalidate();

            mInitWidth = windowWidth;
            mInitHeight = windowHeight;

            YYLog.info(TAG, String.format("VIDEO: %dx%dx%f, Surface: %dx%d, LP: %dx%d, Window: %dx%dx", mVideoWidth, mVideoHeight, videoRatio,
                    mSurfaceWidth, mSurfaceHeight, lp.width, lp.height, windowWidth, windowHeight));
        }
    }

    @Override
    public void setLayoutMode(int layoutMode) {
        this.mVideoLayout = layoutMode;
    }

    /**
     * Set the display options
     *
     * @param layout <ul>
     *               <li>{@link #LAYOUT_ORIGINAL}
     *               <li>{@link #LAYOUT_SCALE_FIT}
     *               <li>{@link #LAYOUT_SCALE_FILL}
     *               <li>{@link #LAYOUT_STRETCH_FILL}
     *               </ul>
     *               video aspect ratio, will audo detect if 0.
     */
    public void setVideoLayout(int layout) {
        if (mBaseVideoView == null /*|| RecordContants.SVPLAYER_ROTATE_SCALE*/) {
            return;
        }

        if (mBaseVideoView instanceof BaseVideoView) {
            YYLog.info(TAG, "setVideoLayout mEnableRotate " + mEnableRotate + " layout " + layout);

            ViewGroup.LayoutParams lp = ((BaseVideoView) mBaseVideoView).getLayoutParams();

            int windowWidth, windowHeight;

            if (mInitWidth == 0 || mInitHeight == 0) {
                mInitWidth = mBaseVideoView.getWidth();
                mInitHeight = mBaseVideoView.getHeight();
            }

            windowWidth = mInitWidth;
            windowHeight = mInitHeight;

            float windowRatio = windowWidth / (float) windowHeight;
            if (mVideoHeight > 0 && mVideoWidth > 0) {
                float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;
                mSurfaceHeight = mVideoHeight;
                mSurfaceWidth = mVideoWidth;

                if (LAYOUT_ORIGINAL == layout && mSurfaceWidth < windowWidth && mSurfaceHeight < windowHeight) {
                    lp.width = (int) (mSurfaceHeight * videoRatio);
                    lp.height = mSurfaceHeight;
                } else if (layout == LAYOUT_SCALE_FILL) {
                    lp.width = windowRatio > videoRatio ? windowWidth : (int) (videoRatio * windowHeight);
                    lp.height = windowRatio < videoRatio ? windowHeight : (int) (windowWidth / videoRatio);
                } else if (layout == LAYOUT_STRETCH_FILL) {
                    lp.width = windowWidth;
                    lp.height = windowHeight;
                } else if (layout == LAYOUT_SCALE_FIT) {
                    lp.width = (windowRatio < videoRatio) ? windowWidth : (int) (videoRatio * windowHeight);
                    lp.height = (windowRatio > videoRatio) ? windowHeight : (int) (windowWidth / videoRatio);
                } else if (layout == LAYOUT_SCALE_FILL_FOR_LOCAL_VIDEO) {
                    if (mVideoWidth < mVideoHeight) {   // 竖屏视频，铺满整个view 同 LAYOUT_SCALE_FILL
                        lp.width = windowRatio > videoRatio ? windowWidth : (int) (videoRatio * windowHeight);
                        lp.height = windowRatio < videoRatio ? windowHeight : (int) (windowWidth / videoRatio);
                    } else {   // 横屏视频, 上下加黑边 同LAYOUT_SCALE_FIT
                        lp.width = (windowRatio < videoRatio) ? windowWidth : (int) (videoRatio * windowHeight);
                        lp.height = (windowRatio > videoRatio) ? windowHeight : (int) (windowWidth / videoRatio);
                    }
                } else if (layout == LAYOUT_SCALE_FILL_FOR_LOCAL_VIDEO_INTERNATIONAL) {
                    if (mVideoWidth < mVideoHeight) {   // 竖屏视频， LAYOUT_SCALE_FIT
                        lp.width = (windowRatio < videoRatio) ? windowWidth : (int) (videoRatio * windowHeight);
                        lp.height = (windowRatio > videoRatio) ? windowHeight : (int) (windowWidth / videoRatio);
                    } else {   // 横屏视频, 上下加黑边 同LAYOUT_SCALE_FIT
                        lp.width = mSurfaceWidth;
                        lp.height = mSurfaceHeight;
                    }
                }

                if (mEnableRotate) {
                    lp.width = windowWidth;
                    lp.height = windowHeight;

                    mSurfaceWidth = windowWidth;
                    mSurfaceHeight = windowHeight;
                }

                ((BaseVideoView) mBaseVideoView).setLayoutParams(lp);
                ((BaseVideoView) mBaseVideoView).getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
                ((BaseVideoView) mBaseVideoView).invalidate();
                YYLog.info(TAG, String.format("setVideoLayout VIDEO: %dx%dx%f, Surface: %dx%d, LP: %dx%d, Window: %dx%dx%f", mVideoWidth, mVideoHeight, videoRatio,
                        mSurfaceWidth, mSurfaceHeight, lp.width, lp.height, windowWidth, windowHeight, windowRatio));
            }
            mVideoLayout = layout;
        }

    }

    public void startRepeatRender() {
        if (mPlayer != null) {
            mPlayer.startRepeatRender();
        }
    }

    public void stopRepeatRender() {
        if (mPlayer != null) {
            mPlayer.stopRepeatRender();
        }
    }

    public void renderLastFrame() {
        if (mPlayer != null) {
            mPlayer.renderLastFrame();
        }
    }

    public void resetSurface() {
        mSource = null;
        mSurfaceHolder = null;
        mSurface = null;
    }

    @Override
    public int addAudioFileToPlay(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long positionMS) {
        if (mAudioPlayEditor == null) {
            mAudioPlayEditor = new AudioPlayEditor();
            mAudioPlayEditor.enableFrequencyCalculate(mEnableAudioFrequencyCalculate);
            if (mPlayer != null && mPlayer.isPlaying()) {
                mAudioPlayEditor.start();
            }
        }
        if (mAudioPlayEditor != null && path != null) {
            return mAudioPlayEditor.addPlayer(path, beginReadPositionMS, endReadPositionMS, loop, positionMS);
        }
        return -1;
    }

    @Override
    public int addMagicAudioToPlay(int positionMS, String[] audioPaths) {
        if (audioPaths == null || audioPaths.length < 3) {
            YYLog.info(TAG, " addMagicAudioToPlay audioPaths is null");
            return -1;
        }
        if (mAudioPlayEditor == null) {
            mAudioPlayEditor = new AudioPlayEditor();
            mAudioPlayEditor.start();
            mAudioPlayEditor.enableFrequencyCalculate(mEnableAudioFrequencyCalculate);
        }
        if (mAudioPlayEditor != null) {
            return mAudioPlayEditor.addMagicPlayer(positionMS, audioPaths);
        }
        return -1;
    }

    @Override
    public void stopPlayAudio(int ID, int stopPositionMS) {
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.stopPlayPlayer(ID, stopPositionMS);
        }
    }

    @Override
    public void removeAudio(int ID) {
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.removePlayer(ID);
        }
    }

    @Override
    public void setAudioVolume(int ID, float volume) {
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.setPlayerVolume(ID, volume);
        }
    }

    @Override
    public String getAudioFilePath() {
        String path = null;
        if (mAudioPlayEditor != null) {
            String magicAudioFilePath = mCacheDir + "magic_audio.wav";
            path = mAudioPlayEditor.record(magicAudioFilePath, getDuration());
            mAudioPlayEditor.seek(getCurrentPosition());
        }
        return path;
    }

    @Override
    public boolean haveMicAudio() {
        MediaExtractor mediaExtractor = null;
        if (mPlayer != null) {
            mediaExtractor = mPlayer.getAudioExtractor();
            if (mediaExtractor != null) {
                try {
                    int count = mediaExtractor.getTrackCount();
                    for (int i = 0; i < count; i++) {
                        MediaFormat format = mediaExtractor.getTrackFormat(i);
                        if (format != null) {
                            String mime = format.getString(MediaFormat.KEY_MIME);
                            if (mime.startsWith("audio/")) {
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    YYLog.e(TAG, " haveMicAudio exception " + e.toString());
                    return false;
                }
            }
        }
        return false;
    }

    public int addEffectAudioToPlay(int positionMS, String[] audioPaths) {
        if (audioPaths == null || audioPaths.length < 3) {
            YYLog.info(TAG, " addEffectAudioToPlay audioPaths is null");
            return -1;
        }
        if (mAudioPlayEditor == null) {
            mAudioPlayEditor = new AudioPlayEditor();
            mAudioPlayEditor.start();
            mAudioPlayEditor.enableFrequencyCalculate(mEnableAudioFrequencyCalculate);
        }
        if (mAudioPlayEditor != null) {
            return mAudioPlayEditor.addEffectPlayer(positionMS, audioPaths);
        }
        return -1;
    }

    public int addErasureAudioToPlay(int position) {
        if (mAudioPlayEditor == null) {
            mAudioPlayEditor = new AudioPlayEditor();
            mAudioPlayEditor.start();
            mAudioPlayEditor.enableFrequencyCalculate(mEnableAudioFrequencyCalculate);
        }
        if (mAudioPlayEditor != null) {
            return mAudioPlayEditor.addErasurePlayer(position);
        }
        return -1;
    }

    @Override
    public void enableAudioFrequencyCalculate(boolean enable) {
        mEnableAudioFrequencyCalculate = enable;
        if (mAudioPlayEditor != null) {
            mAudioPlayEditor.enableFrequencyCalculate(enable);
        }
    }

    @Override
    public int audioFrequencyData(float[] buffer, int len) {
        if (mAudioPlayEditor != null) {
            return mAudioPlayEditor.frequencyData(buffer, len);
        }
        return 0;
    }

    @Override
    public void setTimeEffectConfig(String jsonStr) {
        TimeEffectParameter.instance().setConfig(jsonStr);
        if (mPlayer != null) {
            mPlayer.setOnProgresslistener(mOnProgresslistener);
        }
    }

    @Override
    public int addTimeEffectBegin() {
        return FilterIDManager.getFilterID();
    }

    @Override
    public void addTimeEffectEnd(int segmentId, float startTime, float duration) {
        TimeEffectParameter.instance().addTimeEffect(segmentId, startTime, duration, 1.0f);
    }

    @Override
    public void removeTimeEffect(int segmentId) {
        TimeEffectParameter.instance().removeTimeEffect(segmentId);
        if (!TimeEffectParameter.instance().IsExistTimeEffect()) {
            if (mPlayer != null) {
                YYLog.info(TAG, "All Time Effect removed, recover to normal speed 1.0. ");
                mPlayer.setPlaybackSpeed(1.0f);                     // 删除最后一个时间特效,恢复正常速度
            }
        }
    }

    @Override
    public int addTimeEffect() {
        return FilterIDManager.getFilterID();
    }

    @Override
    public void updateTimeEffect(int segmentId, float startTime, float duration, float playbackSpeed) {
        TimeEffectParameter.instance().addTimeEffect(segmentId, startTime, duration, playbackSpeed);
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public Context getContext() {
        return null;
    }
}
