/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2012 YIXIA.COM
 * Copyright (C) 2013 Zhang Rui <bbcallen@gmail.com>
 * Copyright (C) 2015 YY.COM

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.player.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.ViewGroup.LayoutParams;
import com.ycloud.api.common.BaseVideoView;
import com.ycloud.api.common.IVideoViewInternal;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.gpuimagefilter.filter.PlayerFilterSessionWrapper;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.IMediaPlayer;
import com.ycloud.player.IMediaPlayer.OnBufferingUpdateListener;
import com.ycloud.player.IMediaPlayer.OnCompletionListener;
import com.ycloud.player.IMediaPlayer.OnErrorListener;
import com.ycloud.player.IMediaPlayer.OnInfoListener;
import com.ycloud.player.IMediaPlayer.OnPreparedListener;
import com.ycloud.player.IMediaPlayer.OnSeekCompleteListener;
import com.ycloud.player.IMediaPlayer.OnVideoSizeChangedListener;
import com.ycloud.player.IjkMediaPlayer;
import com.ycloud.svplayer.surface.ImgProCallBack;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;
import java.io.File;
import java.io.IOException;

//import com.ycloud.svplayer.IVideoView;

/**
 * Displays a video file. The SvVideoViewInternal class can load images from various sources (such as resources or content providers), takes care of computing its measurement from the video so that it can be
 * used in any layout manager, and provides various display options such as scaling and tinting.
 */
@SuppressLint("ClickableViewAccessibility")
public class IjkVideoViewInternal  implements MediaPlayerControl,IVideoViewInternal{
	private static final String TAG = IjkVideoViewInternal.class.getName();

	private Uri mUri;
	private int mDuration;
	private String mUserAgent;

	private static final int STATE_ERROR = -1;
	private static final int STATE_IDLE = 0;
	private static final int STATE_PREPARING = 1;
	private static final int STATE_PREPARED = 2;
	private static final int STATE_PLAYING = 3;
	private static final int STATE_PAUSED = 4;
	private static final int STATE_PLAYBACK_COMPLETED = 5;
	private static final int STATE_SUSPEND = 6;
	private static final int STATE_RESUME = 7;
	private static final int STATE_SUSPEND_UNSUPPORTED = 8;

	private int mCurrentState = STATE_IDLE;
	private int mTargetState = STATE_IDLE;

	private int mAudioPlayerState = STATE_IDLE;

	private int mVideoLayout = VIDEO_LAYOUT_SCALE;
	public static final int VIDEO_LAYOUT_ORIGIN = 0;
	public static final int VIDEO_LAYOUT_SCALE = 1;
	public static final int VIDEO_LAYOUT_STRETCH = 2;
	public static final int VIDEO_LAYOUT_ZOOM = 3;

	private SurfaceHolder mSurfaceHolder = null;
	private IMediaPlayer mMediaPlayer = null;
	private int mVideoWidth;
	private int mVideoHeight;
	private int mVideoSarNum;
	private int mVideoSarDen;
	private int mSurfaceWidth;
	private int mSurfaceHeight;
	private int mInitWidth = 0;
	private int mInitHeight = 0;
	private int mCurrentBufferPercentage;
	private int mSeekWhenPrepared;
	private Context mContext;

	private MediaPlayerListener mpListener = null;
	private String mVFilters = null;
	private VideoFilter mVideoFilter = null;

	MediaPlayer mAudioPlayer = null;
	private String mAudioPath = null;

	private float mVideoVolume = -1;

	private float mBackgroundMusicVolume = 1.0f;
	private boolean mIsSeeking = false;
	private long mAudioStartPlaytime = 0;
    private String mVideoPath;

    private String mCacheDir;
	private boolean mIsVideoReversed;

	private int mLastSeekPoint;

	private static final String mTempReverseFileName = "reverse.mp4";

	private BaseVideoView mBaseVideoView;

	public IjkVideoViewInternal(BaseVideoView baseVideoView)
	{
		mBaseVideoView = baseVideoView;
	}

	/**
	 * 封面处理
	 * @param imageBasePath
	 * @param imageRate
	 */
	public void processImages(String imageBasePath, int imageRate, ImgProCallBack imgProCallBack) {
	}

	/**
	 * This function should be called if video view changed manually by user.
	 */
	public void resetVideoViewSize() {
		mInitWidth = 0;
		mInitHeight = 0;
	}

