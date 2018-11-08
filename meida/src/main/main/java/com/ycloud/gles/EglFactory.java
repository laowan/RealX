package com.ycloud.gles;


import android.view.SurfaceHolder;

import com.ycloud.ymrmodel.AbstractSurfaceInfo;
import com.ycloud.ymrmodel.SurfaceHolderInfo;
import com.ycloud.ymrmodel.SurfaceInfo;


/**
 * Created by Administrator on 2017/1/6.
 */
public class EglFactory {
    private static boolean sForceUseEgl10 = false;

    public static void setForceUseEgl10(boolean useEgl10) {
        sForceUseEgl10 = useEgl10;
    }

    public static IEglCore createEGL() {
        IEglCore core;
        if (isUseEgl14()) {
            core = new EglCore();
        } else {
            core = new EglCoreKhronos();
        }
        return core;
    }

    public static IEglCore createEGL(Object sharedContext, int flags) {
        IEglCore core;
        if (isUseEgl14()) {
            core = new EglCore(sharedContext, flags);
        } else {
            core = new EglCoreKhronos();
        }
        return core;
    }

    public static boolean isUseEgl14() {
        return (!sForceUseEgl10 && android.os.Build.VERSION.SDK_INT >= 17);
    }

    public static AbstractSurfaceInfo newSurfaceInfo(SurfaceHolder holder, int with, int height) {
        if (isUseEgl14()) {
            return new SurfaceInfo(holder.getSurface(), with, height);
        } else {
            return new SurfaceHolderInfo(holder, with, height);
        }
    }

    public static IWindowSurface newWindowSurface(IEglCore elgCore, AbstractSurfaceInfo sfInfo, boolean releaseSurace) {
        if (isUseEgl14()) {
            SurfaceInfo surfaceInfo = (SurfaceInfo) sfInfo;
            return elgCore.createWindowSurface(surfaceInfo.mSurface, releaseSurace);
        } else {
            SurfaceHolderInfo holderInfo = (SurfaceHolderInfo) sfInfo;
            return elgCore.createWindowSurface(holderInfo.mSurfaceHolder, releaseSurace);
        }
    }
}
