/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.camera.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import com.ycloud.common.Constant;
import com.ycloud.common.Dev_MountInfo;
import com.ycloud.common.Dev_MountInfo.DevInfo;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraUtils {
    private static String TAG = "CameraUtils";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MEDIA_TYPE_VIDEO_TS = 3;

    public static int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    public static Camera openCamera(final int id) {
        return Camera.open(id);
    }

    public static Camera openDefaultCamera() {
        return Camera.open(0);
    }

    public static boolean hasCamera(final int facing) {
        return getCameraId(facing) != -1;
    }

    public static Camera openCameraFacing(final int facing) {
        return Camera.open(getCameraId(facing));
    }

    public static void getCameraInfo(final int cameraId, final CameraInfo2 cameraInfo) {
        try {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            cameraInfo.facing = info.facing;
            cameraInfo.orientation = info.orientation;
        } catch (Throwable t) {
            YYLog.error("[ymrsdk]", "getCameraInfo exception: " + t.toString());
        }
    }

    private static int getCameraId(final int facing) {
        try {
            int numberOfCameras = Camera.getNumberOfCameras();
            CameraInfo info = new CameraInfo();
            for (int id = 0; id < numberOfCameras; id++) {
                Camera.getCameraInfo(id, info);
                if (info.facing == facing) {
                    return id;
                }
            }
        } catch(Throwable t) {
            YYLog.error("[ymrsd]", "getCameraId exception: "+t.toString());
        }
        return -1;
    }

    public static void setCameraDisplayOrientation(final Activity activity,
            final int cameraId, final Camera camera) {
        int result = getCameraDisplayOrientation(activity, cameraId);
        camera.setDisplayOrientation(result);
    }
    public static int getCameraDisplayOrientation(final Activity activity, final int cameraId,boolean isNotGL) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
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
        }

        int result;
        CameraInfo2 info = new CameraInfo2();
        getCameraInfo(cameraId, info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            if(isNotGL)result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        Log.i(TAG, "Camera config:2info.orientation-- [" + info.orientation+"] degrees:["+degrees+"]");
        return result;
    }

    public static int getDisplayRotation(final  Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
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
        }

        YYLog.i("ymrsdk", Constant.MEDIACODE_CAMERA+"getDisplayRotation:" + degrees);
        return degrees;
    }

    public static int getCameraDisplayOrientation(final Activity activity, final int cameraId) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
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
        }

        int result;
        CameraInfo2 info = new CameraInfo2();
        getCameraInfo(cameraId, info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            //TODO. add for new record.
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        YYLog.i("ymrsdk", Constant.MEDIACODE_CAMERA+"Camera config:info.orientation-- " + info.orientation+" degrees:"+degrees + "displayOrientation="+result);
        return result;
    }

    public static class CameraInfo2 {
        public int facing;
        public int orientation;
    }

    public static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean isMeteringAreaSupported(Camera.Parameters params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return params.getMaxNumMeteringAreas() > 0;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean isFocusAreaSupported(Camera.Parameters params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                && params != null) {
            return (params.getMaxNumFocusAreas() > 0
                    && isSupported(Camera.Parameters.FOCUS_MODE_AUTO,
                    params.getSupportedFocusModes()));
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static boolean isCameraSupported(int position) {
        // Find the total number of cameras available

        try {
            int mNumberOfCameras = Camera.getNumberOfCameras();

            // Find the ID of the back-facing ("default") camera
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < mNumberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == position) {
                    return true;
                }
            }
        } catch(Throwable t) {
            YYLog.error("[ymrsdk]", "isCameraSupported exception:" + t.toString());
        }
        return false;
    }
    
    /**
     * Creates a media file in the {@code Environment.DIRECTORY_PICTURES} directory. The directory
     * is persistent and available to other applications like gallery.
     *
     * @param type Media type. Can be video or image.
     * @return A file object pointing to the newly created file.
     */
    public  static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

    	File mediaStorageDir =null;
		if (Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
			mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "");
		} else {
			Dev_MountInfo dev = Dev_MountInfo.getInstance();
			DevInfo info = dev.getInternalInfo();// Internal SD Card Informations
			info = dev.getExternalInfo();// External SD Card Informations
			if (null != info)
				mediaStorageDir = new File(info.getPath()+"/Movies/");
			else
				mediaStorageDir = new File("/mnt/sdcard2/Movies/");
		}
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()) {
                Log.d("CameraSample", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.CHINA).format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        }  else if(type == MEDIA_TYPE_VIDEO_TS) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".ts");
        }  else {
            return null;
        }

        return mediaFile;
    }
    
    /**
     * Iterate over supported camera preview sizes to see which one best fits the
     * dimensions of the given view while maintaining the aspect ratio. If none can,
     * be lenient with the aspect ratio.
     *
     * @param sizes Supported camera preview sizes.
     * @param w The width of the view.
     * @param h The height of the view.
     * @return Best match camera preview size to fit in the view.
     */
    public static  Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;

        // Start with max value and refine as we iterate over available preview sizes. This is the
        // minimum difference between view and camera height.
        double minDiff = Double.MAX_VALUE;

        // Target view height
        int targetHeight = h;

        // Try to find a preview size that matches aspect ratio and the target view size.
        // Iterate over all available sizes and pick the largest size that can fit in the view and
        // still maintain the aspect ratio.
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find preview size that matches the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
}
