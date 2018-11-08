package com.ycloud.api.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.view.Surface;

import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.gpuimagefilter.filter.PlayerFilterSessionWrapper;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.widget.MediaPlayerListener;
import com.ycloud.svplayer.DecoderFactory;
import com.ycloud.svplayer.MediaPlayer;
import com.ycloud.svplayer.SvVideoViewInternal;
import com.ycloud.svplayer.surface.ImgProCallBack;
import com.ycloud.utils.YYLog;

/**
 * 提供一个播放器view，由外部传入Surface及SurfaceTexture的width和height，作为渲染画布大小
 * 同时也由外部传入view的width和height，作为glViewPort大小
 *
 * Created by jinyongqing on 2017/7/6.
 */

public class FlutterVideoView implements IBaseVideoView {
    private String TAG = "FlutterVideoView";
    SvVideoViewInternal mVideoViewInternal;
    Context mContext = null;
    int mWidth = 0;
    int mHeight = 0;

    public FlutterVideoView(Context context, Surface surface) {
        mContext = context;
        createVideoViewInternal();
        surfaceCreated(surface);
    }

    public void enableRotate(boolean enable) {
        YYLog.info(TAG, "enableRotate " + enable);
        mVideoViewInternal.enableRotate(enable);
    }

    /**
     * 设置旋转方向， 不调用此接口默认逆时针
     *
     * @param clockwise true ：顺时针 false：逆时针
     */
    public void setRotateDirection(boolean clockwise) {
        YYLog.info(TAG, "setRotateDirection " + clockwise);
        if (mVideoViewInternal instanceof SvVideoViewInternal) {
            mVideoViewInternal.setRotateDirection(clockwise);
        }
    }

    private void createVideoViewInternal() {
        if (mVideoViewInternal == null) {
            DecoderFactory.setDecodeMode(true);
            mVideoViewInternal = new SvVideoViewInternal(this);
            mVideoViewInternal.initVideoView(mContext);
        }
    }

    public void surfaceCreated(Surface surface) {
        YYLog.info(this, "surfaceCreated");
        mVideoViewInternal.surfaceCreated(surface);
    }

    public void surfaceChanged(Surface surface, int format, int width, int height) {
        YYLog.info(this, "surfaceChanged, width=" + width + "height=" + height);
        mVideoViewInternal.surfaceChanged(surface, format, width, height);
    }

    public void surfaceDestroyed() {
        YYLog.info(this, "surfaceDestroyed");
        mVideoViewInternal.surfaceDestroyed();
    }

    @Override
    public float getBackgroundMusicVolume() {
        return mVideoViewInternal.getBackgroundMusicVolume();
    }

    @Override
    public float getVideoVolume(float volume) {
        return mVideoViewInternal.getVideoVolume(volume);
    }

    @Override
    public int getCurrentPosition() {
        return mVideoViewInternal.getCurrentPosition();
    }

    @Override
    public int getCurrentVideoPostion() {
        if (TimeEffectParameter.instance().IsExistTimeEffect()) {   // 带时间特效进度条匀速
            return mVideoViewInternal.getCurrentAudioPosition();
        } else {
            return mVideoViewInternal.getCurrentVideoPostion();
        }
    }

    @Override
    public int getCurrentAudioPosition() {
        return mVideoViewInternal.getCurrentAudioPosition();
    }

    @Override
    public int getDuration() {
        return mVideoViewInternal.getDuration();
    }

    @Override
    public boolean isPlaying() {
        return mVideoViewInternal.isPlaying();
    }

    @Override
    public void setVideoPath(String path) {
        YYLog.info(this, "BaseVideoView.setVideoPath:" + path);
        mVideoViewInternal.setVideoPath(path);
    }

    @Override
    public void setMediaPlayerListener(MediaPlayerListener mediaPlayerListener) {
        YYLog.info(this, "BaseVideoView.setMediaPlayerListener");
        mVideoViewInternal.setMediaPlayerListener(mediaPlayerListener);
    }

    @Override
    public void setVFilters(VideoFilter videoFilter) {
        YYLog.info(this, "BaseVideoView.setVFilters");
        mVideoViewInternal.setVFilters(videoFilter);
    }

    @Override
    public PlayerFilterSessionWrapper getPlayerFilterSessionWrapper() {
        return mVideoViewInternal.getPlayerFilterSessionWrapper();
    }

    @Override
    public void processImages(String imageBasePath, int imageRate, ImgProCallBack imgProCallBack) {
        YYLog.info(TAG, "processImages imageBasePath=" + imageBasePath + " imageRate=" + imageRate);
        mVideoViewInternal.processImages(imageBasePath, imageRate, imgProCallBack);
    }

    @Override
    public void setVideoVolume(float volume) {
        mVideoViewInternal.setVideoVolume(volume);
    }

    @Override
    public void setBackgroundMusicVolume(float volume) {
        mVideoViewInternal.setBackgroundMusicVolume(volume);
    }

    @Override
    public void updateVideoLayout(int fitMode, int windowWidth, int windowHeight) {
        YYLog.info(TAG, "updateVideoLayout");
    }

    @Override
    public void setLayoutMode(int layoutMode) {
        YYLog.info(TAG, "setLayoutMode");
    }

    @Override
    public void start() {
        YYLog.info(this, "FlutterVideoView.start");
        mVideoViewInternal.start();
    }

    public void startRotate() {
        YYLog.info(this, "FlutterVideoView.startRotate");
        throw new RuntimeException("Fultter Video view Not Support startRotate Method.");
    }

