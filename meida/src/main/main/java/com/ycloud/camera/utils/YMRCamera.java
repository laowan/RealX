package com.ycloud.camera.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.Build;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.api.config.TakePictureConfig;
import com.ycloud.api.config.TakePictureParam;
import com.ycloud.common.Constant;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.utils.YYLog;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static com.ycloud.api.config.AspectRatioType.ASPECT_RATIO_1_1;

/**
 * Created by kele on 2017/4/14.
 */

public class YMRCamera {

    public final static String TAG = "[camera]";

    private WeakReference<SurfaceTexture> mPreviewSurfaceTextureRef;
    private WeakReference<Camera.PreviewCallback> mPreviewCallbackRef;

    YMRCameraInfo mYMRCameraInfo = null;
    Camera mCamera = null;
    private Object mCameraLock = new Object();
    private int mExpectWidth = 0;
    private int mExpectHeight = 0;
    private YMRCameraMgr mCameraMgr = null;

    private TakePictureParam mTakePictureParam;
    private TakePictureConfig mTakePictureConfig;
    // 图片编码线程，防止耗时过久阻塞系统拍照回调
    private ExecutorService mSingleThreadExecutor = null;
    private Context mContext = null;

    private static AtomicLong sCameraLindIDGenerator = new AtomicLong(0);

    private YMRCameraMgr.CameraResolutionMode mResolutionMode = YMRCameraMgr.CameraResolutionMode.CAMERA_RESOLUTION_PRECISE_MODE;

    private String mDefaultMasterFocusMode;

    public YMRCamera(int cameraID, YMRCameraMgr mgr) {
        mYMRCameraInfo = new YMRCameraInfo(cameraID);
        mCameraMgr = mgr;
    }

    /**
     * @param conf
     * @param activity, 用来计算displayOrientaion.
     */
    public long open(RecordConfig conf, Activity activity, YMRCameraMgr.CameraResolutionMode resMode) {
        if (SDKCommonCfg.getRecordModePicture()) {
            mContext = activity.getApplicationContext();
        }
        int displayRotation = CameraUtils.getDisplayRotation(activity);
        return open(displayRotation, conf, resMode);
    }

