package com.ycloud.camera.utils;

import android.hardware.Camera;
import android.util.Log;
import android.widget.ImageView;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.config.TakePictureConfig;
import com.ycloud.utils.YYLog;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Camera工具2
 * Created by zhongyongsheng on 2016/2/18.
 */
public class YMRCameraUtils2 {
    private static final String TAG = "[camera]";

    private final static float RATIO_16_9 = 16.0f / 9;
    private final static float RATIO_9_16 = 9.0f / 16;
    private final static float RATIO_4_3 = 4.0f / 3f;
    private final static float RATIO_3_4 = 3.0f / 4;

    /**
     * 按比例更准确的【选择】合适的预览分辨率
     * @param displayOrientation
     * @param width
     * @param height
     * @param parameters
     * @param threshold
     */
    public static void chooseBestAspectPreviewSize(int displayOrientation,
                                                   int width,
                                                   int height,
                                                   Camera.Parameters parameters,
                                                   double threshold,
                                                   YMRCameraMgr.CameraResolutionMode mode) {
        //先选择一个默认的预览分辨率
        Camera.Size ppsfv = parameters.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Log.d(TAG, "Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        if(mode == YMRCameraMgr.CameraResolutionMode.CAMERA_RESOLUTION_RANGE_MODE) {
            float ratio = width * 1.0f / height;
            if (width > height) {
                if (Math.abs(ratio - RATIO_16_9) > Math.abs(ratio - RATIO_4_3)) {
                    // 比率接近4：3或者3:4的，采用640x480
                    width = 640;
                    height = 480;
                }else {
                    width = 1280;
                    height = 720;
                }
            }else {
                if (Math.abs(ratio - RATIO_9_16) > Math.abs(ratio - RATIO_3_4)) {
                    // 比率接近4：3或者3:4的，采用480x640
                    width = 480;
                    height = 640;
                }else {
                    width = 720;
                    height = 1280;
                }
            }
        }

        Camera.Size bestSize = getBestAspectPreviewSize(displayOrientation, width, height, parameters, threshold);
        if (bestSize != null) {
            YYLog.i(TAG, "prefer " + width + "x" + height + ", choose " + bestSize.width + "x" + bestSize.height);
            parameters.setPreviewSize(bestSize.width, bestSize.height);
            return;
        }

        //选择失败，使用默认的预览分辨率
        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parameters.setPreviewSize(ppsfv.width, ppsfv.height);
        }
    }

    /**
     * 按比例更准确的【返回】合适的预览分辨率,系统不支持getSupportedPreviewSizes则返回null
     * @param displayOrientation
     * @param width
     * @param height
     * @param parameters
     * @return
     */
    public static Camera.Size getBestAspectPreviewSize(int displayOrientation,
                                                       int width,
                                                       int height,
                                                       Camera.Parameters parameters) {
        return (getBestAspectPreviewSize(displayOrientation, width, height,
                parameters, 0.0d));
    }

    /**
     * 取最合适比例的摄像头size,系统不支持getSupportedPreviewSizes则返回null
     * @param displayOrientation
     * @param width
     * @param height
     * @param parameters
     * @param threshold
     * @return
     */
    public static Camera.Size getBestAspectPreviewSize(int displayOrientation,
                                                       int width,
                                                       int height,
                                                       Camera.Parameters parameters,
                                                       double threshold) {
        double targetRatio = (double) height / width;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int optimalGap = Integer.MAX_VALUE;
        int gap = Integer.MAX_VALUE - 1;

        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) width / height;
        }

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        if (sizes == null){
            return null;
        }

        Collections.sort(sizes,
                Collections.reverseOrder(new SizeComparator()));

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            YYLog.i(TAG, "getSupportedPreviewSizes " + size.width + "x" + size.height + ", ratio:" + ratio);

            if (displayOrientation == 90 || displayOrientation == 270) {
                gap = Math.abs(size.width - width)
                        + Math.abs(size.height - height);
            }else {
                gap = Math.abs(size.width - height)
                        + Math.abs(size.height - width);
            }

            if (Math.abs(ratio - targetRatio) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
                optimalGap = gap;
            }

            if ( Math.abs(Math.abs(ratio - targetRatio) - minDiff) <= threshold){
                if (gap < optimalGap){
                    optimalSize = size;
                    minDiff = Math.abs(ratio - targetRatio);
                    optimalGap = gap;
                }
            }

        }

        return optimalSize;
    }


    /**
     * 取最合适比例的摄像头拍照size,系统不支持getSupportedPictureSizes则返回null
     * @param displayOrientation
     * @param width
     * @param height
     * @param parameters
     * @param threshold
     * @return
     */
    public static Camera.Size getBestAspectPictureSize(int displayOrientation,
                                                       int width,
                                                       int height,
                                                       Camera.Parameters parameters,
                                                       double threshold,
                                                       boolean thumbnail) {
        double targetRatio = (double) height / width;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int optimalGap = Integer.MAX_VALUE;
        int gap = Integer.MAX_VALUE - 1;

        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) width / height;
        }

        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
        if (thumbnail) {
            sizes = parameters.getSupportedJpegThumbnailSizes();
        }
        if (sizes == null){
            return null;
        }

        Collections.sort(sizes,
                Collections.reverseOrder(new SizeComparator()));

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            YYLog.info(TAG, (thumbnail ? "getSupportedThumbnailSizes ":"getSupportedPictureSizes ") + size.width + "x" + size.height + ", ratio:" + ratio);

            if (displayOrientation == 90 || displayOrientation == 270) {
                gap = Math.abs(size.width - width)
                        + Math.abs(size.height - height);
            }else {
                gap = Math.abs(size.width - height)
                        + Math.abs(size.height - width);
            }

            if (Math.abs(ratio - targetRatio) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
                optimalGap = gap;
            }

            if ( Math.abs(Math.abs(ratio - targetRatio) - minDiff) <= threshold){
                if (gap < optimalGap){
                    if (SDKCommonCfg.getRecordModePicture()) {
                        if (size.width >= width && size.height >= height) {
                            optimalSize = size;
                            minDiff = Math.abs(ratio - targetRatio);
                            optimalGap = gap;
                        }
                    } else {
                        optimalSize = size;
                        minDiff = Math.abs(ratio - targetRatio);
                        optimalGap = gap;
                    }
                }
            }

        }

        return optimalSize;
    }

    public static Camera.Size getUltraHDPictureSize(Camera.Parameters parameters, AspectRatioType aspect, double threshold) {
        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
        if (sizes == null){
            return null;
        }

        double targetRatio = (double)4.0f/(double)3.0f;
        if (aspect == AspectRatioType.ASPECT_RATIO_16_9) {
            targetRatio = (double)16.0f/(double)9.0f;
        }

        Collections.sort(sizes, Collections.reverseOrder(new SizeComparator()));

        for (Camera.Size size : sizes) {
            YYLog.info(TAG, " getSupportedPictureSizes " + size.width + "x" + size.height + " ratio :" + (float)size.width/(float)size.height);
        }

        for (Camera.Size size : sizes) {
            double curRatio = (double) size.width / (double)size.height;
            if (Math.abs(targetRatio - curRatio) < threshold) {
                return size;
            }
        }

        return sizes.get(0);
    }

    static class SizeComparator implements
            Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            int left = lhs.width * lhs.height;
            int right = rhs.width * rhs.height;

            if (left < right) {
                return (-1);
            } else if (left > right) {
                return (1);
            }

            return (0);
        }
    }
}



