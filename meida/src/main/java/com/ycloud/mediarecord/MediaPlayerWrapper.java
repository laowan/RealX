package com.ycloud.mediarecord;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.widget.MediaPlayerListener;
import com.ycloud.svplayer.DecoderFactory;
import com.ycloud.svplayer.FrameInfo;
import com.ycloud.svplayer.MediaPlayer;
import com.ycloud.svplayer.MediaSource;
import com.ycloud.svplayer.UriSource;
import com.ycloud.svplayer.surface.IPlayerGLManager;
import com.ycloud.svplayer.surface.PlayerGLManager;
import com.ycloud.utils.ImageStorageUtil;
import com.ycloud.utils.OESTO2DTool;
import com.ycloud.utils.YYLog;
import java.io.IOException;

import static com.ycloud.gpuimagefilter.filter.RecordFilterGroup.GL_UPDATE_VIDEO_SURFACE;

/**
 * 街舞教练特效视频解码渲染
 * Created by Administrator on 2018/3/29.
 */

public class MediaPlayerWrapper implements IPlayerGLManager {

    private static final String TAG = MediaPlayerWrapper.class.getSimpleName();

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnSeekListener mOnSeekListener;
    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnInfoListener mOnInfoListener;
    private MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener;
    private MediaPlayer.OnRenderStartListener mOnRenderStartListener;
    private MediaPlayerListener mMediaPlayerListener = null;

    private int mVideoWidth;
    private int mVideoHeight;
    private MediaPlayer mPlayer;
    private MediaSource mSource;
    private Context mContext;
    private PlayerGLManager.SurfaceWrapper mSurfaceWrapper;
    private MediaFilterContext mVideoFilterContext;
    private Handler mRenderMsgHandle;
    private int mSeekWhenPrepared;
    private float mPlaybackSpeedWhenPrepared;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;
    private boolean mAutoLoop = false;
    private boolean mIsReleased = false;

    public MediaPlayerWrapper(Context context) {
        mContext = context;
        DecoderFactory.setDecodeMode(true);
    }

    public void setMediaFilterContext(MediaFilterContext VideoFilterContext) {
        mVideoFilterContext = VideoFilterContext;
    }

    private void setVideoSource(MediaSource source) {
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        mSource = source;
        mSeekWhenPrepared = 0;
        mPlaybackSpeedWhenPrepared = 1;
        openVideo();
    }

    public void setVideoPath(String path) {
        YYLog.info(TAG, "setVideoPath:" + path);
        setVideoSource(new UriSource(mContext, Uri.parse(path)));
    }

    public void setRenderMSGHandle(Handler handle) {
        mRenderMsgHandle = handle;
    }

    public void setMediaPlayerListener(MediaPlayerListener mediaPlayerListener) {
        mMediaPlayerListener = mediaPlayerListener;
    }

    private void openVideo() {

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
            mPlayer.stop();
            mPlayer = null;
        }

        mPlayer = new MediaPlayer(mContext);
        mPlayer.setUsedForRecord(true);
        mPlayer.setPlayerGLManager(this);
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
            mPlayer.prepareAsync();
            YYLog.info(TAG, "video opened");
        } catch (IOException e) {
            YYLog.error(TAG, "video open failed! " + e.toString());
            exceptionHandler.sendEmptyMessage(0);
        } catch (NullPointerException e) {
            YYLog.error(TAG, "player released while preparing" + e.toString());
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

    public void start() {
        YYLog.info(TAG, "start");
        if (isInPlaybackState()) {
            mPlayer.start();
        }
        mTargetState = STATE_PLAYING;
    }

    public void pause() {
        YYLog.info(TAG, "pause");
        if (isInPlaybackState()) {
            mPlayer.pause();
        }
        mTargetState = STATE_PAUSED;
    }

    public void stopPlayback() {
        YYLog.info(TAG, "stopPlayback");
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer = null;
        }
        if(mSource != null) {
            mSource =null;
        }
        mMediaPlayerListener = null;
        mOnRenderStartListener = null;
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
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

        if (isInPlaybackState()) {
            mPlayer.setPlaybackSpeed(speed);
        }
        mPlaybackSpeedWhenPrepared = speed;
    }

    public void setAutoLoop(boolean loop) {
        YYLog.info(TAG, "setAutoLoop " + loop);
        mAutoLoop = loop;
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

    public int getDuration() {
        return mPlayer != null ? mPlayer.getDuration() : 0;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getCurrentVideoPostion() {
        if (isInPlaybackState()) {
            return mPlayer.getCurrentVideoPostion();
        }
        return 0;
    }

    public void seekTo(int msec) {
        YYLog.info(TAG, "seekTo:" + msec);
        if (isInPlaybackState()) {
            mPlayer.seekTo(msec);
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

    public boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }


    private MediaPlayer.OnPreparedListener mPreparedListener =
            new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mCurrentState = STATE_PREPARED;
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
                        if (90 == rotation || 270 == rotation) {
                            int tmp = mVideoWidth;
                            mVideoWidth = mVideoHeight;
                            mVideoHeight = tmp;
                        }

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

                    if (mAutoLoop) {
                        YYLog.info(TAG, " start loop play again.");
                        seekTo(0);
                        start();
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

    public void release() {
        YYLog.info(TAG, "release begin");
        if (mPlayer != null) {
            mIsReleased = true;
            if (mRenderMsgHandle != null) {
                mRenderMsgHandle.removeMessages(GL_UPDATE_VIDEO_SURFACE);
            }
            mPlayer.release();
            mPlayer = null;
        }
    }

    public void setInputSurface(PlayerGLManager.SurfaceWrapper surfaceWrapper) {
        mSurfaceWrapper = surfaceWrapper;
    }

    @Override
    public PlayerGLManager.SurfaceWrapper getInputSurface() {
        return mSurfaceWrapper;
    }

    @Override
    public void returnSurface(int surfaceIndex) {

    }

    @Override
    public void renderFrame(final FrameInfo frameInfo, int surfaceIndex) {

        if (mRenderMsgHandle != null && mPlayer != null) {

            if (mIsReleased) {
                YYLog.info(TAG, " renderFrame is released!");
                return;
            }

            mRenderMsgHandle.removeMessages(GL_UPDATE_VIDEO_SURFACE);
            mRenderMsgHandle.sendMessage(mRenderMsgHandle.obtainMessage(GL_UPDATE_VIDEO_SURFACE,
                    mVideoWidth, mVideoWidth));
        }
    }

    @Override
    public void processImages(String imageBasePath, int imageRate) {

    }

    @Override
    public void setVideoFilter(VideoFilter videoFilter) {

    }
}
