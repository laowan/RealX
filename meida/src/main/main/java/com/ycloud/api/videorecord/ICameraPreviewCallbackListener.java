package com.ycloud.api.videorecord;

/**
 * Created by Administrator on 2018/7/9.
 */

public interface ICameraPreviewCallbackListener {
    /**
     * 回调摄像头原始数据给业务层，实现UI层毛玻璃效果
     * @param data   the contents of the preview frame in the format defined in format
     * @param format reference android.graphics.ImageFormat
     * @param width  the preview width
     * @param height the preview height
     */
    public void onPreviewFrame(byte[] data, int format, int width, int height);
}