    public long open(int displayRotation, RecordConfig conf, YMRCameraMgr.CameraResolutionMode resMode) {

        YYLog.info(this, Constant.MEDIACODE_CAMERA + " open camera, captureWidth=" + conf.getCaptureWidth() + " captureHeight=" + conf.getCaptureHeight());
        synchronized (mCameraLock) {
            if (mCamera != null) {
                YYLog.i(TAG, "camera already opened, release first");
                release();
                if (mCamera != null) {
                    YYLog.e(TAG, "camera already initialized, should release first!");
                    return -1;
                }
            }

            try {
                mCamera = CameraUtils.openCamera(mYMRCameraInfo.getCameraID());
                if (mCamera == null) {
                    YYLog.e(TAG, "Unable to open camera");
                    if (mCamera != null && mCameraMgr != null) {
                        mCameraMgr.notifyOpenFail(mYMRCameraInfo.getCameraID(), "Unable to open camera");
                    }
                    return -1;
                }
                Camera.Parameters parms = mCamera.getParameters();
                mExpectWidth = conf.getCaptureWidth();
                mExpectHeight = conf.getCaptureHeight();

                mDefaultMasterFocusMode = parms.getFocusMode();
                YYLog.info(this, "mDefaultMasterFocusMode: " + mDefaultMasterFocusMode);

                //CameraUtils.choosePreviewSize(parms, desiredHeight, desiredWidth);
                YMRCameraUtils2.chooseBestAspectPreviewSize(displayRotation, mExpectWidth, mExpectHeight, parms, 0.05, resMode);
                YMRCameraUtils.PreviewSize specialSize = YMRCameraUtils.getSpecialCameraPreviewSizeWithModel(Build.MODEL, parms.getPreviewSize(), mYMRCameraInfo.mCameraFacingFront);
                if (specialSize != null) {
                    parms.setPreviewSize(specialSize.width, specialSize.height);
                }

                if (SDKCommonCfg.getRecordModePicture() && mTakePictureConfig != null) {
                    Camera.Size size;
                    if (mTakePictureConfig.mResolutionType == TakePictureConfig.ResolutionSetType.SET_RESOLUTION) {
                        size = YMRCameraUtils2.getBestAspectPictureSize(displayRotation, mTakePictureConfig.mPictureWidth,
                                mTakePictureConfig.mPictureHeight, parms, 0.05, false);
                    } else {
                        size = YMRCameraUtils2.getUltraHDPictureSize(parms, mTakePictureConfig.mAspectRatio, 0.05);
                    }
                    YYLog.info(TAG, "setPictureSize " + size.width + "x" + size.height);
                    parms.setPictureSize(size.width, size.height);

                    //List<Integer> formats = parms.getSupportedPictureFormats();
                    //for(Integer format : formats) {
                    //    YYLog.info(TAG," Supported Picture format: " + format.toString());
                    //}
                    /** This format is always supported as an output format for the camera2 API, and as a picture format for the older Camera API */
                    parms.setPictureFormat(ImageFormat.JPEG);
                    if (mYMRCameraInfo.getCameraID() == 1 /*YMRCameraUtils.CameraFacing.FacingFront*/) {
                        parms.setRotation(270);
                    } else if (mYMRCameraInfo.getCameraID() == 0 /*YMRCameraUtils.CameraFacing.FacingBack*/) {
                        parms.setRotation(90);
                    }
                    parms.setJpegQuality(TakePictureConfig.DEFAULT_JPEG_QUALITY);

                    size = YMRCameraUtils2.getBestAspectPictureSize(displayRotation, mTakePictureConfig.mPictureWidth,
                            mTakePictureConfig.mPictureHeight, parms, 0.05, true);
                    if (size != null) {
                        YYLog.info(TAG, "setJpegThumbnailSize width " + size.width + " height " + size.height);
                        parms.setJpegThumbnailSize(size.width, size.height);
                    }
                    parms.setJpegThumbnailQuality(mTakePictureConfig.DEFAULT_JPEG_QUALITY);

                    mSingleThreadExecutor = Executors.newSingleThreadExecutor();
                }

                //focus
                List<String> focusModes = parms.getSupportedFocusModes();
                for (int i = 0; i < focusModes.size(); i++) {
                    YYLog.info(TAG, "focuse mode " + focusModes.get(i));
                }

                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parms.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parms.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }

                if(SDKCommonCfg.getRecordModePicture()) {
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        parms.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    }
                }

                if (Build.VERSION.SDK_INT >= 15) {
                    if (parms.isVideoStabilizationSupported()) {
                        if (!RecordDynamicParam.getInstance().isForbidSetVideoStabilization()) {
                            parms.setVideoStabilization(true);
                        }
                    }
                }

                parms.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);

                int previewFrameRate = 30;
                List<Integer> supportedPreviewFrameRates = parms.getSupportedPreviewFrameRates();
                int supportMaxFrameRate = Collections.max(supportedPreviewFrameRates).intValue();
                if (previewFrameRate > supportMaxFrameRate) {
                    YYLog.warn(TAG, "support max frame rate is:" + supportMaxFrameRate);
                    previewFrameRate = supportMaxFrameRate;
                }
                parms.setPreviewFrameRate(previewFrameRate);

                mCamera.setParameters(parms);

                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(mYMRCameraInfo.getCameraID(), cameraInfo);
                int displayOrientation = YMRCameraUtils.setCameraDisplayOrientation(displayRotation, mCamera, cameraInfo);
                if (displayOrientation != -1) {
                    mYMRCameraInfo.mDisplayOrientation = displayOrientation;
                    YYLog.info(this, Constant.MEDIACODE_CAMERA + " mDisplayOrientation=" + mYMRCameraInfo.mDisplayOrientation);
                }

