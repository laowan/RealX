package com.ycloud.mediaprocess;

import java.util.LinkedList;

/**
 * Created by Administrator on 2018/3/21.
 */

public interface IMediaSnapshotPictureListener {

    /**
     * 截图图片回调
     * @param picture 图片路径
     * @param pts     图片对应视频中的PTS (单位ms)
     */
    public void onPictureAvaliable(String picture, long pts);
}
