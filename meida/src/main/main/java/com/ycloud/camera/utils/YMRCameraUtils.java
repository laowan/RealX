/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.camera.utils;

import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

import com.ycloud.utils.YYLog;

import java.util.List;

/**
 * Camera-related utility functions.
 */
public class YMRCameraUtils {
    public final static String TAG = "[YMRCameraUtils]";

    private final static int DEFAULT_PREVIEW_WIDTH = 1280;
    private final static int DEFAULT_PREVIEW_HEIGHT = 720;

    public static void setCameraDisplayOrientation(Activity activity, Camera camera, Camera.CameraInfo cameraInfo) {
        if (activity != null && camera != null) {
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
                default:
                    YYLog.e(TAG, "invalid rotation:" + rotation);
                    break;
            }

            setCameraDisplayOrientation(degrees, camera, cameraInfo);
        }
    }


    public static int setCameraDisplayOrientation(int displayRotation, Camera camera, Camera.CameraInfo cameraInfo) {
        if (camera == null) {
            return -1;
        }
        int result = 90;
        if (cameraInfo != null) {
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (cameraInfo.orientation + displayRotation) % 360;
                result = (360 - result) % 360; // compensate the mirror
            } else { // back-facing
                result = (cameraInfo.orientation - displayRotation + 360) % 360;
            }
        } else {
            YYLog.e(TAG, "setCameraDisplayOrientation cameraInfo null");
        }
        YYLog.i(TAG, "setCameraDisplayOrientation " + result);
        camera.setDisplayOrientation(result);
        return result;
    }

    public static Camera.Size selectPreferredSize(Camera.Parameters params,
                                                  int width, int height) {
        List<Camera.Size> cameraSizeList = params.getSupportedPreviewSizes();
        int count = cameraSizeList.size();
        for (int i = 0; i < count; ++i) {
            Camera.Size previewSize = cameraSizeList.get(i);
            YYLog.i(TAG, "camera preview size: "
                    + previewSize.width + "x" + previewSize.height);
        }

        Camera.Size result = null;
        for (Camera.Size previewSize : cameraSizeList) { // find the size that
            // equal
            if (previewSize.width == width && previewSize.height == height) {
                result = previewSize;
                break;
            }
        }

        if (null == result) {
            int minGap = Integer.MAX_VALUE;
            for (Camera.Size previewSize : cameraSizeList) {
                if (previewSize.width >= width && previewSize.height >= height) {
                    int gap = previewSize.width - width + previewSize.height
                            - height;
                    if (gap < minGap) {
                        minGap = gap;
                        result = previewSize;
                    }
                }
            }
        }

        if (null == result) {
            int minGap = Integer.MAX_VALUE;
            for (Camera.Size previewSize : cameraSizeList) {
                int gap = Math.abs(previewSize.width - width)
                        + Math.abs(previewSize.height - height);
                if (gap < minGap) {
                    minGap = gap;
                    result = previewSize;
                }
            }
        }

        if (result != null) {
            params.setPreviewSize(result.width, result.height);
        }

        return result;
    }

    /**
     * Attempts to find a preview size that matches the provided width and height (which
     * specify the dimensions of the encoded video).  If it fails to find a match it just
     * uses the default preview size for video.
     * <p>
     * TODO: should do a best-fit match, e.g.
     * https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
     */
    public static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            YYLog.i(TAG, "preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        int tempWidth = Integer.MAX_VALUE;
        int tempHeight = Integer.MAX_VALUE;

        for (Camera.Size size : parms.getSupportedPreviewSizes()) {
            YYLog.i(TAG, "supported: " + size.width + "x" + size.height);
            if (size.width >= width && size.height >= height) {
                if (size.width <= tempWidth && size.height <= tempHeight) {
                    tempWidth = size.width;
                    tempHeight = size.height;
                }
            }
        }
        if (tempWidth != Integer.MAX_VALUE && tempHeight != Integer.MAX_VALUE) {
            YYLog.i(TAG, "prefer " + width + "x" + height + ", choose " + tempWidth + "x" + tempHeight);
            parms.setPreviewSize(tempWidth, tempHeight);
            return;
        }

        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
        // else use whatever the default size is
    }

    /**
     * Attempts to find a fixed preview frame rate that matches the desired frame rate.
     * <p>
     * It doesn't seem like there's a great deal of flexibility here.
     * <p>
     * TODO: follow the recipe from http://stackoverflow.com/questions/22639336/#22645327
     *
     * @return The expected frame rate, in thousands of frames per second.
     */
    public static int chooseFixedPreviewFps(Camera.Parameters parms, int desiredThousandFps) {
        List<int[]> supported = parms.getSupportedPreviewFpsRange();

        for (int[] entry : supported) {
            YYLog.i(TAG, "getSupportedPreviewFpsRange entry: " + entry[0] + " - " + entry[1]);
            if ((entry[0] == entry[1]) && (entry[0] == desiredThousandFps)) {
                parms.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }

        int[] tmp = new int[2];
        parms.getPreviewFpsRange(tmp);
        int guess;
        if (tmp[0] == tmp[1]) {
            guess = tmp[0];
        } else {
            guess = tmp[1] / 2;     // shrug
        }

        Log.d(TAG, "Couldn't find match for " + desiredThousandFps + ", using " + guess);
        return guess;
    }

    /**
     * 打开摄像头
     * @param cameraFacing 正面，还是反面
     * @return
     */
    public static Camera openCamera(CameraFacing cameraFacing){
        return openCamera(cameraFacing, null);
    }

    public static Camera openCamera(CameraFacing cameraFacing, Camera.CameraInfo info){
        if (info == null) {
            info = new Camera.CameraInfo();
        }
        Camera camera = null;
        try {
            // Try to find a front-facing camera (e.g. for videoconferencing).
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                //前置摄像头

                if (cameraFacing == CameraFacing.FacingFront) {
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        camera = Camera.open(i);
                        break;
                    }
                }else {
                    //后置摄像头
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        camera = Camera.open(i);
                        break;
                    }
                }
            }
            if (camera == null) {
                Log.d(TAG, "No front-facing camera found; opening default");
                camera = Camera.open();    // opens first back-facing camera
            }
        } catch (Throwable throwable) {
            YYLog.e(TAG, "openCamera error! " + throwable);
        }
        return camera;
    }

    /**
     * 摄像头方向
     */
    public enum CameraFacing{
        FacingFront, FacingBack;
    }

    public static class PreviewSize {
        public int width, height;
        public PreviewSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return "" + width + "x" + height;
        }
    }

    public static PreviewSize getSpecialCameraPreviewSizeWithModel(String model, Camera.Size size, boolean mFaceFront) {
        PreviewSize result = null;
        if ("MI 4LTE".equalsIgnoreCase(model)) {
            if (size != null && size.width != DEFAULT_PREVIEW_WIDTH && size.height != DEFAULT_PREVIEW_HEIGHT && !mFaceFront) {
                result = new PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
            }
        } else if ("HM NOTE 1W".equalsIgnoreCase(model)) {
            if (size != null && size.width != DEFAULT_PREVIEW_WIDTH && size.height != DEFAULT_PREVIEW_HEIGHT) {
                result = new PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
            }
        } else if ("2013023".equalsIgnoreCase(model)) { //xiaomi hm
            if (size != null && size.width == 960 && size.height == 540) {
                result = new PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
            }
        } else if ("vivo X5L".equalsIgnoreCase(model)) {
            if (size != null && size.width == 960 && size.height == 540) {
                result = new PreviewSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);
            }
        }
        if (result != null) {
            YYLog.i(TAG, "getSpecialCameraPreviewSizeWithModel size " + result + ", model " + model + ", facefront:" + mFaceFront);
        }
        return result;
    }

    public static int toAndroidCameraFacing(CameraFacing facing) {
        if(facing == CameraFacing.FacingFront) {
            return Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            return Camera.CameraInfo.CAMERA_FACING_BACK;
        }
    }


    public static boolean isCameraIDAvailable(final int cameraId) {
        final int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            if(cameraId == i) {
                YYLog.info(TAG, "isCameraIDAvailable:true，cameraID="+cameraId);
                return true;
            }
        }

        YYLog.error(TAG, "isCameraIDAvailable:false，cameraID="+cameraId);
        return false;
    }

    public static boolean isCameraAvailable(final int facing) {
        try {
            final int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, info);
                if (facing == info.facing) {
                    YYLog.i(TAG, "isCameraAvailable true " + facing);
                    return true;
                }
            }
        }catch(Throwable t) {
            YYLog.error("[ymrsdk]", "isCameraAvailable exception: " + t.toString());
        }
        YYLog.i(TAG, "isCameraAvailable false " + facing);
        return false;
    }

    public static int[] getMaxFrameRateRange(Camera.Parameters params) {
        List<int[]> listFpsRange = params.getSupportedPreviewFpsRange();
        int[] maxRange = null;
        int minIndex = Camera.Parameters.PREVIEW_FPS_MIN_INDEX;
        int maxIndex = Camera.Parameters.PREVIEW_FPS_MAX_INDEX;
        for (int[] range: listFpsRange) {
            if (null == maxRange) {
                maxRange = range;
            }
            else {
                if (range[maxIndex] > maxRange[maxIndex]) {
                    maxRange = range;
                }
                else if (range[maxIndex] == maxRange[maxIndex]) {
                    if (range[minIndex] < maxRange[minIndex]) {
                        maxRange = range;
                    }
                }
            }
        }
        return maxRange;
    }
}
