package com.ycloud.api.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.common.BlackList;
import com.ycloud.gpuimagefilter.filter.PlayerFilterSessionWrapper;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.widget.MediaPlayerListener;
import com.ycloud.svplayer.DecoderFactory;
import com.ycloud.svplayer.MediaPlayer;
import com.ycloud.svplayer.SvVideoViewInternal;
import com.ycloud.svplayer.surface.ImgProCallBack;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.YYLog;

/**
 * 视频播放器类，支持本地视频播放，旋转，实时添加特效
 * Created by Administrator on 2017/7/6.
 */

public class BaseVideoView extends SurfaceView implements SurfaceHolder.Callback,IBaseVideoView {
    private String TAG = "BaseVideoView";
    IVideoViewInternal mVideoViewInternal;
    Context  mContext = null;

    public BaseVideoView(Context context) {
        super(context);
        mContext = context;
        createVideoViewInternal();
    }

    public BaseVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext = context;
        createVideoViewInternal();
    }

    public BaseVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        createVideoViewInternal();
    }

    public void enableRotate(boolean enable) {
        YYLog.info(TAG, "enableRotate " + enable);
        if (mVideoViewInternal instanceof SvVideoViewInternal) {
            ((SvVideoViewInternal)mVideoViewInternal).enableRotate(enable);
        }
    }

    /**
     * 设置旋转方向， 不调用此接口默认逆时针
     * @param clockwise  true ：顺时针 false：逆时针
     */
    public void setRotateDirection(boolean clockwise) {
        YYLog.info(TAG, "setRotateDirection " + clockwise);
        if (mVideoViewInternal instanceof SvVideoViewInternal) {
            ((SvVideoViewInternal)mVideoViewInternal).setRotateDirection(clockwise);
        }
    }

    private void createVideoViewInternal() {
        if (mVideoViewInternal == null) {
            if (RecordDynamicParam.getInstance().getUseIJKPlayer()) {
                DecoderFactory.setDecodeMode(false);
            } else {
                DecoderFactory.setDecodeMode(true);
            }

            if (BlackList.inSoftwareDecoderList(DeviceUtil.getPhoneModel())){
                DecoderFactory.setDecodeMode(false);
            }

            if (RecordDynamicParam.getInstance().getUseFfmpegDecode()) {
                YYLog.info(TAG, "switch to ffmpeg decoder.");
                DecoderFactory.setDecodeMode(false);
            }

            mVideoViewInternal = new SvVideoViewInternal(this);
            mVideoViewInternal.initVideoView(mContext);
            //enableRotate(true);  // test only
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        YYLog.info(this, "BaseVideoView.surfaceCreated");
        mVideoViewInternal.surfaceCreated(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        YYLog.info(this, "BaseVideoView.surfaceChanged, width="+width + "height="+height);
        mVideoViewInternal.surfaceChanged(holder, format, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        YYLog.info(this, "BaseVideoView.surfaceDestroyed");
        mVideoViewInternal.surfaceDestroyed(holder);
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
        YYLog.info(this, "BaseVideoView.setVideoPath:"+path);
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
        YYLog.info(TAG, "updateVideoLayout  fitMode " + fitMode + " windowWidth " + windowWidth + " windowHeight " + windowHeight);
        if (mVideoViewInternal instanceof SvVideoViewInternal) {
            if (((SvVideoViewInternal) mVideoViewInternal).getEnableRotate()) {
                YYLog.info(TAG, "in Rotate mode, don't set updateVideoLayout . return.");
                return;
            }
        }

        mVideoViewInternal.updateVideoLayout(fitMode, windowWidth, windowHeight);
    }

    @Override
    public void setLayoutMode(int layoutMode) {
        mVideoViewInternal.setLayoutMode(layoutMode);
    }

    @Override
    public void start() {
        YYLog.info(this, "BaseVideoView.start");
        mVideoViewInternal.start();
    }

    public void startRotate() {
        YYLog.info(this, "BaseVideoView.startRotate");
        mVideoViewInternal.startRotate();
    }

    @Override
    public float getCurrentRotateAngle() {
        return mVideoViewInternal.getCurrentRotateAngle();
    }

    @Override
    public void setLastRotateAngle(int angle) {
        if(angle == 90 || angle == 180 || angle == 270 || angle == 0) {
            mVideoViewInternal.setLastRotateAngle(angle);
        } else {
            YYLog.error(TAG,"setLastRotateAngle angle " + angle + " failed. Invalid angle.");
        }
    }

    @Override
    public RectF getCurrentVideoRect() {
        return mVideoViewInternal.getCurrentVideoRect();
    }

    @Override
    public void pause() {
        YYLog.info(this, "BaseVideoView.pause");
        mVideoViewInternal.pause();
    }

    @Override
    public void seekTo(int msec) {
        YYLog.info(this, "BaseVideoView.seekTo:" + msec);
        mVideoViewInternal.seekTo(msec);
    }

    @Override
    public void stopPlayback() {
        YYLog.info(this, "BaseVideoView.stopPlayback");
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
        YYLog.info(this, "BaseVideoView.renderLastFrame");
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

    public void callSetMeasuredDimension(int measuredWidth, int measuredHeight) {
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        /*if(mVideoViewInternal instanceof SvVideoViewInternal) {
            mVideoViewInternal.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }*/
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
    public int addTimeEffect() {
        setTimeEffectConfig(null);
        return mVideoViewInternal.addTimeEffect();
    }

    @Override
    public void updateTimeEffect(int segmentId, float startTime, float duration, float playbackSpeed) {
        mVideoViewInternal.updateTimeEffect(segmentId, startTime, duration, playbackSpeed);
    }

    @Override
    public void setAVSyncBehavior(int behavior) {
        if (mVideoViewInternal != null) {
            mVideoViewInternal.setAVSyncBehavior(behavior);
        }
    }

    @Override
    public void setFlutterRotateAngel(int angle) {
        if (mVideoViewInternal != null) {
            mVideoViewInternal.setFlutterRotateAngel(angle);
        }
    }
}
