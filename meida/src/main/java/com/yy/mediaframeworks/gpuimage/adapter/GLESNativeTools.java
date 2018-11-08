package com.yy.mediaframeworks.gpuimage.adapter;


import com.ycloud.utils.CPUMemUtils;
import com.ycloud.utils.YYLog;

public class GLESNativeTools {

    static {
        try {
            System.loadLibrary("audioengine");
            System.loadLibrary("ycmediayuv");
            if ((CPUMemUtils.getCPUInfo().mCPUFeature & CPUMemUtils.CPUInfo.CPU_FEATURE_NEON) > 0) {
                System.loadLibrary("ffmpeg-neon");
                System.loadLibrary("ycmedia");
            } else {
                YYLog.w("MediaNative", "non neon cpu!");
                System.loadLibrary("ffmpeg-neon");
                System.loadLibrary("ycmedia");
            }
        } catch (UnsatisfiedLinkError e) {
            YYLog.e("MediaNative", "load so fail");
            e.printStackTrace();
        }
    }

    public static native void glReadPixelWithJni(int x, int y, int width, int height, int format, int type, int offset);
}
