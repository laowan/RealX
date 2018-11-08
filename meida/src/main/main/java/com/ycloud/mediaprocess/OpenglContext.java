package com.ycloud.mediaprocess;

import android.graphics.SurfaceTexture;
import android.view.Surface;

/**
 * Created by Administrator on 2017/2/24.
 */

public interface OpenglContext {
    void init(SurfaceTexture surfaceTexture);
    void release();
}
