package com.ycloud.mediafilters;

import com.ycloud.ymrmodel.YYMediaSample;

/**
 * Created by kele on 2017/4/8.
 */

public class VideoEndPointFilter extends AbstractYYMediaFilter
{
    public MediaFilterContext mFilterContext;

    public VideoEndPointFilter(MediaFilterContext filterContext) {
        mFilterContext = filterContext;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        //是否要重设这个context.
//        mFilterContext.getGlManager().resetContext();
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);  //add by liush
        return false;
    }

    @Override
    public void deInit() {
        //可重入的.
//        if (mFilterContext.getScreenShot() != null) {
//            mFilterContext.getScreenShot().deInit();
//        }
//
//        if (mFilterContext.getDynamicTexture() != null) {
//            mFilterContext.getDynamicTexture().onRelease();
//            mFilterContext.setDynamicTexture(null);
//        }
//
//        if (mFilterContext.getWaterMarkTexture() != null) {
//            mFilterContext.getWaterMarkTexture().destroy();
//            mFilterContext.setWaterMarkTexture(null);
//        }
    }
}
