package com.ycloud.svplayer;

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * Created by kele on 2017/6/15.
 */

public interface ISvPlayer {

    int MEDIA_ERROR_UNKNOWN = 1;
    int MEDIA_INFO_BUFFERING_START = 701;
    int MEDIA_INFO_BUFFERING_END = 702;

    public interface EventListener {
        //same as ijkplayer
        void onPrepared(ISvPlayer mp);

        void onCompletion(ISvPlayer mp);

        void onBufferingUpdate(ISvPlayer mp, int percent);

        void onVideoSizeChanged(ISvPlayer mp, int width, int height, int sar_num, int sar_den);

        boolean onError(ISvPlayer mp, int what, int extra);
    }

    interface OnPreparedListener {
        void onPrepared(ISvPlayer mp);
    }

    interface OnCompletionListener {
        void onCompletion(ISvPlayer mp);
    }

    interface OnErrorListener {
        boolean onError(ISvPlayer mp, int what, int extra);
    }

    interface OnVideoSizeChangedListener {
        void onVideoSizeChanged(ISvPlayer mp, int width, int height);
    }

    interface OnBufferingUpdateListener {
        void onBufferingUpdate(ISvPlayer mp, int percent);
    }

    interface OnInfoListener {
        boolean onInfo(ISvPlayer mp, int what, int extra);
    }

    interface OnSeekCompleteListener {
        void onSeekComplete(ISvPlayer mp);
    }

    // void onTimedText(ISvPlayer mp, IjkTimedText text);
    /**
     * The player does not have a source to play, so it is neither buffering nor ready to play.
     */
    public static final int STATE_IDLE = 1;

    public static final int STATE_INITIALIZED = 2;

    public static final int STATE_PREPARING = 3;

    public static final int STATE_PREPARED = 3;
    /**
     * The player not able to immediately play from the current position. The cause is
     * render specific, but this state typically occurs when more data needs to be
     * loaded to be ready to play, or more data needs to be buffered for playback to resume.
     */
    public static final int STATE_BUFFERING = 4;

    public static final int STATE_PLAYING = 5;

    public static final int STATE_PAUSED = 6;

    /**
     * The player has finished playing the media.
     */
    public static final int STATE_ENDED = 7;


    public static final int STATE_ERROR = 8;


    /**
     * Register a listener to receive events from the player. The listener's methods will be called on
     * the thread that was used to construct the player. However, if the thread used to construct the
     * player does not have a {@link Looper}, then the listener will be called on the main thread.
     *
     * @param listener The listener to register.
     */
    void addListener(EventListener listener);

    /**
     * Unregister a listener. The listener will no longer receive events from the player.
     *
     * @param listener The listener to unregister.
     */
    void removeListener(EventListener listener);

    /**
     * Returns the current state of the player.
     *
     * @return One of the {@code STATE} constants defined in this interface.
     */
    int getPlaybackState();

    /**
     * Prepares the player to play the provided {@link MediaSource}. Equivalent to
     * {@code prepare(mediaSource, true, true)}.
     */
    void prepare();

    /**
     * Seeks to a position specified in milliseconds in the current window.
     *
     * @param positionMs
     */
    void seekTo(long positionMs);

    /**
     * Stops playback. Use {@code setPlayWhenReady(false)} rather than this method if the intention
     * is to pause playback.
     * <p>
     * Calling this method will cause the playback state to transition to {@link #STATE_IDLE}. The
     * player instance can still be used, and {@link #release()} must still be called on the player if
     * it's no longer required.
     * <p>
     * Calling this method does not reset the playback position.
     */
    void stop();

    void pause();

    void start();

    /**
     * Releases the player. This method must be called when the player is no longer required. The
     * player must not be used after calling this method.
     */
    void release();

    /**
     * Returns the duration of the current video in milliseconds, or {@link SvpConst#TIME_UNSET} if the
     * duration is not known.
     */
    long getDuration();

    /**
     * Returns the playback position in the current window, in milliseconds.
     */
    long getCurrentPosition();

    /**
     * Returns an estimate of the position in the current window up to which data is buffered, in
     * milliseconds.
     */
    long getBufferedPosition();

    /**
     * Returns an estimate of the percentage in the current window up to which data is buffered, or 0
     * if no estimate is available.
     */
    int getBufferedPercentage();

    /**
     * Sets the audio volume, with 0 being silence and 1 being unity gain.
     *
     * @param audioVolume The audio volume.
     */
    public void setVolume(float audioVolume);

    void setVolume(float leftVolume, float rightVolume);

    /**
     * Returns the audio volume, with 0 being silence and 1 being unity gain.
     */
    public float getVolume();

    public void setScreenOnWhilePlaying(boolean screenOn);

    public void setSurface(Surface sf);

    public void surfaceChanged(Surface sf, int width, int height);

    public void surfaceDestroy(Surface sf);


    void setOnPreparedListener(OnPreparedListener listener);

    void setOnCompletionListener(OnCompletionListener listener);

    void setOnErrorListener(OnErrorListener listener);

    boolean setVFilters(String videoFilters);

    boolean isPlaying();

    void setDataSource(String path);

    void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener);

    int getVideoHeight ();

    int getVideoWidth();

    void setOnBufferingUpdateListener(OnBufferingUpdateListener listener);

    void setOnInfoListener(OnInfoListener listener);

    void setOnSeekCompleteListener(OnSeekCompleteListener listener);

    void prepareAsync();
    void setDisplay(SurfaceHolder sh);
}
