package com.ycloud.api.common;

import android.content.Context;
import android.view.SurfaceHolder;

//import com.ycloud.svplayer.IVideoView;

/**
 * Created by Administrator on 2017/7/6.
 */

public interface IVideoViewInternal extends SurfaceHolder.Callback, IBaseVideoView {
    void initVideoView(Context ctx);
    void onMeasure(int widthMeasureSpec, int heightMeasureSpec);
}
