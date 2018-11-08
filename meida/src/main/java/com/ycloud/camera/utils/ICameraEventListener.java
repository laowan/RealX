package com.ycloud.camera.utils;
/**
 * Created by kele on 2017/4/14.
 */

public interface ICameraEventListener {
    public void onCameraOpenSuccess(int cameraID);
    public void onCameraOpenFail(int cameraID, String reason);
    public void onCameraPreviewParameter(int cameraID,  YMRCameraInfo cameraInfo);
    public void onCameraRelease(int cameraID);
}
