package com.ycloud.api.videorecord;

/**
 * Created by Administrator on 2018/5/28.
 */

public interface IPreviewSnapshotListener {
    /**
     * 截图回调,带预览特效
     * @param result 0 : success , -1 : failed
     * @param path   picture path
     */
    void onScreenSnapshot(int result, String path);
}
