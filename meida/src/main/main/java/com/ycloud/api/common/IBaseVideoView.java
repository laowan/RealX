package com.ycloud.api.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.gpuimagefilter.filter.PlayerFilterSessionWrapper;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.player.widget.MediaPlayerListener;
import com.ycloud.svplayer.MediaPlayer;
import com.ycloud.svplayer.surface.ImgProCallBack;

/**
 * Created by Administrator on 2017/7/6.
 */

public interface IBaseVideoView {
    /**
     * 获取背景音乐的音量大小
     * @return
     */
    float getBackgroundMusicVolume();

    /**
     * 获取视频的音量大小
     * @param volume
     * @return
     */
    float getVideoVolume(float volume);

    /**
     * 获取当前播放的进度
     * @return audio与video中pts较小的值，单位ms
     */
    int getCurrentPosition();

    /**
     * 获取当前播放的视频进度
     * @return video的pts值，单位ms
     */
    int getCurrentVideoPostion();

    /**
     * 获取当前播放的音频进度
     * @return audio的pts值，单位ms
     */
    int getCurrentAudioPosition();

    /**
     * 获取视频的时长
     * @return 视频时长，单位ms
     */
    int getDuration();
    //ViewGroup.LayoutParams getLayoutParams();

    /**
     * 判断当前视频是否处于播放状态
     * @return
     */
    boolean isPlaying();

    /**
     * 设置视频源
     * @param path
     */
    void setVideoPath(String path);

    /**
     * 设置播放状态监听
     * @param mediaPlayerListener
     */
    void setMediaPlayerListener(MediaPlayerListener mediaPlayerListener);

    /** @deprecated  一代特效接口, 将要被替换掉*/
    void setVFilters(VideoFilter videoFilter);

    /**
     * 获取添加视频特效接口类的句柄，所有添加视频特效，都需要获取对象，然后
     * 通过PlayerFilterSessionWrapper对象来添加
     * @return
     */
    PlayerFilterSessionWrapper getPlayerFilterSessionWrapper();

    /**
     * 生成视频的截图，截图包含添加的特效
     * @param imageBasePath
     * @param imageRate      截图频率，1s钟截几张图
     * @param imgProCallBack 截图回调
     */
    void processImages(String imageBasePath, int imageRate, ImgProCallBack imgProCallBack);

    /**
     * 设置音量
     * @param volume
     */
    void setVideoVolume(float volume);

    /**
     * 设置背景音乐的音量
     * @param volume
     */
    void setBackgroundMusicVolume(float volume);

    /**
     * 改变视频缩放模式
     * @param fitMode
     * @param windowWidth
     * @param windowHeight
     */
    void updateVideoLayout(int fitMode, int windowWidth, int windowHeight);

    /**
     * 设置视频缩放模式
     * @param layoutMode
     */
    void setLayoutMode(int layoutMode);

    /**
     * 开始播放
     */
    void start();

    /**
     * 暂停
     */
    void pause();

    /**
     * seek到某个时间点播放，单位ms
     * @param msec
     */
    void seekTo(int msec);

    /**
     * 停止播放，释放播放器
     */
    void stopPlayback();

    /**
     * 旋转
     */
    void startRotate();

    /**
     * 获取当前旋转角度
     * @return
     */
    float getCurrentRotateAngle();

    /**
     * Flutter 设置视频旋转角度(加特效前旋转),顺时针方向
     * @param angle  角度只支持 0 90 180 270 四个值
     */
    void setFlutterRotateAngel(int angle);

    /**
     * 设置上次旋转的角度
     * @param angle 90 180 270 only
     */
    void setLastRotateAngle(int angle);

    /**
     * 获取视频在当前viewport中的区域（除去黑边的区域）
     * @return
     */
    RectF getCurrentVideoRect();

    /**
     * 设置播放速度
     * @param speed 0.25 0.5 1.0 2.0 4.0
     */
    void setPlaybackSpeed(float speed);

    /**
     * 设置背景颜色
     * @param color
     */
    void setBackGroundColor(int color);

