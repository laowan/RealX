package com.yy.media;

import com.ycloud.api.config.ResolutionType;
import com.ycloud.api.videorecord.VideoSurfaceView;
import com.ycloud.mediarecord.VideoRecordConstants;

public class MediaConfig {
    VideoSurfaceView surfaceView;
    ResolutionType resolutionType = ResolutionType.R540P;
    int cameraId = VideoRecordConstants.FRONT_CAMERA;

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
         * 返回build实例
         *
         * @return
         */
        public MediaConfig build() {
            return this.config;
        }
    }
}
