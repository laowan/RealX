package com.ycloud.camera.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.TakePictureConfig;
import com.ycloud.api.config.TakePictureParam;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.utils.YYLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class YMRCameraMgr {

    public final static String TAG = "[camera]";

    //private WeakReference<ICameraEventListener> mCameraListenerRef = new WeakReference<ICameraEventListener>(null);

    private HashSet<WeakReference<ICameraEventListener>> mCameraListerSet = new  HashSet<WeakReference<ICameraEventListener>>();

    /** 以备用有多摄像头的玩法.*/
    ConcurrentHashMap<Integer, YMRCamera>   mCameraHashMap = new ConcurrentHashMap<>(0);

    private static volatile YMRCameraMgr singleton;

    public boolean bAutoCancelFocusAndMetering = false;
    public int mMeteringCount = -15;
    public long mMeteringValue = 0;

    public int frameCount = 0;
    public boolean bLockExpose = false;

    public int sceneStableCount = 0;
    public long sceneLuma = 0;

    public int exposureMode = 0;

    private int mCameraWidth;
    private int mCameraHeight;

    private TakePictureConfig mTakePictureConfig = null;
	
	private Timer mCameraTimer;
    private TimerTask mFocusTimerTask;
    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
    private boolean mFocusAreaSupported = false;
    private boolean mMeteringAreaSupported = false;
    private List<Camera.Area> mFocusArea;
    private List<Camera.Area> mMeteringArea;
    private AtomicBoolean mFocusing = new AtomicBoolean(false);
    private String mDefaultMasterFocusMode;

    private YMRCameraMgr() {
    }

    public static YMRCameraMgr getInstance() {
        if (singleton == null) {
            synchronized (YMRCameraMgr.class) {
                if (singleton == null) {
                    singleton = new YMRCameraMgr();
                }
            }
        }
        return singleton;
    }

    public void cancelAutoFocus(int cameraID) {
        // mVideoRecord.setFlashMode(flashMode);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            ycam.cancelAutoFocus();
        }
    }

    public void autoFocus(int cameraID,Camera.AutoFocusCallback autoFocusCallback) {
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            ycam.autoFocus(autoFocusCallback);
        }
    }

    public static enum CameraResolutionMode
    {
        /**精确的匹配输入的采集分辨率*/
        CAMERA_RESOLUTION_PRECISE_MODE,

        /** 模糊的匹配输入的采集分辨率,
         * 譬如说接近高清720*1080按照 720*1080采集,
         * 接近540*960的按照 540*960分辨率进行采集.
         * 采集后的预览编码数据，做缩放，裁剪.
         * */
        CAMERA_RESOLUTION_RANGE_MODE,
    };


    public YMRCamera getCamera(int cameraID) {
        YMRCamera  ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam == null) {
            synchronized (mCameraHashMap) {
                ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
                if(ycam == null) {
                    ycam = new YMRCamera(cameraID, this);
                    mCameraHashMap.put(Integer.valueOf(cameraID), ycam);
                }
            }
        }
        return ycam;
    }

    /**
     *
     * @param cameraID
     * @param conf
     * @param activity,   用来计算displayOrientation.
     */
    public long open(int cameraID, RecordConfig  conf, Activity activity, CameraResolutionMode resMode) {

        YYLog.i(TAG, "YMRCameraMGR.open, cameraID="+cameraID);

        YMRCamera ycam = getCamera(cameraID);
        if (SDKCommonCfg.getRecordModePicture()) {
            ycam.setTakePictureConfig(mTakePictureConfig);
        }
		
		if (mCameraTimer == null) {
            mCameraTimer = new Timer();
        }
        mFocusing.set(false);
        return ycam.open(conf, activity, resMode);
    }

    public long open(int cameraID, int displayOrientation, RecordConfig conf, CameraResolutionMode resMode) {
        YYLog.i(TAG, "YMRCameraMGR.open, cameraID="+cameraID);
        YMRCamera ycam = getCamera(cameraID);
        if (SDKCommonCfg.getRecordModePicture()) {
            ycam.setTakePictureConfig(mTakePictureConfig);
        }
		if (mCameraTimer == null) {
            mCameraTimer = new Timer();
        }
        mFocusing.set(false);
        return ycam.open(displayOrientation, conf,  resMode);
    }

    public void startPreview(int cameraID, SurfaceTexture surfaceTexture) {
        YYLog.i(TAG, "YMRCameraMGR.startPreview, cameraID="+cameraID);
        YMRCamera ycam = getCamera(cameraID);
        ycam.startPreview(surfaceTexture);
    }

    public void release(int cameraID) {
        YYLog.i(TAG, "YMRCameraMGR.release, cameraID="+cameraID);
        YMRCamera ycam = getCamera(cameraID);
        ycam.release();
    }

    public void releaseAll() {

        YYLog.i(TAG, "release All begin");

        if (mCameraTimer != null) {
            mCameraTimer.cancel();
            mCameraTimer = null;
        }

        if (mFocusTimerTask != null) {
            mFocusTimerTask.cancel();
            mFocusTimerTask = null;
        }

        synchronized (mCameraHashMap) {
            Iterator it = mCameraHashMap.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Integer, YMRCamera> ent = ( Map.Entry<Integer, YMRCamera>)it.next();
                ent.getValue().release();
            }
        }

        YYLog.i(TAG, "release All end");
    }

    public YMRCameraInfo getYMRCameraParameterInfo(int cameraID) {
        YYLog.info(this, "YMRCameraMGR.getYMRCameraParameterInfo, cameraID="+cameraID);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            return ycam.getCameraParameterInfo();
        }
        return null;
    }


      public void setFlashMode(int cameraID, String flashMode) {
        // mVideoRecord.setFlashMode(flashMode);
          YYLog.info(this, "YMRCameraMGR.setFlashMode, cameraID="+cameraID + "flashMode="+flashMode);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            ycam.setFlashMode(flashMode);
        }
    }

    public int getZoom(int cameraId) {
        YYLog.info(this, "YMRCameraMGR.getZoom, cameraID="+cameraId);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraId));
        if(ycam != null) {
            ycam.getZoom();
        }
        return  0;
    }

    public int getMaxZoom(int cameraId) {
        YYLog.info(this, "YMRCameraMGR.getMaxZoom, cameraID="+cameraId);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraId));
        if(ycam != null) {
            ycam.getMaxZoom();
        }
        return  0;
    }

    public void setZoom(int cameraId, int zoom) {

        YYLog.info(this, "YMRCameraMGR.setZoom, cameraID="+cameraId + ",zoom=" + zoom);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraId));
        if(ycam != null) {
            ycam.setZoom(zoom);
        }
    }

    public String getDefaultFocusMode(int cameraID) {
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            return ycam.getDefaultFocusMode();
        }
        YYLog.error(TAG, "get camera failed:" + cameraID + ",maybe not released");
        return null;
    }

    public Camera.Parameters getCameraParameters(int cameraID) {
//        YYLog.info(this, "YMRCameraMGR.getCameraParameters, cameraID="+ cameraID);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            return ycam.getCameraParameters();
        }
        YYLog.error(TAG, "get camera failed:" + cameraID + ",maybe not released");
        return null;
    }

    public void setPreviewCallbackWithBuffer(int cameraID, Camera.PreviewCallback previewCallback) {
        YMRCamera yCam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if (yCam != null) {
            yCam.setPreviewCallbackWithBuffer(previewCallback);
            YMRCameraInfo cameraInfo = getYMRCameraParameterInfo(cameraID);
            if (cameraInfo != null) {
                mCameraWidth = cameraInfo.mPreviewWidth;
                mCameraHeight = cameraInfo.mPreviewHeight;
            }
        }
    }

    public void addCallbackBuffer( int cameraID, byte[] callbackBuffer) {
        //YYLog.info(this, "YMRCameraMGR.addCallbackBuffer, cameraID="+cameraID);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
           ycam.addCallbackBuffer(callbackBuffer);
        }
    }


    public boolean setCameraParameters(int cameraID, Camera.Parameters cameraParameters) {
//        YYLog.info(this, "YMRCameraMGR.setCameraParameters, cameraID="+ cameraID);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            return ycam.setParameters(cameraParameters);
        }
        return false;
    }

    public Camera.CameraInfo getCameraInfo(int cameraID) {
        YYLog.info(this, "YMRCameraMGR.getCameraInfo, cameraID="+ cameraID);
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            return ycam.getCameraInfo();
        }
        return null;
    }

    public boolean addCameraEventListener(ICameraEventListener listener) {
        if(listener == null)
            return false;

        YYLog.i(TAG, "YMRCameraMGR.addCameraEventListener=");
        synchronized (mCameraListerSet) {

            Iterator<WeakReference<ICameraEventListener>> it = mCameraListerSet.iterator();
            while(it.hasNext()) {
                ICameraEventListener entry = it.next().get();
                if(entry != null &&  entry.equals(listener)) {
                    return true;
                }
            }
            mCameraListerSet.add(new WeakReference<ICameraEventListener>(listener));
        }
        return true;
    }

    public void removeCameraEventListener(ICameraEventListener listener) {

        if(listener == null)
            return ;

        YYLog.i(TAG, "YMRCameraMGR.removeCameraEventListener=");
        synchronized (mCameraListerSet) {
            Iterator<WeakReference<ICameraEventListener>> it = mCameraListerSet.iterator();
            while (it.hasNext()) {
                WeakReference<ICameraEventListener> itRef = it.next();
                ICameraEventListener entry = itRef.get();
                if (entry != null && entry.equals(listener)) {
                    mCameraListerSet.remove(itRef);
                    return;
                }
            }
        }
    }

    public void notifyOpenSuccess(int cameraID) {
        //copy the cameraList set, 摄像头事件不会太多.
        HashSet<WeakReference<ICameraEventListener>> tmpSet = copyCurrentListenerSet();

        Iterator<WeakReference<ICameraEventListener>> it = tmpSet.iterator();
        while(it.hasNext()) {
            ICameraEventListener listener = it.next().get();
            if(listener != null) {
                listener.onCameraOpenSuccess(cameraID);
            }
        }

        mDefaultMasterFocusMode =  getDefaultFocusMode(cameraID);
        YYLog.info(TAG, "notifyOpenSuccess , mDefaultMasterFocusMode " + mDefaultMasterFocusMode);
    }

    public void notifyOpenFail(int cameraID, String reason) {

        HashSet<WeakReference<ICameraEventListener>> tmpSet = copyCurrentListenerSet();
        Iterator<WeakReference<ICameraEventListener>> it = tmpSet.iterator();
        while(it.hasNext()) {
            ICameraEventListener listener = it.next().get();
            if(listener != null) {
                listener.onCameraOpenFail(cameraID, reason);
            }
        }
    }

    public void notifyCameraPreviewParameter(int cameraID,  YMRCameraInfo cameraInfo) {

        HashSet<WeakReference<ICameraEventListener>> tmpSet = copyCurrentListenerSet();
        Iterator<WeakReference<ICameraEventListener>> it = tmpSet.iterator();
        while(it.hasNext()) {
            ICameraEventListener listener = it.next().get();
            if(listener != null) {
                listener.onCameraPreviewParameter(cameraID, cameraInfo);
            }
        }
    }



    public void notifyCameraRelease(int cameraID) {
        HashSet<WeakReference<ICameraEventListener>> tmpSet = copyCurrentListenerSet();

        Iterator<WeakReference<ICameraEventListener>> it = tmpSet.iterator();
        while(it.hasNext()) {
            ICameraEventListener listener = it.next().get();
            if(listener != null) {
                listener.onCameraRelease(cameraID);
            }
        }
    }

    private  HashSet<WeakReference<ICameraEventListener>> copyCurrentListenerSet() {
        HashSet<WeakReference<ICameraEventListener>> tmpSet = new HashSet<WeakReference<ICameraEventListener>>();
        synchronized (mCameraListerSet) {
            Iterator<WeakReference<ICameraEventListener>> it = mCameraListerSet.iterator();
            while(it.hasNext()) {
                tmpSet.add(it.next());
            }
        }
        return tmpSet;
    }

    public void setAutoCancelFocusAndMetering(){
        bAutoCancelFocusAndMetering = true;
        mMeteringCount = -15;
    }

    public long calculateMeteringValue(byte[] data){
        if(mCameraWidth == 0 || mCameraHeight == 0){
            return  YMRCameraMgr.getInstance().mMeteringValue;
        }
        long tmp = 0,tmpMetering = 0;
        for(int i = 0;i < mCameraWidth*mCameraHeight - 1;i += 20){
            tmp = data[i];
            if(tmp < 0) {
                tmp = -tmp;
                tmp += 128;
            }
            tmpMetering += tmp;
        }
        return tmpMetering / (mCameraWidth * mCameraHeight / 20);
    }


    private void resetFocusMeteringArea(Camera.Parameters params) {
        mFocusArea = null;
        mMeteringArea = null;
        if(mFocusAreaSupported){
            params.setFocusAreas(mFocusArea);
        }
        if(mMeteringAreaSupported){
            params.setMeteringAreas(mMeteringArea);
        }
    }

    private final class AutoFocusCallback implements android.hardware.Camera.AutoFocusCallback {
        @Override
        public void onAutoFocus(boolean focused, final android.hardware.Camera camera) {
            YYLog.info(TAG, " onAutoFocus focused " + focused);
            mFocusing.set(false);
            if (mCameraTimer != null) {
                mFocusTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            //autoFocus调用后，会锁住对焦位置。要想恢复之前的对焦模式，需要调用cancelAutoFocus
                            if (bAutoCancelFocusAndMetering) {
                                camera.cancelAutoFocus();
                            } /*else {
                                Camera.Parameters parameters = camera.getParameters();
                                parameters.setFocusMode(mDefaultMasterFocusMode);
                                camera.setParameters(parameters);
                            }*/
                        } catch (RuntimeException e) {
                            YYLog.error(TAG, "RuntimeException at setParameters. ");
                        }
                    }
                };
                mCameraTimer.schedule(mFocusTimerTask, 3 * 1000);
            }
        }
    }

    public void focusAndMetering(final int cameraID, ArrayList<Camera.Area> areas, boolean autoCancel){
        if (mFocusing.get()) {  // 上次对焦还未完成
            YYLog.info(TAG, "focusAndMetering last focus not finish yet ~!");
            return;
        }
        mFocusing.set(true);
        bAutoCancelFocusAndMetering = autoCancel;
        if (mFocusTimerTask != null) {
            mFocusTimerTask.cancel();
            mFocusTimerTask = null;
        }
        Camera.Parameters params = getCameraParameters(cameraID);
        if (params == null) {
            YYLog.error(TAG, "focusAndMetering params == null !");
            return;
        }
        mFocusAreaSupported = CameraUtils.isFocusAreaSupported(params);
        mMeteringAreaSupported = CameraUtils.isMeteringAreaSupported(params);
        YYLog.info(TAG, " mFocusAreaSupported " + mFocusAreaSupported +  " mMeteringAreaSupported " + mMeteringAreaSupported);
        if (mFocusArea != null || mMeteringArea != null) {
            resetFocusMeteringArea(params);
            setCameraParameters(cameraID, params);
        }

        cancelAutoFocus(cameraID);

        if (mFocusAreaSupported) {
            params.setFocusAreas(areas);
            mFocusArea = areas;
        } else {
            YYLog.warn(TAG,  "focus areas not supported !");
            mFocusing.set(false);
        }

        if (mMeteringAreaSupported) {
            if(mMeteringArea == null){
                params.setMeteringAreas(areas);
                mMeteringArea = areas;
            }
        } else {
            YYLog.warn(TAG,  "metering areas not supported !");
        }


        /*List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }*/

        setCameraParameters(cameraID, params);

        if(mFocusAreaSupported){
            try {
                autoFocus(cameraID, mAutoFocusCallback);
            } catch (Exception e) {
                YYLog.error(TAG, "auto focus error!");
            }
        }
    }

