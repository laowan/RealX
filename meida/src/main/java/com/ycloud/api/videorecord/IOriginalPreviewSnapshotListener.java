package com.ycloud.api.videorecord;

/**
 * Created by Administrator on 2018/6/19.
 */

public interface IOriginalPreviewSnapshotListener {
    /**
     * 截取预览原始图回调,不带预览特效
     * @param result 0 : success , -1 : failed
     * @param path   picture path
     */
    void onScreenSnapshot(int result, String path);
}
