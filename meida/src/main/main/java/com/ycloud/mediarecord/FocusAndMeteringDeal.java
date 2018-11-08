package com.ycloud.mediarecord;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;

import com.ycloud.camera.utils.YMRCameraMgr;
import com.ycloud.camera.utils.YMRCameraUtils;
import com.ycloud.utils.YYLog;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by DZHJ on 2017/4/17.
 */

public class FocusAndMeteringDeal {
    public static final String TAG = FocusAndMeteringDeal.class.getSimpleName();
    private RecordConfig mRecordConfig;
    protected AtomicLong mCurrentCameraLinkID = new AtomicLong(-1);

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private int focus_size = 150;

    private Matrix camera_to_preview_matrix = new Matrix();
    private Matrix preview_to_camera_matrix = new Matrix();

    // video
    private int mSurfaceRotation = 90;

    public FocusAndMeteringDeal(RecordConfig recordConfig){
        mRecordConfig = recordConfig;
    }

    private ArrayList<Camera.Area> getAreas(float x, float y) {
        float[] coords = { x, y };
        calculatePreviewToCameraMatrix();
        preview_to_camera_matrix.mapPoints(coords);
        float focus_x = coords[0];
        float focus_y = coords[1];

        Rect rect = new Rect();
        rect.left = (int) focus_x - focus_size;
        rect.right = (int) focus_x + focus_size;
        rect.top = (int) focus_y - focus_size;
        rect.bottom = (int) focus_y + focus_size;
        if (rect.left < -1000) {
            rect.left = -1000;
            rect.right = rect.left + 2 * focus_size;
        } else if (rect.right > 1000) {
            rect.right = 1000;
            rect.left = rect.right - 2 * focus_size;
        }
        if (rect.top < -1000) {
            rect.top = -1000;
            rect.bottom = rect.top + 2 * focus_size;
        } else if (rect.bottom > 1000) {
            rect.bottom = 1000;
            rect.top = rect.bottom - 2 * focus_size;
        }

        ArrayList<Camera.Area> areas = new ArrayList<Camera.Area>();
        areas.add(new Camera.Area(rect, 1000));
        return areas;
    }

    private void calculatePreviewToCameraMatrix() {
        calculateCameraToPreviewMatrix();
        if (!camera_to_preview_matrix.invert(preview_to_camera_matrix)) {
            YYLog.e(TAG, "calculatePreviewToCameraMatrix failed to invert matrix!?");
        }
    }

    private void calculateCameraToPreviewMatrix() {
        camera_to_preview_matrix.reset();
        // from
        // http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
        Camera.CameraInfo camera_info = new Camera.CameraInfo();

        if (YMRCameraUtils.isCameraIDAvailable(mRecordConfig.getCameraId())) {
            Camera.getCameraInfo(mRecordConfig.getCameraId(), camera_info);
        }

        // Need mirror for front camera.
        boolean mirror = (camera_info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        camera_to_preview_matrix.postRotate(mSurfaceRotation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        camera_to_preview_matrix.postScale(mSurfaceWidth / 2000f, mSurfaceHeight / 2000f);
        camera_to_preview_matrix.postTranslate(mSurfaceWidth / 2f, mSurfaceHeight / 2f);
    }

    public void surfaceChanged(int width, int height){
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    public void setCameraLinkID(long cameraLinkID) {
        mCurrentCameraLinkID.set(cameraLinkID);
    }

    public void focusAndMetering(float x, float y, boolean autoCancel) {
        if ((mCurrentCameraLinkID.get() != -1)) {
            if (YMRCameraMgr.getInstance().bLockExpose) {
                YMRCameraMgr.getInstance().bLockExpose = false;
                YMRCameraMgr.getInstance().lockExpose(mRecordConfig.getCameraId(), false);
            }
            ArrayList<Camera.Area> areas = getAreas(x, y);
            YMRCameraMgr.getInstance().focusAndMetering(mRecordConfig.getCameraId(), areas, autoCancel);
            YMRCameraMgr.getInstance().exposureMode = 1;
        }
    }
}