                mYMRCameraInfo.copyFrom(parms);
                mYMRCameraInfo.mCameraInfoOrientation = cameraInfo.orientation;
                YYLog.i(TAG, "openCamera width:" + mYMRCameraInfo.mPreviewWidth + ", height:" + mYMRCameraInfo.mPreviewHeight + ", displayRotation:" + displayRotation);

                if (displayRotation == 0 || displayRotation == 180) {
                    if (mYMRCameraInfo.mPreviewWidth > mYMRCameraInfo.mPreviewHeight) {
                        //swap width and height
                        mYMRCameraInfo.mPreviewHeight = mYMRCameraInfo.mPreviewWidth + mYMRCameraInfo.mPreviewHeight;
                        mYMRCameraInfo.mPreviewWidth = mYMRCameraInfo.mPreviewHeight - mYMRCameraInfo.mPreviewWidth;
                        mYMRCameraInfo.mPreviewHeight = mYMRCameraInfo.mPreviewHeight - mYMRCameraInfo.mPreviewWidth;

                        YYLog.i(TAG, "landscape view, so switch width with width:" + mYMRCameraInfo.mPreviewWidth + ", height:" + mYMRCameraInfo.mPreviewHeight + ", displayRotation:" + displayRotation);
                    }
                }
                mYMRCameraInfo.mState = YMRCameraInfo.CameraState.CAMERA_STATE_OPEN;

                //TODO.
                mYMRCameraInfo.mDisplayRotation = displayRotation;
                mYMRCameraInfo.mResolutionMode = mResolutionMode;
                mYMRCameraInfo.setCameraLinkID(sCameraLindIDGenerator.addAndGet(1));