    public void setFlutterRotateAngel(int angle) {
        mVideoViewInternal.setFlutterRotateAngel(angle);
    }

    @Override
    public float getCurrentRotateAngle() {
        return mVideoViewInternal.getCurrentRotateAngle();
    }

    @Override
    public void setLastRotateAngle(int angle) {
        if (angle == 90 || angle == 180 || angle == 270 || angle == 0) {
            mVideoViewInternal.setLastRotateAngle(angle);
        } else {
            YYLog.error(TAG, "setLastRotateAngle angle " + angle + " failed. Invalid angle.");
        }
    }

    @Override
    public RectF getCurrentVideoRect() {
        return mVideoViewInternal.getCurrentVideoRect();
    }

    @Override
    public void pause() {
        YYLog.info(this, "FlutterVideoView.pause");
        mVideoViewInternal.pause();
    }

    @Override
    public void seekTo(int msec) {
        YYLog.info(this, "FlutterVideoView.seekTo:" + msec);
        mVideoViewInternal.seekTo(msec);
    }

    @Override
    public void stopPlayback() {
        YYLog.info(this, "FlutterVideoView.stopPlayback");
        mVideoViewInternal.stopPlayback();
        mContext = null;
    }

    @Override
    public void startRepeatRender() {
        mVideoViewInternal.startRepeatRender();
    }

    @Override
    public void stopRepeatRender() {
        mVideoViewInternal.stopRepeatRender();
    }

    @Override
    public void renderLastFrame() {
        YYLog.info(this, "FlutterVideoView.renderLastFrame");
        mVideoViewInternal.renderLastFrame();
    }

    @Override
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mVideoViewInternal.setOnPreparedListener(l);
    }

    public void setOnRenderStartListener(MediaPlayer.OnRenderStartListener l) {
        mVideoViewInternal.setOnRenderStartListener(l);
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        YYLog.info(TAG, "setMediaInfoRequireListener!!!");
        mVideoViewInternal.setMediaInfoRequireListener(listener);
    }

    @Override
    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {
        mVideoViewInternal.setOnErrorListener(l);
    }

    public IVideoViewInternal getVideoViewInternal() {
        return mVideoViewInternal;
    }

    @Override
    public void resetSurface() {
        mVideoViewInternal.resetSurface();
    }

    @Override
    public int addAudioFileToPlay(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long positionMS) {
        return mVideoViewInternal.addAudioFileToPlay(path, beginReadPositionMS, endReadPositionMS, loop, positionMS);
    }

    @Override
    public int addMagicAudioToPlay(int positionMS, String[] audioPaths) {
        return mVideoViewInternal.addMagicAudioToPlay(positionMS, audioPaths);
    }

    @Override
    public void stopPlayAudio(int ID, int stopPositionMS) {
        mVideoViewInternal.stopPlayAudio(ID, stopPositionMS);
    }

    @Override
    public void removeAudio(int ID) {
        mVideoViewInternal.removeAudio(ID);
    }

    @Override
    public void setAudioVolume(int ID, float volume) {
        mVideoViewInternal.setAudioVolume(ID, volume);
    }

    @Override
    public String getAudioFilePath() {
        return mVideoViewInternal.getAudioFilePath();
    }

    @Override
    public boolean haveMicAudio() {
        return mVideoViewInternal.haveMicAudio();
    }


    public int addEffectAudioToPlay(int positionMS, String[] audioPaths) {
        return mVideoViewInternal.addEffectAudioToPlay(positionMS, audioPaths);
    }

    public int addErasureAudioToPlay(int position) {
        return mVideoViewInternal.addErasureAudioToPlay(position);
    }

    @Override
    public void enableAudioFrequencyCalculate(boolean enable) {
        mVideoViewInternal.enableAudioFrequencyCalculate(enable);
    }

    @Override
    public int audioFrequencyData(float[] buffer, int len) {
        return mVideoViewInternal.audioFrequencyData(buffer, len);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        YYLog.info(TAG, "setPlaybackSpeed " + speed);
        mVideoViewInternal.setPlaybackSpeed(speed);
    }

    @Override
    public void setBackGroundColor(int color) {
        mVideoViewInternal.setBackGroundColor(color);
    }

    @Override
    public void setBackGroundBitmap(Bitmap bitmap) {
        mVideoViewInternal.setBackGroundBitmap(bitmap);
    }

    @Override
    public void setTimeEffectConfig(String jsonStr) {
        mVideoViewInternal.setTimeEffectConfig(jsonStr);
    }

    @Override
    public int addTimeEffectBegin() {
        return mVideoViewInternal.addTimeEffectBegin();
    }

    @Override
    public void addTimeEffectEnd(int segmentId, float startTime, float duration) {
        mVideoViewInternal.addTimeEffectEnd(segmentId, startTime, duration);
    }

    @Override
    public void removeTimeEffect(int segmentId) {
        mVideoViewInternal.removeTimeEffect(segmentId);
    }

    @Override
    public void setAVSyncBehavior(int behavior) {
        if (mVideoViewInternal != null) {
            mVideoViewInternal.setAVSyncBehavior(behavior);
        }
    }

    public int addTimeEffect() {
        setTimeEffectConfig(null);
        return mVideoViewInternal.addTimeEffect();
    }

    public void updateTimeEffect(int segmentId, float startTime, float duration, float playbackSpeed) {
        mVideoViewInternal.updateTimeEffect(segmentId, startTime, duration, playbackSpeed);
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }
    
    public void setWidth(int width) {
        mWidth = width;
    }

    public void setHeight(int height) {
        mHeight = height;
    }
}
