package com.ycloud.camera.utils;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;

import com.ycloud.utils.YYLog;

/**
 * Created by kele on 2017/4/14.
 *
 * 对Camera目前设置的parameter中一些关注property抽取.
 * 为什么不直接用Camera.parameter? parameter这个参数在Camera2中已经无效了,　自己定义参数结构，可以使得更换Ｃamera平台接口.
 * 外层使用者不受影响.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class YMRCameraInfo {

    public static enum CameraState {
        /**摄像头被释放了*/
        CAMERA_STATE_CLOSED,
        /**摄象头被打开，占用了*/
        CAMERA_STATE_OPEN,
        /** 摄像头被打开，且处于预览状态*/
        CAMERA_STTAE_PREVIEW,
    };

    public YMRCameraInfo(YMRCameraInfo info) {
        mCameraID = info.getCameraID();
        if(mCameraID != -1) {
            checkCameraFacing();
        } else {
            ///Log error
        }
        this.copyFrom(info);
    }

    public YMRCameraInfo(int cameraID) {
        mCameraID = cameraID;
        if(mCameraID != -1) {
            checkCameraFacing();
        } else {
            ///Log error
        }
    }

    /**[Notice] 摄像头的id, 在setter接口中会获取是否前置摄像头，所有这个域使用了private+setter/getter*/
    private int mCameraID = -1; //

    /**每次成功打开摄象头，都会分配一个业务流水号， -1代表没有成功打开*/
    private long mCameraLinkID = -1;

    /**摄象图的初始状态.*/
    public CameraState mState= CameraState.CAMERA_STATE_CLOSED;

    /**实际预览图像宽度, 像素单位*/
    public int mPreviewWidth = 0;
    /**实际预览图像高度，像素单位*/
    public int mPreviewHeight = 0;
    /***是否设置了video stabilization*/
    public boolean mVideoStabilization = false;

    /** 聚焦类型.*/
    public String mFocus_mode = "";

    /**预览图像格式, 默认是NV21格式*/
    public int mPixelFormat = ImageFormat.NV21;

    /**采集fps 范围*/
    public int [] mPreviewFpsRange = new int[2];

    /**是否前置摄像头*/
    public boolean mCameraFacingFront = false;

    /**摄像头开始时候的display的Rotation，可以用来判断是portrait 还是 landscape 模式下打开的摄像头.*/
    public int mDisplayRotation = 0;

    /**Camera.setCameraDisplayOrientation设置的接口参数*/
    public int mDisplayOrientation = 0;

    /**camerainfo中的orietation.*/
    public int mCameraInfoOrientation = 0;

    /**采集分辨率模式*/
    public YMRCameraMgr.CameraResolutionMode mResolutionMode = YMRCameraMgr.CameraResolutionMode.CAMERA_RESOLUTION_PRECISE_MODE;

    public void reset() {
        mState= CameraState.CAMERA_STATE_CLOSED;

        mPreviewWidth = 0;
        mPreviewHeight = 0;
        mVideoStabilization = false;
        mFocus_mode = "";
        mPixelFormat = ImageFormat.NV21;
        mPreviewFpsRange[0] = 0;
        mPreviewFpsRange[1] = 0;

        //cameraID确定了faceFront，cameraID没有重置， 这里不需要重置.
//        mCameraFacingFront = false;
        mDisplayRotation = 0;
        mDisplayOrientation = 0;

        mCameraLinkID = -1;
        mResolutionMode = YMRCameraMgr.CameraResolutionMode.CAMERA_RESOLUTION_PRECISE_MODE;
    }


    public void copyFrom(Camera.Parameters parameter) {
        mPreviewWidth = parameter.getPreviewSize().width;
        mPreviewHeight = parameter.getPreviewSize().height;
        mPixelFormat = parameter.getPreviewFormat();
        mFocus_mode = parameter.getFocusMode();
        mVideoStabilization = parameter.getVideoStabilization();
       parameter.getPreviewFpsRange(mPreviewFpsRange);
    }

    public void copyFrom(YMRCameraInfo camInfo) {
        mCameraID = camInfo.mCameraID;
        mState = camInfo.mState;
        mPreviewWidth = camInfo.mPreviewWidth;
        mPreviewHeight = camInfo.mPreviewHeight;

        mVideoStabilization = camInfo.mVideoStabilization;
        mFocus_mode = camInfo.mFocus_mode;
        mPixelFormat = camInfo.mPixelFormat;

        System.arraycopy(camInfo.mPreviewFpsRange, 0, this.mPreviewFpsRange, 0, 2);

        mCameraFacingFront = camInfo.mCameraFacingFront;

        mDisplayRotation = camInfo.mDisplayRotation;
        mDisplayOrientation = camInfo.mDisplayOrientation;
		mCameraInfoOrientation = camInfo.mCameraInfoOrientation;

        mResolutionMode = camInfo.mResolutionMode;
    }


    private void checkCameraFacing() {
        try {
            if(YMRCameraUtils.isCameraIDAvailable(mCameraID)) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraID, info);
                mCameraFacingFront = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
            }
        } catch (Throwable t) {
                YYLog.error(this, "[exception] checkCameraFacing: " + t.toString() + " cameraId=" + mCameraID);
            }
    }

    public int getCameraID() {
        return mCameraID;
    }

    public void setCameraID(int cameraID) {
        if(mCameraID  != cameraID) {
            mCameraID = cameraID;
            checkCameraFacing();
        }
    }


    public String toString() {
        StringBuilder  sb = new StringBuilder();
        sb.append(" YMRCameraInfo:");
        sb.append(" mCameraID-").append(mCameraID);
        sb.append(" mCameraLinkID-").append(mCameraLinkID);
        sb.append(" mState-").append(mState);

        sb.append(" mPreviewWidth-").append(mPreviewWidth);
        sb.append(" mPreviewHeight-").append(mPreviewHeight);
        sb.append(" mVideoStabilization-").append(mVideoStabilization);
        sb.append(" mFocus_mode-").append((mFocus_mode==null? "null":mFocus_mode));
        sb.append(" mCameraFacingFront-").append(mCameraFacingFront);
        sb.append(" mPreviewFpsRange-").append("[").append(mPreviewFpsRange[0]).append(", ").append(mPreviewFpsRange[1]).append("]");
        sb.append(" mDisplayRotation-").append(mDisplayRotation);
        sb.append(" mDisplayOrientation-").append(mDisplayOrientation);
        sb.append(" mResolutionMode-").append(mResolutionMode);
        sb.append(" mCameraInfoOrientation-").append(mCameraInfoOrientation);
        return sb.toString();
    }


    public long getCameraLinkID() {
        return mCameraLinkID;
    }

    public void setCameraLinkID(long cameraLinkID) {
        mCameraLinkID = cameraLinkID;
    }

    public boolean getIsFacingFront() {
        return mCameraFacingFront;
    }
}