                YYLog.i(TAG, "openCamera success!!!, " + mYMRCameraInfo.toString());
                if (mCamera != null && mCameraMgr != null) {
                    mCameraMgr.notifyOpenSuccess(mYMRCameraInfo.getCameraID());
                }
            } catch (Throwable throwable) {
                YYLog.e(TAG, "[exception] openCamera error! " + throwable);
                mCamera = null;
                mYMRCameraInfo.mState = YMRCameraInfo.CameraState.CAMERA_STATE_CLOSED;
                return -1;
            }
        }

        if (mCamera != null && mCameraMgr != null) {
            //不要锁住回调.
            mCameraMgr.notifyCameraPreviewParameter(mYMRCameraInfo.getCameraID(), mYMRCameraInfo);
        }

        return mYMRCameraInfo.getCameraLinkID();
    }

    public void release() {
        YYLog.i(TAG, "YMRCamera.release. cameraID=" + mYMRCameraInfo.getCameraID());

        synchronized (mCameraLock) {
            if (mCamera != null) {
                try {
                    mCamera.setPreviewCallback(null);
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                    mYMRCameraInfo.mState = YMRCameraInfo.CameraState.CAMERA_STATE_CLOSED;
                    mYMRCameraInfo.reset();
                    if (SDKCommonCfg.getRecordModePicture()) {
                        if (mSingleThreadExecutor != null) {
                            mSingleThreadExecutor.shutdown();
                        }
                    }

                    YYLog.i(TAG, "releaseCamera -- done");

                } catch (Throwable throwable) {
                    YYLog.e(TAG, "releaseCamera error! " + throwable);
                }
            }
        }

        if (mCamera != null && mCameraMgr != null) {
            //不要锁住回调.
            mCameraMgr.notifyCameraRelease(mYMRCameraInfo.getCameraID());
        }
    }

    YMRCameraInfo getCameraParameterInfo() {
        return mYMRCameraInfo;
    }


    public boolean setFlashMode(String mode) {
        synchronized (mCameraLock) {
            if (mCamera == null)
                return false;
            YYLog.d(TAG, "setFlashMode mode: " + mode);
            Camera.Parameters param = mCamera.getParameters();
            List<String> flashModeList = param.getSupportedFlashModes();
            if (null == flashModeList)
                return false;
            if (flashModeList.contains(mode)) {
                param.setFlashMode(mode);
                setParameters(param);
            } else {
                YYLog.e(TAG, "mode not supported: " + mode);
                return false;
            }
            return true;
        }
    }


    public Camera.Parameters getCameraParameters() {
        synchronized (mCameraLock) {
            if (mCamera == null)
                return null;
            try {
                YYLog.d(TAG, "set getCameraParameters success:");
                return mCamera.getParameters();
            } catch (RuntimeException e) {
                // just in case something has gone wrong
                YYLog.e(TAG, "failed to getCameraParameters: " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Set Camera Parameters
     *
     * @param parameters set Android camera parameters, see Camera.Parameters for more details.
     */
    public boolean setParameters(Camera.Parameters parameters) {
        synchronized (mCameraLock) {
            if (mCamera == null) {
                YYLog.info(this, "set Parameters fail, camera is not open");
                return false;
            }
            try {
                mCamera.setParameters(parameters);
//                YYLog.info(this, "set Parameters success:");
                return true;
            } catch (RuntimeException e) {
                // just in case something has gone wrong
                YYLog.error(this, "[exception] failed to set parameters: " + e.getMessage());
                return false;
            }
        }
    }

    public boolean isCameraFocusModeSupported(String mode) {
        synchronized (mCameraLock) {
            if (null == mCamera)
                return false;
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> focus = parameters.getSupportedFocusModes();
            if (focus.contains(mode))
                return true;
            else
                return false;
        }
    }

    public Camera.CameraInfo getCameraInfo() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        try {
            Camera.getCameraInfo(mYMRCameraInfo.getCameraID(), cameraInfo);
        } catch (Throwable t) {
            YYLog.error(this, "getCameraInfo exception:" + t.toString());
        }
        return cameraInfo;
    }


    public void startPreview(SurfaceTexture surfaceTexture) {
        YYLog.i(TAG, "startPreviewWithSurfaceTexture");

        synchronized (mCameraLock) {
            if (mCamera == null) {
                YYLog.i(TAG, "startPreview, mCamera == null, should openCamera first!");
                return;
            }
            try {
                mCamera.setPreviewTexture(surfaceTexture);
                mPreviewSurfaceTextureRef = new WeakReference<SurfaceTexture>(surfaceTexture);
                mCamera.startPreview();
            } catch (Throwable throwable) {
                YYLog.e(TAG, "startPreviewWithSurfaceTexture error! " + throwable);
            }
        }
    }


    public void setPreviewCallbackWithBuffer(Camera.PreviewCallback previewCallback) {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                int width = mYMRCameraInfo.mPreviewWidth;
                int height = mYMRCameraInfo.mPreviewHeight;
                int buffSize = width * height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
                for (int i = 0; i < 3; i++) {
                    byte[] buffer = new byte[buffSize];
                    mCamera.addCallbackBuffer(buffer);
                }
                mCamera.setPreviewCallbackWithBuffer(previewCallback);
                mPreviewCallbackRef = new WeakReference<Camera.PreviewCallback>(previewCallback);

                YYLog.info(TAG, "setPreviewCallbackWithBuffer success");
            }
        }
    }

    public void setPreviewCallback(Camera.PreviewCallback previewCallback) {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                mCamera.setPreviewCallback(previewCallback);
                mPreviewCallbackRef = new WeakReference<Camera.PreviewCallback>(previewCallback);
            }
        }
    }

    /**
     * 需要考虑多线程问题，如下的逻辑.
     * 1. 主线程close了Camera, 然后更换了preview size.
     *
     * @param callbackBuffer
     */
    public void addCallbackBuffer(byte[] callbackBuffer) {
        if (callbackBuffer == null)
            return;

        synchronized (mCameraLock) {
            if (mCamera != null) {
                /**
                 * 考虑多个线程对摄像头的操作问题，譬如
                 * 1)主线程release camera
                 * 2)主线程更改了preview size1, 重现打开camera
                 * 3)人脸识别的线程中处理之前messagequeue中的preview data(之前的preview size1的数据), 然后再把buffer加入到
                 *   camera 的buffer queue中来，这里需要判断是否与preview size2匹配， 如果匹配则加入.
                 */
                if (callbackBuffer.length != 0 && callbackBuffer.length == getCurrentPreviewBufferSize()) {
                    mCamera.addCallbackBuffer(callbackBuffer);
                }
            }
        }
    }

    private int getCurrentPreviewBufferSize() {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                return (mYMRCameraInfo.mPreviewWidth * mYMRCameraInfo.mPreviewHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8);
            }
        }
        return 0;
    }

    public void cancelAutoFocus() {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                mCamera.cancelAutoFocus();
            }
        }
    }

    public void autoFocus(Camera.AutoFocusCallback autoFocusCallback) {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                mCamera.autoFocus(autoFocusCallback);
            }
        }
    }

    public int getZoom() {

        synchronized (mCameraLock) {
            if (mCamera != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters.isZoomSupported()) {
                    return parameters.getZoom();
                } else {
                    YYLog.info(TAG, "camera zoom not Supported");
                }
            }
        }

        return 0;
    }

    public int getMaxZoom() {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters.isZoomSupported()) {
                    return parameters.getMaxZoom();
                } else {
                    YYLog.info(TAG, "camera zoom not Supported");
                }
            }
        }

        return 0;
    }

    public void setZoom(int zoom) {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters.isZoomSupported()) {
                    parameters.setZoom(zoom);
                    mCamera.setParameters(parameters);
                } else {
                    YYLog.info(TAG, "camera zoom not Supported");
                }
            }
        }
    }


	/* for YOYI */
	public String getDefaultFocusMode() {
	    return mDefaultMasterFocusMode;
    }


    private Camera.ShutterCallback shutterCB = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            /* Empty Callbacks play a system default sound effect ! */
            // TODO. add specified sound effect.
            if (!mTakePictureConfig.mUseDefaultSoundEffect) {

            }
        }
    };

    private Camera.PictureCallback rawCB = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            YYLog.info(TAG, "rawCB onPictureTaken format : " + camera.getParameters().getPictureFormat());
            if (camera.getParameters().getPictureFormat() == ImageFormat.JPEG) {

            }
        }
    };

    // 此时的图片是横屏
    private Rect getCropRect(int w, int h, AspectRatioType aspect) {
        Rect rect = new Rect(0, 0, w, h);
        float inputAspect = (float) w / (float) (h);

        float aspect3v4 = (float) 3 / (float) 4;
        float aspect9v16 = (float) 9 / (float) 16;
        if (w >= h) {                               // 1.0 aspect 按横屏图像处理
            aspect3v4 = (float) 4 / (float) 3;
            aspect9v16 = (float) 16 / (float) 9;
        }

        float targetAspect = aspect3v4;             // default
        switch (aspect) {
            case ASPECT_RATIO_4_3:
                targetAspect = aspect3v4;
                break;
            case ASPECT_RATIO_16_9:
                targetAspect = aspect9v16;
                break;
            case ASPECT_RATIO_1_1:
                targetAspect = 1.0f;
                break;
            default:
                break;
        }

        if (Float.compare(inputAspect, targetAspect) < 0) {         // inputAspect < targetAspect, 上下裁剪
            int newH = (int) (w / targetAspect);
            rect.top += (h - newH) / 2;
            rect.bottom -= (h - newH) / 2;
        } else if (Float.compare(inputAspect, targetAspect) > 0){   // inputAspect > targetAspect 左右裁剪
            int newW = (int) (h * targetAspect);
            rect.left += (w - newW) / 2;
            rect.right -= (w - newW) / 2;
        } else {
            // nothing
        }

        return rect;
    }

    private Bitmap rotate(Bitmap bitmap, int degree, boolean mirrorX, boolean mirrorY, AspectRatioType aspect) {
//        if (degree == 0 && !mirrorX && !mirrorY) {
//            return bitmap;
//        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix mtx = new Matrix();
        if (degree == 90 || degree == 180 || degree == 270) {
            mtx.setRotate(degree);
        }
        if (mirrorY) {
            mtx.preScale(1.0f, -1.0f);   // mirror by Y axis
        }
        if (mirrorX) {
            mtx.preScale(-1.0f, 1.0f);   // mirror by X axis
        }

        Rect r = getCropRect(w, h, aspect);
        return Bitmap.createBitmap(bitmap, r.left, r.top, (r.right-r.left), (r.bottom-r.top), mtx, true);
    }

    private int writeToFile(String path, byte[] data) {
        FileOutputStream out;
        try {
            out = new FileOutputStream(path);
            out.write(data);
            out.flush();
            out.close();
            return 0;
        } catch (FileNotFoundException e) {
            YYLog.error(TAG, String.format(Locale.getDefault(), "%s not found: %s", path, e.toString()));
        } catch (IOException e) {
            YYLog.error(TAG, "IOException: " + e.getMessage());
        }
        return -1;
    }

    private void notifyResult(int result, String path, boolean thumbnail) {
        if (mTakePictureConfig != null && mTakePictureConfig.mListener != null) {
            if (!thumbnail) {
                mTakePictureConfig.mListener.onTakenPicture(result, path);
            } else {
                mTakePictureConfig.mListener.onTakenThumbnailPicture(result, path);
            }
        }
    }

    private Bitmap processExif(String path, Bitmap bitmap, AspectRatioType aspect, boolean bFlipX) {
        try {
            boolean mirror = false;
            if (mYMRCameraInfo.getIsFacingFront() && !bFlipX) {
                mirror = true;
            }
            Bitmap thumbnail = null;
            ExifInterface exif = new ExifInterface(path);
            String rotate = exif.getAttribute(ExifInterface.TAG_ORIENTATION);

            if (mTakePictureParam.mThumbnailPath != null) {
                if (exif.hasThumbnail()) {
                    byte[] thumb = exif.getThumbnail();
                    thumbnail = BitmapFactory.decodeByteArray(thumb, 0, thumb.length);
                } else {
                    notifyResult(-1, mTakePictureParam.mThumbnailPath, true);
                }
            }

            YYLog.info(TAG, " EXIF value TAG_ORIENTATION : " + rotate + " bFlipX " + bFlipX);
            int rotateDegree = Integer.parseInt(rotate);
            switch (rotateDegree) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    bitmap = rotate(bitmap, 90, false, mirror, aspect);
                    if (thumbnail != null) {
                        thumbnail = rotate(thumbnail, 90, false, mirror, aspect);
                    }
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    bitmap = rotate(bitmap, 180, mirror, false, aspect);
                    if (thumbnail != null) {
                        thumbnail = rotate(thumbnail, 180, mirror, false, aspect);
                    }
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    bitmap = rotate(bitmap, 270, false, mirror, aspect);
                    if (thumbnail != null) {
                        thumbnail = rotate(thumbnail, 270, false, mirror, aspect);
                    }
                    break;
                case ExifInterface.ORIENTATION_UNDEFINED:
                case ExifInterface.ORIENTATION_NORMAL:
                    bitmap = rotate(bitmap, 0, mirror, false, aspect);
                    if (thumbnail != null) {
                        thumbnail = rotate(thumbnail, 0, mirror, false, aspect);
                    }
                    break;
                default:
                    break;
            }

            if (exif.hasThumbnail() && thumbnail != null) {
                FileOutputStream out = new FileOutputStream(mTakePictureParam.mThumbnailPath);
                if (mTakePictureParam.mThumbnailCodeType == TakePictureParam.PictureCodingType.PICTURE_CODING_TYPE_PNG) {
                    thumbnail.compress(Bitmap.CompressFormat.PNG, mTakePictureParam.mThumbnailQuality, out);
                } else {
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, mTakePictureParam.mThumbnailQuality, out);
                }
                out.flush();
                out.close();
                notifyResult(0, mTakePictureParam.mThumbnailPath, true);
            }

        } catch (IOException e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }
        return bitmap;
    }

    private void ProcessHumanActionInfo(Bitmap bitmap) {
        if (mContext == null || bitmap == null) {
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        ByteBuffer pixBuf = ByteBuffer.allocate(width * height * 4);
        pixBuf.clear();
        bitmap.copyPixelsToBuffer(pixBuf);
        int tryCount = 3;
        STMobileFaceDetectionWrapper.FacePointInfo point = null;
        while (tryCount > 0) {
            STMobileFaceDetectionWrapper.getPIctureInstance(mContext).onVideoFrame(pixBuf.array(), 0, width, height, true);
            point = STMobileFaceDetectionWrapper.getPIctureInstance(mContext.getApplicationContext()).getCurrentFacePointInfo();
            tryCount--;
            if (point != null && point.mFaceCount != 0) {
                break;
            }
        }
        if (mTakePictureConfig != null && mTakePictureConfig.mListener != null) {
            mTakePictureConfig.mListener.onTakenFacePoint(point);
        }
    }

    private void ProcessPicture(String path) {
        try {
            long time = System.currentTimeMillis();
            Bitmap realImage = BitmapFactory.decodeFile(path);
            if (realImage == null) {
                notifyResult(-1, path, false);
                return;
            }
            realImage = processExif(path, realImage, mTakePictureParam.mAspect, mTakePictureParam.mFlipX);
            if (mTakePictureParam.mDoFaceDetect) {
                ProcessHumanActionInfo(realImage);
            }
            FileOutputStream out = new FileOutputStream(path);
            boolean result = realImage.compress(Bitmap.CompressFormat.JPEG, mTakePictureParam.mQuality, out);
            out.flush();
            out.close();
            notifyResult(result ? 0 : -1, path, false);
            realImage.recycle();
            YYLog.info(TAG, "onPictureTaken cost : " + (System.currentTimeMillis() - time));
            return;
        } catch (IOException e) {
            YYLog.error(TAG, "Exception:" + e.getMessage());
            notifyResult(-1, path, false);
        }
    }

    /**
     * we don't use system jpeg which without our effect, but parms.setRotation() not work on all devices!
     * and ImageFormat.JPEG is the only MUST supported format for setPictureFormat() on all android devices!
     * some devices store rotation information in Exif but not rotate the jpeg.
     * TODO. 耗时较长，需要优化
     */
    private Camera.PictureCallback jpgCB = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (mCamera == null || data == null || camera == null) {
                YYLog.error(TAG, "onPictureTaken camera released ! return.");
                return;
            }

            Camera.Size size = camera.getParameters().getPictureSize();
            YYLog.info(TAG, "jpgCB onPictureTaken size : " + size.width + " x " + size.height);
            final String path = mTakePictureParam.mImagePath;
            if (camera.getParameters().getPictureFormat() == ImageFormat.JPEG) {
                if (writeToFile(path, data) != 0) {
                    notifyResult(-1, path, false);
                    return;
                }
                if (mSingleThreadExecutor != null) {
                    mSingleThreadExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            ProcessPicture(path);
                        }
                    });
                }
            }
        }
    };

    public void takePicture(TakePictureParam param) {
        synchronized (mCameraLock) {
            if (null != mCamera) {
                mTakePictureParam = param;
                mCamera.takePicture(shutterCB, null, jpgCB);
            } else {
                if (mTakePictureConfig.mListener != null) {
                    mTakePictureConfig.mListener.onTakenPicture(-1, param.mImagePath);
                }
            }
        }
    }

    public void setTakePictureConfig(TakePictureConfig config) {
        synchronized (mCameraLock) {
            mTakePictureConfig = config;
        }
    }
}