	/**
	 * Set the display options
	 *
	 * @param layout
	 *            <ul>
	 *            <li>{@link #VIDEO_LAYOUT_ORIGIN}
	 *            <li>{@link #VIDEO_LAYOUT_SCALE}
	 *            <li>{@link #VIDEO_LAYOUT_STRETCH}
	 *            <li>{@link #VIDEO_LAYOUT_ZOOM}
	 *            </ul>
	 *            video aspect ratio, will audo detect if 0.
	 */
	public void setVideoLayout(int layout) {
		LayoutParams lp = mBaseVideoView.getLayoutParams();

		int windowWidth, windowHeight;

		if (mInitWidth == 0 || mInitHeight == 0) {
			mInitWidth = mBaseVideoView.getWidth();
			mInitHeight = mBaseVideoView.getHeight();
		}

		windowWidth = mInitWidth;
		windowHeight = mInitHeight;

		float windowRatio = windowWidth / (float) windowHeight;
		int sarNum = mVideoSarNum;
		int sarDen = mVideoSarDen;
		if (mVideoHeight > 0 && mVideoWidth > 0) {
			float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;
			if (sarNum > 0 && sarDen > 0)
				videoRatio = videoRatio * sarNum / sarDen;
			mSurfaceHeight = mVideoHeight;
			mSurfaceWidth = mVideoWidth;

			if (VIDEO_LAYOUT_ORIGIN == layout && mSurfaceWidth < windowWidth && mSurfaceHeight < windowHeight) {
				lp.width = (int) (mSurfaceHeight * videoRatio);
				lp.height = mSurfaceHeight;
			} else if (layout == VIDEO_LAYOUT_ZOOM) {
				lp.width = windowRatio > videoRatio ? windowWidth : (int) (videoRatio * windowHeight);
				lp.height = windowRatio < videoRatio ? windowHeight : (int) (windowWidth / videoRatio);
			} else {
				boolean full = layout == VIDEO_LAYOUT_STRETCH;
				lp.width = (full || windowRatio < videoRatio) ? windowWidth : (int) (videoRatio * windowHeight);
				lp.height = (full || windowRatio > videoRatio) ? windowHeight : (int) (windowWidth / videoRatio);
			}
			mBaseVideoView.setLayoutParams(lp);
			mBaseVideoView.getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
			Log.d(TAG, String.format("VIDEO: %dx%dx%f[SAR:%d:%d], Surface: %dx%d, LP: %dx%d, Window: %dx%dx%f", mVideoWidth, mVideoHeight, videoRatio, mVideoSarNum, mVideoSarDen,
					mSurfaceWidth, mSurfaceHeight, lp.width, lp.height, windowWidth, windowHeight, windowRatio));
		}
		mVideoLayout = layout;
	}


	/**
	 *  reset player size
	 *  @param fitMode  更新大小的适配模式,自适应，居中，拉伸等
	 *  @param windowWidth  videoview的父窗口宽度
	 *  @param windowHeight videoview的父窗口高度
	 */
	public void updateVideoLayout(int fitMode, int windowWidth, int windowHeight) {
		LayoutParams lp = mBaseVideoView.getLayoutParams();

		float windowRation = windowWidth / (float) windowHeight;
		int sarNum = mVideoSarNum;
		int sarDen = mVideoSarDen;
		float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;
		if (sarNum > 0 && sarDen > 0)
			videoRatio = videoRatio * sarNum / sarDen;

		switch (fitMode) {
			case VIDEO_LAYOUT_SCALE:
				if(videoRatio > windowRation) {
					lp.width = windowWidth;
					lp.height = (int)(windowWidth / videoRatio);
				} else {
					lp.height = windowHeight;
					lp.width = (int)(windowHeight * videoRatio);
				}
				break;
			case VIDEO_LAYOUT_ORIGIN:
				//TODO
				break;
			case VIDEO_LAYOUT_STRETCH:
				//TODO
				break;
		}

		mBaseVideoView.setLayoutParams(lp);
		mBaseVideoView.getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
		mBaseVideoView.invalidate();

		mInitWidth = windowWidth;
		mInitHeight = windowHeight;
	}

	@Override
	public void setLayoutMode(int layoutMode) {
		this.mVideoLayout = layoutMode;
	}