    /**
     * 设置背景图
     * @param bitmap
     */
    void setBackGroundBitmap(Bitmap bitmap);

    /**
     * 设置时间特效配置信息，必须在所有其它时间特效接口调用前设置。
     * 这个接口从后台拿到这个数据后只需要设置一次，重复调用会覆盖上一次设置的配置信息。
     * @param jsonStr 特效后台下发的时间特效配置信息
     */
    void setTimeEffectConfig(String jsonStr);

    /**
     * 用户按下添加时间特效按钮时，返回时间特效对应ID。
     */
    int addTimeEffectBegin();

    /**
     * 用户松开时间特效添加按钮，完成一段时间特效的添加时，通知SDK层该段时间特效的起始时间，用于最终视频合成时间特效。
     * 另外，调用这个接口，SDK会设置后面部分（时间特效第二部分）视频的播放速度。
     * @param segmentId 一段时间特效的索引，由客户端定义，SDK保存索引和时间段的对应关系。
     * @param startTime 一段时间特效的开始时间，单位毫秒(ms)
     * @param duration  一段时间特效的持续时长，单位毫秒(ms)
     */
    void addTimeEffectEnd(int segmentId, float startTime, float duration);

    /**
     * 删除一个时间段的时间特效
     * @param segmentId 一段时间特效对应的索引值
     */
    void removeTimeEffect(int segmentId);

    /**
     * 设置视频追赶音频的行为
     * @param behavior  0：快速播放 1：丢帧，直到追上才渲染
     */
    void setAVSyncBehavior(int behavior); // AV_SYNC_BEHAVIOR_FASTPLAY / AV_SYNC_BEHAVIOR_DROP_FRAME

    /**
     * 添加一段时间特效，分配一个时间特效的id
     *
     * @return effect id
     */
    int addTimeEffect();

    /**
     * 更新时间特效的参数
     *
     * @param segmentId     effect id
     * @param startTime     起始时间
     * @param duration      持续时长
     * @param playbackSpeed 播放速度
     */
    void updateTimeEffect(int segmentId, float startTime, float duration, float playbackSpeed);

    /**
     * 开启重复渲染当前帧模式
     */
    void startRepeatRender();

    /**
     * 停止重复渲染当前帧模式
     */
    void stopRepeatRender();

    /**
     * 渲染最后一个已经解码的视频帧
     */
    void renderLastFrame();

    void setOnPreparedListener(MediaPlayer.OnPreparedListener l);
    void setOnErrorListener(MediaPlayer.OnErrorListener l);
    void setOnRenderStartListener(MediaPlayer.OnRenderStartListener l);
    void setMediaInfoRequireListener(IMediaInfoRequireListener l);

    /**
     * 切换url的时候调用，将之前的surfaceHolder置空
     * 主要是因为surfaceDestroy回调的时机不可控，有可能在触发之前，业务曾调用了setVideoPath，重新打开一个新的mediaSource
     * 置空可以避免使用之前的surface
     */
    void resetSurface();

    /**
     *
     * @param beginReadPositionMS, endReadPositionMS: the file play range.
     * @param positionMS: the position should play, its relative to video
     * @return playID
     */
    int addAudioFileToPlay(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long positionMS );
    int addMagicAudioToPlay(int positionMS, String[] audioPaths );

    /**
     *
     * @param ID playID
     */
    void stopPlayAudio(int ID, int stopPositionMS);
    void removeAudio(int ID);
    void setAudioVolume(int ID, float volume);
    String getAudioFilePath();
    boolean haveMicAudio();
    // the different with "addMagicAudioToPlay" is that only one effect audio will play in a time
    int addEffectAudioToPlay(int positionMS, String[] audioPaths);
    // erasure all effect audio
    int addErasureAudioToPlay(int positionMS);

    void enableAudioFrequencyCalculate(boolean enable);
    int audioFrequencyData(float[] buffer, int len) ;

    Context getContext();
    int getWidth();
    int getHeight();
}
