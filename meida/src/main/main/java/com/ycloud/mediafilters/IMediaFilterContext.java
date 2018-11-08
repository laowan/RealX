package com.ycloud.mediafilters;

import android.content.Context;

import com.ycloud.mediacodec.VideoEncoderConfig;

/**
 * Created by kele on 2017/4/24.
 */

public interface IMediaFilterContext {
    public VideoEncoderConfig getVideoEncoderConfig();
    public Context getAndroidContext();

    public int getWatermarkTextureID();
    public int getDynamicTextureID();
    public GlManager getGLManager();

}
