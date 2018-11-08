package com.ycloud.api.videorecord;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * 视频录制画面预览view
 * VideoSurfaceView
 */
public class VideoSurfaceView extends SurfaceView {
    public VideoSurfaceView(Context context) {
        this(context, null);

    }

    public VideoSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setWillNotDraw(false);
    }
}
