package com.yy.media;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        //生命周期绑定
        if (context instanceof LifecycleOwner) {
            Lifecycle lifecycle = ((LifecycleOwner) context).getLifecycle();
            _LifecycleObserver observer = new _LifecycleObserver(lifecycle, record);
            lifecycle.addObserver(observer);
        }
        //返回handle实例
        return record;
    }

    /**
     * surfaceView生命周期回调
     */
    public static final class _LifecycleObserver implements LifecycleObserver {
        private final Lifecycle lifecycle;
        private final IVideoRecord record;

        /**
         * @param lifecycle
         * @param record
         */
        private _LifecycleObserver(Lifecycle lifecycle, IVideoRecord record) {
            this.lifecycle = lifecycle;
            this.record = record;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        public void onResume() {
            Log.d(TAG, "onResume()");
            try {
                this.record.onResume();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        public void onPause() {
            Log.d(TAG, "onPause()");
            try {
                this.record.onPause();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            try {
                this.record.release();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            this.lifecycle.removeObserver(this);
        }
    }

    /**
     * 合成音频文件到视频中
     *
     * @param config
     */
    public static final void fireAudio(MediaConfig config) {

    }

    /**
     * 音频文件调整
     *
     * @param config
     */
    public static final void reduxAudio(MediaConfig config) {

    }
}