	public void initVideoView(Context ctx) {

        mCacheDir = FileUtils.getDiskCacheDir(ctx) + File.separator;

		mContext = ctx;
		mVideoWidth = 0;
		mVideoHeight = 0;
		mVideoSarNum = 0;
		mVideoSarDen = 0;
		mLastSeekPoint = 0;
		mBaseVideoView.getHolder().addCallback(this);
		mBaseVideoView.setFocusable(true);
		mBaseVideoView.setFocusableInTouchMode(true);
		mBaseVideoView.requestFocus();
		mCurrentState = STATE_IDLE;
		mTargetState = STATE_IDLE;
		if (ctx instanceof Activity)
			((Activity) ctx).setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		//do nothing, use system's onMeasue.
	}

	public boolean isValid() {
		return (mSurfaceHolder != null && mSurfaceHolder.getSurface().isValid());
	}

	public void setVideoPath(String path) {
        mVideoPath  =path;
		setVideoURI(Uri.parse(mVideoPath));
	}

	private void setBackgroundMusicPath(String path) {
		if (mAudioPath == path) {
			return;
		}
		if (TextUtils.isEmpty(path)) {
			if (null != mAudioPlayer && !TextUtils.isEmpty(mAudioPath)) {
				try {
					mAudioPath = "";
					mAudioPlayerState = STATE_IDLE;
					if (mAudioPlayer.isPlaying()) {
						mAudioPlayer.stop();
					}
					mAudioPlayer.reset();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
			}
			return;
		}

		if (null == mAudioPlayer)
			mAudioPlayer = new MediaPlayer();
		else {
			if (!TextUtils.isEmpty(mAudioPath)) {
				try {
					mAudioPlayerState = STATE_IDLE;
					if (mAudioPlayer.isPlaying()) {
						mAudioPlayer.stop();
					}
					mAudioPlayer.reset();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
			}
		}
		mAudioPath = path;
		if (null != mAudioPlayer) {
			Log.d(TAG, "AudioPalyer start");
			try {
				Log.d(TAG, "init AudioPalyer mAudioPath:" + mAudioPath);
				mAudioPlayer.reset();
				mAudioPlayer.setDataSource(mAudioPath);
				mAudioPlayer.setOnCompletionListener(mBackgroundMusicCompletionListener);
				mAudioPlayer.setOnPreparedListener(mBackgroundMusicOnPreparedListener);
				mAudioPlayer.setOnErrorListener(mBackgroundMusicOnErrorListener);
				mAudioPlayer.prepareAsync();
				mAudioPlayerState = STATE_PREPARING;
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
//		start();
	}

	public void setVideoURI(Uri uri) {
		mUri = uri;
		mSeekWhenPrepared = 0;
		openVideo();
		mBaseVideoView.requestLayout();
		mBaseVideoView.invalidate();
	}

	public void setUserAgent(String ua) {
		mUserAgent = ua;
	}

	public void stopPlayback() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
			mTargetState = STATE_IDLE;
			if (isInAudioPlaybackState()) {
				try {
					if (mAudioPlayer.isPlaying()) {
						mAudioPlayer.stop();
					}
					mAudioPlayer.release();
					mAudioPlayerState = STATE_IDLE;
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void startRepeatRender() {

	}

	@Override
	public void stopRepeatRender() {

	}

	@Override
	public void renderLastFrame() {

	}

	@Override
	public void setOnPreparedListener(com.ycloud.svplayer.MediaPlayer.OnPreparedListener l) {

	}

	@Override
	public void setOnErrorListener(com.ycloud.svplayer.MediaPlayer.OnErrorListener l) {

	}

	@Override
	public void setOnRenderStartListener(com.ycloud.svplayer.MediaPlayer.OnRenderStartListener l) {
	}

	@Override
	public void setMediaInfoRequireListener(IMediaInfoRequireListener l) {
	}

	private void openVideo() {
		if (mUri == null || mSurfaceHolder == null)
			return;

		Intent i = new Intent("com.android.music.musicservicecommand");
		i.putExtra("command", "pause");
		mContext.sendBroadcast(i);

		release(false);
		try {
			mDuration = -1;
			mCurrentBufferPercentage = 0;
			// mMediaPlayer = new AndroidMediaPlayer();
			IjkMediaPlayer ijkMediaPlayer = null;
			ijkMediaPlayer = new IjkMediaPlayer();
			ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", "0");
			ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
			ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", "-16");
			ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 12);
			// ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
			if (mUserAgent != null) {
				ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", mUserAgent);
			}
			ijkMediaPlayer.setVFilters(mVFilters);
			mMediaPlayer = ijkMediaPlayer;
			mMediaPlayer.setOnPreparedListener(mPreparedListener);
			mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
			mMediaPlayer.setOnCompletionListener(mCompletionListener);
			mMediaPlayer.setOnErrorListener(mErrorListener);
			mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
			mMediaPlayer.setOnInfoListener(mInfoListener);
			mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
			mMediaPlayer.setDataSource(mUri.toString());
			mMediaPlayer.setDisplay(mSurfaceHolder);
			mMediaPlayer.setScreenOnWhilePlaying(true);
			mMediaPlayer.prepareAsync();
			mCurrentState = STATE_PREPARING;
		} catch (IOException ex) {
			Log.e(TAG, "Unable to open content: " + mUri, ex);
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;
			mErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
			return;
		} catch (IllegalArgumentException ex) {
			Log.e(TAG, "Unable to open content: " + mUri, ex);
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;
			mErrorListener.onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
			return;
		}
	}

	OnVideoSizeChangedListener mSizeChangedListener = new OnVideoSizeChangedListener() {
		public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
			Log.d(TAG, String.format("onVideoSizeChanged: (%dx%d)[%d:%d]", width, height, sarNum, sarDen));
			mVideoWidth = mp.getVideoWidth();
			mVideoHeight = mp.getVideoHeight();
			mVideoSarNum = sarNum;
			mVideoSarDen = sarDen;
			if (mVideoWidth != 0 && mVideoHeight != 0)
				setVideoLayout(mVideoLayout);
		}
	};

	OnPreparedListener mPreparedListener = new OnPreparedListener() {
		public void onPrepared(IMediaPlayer mp) {
			Log.d(TAG, "onPrepared");
			mCurrentState = STATE_PREPARED;
			mTargetState = STATE_PREPARED;

			if (mpListener != null) {
				Message msg = new Message();
				msg.what = MediaPlayerListener.MSG_PLAY_PREPARED;
				mpListener.notify(msg);
			}

			mVideoWidth = mp.getVideoWidth();
			mVideoHeight = mp.getVideoHeight();

			int seekToPosition = mSeekWhenPrepared;

			if (-1 != mVideoVolume)
				mMediaPlayer.setVolume(mVideoVolume, mVideoVolume);

			if (seekToPosition != 0)
				seekTo(seekToPosition);
			if (mVideoWidth != 0 && mVideoHeight != 0) {
				setVideoLayout(mVideoLayout);
				if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
					if (mTargetState == STATE_PLAYING) {
						start();
					}
				}
			} else if (mTargetState == STATE_PLAYING) {
				start();
			}

			if (mCurrentState == STATE_PLAYING && mpListener != null) {
				Message msg = new Message();
				msg.what = MediaPlayerListener.MSG_PLAY_PLAYING;
				mpListener.notify(msg);
			}
		}
	};

	private OnCompletionListener mCompletionListener = new OnCompletionListener() {
		public void onCompletion(IMediaPlayer mp) {
			Log.d(TAG, "onCompletion");
            if (isInAudioPlaybackState()) {
                Log.d(TAG, "AudioPalyer VideoOnCompletion pause mAudioPlayerState:" + mAudioPlayerState + " AudioPalyer:" + mAudioPlayer);
                mAudioPlayerState = STATE_PAUSED;
                if (null != mVideoFilter) {
                    try {
                        String audioPath = mAudioPath;
                        mAudioPath = null;
                        setBackgroundMusicPath(audioPath);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
            mCurrentState = STATE_PLAYBACK_COMPLETED;
			mTargetState = STATE_PLAYBACK_COMPLETED;
			if (mpListener != null) {
				Message msg = new Message();
				msg.what = MediaPlayerListener.MSG_PLAY_COMPLETED;
				mpListener.notify(msg);
			}

		}
	};

	private OnErrorListener mErrorListener = new OnErrorListener() {
		public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
			Log.d(TAG, String.format("Error: %d, %d", framework_err, impl_err));
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;

			if (mpListener != null) {
				Message msg = new Message();
				msg.what = MediaPlayerListener.MSG_PLAY_ERROR;
				mpListener.notify(msg);
			}

			return true;
		}
	};

	private OnBufferingUpdateListener mBufferingUpdateListener = new OnBufferingUpdateListener() {
		public void onBufferingUpdate(IMediaPlayer mp, int percent) {
			mCurrentBufferPercentage = percent;
			if (mpListener != null) {
				Message msg = new Message();
				msg.what = MediaPlayerListener.MSG_PLAY_BUFFERING_UPDATE;
				msg.arg1 = percent;
				mpListener.notify(msg);
			}
		}
	};

	private OnInfoListener mInfoListener = new OnInfoListener() {
		@Override
		public boolean onInfo(IMediaPlayer mp, int what, int extra) {
			Log.d(TAG, String.format("onInfo: (%d, %d)", what, extra));
			if (mMediaPlayer != null) {
				if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
					Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
					Message msg = new Message();
					msg.what = MediaPlayerListener.MSG_PLAY_BUFFERING_START;
					mpListener.notify(msg);
				} else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
					Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
					Message msg = new Message();
					msg.what = MediaPlayerListener.MSG_PLAY_BUFFERING_END;
					mpListener.notify(msg);
				}
			}
			return true;
		}
	};

	private OnSeekCompleteListener mSeekCompleteListener = new OnSeekCompleteListener() {
		@Override
		public void onSeekComplete(IMediaPlayer mp) {
			Log.d(TAG, "onSeekComplete, pos: " + mp.getCurrentPosition() + ", mCurrentState = " + mCurrentState);
			mIsSeeking = false;
			if (mpListener != null) {
				Message msg = new Message();
				msg.what = MediaPlayerListener.MSG_PLAY_SEEK_COMPLETED;
				mpListener.notify(msg);

				if(mLastSeekPoint != 0) {
					Log.d(TAG, "onSeekComplete, seek to mLastSeekPoint:" + mLastSeekPoint);
					seekTo(mLastSeekPoint);
					mLastSeekPoint = 0;
				}
			}
		}
	};

	@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			mSurfaceHolder = holder;
			if (mMediaPlayer != null) {
				mMediaPlayer.setDisplay(mSurfaceHolder);
			}

			mSurfaceWidth = w;
			mSurfaceHeight = h;

			boolean isValidState = (mTargetState == STATE_PLAYING);
			boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
			if (mMediaPlayer != null && isValidState && hasValidSize) {
				Log.e(TAG, "xlb test do seek while surfaceChanged");
				seekTo(mSeekWhenPrepared);
			} else {
				Log.e(TAG, "xlb test do seek while surfaceChanged do nothing");
				seekTo(mSeekWhenPrepared);
			}
		}

	@Override
		public void surfaceCreated(SurfaceHolder holder) {
			mSurfaceHolder = holder;
			if (mMediaPlayer != null && mCurrentState == STATE_SUSPEND && mTargetState == STATE_RESUME) {
				mMediaPlayer.setDisplay(mSurfaceHolder);
				resume();
			} else {
				openVideo();
			}
		}

	@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			mSurfaceHolder = null;
			mSeekWhenPrepared = getCurrentPosition();
			if (mCurrentState != STATE_SUSPEND)
				release(true);
		}

	public void release(boolean cleartargetstate) {
		if (mMediaPlayer != null) {
			mMediaPlayer.reset();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
			if (cleartargetstate)
				mTargetState = STATE_IDLE;
		}

        if (mAudioPlayer != null) {
            try {
                mAudioPlayer.stop();
                mAudioPlayer.release();
                mAudioPlayer = null;
            } catch(IllegalStateException ex) {
                ex.printStackTrace();
            }
        }
	}

	@Override
	public void start() {
		Log.e(TAG,"start isInPlaybackState:" + isInPlaybackState());
		if (isInPlaybackState()) {
			Log.d(TAG, "SvVideoViewInternal preview start play");
			mMediaPlayer.start();
			mCurrentState = STATE_PLAYING;
			if (isInAudioPlaybackState() && !mAudioPlayer.isPlaying()) {
				Log.d(TAG, "AudioPalyer start mAudioPlayerState:" + mAudioPlayerState);
				try {
					mAudioPlayer.start();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}

				mAudioPlayerState = STATE_PLAYING;
				mAudioStartPlaytime = System.currentTimeMillis();
			}
		}
		mTargetState = STATE_PLAYING;
	}

	@Override
	public void pause() {
		if (isInPlaybackState()) {
			Log.d(TAG, "SvVideoViewInternal preview pause play");
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.pause();
				mCurrentState = STATE_PAUSED;
			}
		}

		if (isInAudioPlaybackState() && mAudioPlayer.isPlaying()) {
			mAudioPlayerState = STATE_PAUSED;
			try {
				mAudioPlayer.pause();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			}
		}

		mTargetState = STATE_PAUSED;
	}

