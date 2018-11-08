package com.ycloud.ymrmodel;
/**
 * Created by wangqiming on 16/11/21.
 */

import android.view.Surface;

/**
 * TODO: Add a class header comment!
 */

public class SurfaceInfo extends AbstractSurfaceInfo
{
    public Surface mSurface = null;
    public SurfaceInfo(Surface sf, int width, int height)
    {
        super(width, height);
        mSurface = sf;
    }
}
