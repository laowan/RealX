package com.yy.media;

import android.content.Context;
import android.util.Log;
import com.ycloud.api.videorecord.IVideoRecord;
import com.ycloud.api.videorecord.NewVideoRecord;
import com.ycloud.api.videorecord.VideoSurfaceView;

public class MediaUtils {
    private static final String TAG = MediaUtils.class.getSimpleName();

    /**
     * 获取录制实例
     *
     * @param context
     * @param config
     * @return
     */
    public static final IVideoRecord prepare(Context context, MediaConfig config) {
        Log.d(TAG, "prepare()");
        IVideoRecord record;
        if (null == config.surfaceView) {
            config.surfaceView = new VideoSurfaceView(context);
        }
        record = new NewVideoRecord(context, config.surfaceView, config.resolutionType);
        try {
            record.setCameraID(config.cameraId);
            record.setAspectRatio(config.aspectRatioType, config.aspectOffset.first, config.aspectOffset.second);
            record.setEnableAudioRecord(config.audioEnable);
            record.setFlashMode(config.flashMode);
//            record.startPreview(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //返回handle实例
        return record;
    }

    /**
     * 合成音频文件到视频中
     *
     * @param config
     */
    public static final void fireAudio(MediaConfig config) {
        Log.d(TAG, "fireAudio()");
    }

    /**
     * 音频文件调整
     *
     * @param config
     */
    public static final void reduxAudio(MediaConfig config) {
        Log.d(TAG, "reduxAudio()");
    }
}
