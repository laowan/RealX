package com.ycloud.facedetection;

/**
 * Created by sjc on 2017/3/8.
 */

public interface IFaceDetectionListener {
    public static final int NO_MATTER = 0;             //没有开启动态贴纸时，检测到人脸与没有检测到人脸都是无所谓的
    public static final int HAS_FACE = 1;             //开启了动态贴纸，并检测到了人脸
    public static final int NO_FACE = 2;              //开启了动态贴纸，没检测到人脸

    void onFaceStatus(int status);
}