	public void resume() {
		if (mSurfaceHolder == null && mCurrentState == STATE_SUSPEND) {
			mTargetState = STATE_RESUME;
		} else if (mCurrentState == STATE_SUSPEND_UNSUPPORTED) {
			openVideo();
		}
	}

	@Override
	public int getDuration() {
		if (isInPlaybackState()) {
			if (mDuration > 0)
				return (int) mDuration;
			mDuration = (int)mMediaPlayer.getDuration();
			return (int) mDuration;
		}
		mDuration = -1;
		return (int) mDuration;
	}

	@Override
	public int getCurrentPosition() {
		if (isInPlaybackState()) {
			long position = mMediaPlayer.getCurrentPosition();
			return (int) position;
		}
		return 0;
	}

	@Override
	public int getCurrentVideoPostion() {
		return 0;
	}

	@Override
	public void seekTo(int msec) {
		Log.d(TAG, "SvVideoViewInternal preview seekTo:" + msec );
		if (mIsSeeking) {
			Log.d(TAG, "SvVideoViewInternal seek will be delay for in seeking state,last seek point:" + mLastSeekPoint);
			mLastSeekPoint	 = msec;
			return;
		}

		if(Math.abs(msec - mDuration) < 200) {
			msec = mDuration - 200;
			Log.d(TAG, "SvVideoViewInternal seek to end,adjust seek position:" + msec);
		}

		if (isInPlaybackState()) {
			mIsSeeking = true;
			mMediaPlayer.seekTo(msec);
			mSeekWhenPrepared = 0;
			if (null != mAudioPlayer) {
				if (!isInAudioPlaybackState())
					return;
				Log.d(TAG, "AudioPalyer seekTo" + msec + " mAudioPlayerState:" + mAudioPlayerState);
				double audtionDuration = mAudioPlayer.getDuration();
				if (null != mVideoFilter) {
					if (msec > audtionDuration && audtionDuration != 0) {
						Log.d(TAG, "AudioPalyer pause mAudioPlayerState:" + mAudioPlayerState);
						mAudioPlayer.pause();
					} else {
						try {
							mAudioPlayer.seekTo((int) msec);
						} catch (IllegalStateException e) {
							e.printStackTrace();
						}

						mAudioStartPlaytime = System.currentTimeMillis();
					}
				}
				try {
					mAudioPlayer.seekTo((int) msec);
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
			}

		} else {
			mSeekWhenPrepared = msec;
		}
	}

	@Override
	public boolean isPlaying() {
		return isInPlaybackState() && mMediaPlayer.isPlaying();
	}

	@Override
	public int getBufferPercentage() {
		if (mMediaPlayer != null)
			return mCurrentBufferPercentage;
		return 0;
	}

	public int getVideoWidth() {
		return mVideoWidth;
	}

	public int getVideoHeight() {
		return mVideoHeight;
	}

	protected boolean isInPlaybackState() {
		return (mMediaPlayer != null && mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
	}

	protected boolean isInAudioPlaybackState() {
		return (mAudioPlayer != null && mAudioPlayerState != STATE_ERROR && mAudioPlayerState != STATE_IDLE && mAudioPlayerState != STATE_PREPARING);
	}

	public void setVFilters(VideoFilter videoFilter) {
	}

	@Override
	public PlayerFilterSessionWrapper getPlayerFilterSessionWrapper() {
		return null;
	}


	public void setMediaPlayerListener(MediaPlayerListener l) {
		mpListener = l;
	}

	private MediaPlayer.OnCompletionListener mBackgroundMusicCompletionListener = new MediaPlayer.OnCompletionListener() {

		@Override
		public void onCompletion(MediaPlayer mp) {
			Log.d(TAG, "backgroundMuscic onCompletion");
			mAudioPlayerState = STATE_PLAYBACK_COMPLETED;
		}
	};

	private MediaPlayer.OnPreparedListener mBackgroundMusicOnPreparedListener = new MediaPlayer.OnPreparedListener() {

		@Override
		public void onPrepared(MediaPlayer mp) {
			mAudioPlayerState = STATE_PREPARED;
			if (null != mAudioPlayer) {
				if (null != mVideoFilter) {
					float audioVolume = mVideoFilter.getMusicVolume();
					if (audioVolume >= 0)
						mAudioPlayer.setVolume(audioVolume, audioVolume);
					float videoVolume = mVideoFilter.getmVideoVolume();
					if (null != mMediaPlayer && videoVolume >= 0)
						mMediaPlayer.setVolume(videoVolume, videoVolume);
				}
				try {
					mAudioPlayer.seekTo(getCurrentPosition());
					if (mTargetState == STATE_PLAYING) {
						start();
						mAudioPlayerState = STATE_PLAYING;
					}
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
			}
		}
	};

	private MediaPlayer.OnErrorListener mBackgroundMusicOnErrorListener = new MediaPlayer.OnErrorListener() {

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			Log.e(TAG, "audio mediaplayer error:what" + what + " extra:" + extra);
//			if (mAudioPlayerState == STATE_PLAYING)
//			{
//				
//				setBackgroundMusicPath(mAudioPath);
//			}		
			
			 mAudioPlayerState = STATE_ERROR;
			return false;
		}

	};

	@Override
	public void setVideoVolume(float volume) {
		if (volume >= 0)
			mVideoVolume = volume;
		if (null != mMediaPlayer) {
			mMediaPlayer.setVolume(volume, volume);
		}

		if (null != mVideoFilter)
			mVideoFilter.setVideoVolume(volume);
	}

	public float getVideoVolume(float volume) {
		if (-1 == mVideoVolume)
			return 1.0f;
		return mVideoVolume;

	}

	public void setBackgroundMusicVolume(float volume) {

		if (null != mAudioPlayer) {
			mBackgroundMusicVolume = volume;
			mAudioPlayer.setVolume(volume, volume);
		}
		if (null != mVideoFilter)
			mVideoFilter.setMusicVolume(volume);

	}

	public float getBackgroundMusicVolume() {
		return mBackgroundMusicVolume;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();

		if(mCacheDir != null) {
			FileUtils.deleteFileSafely(new File(mCacheDir+mTempReverseFileName));
		}
	}

	public void resetSurface() {
		mSurfaceHolder = null;
	}

	@Override
	public int addAudioFileToPlay(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long positionMS) {
		return 0;
	}

	@Override
	public int addMagicAudioToPlay(int positionMS, String[] audioPaths) {
		return 0;
	}

	@Override
	public void stopPlayAudio(int ID, int stopPositionMS) {

	}

	@Override
	public void removeAudio(int ID) {

	}

	@Override
	public void setAudioVolume(int ID, float volume) {

	}

	@Override
	public String getAudioFilePath() {
		return null;
	}

	@Override
	public boolean haveMicAudio() {
		return true;
	}

	@Override
	public int addEffectAudioToPlay(int positionMS, String[] audioPaths) {
		return -1;
	}

	@Override
	public int addErasureAudioToPlay(int position) {
		return -1;
	}

	@Override
	public void enableAudioFrequencyCalculate(boolean enable) {

	}

	@Override
	public int audioFrequencyData(float[] buffer, int len) {
		return 0;
	}

	@Override
	public void startRotate() {

	}

	@Override
	public float getCurrentRotateAngle() {
		return 0;
	}

	@Override
	public RectF getCurrentVideoRect() {
		return null;
	}

	@Override
	public void setPlaybackSpeed(float speed) {

	}

	@Override
	public void setBackGroundColor(int color) {

	}

	@Override
	public void setTimeEffectConfig(String jsonStr) {

	}

	@Override
	public void setBackGroundBitmap(Bitmap bitmap) {

	}
	@Override
	public int addTimeEffectBegin() {
		return 0;
	}

	@Override
	public void addTimeEffectEnd(int segmentId, float startTime, float duration) {

	}

	@Override
	public void removeTimeEffect(int segmentId) {

	}

	@Override
	public int addTimeEffect() {
		return 0;
	}

	@Override
	public void updateTimeEffect(int segmentId, float startTime, float duration, float playbackSpeed) {

	}

	@Override
	public int getCurrentAudioPosition() {
		return 0;
	}

	@Override
	public void setLastRotateAngle(int angle) {

	}

	@Override
	public void setAVSyncBehavior(int behavior) {

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
		return mContext;
	}

	@Override
	public void setFlutterRotateAngel(int angle) {

	}
}