//    public void focusAndMetering(int cameraID, ArrayList<Camera.Area> areas, boolean autoCancel){
//        try {
//            Camera.Parameters parameters = getCameraParameters(cameraID);
//            cancelAutoFocus(cameraID);
//
//            boolean supportFocusOrMetering = false;
//            if (parameters.getMaxNumFocusAreas() > 0) {
//                parameters.setFocusAreas(areas);
//                supportFocusOrMetering = true;
//            } else {
//                YYLog.warn(TAG, "Device does not support focus areas");
//            }
//
//            if (parameters.getMaxNumMeteringAreas() > 0) {
//                parameters.setMeteringAreas(areas);
//                supportFocusOrMetering = true;
//                if (autoCancel) {
//                    setAutoCancelFocusAndMetering();
//                }
//            } else {
//                YYLog.warn(TAG, "Device does not support metering areas");
//            }
//
//            if (supportFocusOrMetering) {
//                setCameraParameters(cameraID, parameters);
//            }
//
//            try {
//                autoFocus(cameraID, new Camera.AutoFocusCallback() {
//                    @Override
//                    public void onAutoFocus(boolean success, Camera camera) {
//                        YYLog.i(TAG, "focusAndMetering");
//                    }
//                });
//            }catch (Exception e) {
//                YYLog.error(TAG,e.getMessage());
//            }
//        }catch (Exception e) {
//            YYLog.error(TAG,e.getMessage());
//        }
//
//    }

    public void cancelFocusAndMetering(int cameraID){
        bAutoCancelFocusAndMetering = false;
        mMeteringCount = -15;
        exposureMode = 0;
        cancelAutoFocus(cameraID);
        Camera.Parameters parameters = getCameraParameters(cameraID);
        if (parameters == null) {
            YYLog.error(TAG, "get camera param return null, just return!!!");
            return;
        }

        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        ArrayList<Camera.Area> areas = new ArrayList<Camera.Area>();
        areas.add(new Camera.Area(new Rect(0,0,0,0), 0));
        if(parameters.getMaxNumFocusAreas() != 0){
            parameters.setFocusAreas(areas);
            if(parameters.getMaxNumMeteringAreas() != 0){
                parameters.setMeteringAreas(areas);
            }
        }
        else if(parameters.getMaxNumMeteringAreas() != 0){
            parameters.setMeteringAreas(areas);
        }
        setCameraParameters(cameraID,parameters);
        autoFocus(cameraID, new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                YYLog.i(TAG, "cancelFocusAndMetering");
            }
        });
    }

    public boolean detectSceneStable(byte[] data){
        int tmp = 0,curSceneLuma = 0;
        int wOffset = mCameraWidth/4;
        int hOffset = mCameraHeight/4;
        for(int i = 0;i < wOffset; i++){
            for(int j = 0;j < hOffset; j++){
                tmp = data[j*mCameraWidth + i];
                if(tmp < 0) {
                    tmp = -tmp;
                    tmp += 128;
                }
                curSceneLuma += tmp;
            }
        }
        for(int i = mCameraWidth - wOffset;i < mCameraWidth; i++){
            for(int j = 0;j < hOffset; j++){
                tmp = data[j*mCameraWidth + i];
                if(tmp < 0) {
                    tmp = -tmp;
                    tmp += 128;
                }
                curSceneLuma += tmp;
            }
        }
        for(int i = 0;i < wOffset; i++){
            for(int j = mCameraHeight - hOffset;j < mCameraHeight; j++){
                tmp = data[j*mCameraWidth + i];
                if(tmp < 0) {
                    tmp = -tmp;
                    tmp += 128;
                }
                curSceneLuma += tmp;
            }
        }
        for(int i = mCameraWidth - wOffset;i < mCameraWidth; i++){
            for(int j = mCameraHeight - hOffset;j < mCameraHeight; j++){
                tmp = data[j*mCameraWidth + i];
                if(tmp < 0) {
                    tmp = -tmp;
                    tmp += 128;
                }
                curSceneLuma += tmp;
            }
        }

        if (wOffset == 0 || hOffset == 0) {
            YYLog.error(TAG, "wOffset or hOffset is zero");
            return true;
        }

        curSceneLuma = curSceneLuma/wOffset/hOffset/4;
        //YYLog.i(TAG, "curSceneLuma: " + curSceneLuma + " sceneLuma: " + sceneLuma);
        if((curSceneLuma - sceneLuma) > 8 || (sceneLuma - curSceneLuma) > 8){
            sceneLuma = curSceneLuma;
            sceneStableCount = 0;
            return false;
        }
        else{
            sceneStableCount++;
            if(sceneStableCount > 3){
                return true;
            }
            else{
                return false;
            }
        }
    }

    public void lockExpose(int cameraID, boolean lock){
        Camera.Parameters parameters = getCameraParameters(cameraID);
        if(parameters != null){
            if(parameters.isAutoExposureLockSupported()){
                parameters.setAutoExposureLock(lock);
            }
            if(parameters.isAutoWhiteBalanceLockSupported()){
                parameters.setAutoWhiteBalanceLock(lock);
            }
            setCameraParameters(cameraID,parameters);
        }
    }

    public void setTakePictureConfig(TakePictureConfig config) {
        mTakePictureConfig = config;
    }

    public void takePicture(int cameraID, TakePictureParam param) {
        YMRCamera ycam = mCameraHashMap.get(Integer.valueOf(cameraID));
        if(ycam != null) {
            ycam.takePicture(param);
        } else {
            YYLog.error(TAG, "takePicture error ! ycam == null.");
        }
    }
}