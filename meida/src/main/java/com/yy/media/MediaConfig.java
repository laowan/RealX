package com.yy.media;

import android.util.Pair;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.config.ResolutionType;
import com.ycloud.api.videorecord.FlashMode;
import com.ycloud.api.videorecord.VideoSurfaceView;
import com.ycloud.mediarecord.VideoRecordConstants;

public class MediaConfig {
    VideoSurfaceView surfaceView;
    //可变设置
    public ResolutionType resolutionType = ResolutionType.R540P;
    public int cameraId = VideoRecordConstants.FRONT_CAMERA;
    public AspectRatioType aspectRatioType = AspectRatioType.ASPECT_RATIO_4_3;
    public Pair<Integer, Integer> aspectOffset = new Pair<>(0, 0);
    public boolean audioEnable = true;
    public String flashMode = FlashMode.FLASH_MODE_AUTO;

    /**
     * 私有构造函数
     */
    private MediaConfig() {
    }

    public static class Builder {
        final MediaConfig config;

        /**
         * 构造函数
         */
        public Builder() {
            this.config = new MediaConfig();
        }

        /**
         * 设置surfaceView
         *
         * @param view
         * @return
         */
        public Builder attach(VideoSurfaceView view) {
            this.config.surfaceView = view;
            return this;
        }

        /**
         * 设定前置还是后置摄像头
         *
         * @param id
         * @return
         */
        public Builder setCameraId(int id) {
            this.config.cameraId = id;
            return this;
        }

        /**
         * 设置宽高比
         *
         * @param type
         * @return
         */
        public Builder setAspectRatioType(AspectRatioType type) {
            Pair<Integer, Integer> offset = new Pair<>(0, 0);
            return setAspectRatioType(type, offset);
        }

        /**
         * 设置宽高比
         *
         * @param type
         * @param offset
         * @return
         */
        public Builder setAspectRatioType(AspectRatioType type, Pair<Integer, Integer> offset) {
            this.config.aspectRatioType = type;
            this.config.aspectOffset = offset;
            return this;
        }

        /**
         * 是否采集声音
         *
         * @param enable
         * @return
         */
        public Builder setAudioEnable(boolean enable) {
            this.config.audioEnable = enable;
            return this;
        }

        /**
         * 设置闪光灯模式
         *
         * @param mode
         * @return
         */
        public Builder setFlashMode(String mode) {
            this.config.flashMode = mode;
            return this;
        }

        /**
         * 返回build实例
         *
         * @return
         */
        public MediaConfig build() {
            return this.config;
        }
    }
}
