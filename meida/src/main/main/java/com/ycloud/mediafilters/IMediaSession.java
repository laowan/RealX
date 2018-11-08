package com.ycloud.mediafilters;

import android.media.MediaFormat;
import android.provider.MediaStore;

/**
 * Created by Administrator on 2017/5/9.
 */

public interface IMediaSession {
    /**
     * glMgr线程退出前清理工作
     */
    void glMgrCleanup();

    /**
     * audioMgr线程退出前的清理工作
     */
    void audioMgrCleanup();

    //设置输入流的media format.
    void setInputVideoFormat(MediaFormat mediaFromat);

    void setInputAudioFormat(MediaFormat mediaFormat);
}
