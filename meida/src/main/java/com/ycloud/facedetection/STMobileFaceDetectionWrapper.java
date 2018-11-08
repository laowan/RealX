package com.ycloud.facedetection;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.WindowManager;

import com.orangefilter.OrangeFilter;
import com.orangefilter.OrangeFilterApi;
import com.sensetime.stmobile.STCommonNative;
import com.sensetime.stmobile.STHumanActionParamsType;
import com.sensetime.stmobile.STMobileFaceAttributeNative;
import com.sensetime.stmobile.STMobileHumanActionNative;
import com.sensetime.stmobile.STRotateType;
import com.sensetime.stmobile.model.STFaceAttribute;
import com.sensetime.stmobile.model.STHumanAction;
import com.sensetime.stmobile.model.STMobile106;
import com.sensetime.stmobile.model.STMobileBodyInfo;
import com.sensetime.stmobile.model.STMobileFaceInfo;
import com.sensetime.stmobile.model.STPoint;
import com.sensetime.stmobile.utils.Accelerometer;
import com.sensetime.stmobile.utils.FileUtils;
import com.sensetime.stmobile.utils.STLicenseUtils;
import com.sensetime.stmobile.utils.STUtils;
import com.venus.Venus;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.videorecord.ICameraPreviewCallbackListener;
import com.ycloud.camera.utils.ICameraEventListener;
import com.ycloud.camera.utils.YMRCameraInfo;
import com.ycloud.camera.utils.YMRCameraMgr;
import com.ycloud.common.Constant;
import com.ycloud.gpuimagefilter.utils.SegmentCacheDetectWrapper;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.YYLog;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by jtzhu on 2018/3/1.
 * STMobile library wrapper, include Face Detecting and Body Detecting feature
 */

public class STMobileFaceDetectionWrapper implements Camera.PreviewCallback, ICameraEventListener {
    private static final String TAG = "STMobileFaceDetectionWrapper";

    static {
        try {
            STMobileHumanActionNative.loadLibrary();
        } catch (Throwable throwable) {
            YYLog.error(TAG, "STMobileFaceDetectionWrapper.loadLibrary failed");
        }
    }

    private long mHumanActionDetectConfig = STMobileHumanActionNative.ST_MOBILE_FACE_DETECT;
    private boolean mIsCreateHumanActionHandleSucceeded = false;
    private boolean mIsPaused = false;
    private boolean mCameraChanging = false;
    private boolean mNeedFaceAction = false;
    private boolean mNeedFaceExpression = false;
    private boolean mNeedFaceAttribute = false;
    private boolean mNeedCheckFace = false;
    private static AtomicBoolean mCameraModel = new AtomicBoolean(true);
    private int screenWidth;
    private int screenHeight;
    private int mImageHeight;
    private int mImageWidth;
    private int SCALE_WIDTH ;
    private int SCALE_HEIGHT;
    private static final int mDefaultW = 720;
    private static final int mDefaultH = 1280;
    private int mMaxFace = 5;
    private int mFaceAttributeFrameCount = 0;
    private float scaleFactor = 1.0f;
    private byte[] mImageData;
    private byte[] mTmpBuffer;
    private byte[] mScaleBuffer;
    private byte[] mCameraCallbackImageData;

    private static final int MESSAGE_ADD_SUB_MODEL      = 1001;
    private static final int MESSAGE_REMOVE_SUB_MODEL   = 1002;

    private static final int MESSAGE_PROCESS_IMAGE      = 100;
    private static final int MESSAGE_SETPREVIEWCALLBACK = 103;
    private static final int MESSAGE_INIT_FACEDATE      = 104;
    private static final int MESSAGE_SET_FACE_LIMIT     = 105;
    private static final int MESSAGE_SET_BODY_LIMIT     = 106;

    private static final int MSG_UPDATE_HAND_ACTION_INFO        = 100;
    private static final int MSG_RESET_HAND_ACTION_INFO         = 101;
    private static final int MSG_UPDATE_FACE_ACTION_INFO        = 102;
    private static final int MSG_UPDATE_FACE_EXPRESSION_INFO    = 103;

    private static final int MSG_CAMERA_CALLBACK    = 10000;

    /** 人脸肢体识别线程 */
    private HandlerThread mHandlerThread;
    private Handler mProcessHandler;
    /** 子模块加载卸载线程 */
    private HandlerThread mSubModelsManagerThread;
    private Handler mSubModelsManagerHandler;
    /** 表情消息处理线程 */
    private HandlerThread mActionMsgThread;
    private Handler mHandler;
    /** 摄像头原始数据回调线程 */
    private HandlerThread mCameraCallbackThread;
    private Handler mCameraHandler;

    private static volatile STMobileFaceDetectionWrapper mInstance;
    private static volatile STMobileFaceDetectionWrapper mInstancePicture;
    private STMobileHumanActionNative mSTMobileHumanActionNative = null;
    private STMobileFaceAttributeNative mSTMobileFaceAttributeNative = null;
    private final Object mNv21Lock = new Object();

    private final Object mDetectOutputLock = new Object();

    private final Object mHumanActionHandleLock = new Object();
    private final Object mLastUpdateLock = new Object();
    private final Object mCameraInfoLock = new Object();
    private Context mContext;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private STMobileFaceDetectionWrapper.FacePointInfo mLastUpdateFacePointInfo;
    private ConcurrentLinkedQueue<STMobileFaceDetectionWrapper.FacePointInfo> mFacePointInfoQueue = null;
    private YMRCameraInfo mYMRCameraInfo = null;
    private String mFaceAttributeString;
    private STHumanAction mHumanAction;

    //camera数据检测开关
    private AtomicBoolean mCameraDetectOn = new AtomicBoolean(false);

    private boolean mBodyModelLoad = false;
    private ICameraPreviewCallbackListener mPreviewCallbackExternal = null;

    private VenusSegmentWrapper mVenusSegmentWrapper = null;
    private SegmentCacheDetectWrapper mSegmentCacheWrapper = null;
    private Venus.VN_ImageData mVnImageData;
    private boolean mNeedCpuSegment = false;
    private boolean mSegmentInit = false;
    private boolean mIsFirstFrame = false;

    private static class CameraCallbackData {
        byte[] data;
        int format;
        int width;
        int height;
    }

    public static void enableSTMobileCameraMode() {
        mCameraModel.set(true);
        YYLog.info(TAG, "enableSTMobileCameraMode true.");
    }

    public static void enableSTMobilePlayerMode() {
        mCameraModel.set(false);
        YYLog.info(TAG, "enableSTMobilePlayerMode true.");
    }

    @Override
    public void onCameraOpenSuccess(int cameraID) {
        YYLog.info(TAG, "[face] onCameraOpenSuccess cameraID="+cameraID);
    }

    @Override
    public void onCameraOpenFail(int cameraID, String reason) {
        YYLog.info(TAG, "[face] onCameraOpenFail cameraID="+cameraID + " reason :" + reason);
    }

    @Override
    public void onCameraPreviewParameter(int cameraID, YMRCameraInfo cameraInfo) {
        YYLog.info(TAG, "onCameraPreviewParameter cameraID="+cameraID+" cameraInfo="+cameraInfo.toString());
        synchronized (mCameraInfoLock) {
            mYMRCameraInfo = new YMRCameraInfo(cameraInfo);
            mInstance.setupInputSize(mCameraModel.get(), mDefaultW, mDefaultH);
        }
    }

    @Override
    public void onCameraRelease(int cameraID) {
        YYLog.i(TAG, "[face] onCameraRelease cameraID="+cameraID);
        synchronized (mCameraInfoLock) {
            if(mYMRCameraInfo != null  && mYMRCameraInfo.getCameraID() == cameraID) {
                YYLog.info(this, "[face] onCameraRelease, mYMRCameraInfo "+mYMRCameraInfo.toString());
                mYMRCameraInfo = null;
            }
        }
    }

    public static class FacePointInfo {
        public OrangeFilter.OF_FrameData mFrameData;
        public float[][] mFacePoints = null;
        public int       mFaceCount = 0;  //也就是mFacePoint维数组的有多少列.  float[i][j] 中i的有效值的最大值.
        public float[][] mBodyPoints = null;
        public float[][] mBodySocres = null;
        public int       mBodyCount = 0; //也就是mBodyPoints维数组的有多少列.  float[i][j] 中i的有效值的最大值.
    }

    public static STMobileFaceDetectionWrapper getInstance(Context context) {
        if (mInstance == null) {
            synchronized (STMobileFaceDetectionWrapper.class) {
                if (mInstance == null && context != null) {
                    mInstance = new STMobileFaceDetectionWrapper(context, false);
                    mInstance.init();
                }
            }
        }else if (!mInstance.mInited.get()){
            mInstance.init();
        }
        return mInstance;
    }

    public static STMobileFaceDetectionWrapper getPIctureInstance(Context context) {
        if (mInstancePicture == null) {
            synchronized (STMobileFaceDetectionWrapper.class) {
                if (mInstancePicture == null && context != null) {
                    mInstancePicture = new STMobileFaceDetectionWrapper(context, true);
                    mInstancePicture.initPictureMode();
                }
            }
        }else if (!mInstancePicture.mInited.get()){
            mInstancePicture.initPictureMode();
        }
        return mInstancePicture;
    }

    public static void release() {

    }

    public STMobileFaceDetectionWrapper(Context context, boolean mPictureMode) {
        mContext = context.getApplicationContext();
        mLastUpdateFacePointInfo = null;
        mFacePointInfoQueue = new ConcurrentLinkedQueue<>();
        for(int i = 0; i < 5; i++) {
            STMobileFaceDetectionWrapper.FacePointInfo pointInfo = new STMobileFaceDetectionWrapper.FacePointInfo();
            mFacePointInfoQueue.add(pointInfo);
        }

        if (!mPictureMode) {
            YMRCameraMgr.getInstance().addCameraEventListener(this);
        }
    }

    /*处理视频传过来的数据*/
    public boolean onVideoFrame(byte[] data, long pts, int width, int height, boolean isFirstFrame) {
        //YYLog.info(TAG, "jyq test STMobile:onVideoFrame");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return false;
        }

        //reset之前的抠图检测结果
        if (mImageWidth != width || mImageHeight != height) {
            mVnImageData = null;
        }

        if (!mInited.get()) {
            YYLog.error(TAG, "onVideoFrame before STMobile init, just return");
            return false;
        }

        if (null == data) {
            YYLog.error(TAG, "[face] onVideoFrame data=null");
            return false;
        }

        if (data.length != width * height * 4) {
            YYLog.error(TAG, "[face] onVideoFrame data.length error! length: " + data.length);
            return false;
        }

        setupInputSize(false, width, height);

        synchronized (mNv21Lock) {
            if (mImageData == null || mImageData.length != mImageHeight * mImageWidth * 4) {
                mImageData = new byte[mImageWidth * mImageHeight * 4];
            }
            System.arraycopy(data, 0, mImageData, 0, data.length);
        }

        if (mProcessHandler == null || (!mNeedCheckFace && !getEnableBodyDetect() && !mNeedCpuSegment)) {
            return false;
        }
        if (mProcessHandler != null) {
            mProcessHandler.removeMessages(MESSAGE_PROCESS_IMAGE);
        }
        if (mProcessHandler != null) {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            msg.what = MESSAGE_PROCESS_IMAGE;
            bundle.putBoolean("bYUVData", false);
            bundle.putLong("pts", pts);
            msg.setData(bundle);
            mProcessHandler.sendMessage(msg);
        }

        //第一次送player的数据到stMobile检测，需要wait等到检测结果出来再继续渲染
        if (isFirstFrame) {
            synchronized (mDetectOutputLock) {
                try {
                    YYLog.info(TAG, "onVideoFrame wait");
                    mIsFirstFrame = true;
                    mDetectOutputLock.wait();
                } catch (InterruptedException e) {

                }
            }
        }

        return true;
    }

    /*处理视频传过来的数据*/
    synchronized public boolean  onVideoFrameEx(byte[] data, int width, int height, boolean isFirstFrame, boolean bUseDirection) {
        //YYLog.info(TAG, "jyq test STMobile:onVideoFrame");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return false;
        }

        if (!mInited.get()) {
            YYLog.error(TAG, "onVideoFrame before STMobile init, just return");
            return false;
        }

        if (null == data) {
            YYLog.error(TAG, "[face] onVideoFrame data=null");
            return false;
        }

        if (data.length != width * height * 4) {
            YYLog.error(TAG, "[face] onVideoFrame data.length error! length: " + data.length);
            return false;
        }

        setupInputSize(false, width, height);

        synchronized (mNv21Lock) {
            if(mImageData == null || mImageData.length != mImageHeight * mImageWidth * 4){
                mImageData = new byte[mImageWidth * mImageHeight* 4];
            }
            System.arraycopy(data, 0, mImageData, 0, data.length);
        }

        if (mProcessHandler == null || (!mNeedCheckFace && !getEnableBodyDetect())) {
            return false;
        }
        if (mProcessHandler != null) {
            mProcessHandler.removeMessages(MESSAGE_PROCESS_IMAGE);
        }

        if (mProcessHandler != null) {
            Message msg = Message.obtain();
            Bundle bundle = new Bundle();
            msg.what = MESSAGE_PROCESS_IMAGE;
            bundle.putBoolean("bYUVData", false);
            bundle.putBoolean("useDirection", bUseDirection);
            msg.setData(bundle);
            mProcessHandler.sendMessage(msg);
        }

        //第一次送player的数据到stMobile检测，需要wait等到检测结果出来再继续渲染
        if(isFirstFrame) {
            synchronized (mDetectOutputLock) {
                try {
                    mIsFirstFrame = true;
                    mDetectOutputLock.wait();
                } catch (InterruptedException e) {

                }
            }
        }

        return true;
    }


    private void handlePreviewCallbackExternal(byte[] data, Camera camera) {
        if (mPreviewCallbackExternal != null && camera != null) {
            int dataLen;
            CameraCallbackData cbData = new CameraCallbackData();
            Camera.Parameters parameters = camera.getParameters();
            if (parameters != null) {
                int PixelFormat = parameters.getPreviewFormat();
                Camera.Size size = parameters.getPreviewSize();
                cbData.format = PixelFormat;
                if (size != null) {
                    cbData.width = size.width;
                    cbData.height = size.height;
                }
            }

            dataLen = cbData.width * cbData.height * 3/2;   // NV21
            if (data.length != dataLen) {
                YYLog.error(TAG, "[face] handlePreviewCallbackExternal data.length error! length: " + data.length);
                return ;
            }
            synchronized (mNv21Lock) {
                if(mCameraCallbackImageData == null || mCameraCallbackImageData.length != dataLen){
                    mCameraCallbackImageData = new byte[dataLen];
                }
                System.arraycopy(data, 0, mCameraCallbackImageData, 0, data.length);
            }

            cbData.data = mCameraCallbackImageData;
            if (mCameraHandler != null) {
                mCameraHandler.removeMessages(MSG_CAMERA_CALLBACK);
                Message msg = Message.obtain();
                msg.what = MSG_CAMERA_CALLBACK;
                msg.obj = cbData;
                mCameraHandler.sendMessage(msg);
            }
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        // 4.2以下包括4.2直接返回
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return;
        }

        if (mPreviewCallbackExternal != null) {
            handlePreviewCallbackExternal(data, camera);
        }

        if (!mCameraModel.get()) {
            return;
        }

        //当人脸检测模块没有初始化或者没有开启检测时，直接返回
        if (!mInited.get() || !mCameraDetectOn.get()) {
            synchronized (mCameraInfoLock) {
                if (mYMRCameraInfo != null) {
                    YMRCameraMgr.getInstance().addCallbackBuffer(mYMRCameraInfo.getCameraID(), data);
                }
            }
            return;
        }

        if (null == data || null == camera) {
            YYLog.error(TAG, "[face] onPreviewFrame data=null || camera= null");
            return;
        }

        setupInputSize(true, 0, 0);
        if (data.length != mImageWidth * mImageHeight* 3/2) {
            YYLog.error(TAG, "[face] onPreviewFrame data.length error! length: " + data.length);
            return ;
        }

        synchronized (mNv21Lock) {
            if(mImageData == null || mImageData.length != mImageHeight * mImageWidth *3/2){
                mImageData = new byte[mImageWidth * mImageHeight* 3/2];
            }

            if (mImageData != null ) {
                System.arraycopy(data, 0, mImageData, 0, data.length);
            }
        }

        synchronized (mCameraInfoLock) {
            if(mYMRCameraInfo != null) {
                YMRCameraMgr.getInstance().addCallbackBuffer(mYMRCameraInfo.getCameraID(), data);
            }
        }


        if (mProcessHandler != null) {
            if (mNeedCheckFace || getEnableBodyDetect() || mNeedCpuSegment) {
                mProcessHandler.removeMessages(MESSAGE_PROCESS_IMAGE);
                Message msg = Message.obtain();
                Bundle bundle = new Bundle();
                msg.what = MESSAGE_PROCESS_IMAGE;
                bundle.putBoolean("bYUVData", true);
                msg.setData(bundle);
                if (mProcessHandler != null) {
                    mProcessHandler.sendMessage(msg);
                }
            }
        }
    }

    public void init() {
        YYLog.info(TAG,"STMobileWrapper init begin, mDeviceName " + DeviceUtil.getPhoneModel());
        if (mContext != null) {
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            if (null != wm) {
                screenWidth = wm.getDefaultDisplay().getWidth();//屏幕宽度
                screenHeight = wm.getDefaultDisplay().getHeight();
            }
        }
        setupInputSize(mCameraModel.get(), mDefaultW, mDefaultH);
        initHumanAction(false);
        YYLog.info(TAG, "[face] STMobileWrapper init end, mCameraModel " + mCameraModel);
    }

    public void initPictureMode() {
        YYLog.info(TAG,"STMobileWrapper initPictureMode begin, mDeviceName " + DeviceUtil.getPhoneModel());
        if (mContext != null) {
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            if (null != wm) {
                screenWidth = wm.getDefaultDisplay().getWidth();//屏幕宽度
                screenHeight = wm.getDefaultDisplay().getHeight();
            }
        }
        setupInputSize(false, mDefaultW, mDefaultH);
        initHumanAction(true);
        YYLog.info(TAG,"[face] STMobileWrapper initPictureMode end, mCameraModel " + false);
    }

    private void setupInputSize(boolean bCameraModel, int width, int height){
        if (bCameraModel) {
            synchronized (mCameraInfoLock) {
                if (mYMRCameraInfo != null) {
                    width = mYMRCameraInfo.mPreviewWidth;
                    height = mYMRCameraInfo.mPreviewHeight;
                }
            }
        }

        //在input data的宽高发生变化后，需要重新初始化抠图sdk
        if (mNeedCpuSegment && !mSegmentInit && (mImageWidth != 0 && mImageHeight != 0)) {
            if (mVenusSegmentWrapper == null) {
                mVenusSegmentWrapper = new VenusSegmentWrapper(mContext, 0, 0);
            }
            mVenusSegmentWrapper.deInitWithCpu();
            YYLog.info(TAG, "cpu segment init,cameraModel=" + bCameraModel + ",width=" + mImageHeight + ",height=" + mImageWidth);
            mVenusSegmentWrapper.initWithCpu();
            mSegmentInit = true;
        }

        if (mImageWidth == width && mImageHeight == height) {
            return;
        }

        //width, height发生变化，需要重新初始化抠图
        mSegmentInit = false;

        synchronized (mNv21Lock) {
            mImageWidth = width;
            mImageHeight = height;

            SCALE_WIDTH = (int) (mImageWidth / scaleFactor);
            SCALE_HEIGHT = (int) (mImageHeight / scaleFactor);

            YYLog.info(this, "bCameraModel " + bCameraModel + " [face] STMobileWrapper--mImageWidth: " + mImageWidth + " ,mImageHeight: " + mImageHeight);
            if (bCameraModel) {
                mImageData = new byte[mImageWidth * mImageHeight * 3 / 2];
                mTmpBuffer = new byte[mImageWidth * mImageHeight * 3 / 2];
                mScaleBuffer = new byte[mImageWidth * mImageHeight * 3 / 2];
            } else {
                mImageData = new byte[mImageWidth * mImageHeight * 4];
                mTmpBuffer = new byte[mImageWidth * mImageHeight * 4];
                mScaleBuffer = new byte[mImageWidth * mImageHeight * 4];
            }
        }
    }

    public void deInit(){
        YYLog.info(this,Constant.MEDIACODE_PREPRO + "[face] STMobileWrapper deInit....");
        deInitSTMobile();
        if (mVenusSegmentWrapper != null) {
            mVenusSegmentWrapper.deInitWithCpu();
            mVenusSegmentWrapper = null;
        }
        synchronized (mNv21Lock) {
            //delete the buffer
            mImageData = null;
            mTmpBuffer = null;
            mScaleBuffer = null;
            mImageWidth = 0;
            mImageHeight = 0;
        }

        YYLog.info(this,Constant.MEDIACODE_PREPRO + "[face] STMobileWrapper deInit end");
    }

    public void setEnableFaceAttibute(boolean enable){
        mNeedFaceAttribute = enable;
    }

    public void setEnableFaceExpression(boolean enable){
        mNeedFaceExpression = enable;
    }

    public void setEnableFace106(boolean enable){
        if(enable){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_FACE_DETECT;
        }else{
            mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_FACE_DETECT;
        }
    }

    public void setEnableFaceAction(boolean enable){
        if(enable){
            mNeedFaceAction = true;
            mHumanActionDetectConfig |= (STMobileHumanActionNative.ST_MOBILE_EYE_BLINK | STMobileHumanActionNative.ST_MOBILE_BROW_JUMP | STMobileHumanActionNative.ST_MOBILE_HEAD_PITCH
                    | STMobileHumanActionNative.ST_MOBILE_HEAD_YAW | STMobileHumanActionNative.ST_MOBILE_MOUTH_AH);
        }else{
            mNeedFaceAction = false;
            mHumanActionDetectConfig &= ~(STMobileHumanActionNative.ST_MOBILE_EYE_BLINK | STMobileHumanActionNative.ST_MOBILE_BROW_JUMP | STMobileHumanActionNative.ST_MOBILE_HEAD_PITCH
                    | STMobileHumanActionNative.ST_MOBILE_HEAD_YAW | STMobileHumanActionNative.ST_MOBILE_MOUTH_AH);
        }
    }

    public void setEnableFaceExtra(boolean enable){
        if(enable){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS;

            Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
            msg.obj = FileUtils.FACE_EXTRA_MODEL_NAME;
            mSubModelsManagerHandler.sendMessage(msg);
        }else{
            mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS;
        }
    }

    public void setEnableEyeBallCenter(boolean enable){
        if(enable){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_EYEBALL_CENTER;

            Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
            msg.obj = FileUtils.EYEBALL_CONTOUR_MODEL_NAME;
            mSubModelsManagerHandler.sendMessage(msg);
        }else{
            mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_DETECT_EYEBALL_CENTER;
        }
    }

    public void setEnableEyeBallContour(boolean enable){
        if(enable){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_EYEBALL_CONTOUR;

            Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
            msg.obj = FileUtils.EYEBALL_CONTOUR_MODEL_NAME;
            mSubModelsManagerHandler.sendMessage(msg);
        }else{
            mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_DETECT_EYEBALL_CONTOUR;
        }
    }

    public void setEnableSegment(boolean enable){
        if(enable){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_SEG_BACKGROUND;

            Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
            msg.obj = FileUtils.SEGMENT_MODEL_NAME;
            mSubModelsManagerHandler.sendMessage(msg);
        }else{
            mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_SEG_BACKGROUND;
        }
    }

    public void setEnableHandAction(boolean enable){
        if(enable){
            mHumanActionDetectConfig |= mHandActionConfig;

            Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
            msg.obj = FileUtils.HAND_MODEL_NAME;
            mSubModelsManagerHandler.sendMessage(msg);
        }else{
            mHumanActionDetectConfig &= ~mHandActionConfig;
        }
    }

    private long mHandActionConfig = STMobileHumanActionNative.ST_MOBILE_HAND_CONGRATULATE | STMobileHumanActionNative.ST_MOBILE_HAND_FINGER_HEART
            | STMobileHumanActionNative.ST_MOBILE_HAND_GOOD | STMobileHumanActionNative.ST_MOBILE_HAND_FINGER_INDEX
            | STMobileHumanActionNative.ST_MOBILE_HAND_HOLDUP | STMobileHumanActionNative.ST_MOBILE_HAND_LOVE
            | STMobileHumanActionNative.ST_MOBILE_HAND_PALM | STMobileHumanActionNative.ST_MOBILE_HAND_OK
            | STMobileHumanActionNative.ST_MOBILE_HAND_SCISSOR | STMobileHumanActionNative.ST_MOBILE_HAND_PISTOL;

    private void loadBodyDetectModel(){
        Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
        msg.obj = FileUtils.LARGE_BODY_MODEL_NAME;
        mSubModelsManagerHandler.sendMessage(msg);
    }

    public void setEnableBodyDetect(boolean enable) {
        if (enable) {
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS;
            if (!mBodyModelLoad) {
                mBodyModelLoad = true;
                addSubModelByName(FileUtils.LARGE_BODY_MODEL_NAME);
            }
        } else {
            mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS;
            if (mBodyModelLoad) {
                mBodyModelLoad = false;
                removeSubModelByConfig(STMobileHumanActionNative.ST_MOBILE_ENABLE_BODY_KEYPOINTS);
            }
        }
        YYLog.info(TAG, "setEnableBodyDetect :" + enable);
    }

    public boolean getEnableBodyDetect() {
        return (mHumanActionDetectConfig & STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS) > 0;
    }

    private void addSubModel(final String modelName){
        synchronized (mHumanActionHandleLock) {
            if (mSTMobileHumanActionNative != null) {
                int result = mSTMobileHumanActionNative.addSubModelFromAssetFile(modelName, mContext.getAssets());
                YYLog.info(TAG, "add sub model :" + modelName + " result: %d", result);
            }
        }
    }

    private void removeSubModel(final int config){
        synchronized (mHumanActionHandleLock) {
            if (mSTMobileHumanActionNative != null) {
                int result = mSTMobileHumanActionNative.removeSubModelByConfig(config);
                YYLog.info(TAG, "remove sub model :" + config + " result: %d", result);
            }

            if(config == STMobileHumanActionNative.ST_MOBILE_ENABLE_BODY_KEYPOINTS){
                mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS;
            }else if(config == STMobileHumanActionNative.ST_MOBILE_ENABLE_FACE_EXTRA_DETECT){
                mHumanActionDetectConfig &= ~STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS;
            }
        }
    }

    private void addSubModelByName(String modelName){
        Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_ADD_SUB_MODEL);
        msg.obj = modelName;

        mSubModelsManagerHandler.sendMessage(msg);

        if(modelName.equals(FileUtils.LARGE_BODY_MODEL_NAME)){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_BODY_KEYPOINTS;
        }else if(modelName.equals(FileUtils.FACE_EXTRA_MODEL_NAME)){
            mHumanActionDetectConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_EXTRA_FACE_POINTS;
        }
    }

    private void removeSubModelByConfig(int Config){
        Message msg = mSubModelsManagerHandler.obtainMessage(MESSAGE_REMOVE_SUB_MODEL);
        msg.obj = Config;
        mSubModelsManagerHandler.sendMessage(msg);
    }

    /**
     * 初始化human action
     */
    private void initHumanAction(boolean bPictureMode){

        synchronized (STMobileFaceDetectionWrapper.class) {
            if (!mInited.get()) {
                //默认开启脸部表情检测
                setEnableFaceAction(true);

                YYLog.info(TAG, Constant.MEDIACODE_PREPRO + "[face] STMobileWrapper initSTMobile begin");
                boolean isLicenseValid = STLicenseUtils.checkLicense(mContext);
                if (!isLicenseValid) {
                    YYLog.error(TAG, "checkLicense failed.");
                    return ;
                }

                if (mSTMobileHumanActionNative == null) {
                    mSTMobileHumanActionNative = new STMobileHumanActionNative();
                }
                if (mSTMobileFaceAttributeNative == null) {
                    mSTMobileFaceAttributeNative = new STMobileFaceAttributeNative();
                }

                /*内部默认是多线程 */
                int mHumanActionCreateConfig = STCommonNative.ST_MOBILE_TRACKING_ENABLE_DEBOUNCE
                        | STMobileHumanActionNative.ST_MOBILE_ENABLE_FACE_DETECT;
                String assetModePath;
                if (!bPictureMode) {
                    mHumanActionCreateConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_MODE_VIDEO;
                    assetModePath = FileUtils.FACE_TRACK_MODEL_NAME;
                } else {
                    mHumanActionCreateConfig |= STMobileHumanActionNative.ST_MOBILE_DETECT_MODE_IMAGE;
                    assetModePath = FileUtils.FACE_DETECT_MODEL_NAME;
                }

                /* 创建HumanAction实例 */
                int result = mSTMobileHumanActionNative.createInstanceFromAssetFile(assetModePath, mHumanActionCreateConfig, mContext.getAssets());
                YYLog.info(TAG, "create human action handle result: %d", result);
                if (result == 0) {
                    mIsCreateHumanActionHandleSucceeded = true;
                    mSTMobileHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_BACKGROUND_RESULT_ROTATE, 1.0f);
                    mSTMobileHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_BODY_LIMIT, 3.0f);
                    mSTMobileHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_FACELIMIT, mMaxFace);
                } else {
                    YYLog.error(TAG, "createInstanceFromAssetFile failed ret :" + result);
                    return;
                }

                /* 人脸和肢体识别线程 */
                mHandlerThread = new HandlerThread("ProcessImageThread");
                mHandlerThread.start();
                mProcessHandler = new Handler(mHandlerThread.getLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        if (!mIsPaused && mIsCreateHumanActionHandleSucceeded) {
                            switch (msg.what) {
                                case MESSAGE_PROCESS_IMAGE:
                                    boolean bYUVData = msg.getData().getBoolean("bYUVData");
                                    if (bYUVData) {
                                        processCameraData();
                                    } else {
                                        boolean bUseDirection = msg.getData().getBoolean("useDirection");
                                        long pts = msg.getData().getLong("pts");
                                        processPlayerData(pts, bUseDirection);
                                    }
                                    break;
                                case MESSAGE_SETPREVIEWCALLBACK:
                                    handlePreviewCallbackWithBuffer();
                                    break;
                                case MESSAGE_INIT_FACEDATE:
                                    resetFacePointInfo();
                                    break;
                                case MESSAGE_SET_FACE_LIMIT:
                                    int num = (int) msg.obj;
                                    doSetFaceLimit(num);
                                    break;
                                case MESSAGE_SET_BODY_LIMIT:
                                    int bodyNum = (int) msg.obj;
                                    doSetBodyLimit(bodyNum);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                };

                /* 子模块加载卸载线程 */
                mSubModelsManagerThread = new HandlerThread("SubModelManagerThread");
                mSubModelsManagerThread.start();
                mSubModelsManagerHandler = new Handler(mSubModelsManagerThread.getLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        if(!mIsPaused && !mCameraChanging && mIsCreateHumanActionHandleSucceeded){
                            switch (msg.what){
                                case MESSAGE_ADD_SUB_MODEL:
                                    String modelName = (String) msg.obj;
                                    if(modelName != null){
                                        addSubModel(modelName);
                                    }
                                    break;

                                case MESSAGE_REMOVE_SUB_MODEL:
                                    int config = (int) msg.obj;
                                    if(config != 0){
                                        removeSubModel(config);
                                    }
                                    break;

                                default:
                                    break;
                            }
                        }
                    }
                };

                mActionMsgThread = new HandlerThread("ActionMsgThread");
                mActionMsgThread.start();
                mHandler = new Handler(mActionMsgThread.getLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        if(!mIsPaused && !mCameraChanging && mIsCreateHumanActionHandleSucceeded) {
                            switch (msg.what) {
                                case MSG_UPDATE_HAND_ACTION_INFO:
                                    YYLog.info(TAG, "MSG_UPDATE_HAND_ACTION_INFO .");
                                    break;
                                case MSG_RESET_HAND_ACTION_INFO:
                                    YYLog.info(TAG, "MSG_RESET_HAND_ACTION_INFO .");
                                    break;
                                case MSG_UPDATE_FACE_ACTION_INFO:
                                    YYLog.info(TAG, "MSG_UPDATE_FACE_ACTION_INFO .");
                                    break;
                                case MSG_UPDATE_FACE_EXPRESSION_INFO:
                                    YYLog.info(TAG, "MSG_UPDATE_FACE_EXPRESSION_INFO .");
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                };

                if (SDKCommonCfg.getRecordModePicture()) {
                    mCameraCallbackThread = new HandlerThread("CameraCallbackThread");
                    mCameraCallbackThread.start();
                    mCameraHandler = new Handler(mCameraCallbackThread.getLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            switch (msg.what) {
                                case MSG_CAMERA_CALLBACK:
                                    CameraCallbackData data = (CameraCallbackData) msg.obj;
                                    if (mPreviewCallbackExternal != null && data != null) {
                                        mPreviewCallbackExternal.onPreviewFrame(data.data, data.format, data.width, data.height);
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    };
                }
//                loadBodyDetectModel();

                mInited.set(true);
            }
        }
    }

    private STHumanAction processHumanActionResult(STHumanAction humanAction, boolean isFrontCamera, int cameraOrientation, boolean rotateBackground){
        if(humanAction == null){
            return null;
        }
        if(isFrontCamera && cameraOrientation == 90){
            humanAction = STHumanAction.humanActionRotate(mImageHeight, mImageWidth, STRotateType.ST_CLOCKWISE_ROTATE_90, rotateBackground, humanAction);
            humanAction = STHumanAction.humanActionMirror(mImageWidth, humanAction);
        }else if(isFrontCamera && cameraOrientation == 270){
            humanAction = STHumanAction.humanActionRotate(mImageHeight, mImageWidth, STRotateType.ST_CLOCKWISE_ROTATE_270, rotateBackground, humanAction);
            humanAction = STHumanAction.humanActionMirror(mImageWidth, humanAction);
        }else if(!isFrontCamera && cameraOrientation == 270){
            humanAction = STHumanAction.humanActionRotate(mImageHeight, mImageWidth, STRotateType.ST_CLOCKWISE_ROTATE_270, rotateBackground, humanAction);
        }else if(!isFrontCamera && cameraOrientation == 90){
            humanAction = STHumanAction.humanActionRotate(mImageHeight, mImageWidth, STRotateType.ST_CLOCKWISE_ROTATE_90, rotateBackground, humanAction);
        }

        return humanAction;
    }

    private void faceAttributeDetect(byte[] data, STHumanAction humanAction){

        if (humanAction != null && data != null && data.length > 0) {
            STMobile106[] arrayFaces = null;
            arrayFaces = humanAction.getMobileFaces();

            if (arrayFaces != null && arrayFaces.length != 0) {
                if (mNeedFaceAttribute) { // face attribute
                    STFaceAttribute[] arrayFaceAttribute = new STFaceAttribute[arrayFaces.length];
                    long attributeCostTime = System.currentTimeMillis();
                    int result = mSTMobileFaceAttributeNative.detect(data, STCommonNative.ST_PIX_FMT_NV21, mImageHeight, mImageWidth, arrayFaces, arrayFaceAttribute);
                    YYLog.info(TAG, "face attribute cost time: %d", System.currentTimeMillis() - attributeCostTime);
                    if (result == 0) {
                        if (arrayFaceAttribute[0].attribute_count > 0) {
                            mFaceAttributeString = genFaceAttributeString(arrayFaceAttribute[0]);
                        } else {
                            mFaceAttributeString = null;
                        }
                    }
                } else {
                    mFaceAttributeString = null;
                }
            } else {
                mFaceAttributeString = null;
            }
        }
    }

    private String genFaceAttributeString(STFaceAttribute arrayFaceAttribute){
        String attribute = null;
        String attractive = null;
        String gender = "男";
        String age = "";

        for(int i = 0; i < arrayFaceAttribute.arrayAttribute.length; i++){
            if(arrayFaceAttribute.arrayAttribute[i].category.equals("attractive")){
                attractive = arrayFaceAttribute.arrayAttribute[i].label;
            }

            if(arrayFaceAttribute.arrayAttribute[i].category.equals("gender")){
                gender = arrayFaceAttribute.arrayAttribute[i].label;
                if(gender.equals("male")){
                    gender = "男";
                }else{
                    gender = "女";
                }
            }

            if(arrayFaceAttribute.arrayAttribute[i].category.equals("age")){
                age = arrayFaceAttribute.arrayAttribute[i].label;
            }
        }

        attribute = "颜值:" + attractive + "  " + "性别:" + gender + "  " + "年龄:"+ age;
        return attribute;
    }

    private void processPlayerData(long pts, boolean bUseDirection) {
        if (!mIsCreateHumanActionHandleSucceeded || !mInited.get()) {
            return;
        }
        synchronized (mNv21Lock) {
            if (mTmpBuffer == null || mTmpBuffer.length != mImageHeight * mImageWidth * 4) {
                mTmpBuffer = new byte[mImageWidth * mImageHeight * 4];
            }

            if (mImageData == null || mCameraChanging || mTmpBuffer.length != mImageData.length) {
                return;
            }

            System.arraycopy(mImageData, 0, mTmpBuffer, 0, mImageData.length);
        }

        if (mSTMobileHumanActionNative == null) {
            YYLog.error(TAG, "processPlayerData while mSTMobileHumanActionNative is null, just return");
            return;
        }

        //获取重力传感器返回的方向
        int dir = 0;
        if (bUseDirection ) {
            dir = Accelerometer.getDirection();
            if (dir == 1) {         // 竖直方向
                dir = STRotateType.ST_CLOCKWISE_ROTATE_0;
            } else if (dir == 0) {  // 向左边渲染 90 度
                dir = STRotateType.ST_CLOCKWISE_ROTATE_270;
            } else if (dir == 2) {  // 向右边旋转 90 度
                dir = STRotateType.ST_CLOCKWISE_ROTATE_90;
            } else if (dir == 3) {  // 竖直方向上下颠倒 180 度
                dir = STRotateType.ST_CLOCKWISE_ROTATE_180;
            }
        }

        if (mNeedCheckFace || getEnableBodyDetect()) {
            doBodyAndFaceDetect(false, dir);
        }

        /*
        编辑页异步抠图基本不能用，注释掉编辑也页异步抠图的逻辑
        if (mNeedCpuSegment) {
            doSegment(false, pts);
        }*/

        synchronized (mDetectOutputLock) {
            if (mIsFirstFrame) {
                YYLog.info(TAG, "onVideoFrame notify");
                mIsFirstFrame = false;
                mDetectOutputLock.notify();
            }
        }
    }

    private void processCameraData() {
        synchronized (mNv21Lock) {
            if (mTmpBuffer == null || mTmpBuffer.length != mImageHeight * mImageWidth * 3 / 2) {
                mTmpBuffer = new byte[mImageWidth * mImageHeight * 3 / 2];
            }
            if (mImageData == null || mCameraChanging || mTmpBuffer.length != mImageData.length) {
                return;
            }
            System.arraycopy(mImageData, 0, mTmpBuffer, 0, mImageData.length);
        }

        if (mNeedCheckFace || getEnableBodyDetect()) {
            doBodyAndFaceDetect(true, 0);
        }
        if (mNeedCpuSegment) {
            doSegment(true, 0);
        }
    }

    private void doBodyAndFaceDetect(boolean isCameraData, int dirParam) {
        STHumanAction humanAction;

        if (mSTMobileHumanActionNative == null) {
            YYLog.error(TAG, "processCameraData while mSTMobileHumanActionNative is null, just return");
            return;
        }
        //处理camera数据
        if (isCameraData) {
            //如果使用前置摄像头，请注意显示的图像与帧图像左右对称，需处理坐标
            boolean frontCamera = (mYMRCameraInfo.getCameraID() == Camera.CameraInfo.CAMERA_FACING_FRONT);

            //获取重力传感器返回的方向
            int dir = Accelerometer.getDirection();

            //在使用后置摄像头，且传感器方向为0或2时，后置摄像头与前置orentation相反
            if (!frontCamera && dir == 0) {
                dir = 2;
            } else if (!frontCamera && dir == 2) {
                dir = 0;
            }

            // 请注意前置摄像头与后置摄像头旋转定义不同
            // 请注意不同手机摄像头旋转定义不同
            if (((mYMRCameraInfo.mCameraInfoOrientation == 270 && (dir & 1) == 1) ||
                    (mYMRCameraInfo.mCameraInfoOrientation == 90 && (dir & 1) == 0)))
                dir = (dir ^ 2);

            if (scaleFactor != 1.0f) {
                OrangeFilterApi.nv12DownSample(mTmpBuffer, mScaleBuffer, mImageWidth, mImageHeight, SCALE_WIDTH, SCALE_HEIGHT);
            }

            if (scaleFactor != 1.0f) {
                humanAction = mSTMobileHumanActionNative.humanActionDetect(mScaleBuffer, STCommonNative.ST_PIX_FMT_NV21,
                        mHumanActionDetectConfig, dir, SCALE_HEIGHT, SCALE_WIDTH);
            } else {
                humanAction = mSTMobileHumanActionNative.humanActionDetect(mTmpBuffer, STCommonNative.ST_PIX_FMT_NV21,
                        mHumanActionDetectConfig, dir, mImageHeight, mImageWidth);
            }
            if (mCameraChanging || mIsPaused) {
                return;
            }

            // 人脸表情
            if (mNeedFaceExpression) {
                long expressionStartTime = System.currentTimeMillis();
                mSTMobileHumanActionNative.getExpression(humanAction, dir, mYMRCameraInfo.getCameraID() == Camera.CameraInfo.CAMERA_FACING_FRONT);
                YYLog.info(TAG, "face expression cost time: %d", System.currentTimeMillis() - expressionStartTime);
                Message msg = mHandler.obtainMessage(MSG_UPDATE_FACE_EXPRESSION_INFO);
                mHandler.sendMessage(msg);
            }

            //人脸属性
            if (mNeedFaceAttribute) {
                mFaceAttributeFrameCount++;
                if (mFaceAttributeFrameCount >= 20) {
                    faceAttributeDetect(mTmpBuffer, humanAction);
                    mFaceAttributeFrameCount = 0;
                }
            }

            //humanAction rotate && mirror
            humanAction = processHumanActionResult(humanAction, mYMRCameraInfo.getCameraID() == Camera.CameraInfo.CAMERA_FACING_FRONT,
                    mYMRCameraInfo.mCameraInfoOrientation, true);
            if (mCameraChanging || mIsPaused) {
                return;
            }

        } else {
            //处理video数据
            humanAction = mSTMobileHumanActionNative.humanActionDetect(mTmpBuffer, STCommonNative.ST_PIX_FMT_RGBA8888,
                    mHumanActionDetectConfig, dirParam, mImageWidth, mImageHeight);
        }

        if (humanAction != null) {
            addCurrentHumanActionInfoPoint(humanAction);
            mHumanAction = humanAction;
        } else {
            mHumanAction = null;
        }
    }

    //抠图处理
    private void doSegment(boolean isCameraData, long pts) {
        if (mSegmentInit) {
            //处理camera数据
            if (isCameraData) {
                int inputFormat = VenusSegmentWrapper.SEGMENT_INPUT_YUV_FRONT;
                if (mYMRCameraInfo.getCameraID() == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    inputFormat = VenusSegmentWrapper.SEGMENT_INPUT_YUV_BACK;
                }
                mVnImageData = mVenusSegmentWrapper.updateSegmentDataWithCacheCpu(mTmpBuffer, mImageHeight, mImageWidth,
                        inputFormat, null, null);
            } else {
                //处理player数据
                SegmentCacheDetectWrapper.SegmentCacheDetectRes res = mSegmentCacheWrapper.findSegmentCacheInsertPos(pts);
                if (res.isFound) {
                    SegmentCacheDetectWrapper.SegmentCacheData inCacheData = mSegmentCacheWrapper.segmentCacheDataList.get(res.pos);
//                    YYLog.info(TAG, "jyq test segment with cache:" + pts);
                    Venus.VN_SegmentCacheData cache = new Venus.VN_SegmentCacheData();
                    cache.bytes = inCacheData.bytes;
                    cache.timestamp = inCacheData.timestamp;
                    cache.bytes = inCacheData.bytes;
                    mVnImageData = mVenusSegmentWrapper.updateSegmentDataWithCacheCpu(mTmpBuffer, mImageWidth, mImageHeight,
                            VenusSegmentWrapper.SEGMENT_INPUT_RGBA, cache, null);
                } else {
//                    YYLog.info(TAG, "jyq test segment without cache:" + pts);
                    Venus.VN_SegmentCacheData outCacheData = new Venus.VN_SegmentCacheData();
                    mVnImageData = mVenusSegmentWrapper.updateSegmentDataWithCacheCpu(mTmpBuffer, mImageWidth, mImageHeight,
                            VenusSegmentWrapper.SEGMENT_INPUT_RGBA, null, outCacheData);
                    outCacheData.timestamp = pts;

                    SegmentCacheDetectWrapper.SegmentCacheData cache = new SegmentCacheDetectWrapper.SegmentCacheData();
                    cache.bytes = outCacheData.bytes;
                    cache.timestamp = outCacheData.timestamp;
                    cache.bytes = outCacheData.bytes;
                    mSegmentCacheWrapper.segmentCacheSave(cache, res.pos);
                }
            }
        }
    }


    public void drawHumanActionResults(int width, int height, Canvas canvas, Paint mPaint){
        if (mCameraChanging) {
            return;
        }
        STHumanAction humanAction = mHumanAction;
        if (humanAction == null) {
            return;
        }

        //YYLog.info(TAG, "faceCount : " + humanAction.faceCount + " bodyCount : " + humanAction.bodyCount);
        if(humanAction != null && humanAction.faceCount > 0){

//            mFaceInfo = humanAction.getFaceInfos()[0];
            if(mNeedFaceAction){
                Message msg = mHandler.obtainMessage(MSG_UPDATE_FACE_ACTION_INFO);
                mHandler.sendMessage(msg);
            }

            for (STMobileFaceInfo face : humanAction.faces) {

                //获取人脸106关键点信息
                STPoint[] face106Points = face.getFace().getPointsArray();
                PointF[] rotatedFace106Points = new PointF[face106Points.length];
                for (int i = 0; i < face106Points.length; i++) {
                    rotatedFace106Points[i] = new PointF(face106Points[i].getX(), face106Points[i].getY());
                }
                //获取人脸106关键点信息
                float[] visibles = face.getFace().getVisibilityArray();

                Rect faceRect = face.getFace().getRect().convertToRect();

                if(!mCameraChanging){
                    STUtils.drawFaceKeyPoints(canvas, mPaint, rotatedFace106Points, visibles, width, height, Color.parseColor("#00ee00"));
                    STUtils.drawFaceRect(canvas, faceRect, width, height);
                }

                if(mNeedFaceAttribute && mFaceAttributeString != null && face.face106.getID() == humanAction.faces[0].face106.getID()){
                    int textSize = faceRect.width()/12;
                    if(textSize < 20){
                        textSize = 20;
                    }else if(textSize > 60){
                        textSize = 60;
                    }
                    mPaint.setTextSize(textSize);
                    mPaint.setColor(Color.parseColor("#cd2626"));
                    mPaint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(mFaceAttributeString, faceRect.left, (faceRect.top - faceRect.height()/40), mPaint);
                }

                //extra face info
                if(face.extraFacePointsCount > 0){
                    STPoint[] extraFacePoints = face.getExtraFacePoints();
                    PointF[] rotatedExtraFacePoints = new PointF[extraFacePoints.length];
                    for (int i = 0; i < extraFacePoints.length; i++) {
                        rotatedExtraFacePoints[i] = new PointF(extraFacePoints[i].getX(), extraFacePoints[i].getY());
                    }

                    if(!mCameraChanging){
                        STUtils.drawFaceKeyPoints(canvas, mPaint, rotatedExtraFacePoints, null, width, height, Color.parseColor("#0a8dff"));
                    }
                }

                //eyeball center
                if(face.eyeballCenterPointsCount == 2){
                    STPoint[] eyeballCenterPoints = face.getEyeballCenter();
                    PointF[] leftEyeballCenterPoints = new PointF[1];
                    PointF[] rightEyeballCenterPoints = new PointF[1];
                    leftEyeballCenterPoints[0] = new PointF(eyeballCenterPoints[0].getX(), eyeballCenterPoints[0].getY());
                    rightEyeballCenterPoints[0] = new PointF(eyeballCenterPoints[1].getX(), eyeballCenterPoints[1].getY());

                    float value = 0.4f;
                    if(!mCameraChanging){
                        if(face.leftEyeballScore >= value){
                            STUtils.drawFaceKeyPoints(canvas, mPaint, leftEyeballCenterPoints, null, width, height, Color.parseColor("#ff00f6"));
                        }

                        if(face.rightEyeballScore >= value){
                            STUtils.drawFaceKeyPoints(canvas, mPaint, rightEyeballCenterPoints, null, width, height, Color.parseColor("#ff00f6"));
                        }
                    }
                }

                //eyeball contour
                if(face.eyeballContourPointsCount == 38){
                    STPoint[] eyeballContourPoints = face.getEyeballContour();
                    PointF[] leftEyeballContourPoints = new PointF[eyeballContourPoints.length /2];
                    PointF[] rightEyeballContourPoints = new PointF[eyeballContourPoints.length /2];

                    for (int i = 0; i < eyeballContourPoints.length /2; i++) {
                        leftEyeballContourPoints[i] = new PointF(eyeballContourPoints[i].getX(), eyeballContourPoints[i].getY());
                    }

                    for (int i = eyeballContourPoints.length /2; i < face.eyeballContourPointsCount; i++) {
                        rightEyeballContourPoints[i - eyeballContourPoints.length /2] = new PointF(eyeballContourPoints[i].getX(), eyeballContourPoints[i].getY());
                    }

                    float value = 0.4f;
                    if(!mCameraChanging){
                        if(face.leftEyeballScore >= value){
                            STUtils.drawFaceKeyPoints(canvas, mPaint, leftEyeballContourPoints, null, width, height, Color.parseColor("#ffe763"));
                        }

                        if(face.rightEyeballScore >= value){
                            STUtils.drawFaceKeyPoints(canvas, mPaint, rightEyeballContourPoints, null, width, height, Color.parseColor("#ffe763"));
                        }
                    }
                }
            }
        }

        // 未用商汤手势识别，暂时屏蔽
        //hand action
//        if(humanAction != null && humanAction.handCount > 0){
//            for(STMobileHandInfo hand : humanAction.hands){
//                Rect handRect = hand.handRect.convertToRect();

//                if(!mCameraChanging){
//                    STUtils.drawHandRect(canvas, handRect, width, height);
//
//                    mPaint.setStyle(Paint.Style.STROKE);//设置为空心
//                    int strokeWidth = 5;
//                    if(canvas.getWidth() > 1080){
//                        strokeWidth = (int)(strokeWidth * canvas.getWidth()/1080);
//                    }
//                    mPaint.setStrokeWidth(strokeWidth);
//                    mPaint.setColor(Color.parseColor("#0a8dff"));
//
//                    int gap = 10;
//                    if(canvas.getWidth() != 1080){
//                        gap = (int)(gap * canvas.getWidth()/1080);
//                    }
//                    Path leftTopCorner = new Path();
//                    leftTopCorner.moveTo(handRect.left - gap, handRect.top - gap + gap * 4);
//                    leftTopCorner.lineTo(handRect.left - gap, handRect.top - gap);
//                    leftTopCorner.lineTo(handRect.left - gap  + gap * 4, handRect.top - gap);
//                    canvas.drawPath(leftTopCorner, mPaint);
//
//                    Path rightTopCorner = new Path();
//                    rightTopCorner.moveTo(handRect.right + gap, handRect.top - gap + gap * 4);
//                    rightTopCorner.lineTo(handRect.right + gap, handRect.top - gap);
//                    rightTopCorner.lineTo(handRect.right + gap  - gap * 4, handRect.top - gap);
//                    canvas.drawPath(rightTopCorner, mPaint);
//
//                    Path leftBottomCorner = new Path();
//                    leftBottomCorner.moveTo(handRect.left - gap, handRect.bottom + gap - gap * 4);
//                    leftBottomCorner.lineTo(handRect.left - gap, handRect.bottom + gap);
//                    leftBottomCorner.lineTo(handRect.left - gap  + gap * 4, handRect.bottom + gap);
//                    canvas.drawPath(leftBottomCorner, mPaint);
//
//                    Path rightBottomCorner = new Path();
//                    rightBottomCorner.moveTo(handRect.right + gap, handRect.bottom + gap - gap * 4);
//                    rightBottomCorner.lineTo(handRect.right + gap, handRect.bottom + gap);
//                    rightBottomCorner.lineTo(handRect.right + gap  - gap * 4, handRect.bottom + gap);
//                    canvas.drawPath(rightBottomCorner, mPaint);
//                }

//                mHandAction = hand.handAction;
//                Message msg = mHandler.obtainMessage(MSG_UPDATE_HAND_ACTION_INFO);
//                mHandler.sendMessage(msg);
//            }
//        }else {
//            Message msg = mHandler.obtainMessage(MSG_RESET_HAND_ACTION_INFO);
//            mHandler.sendMessage(msg);
//        }


        if(humanAction != null && humanAction.bodyCount > 0){
            for (STMobileBodyInfo body : humanAction.bodys) {

                //获取肢体关键点信息
                STPoint[] bodyKeyPoints = body.getKeyPoints();
                PointF[] rotatedBodyKeyPoints = new PointF[bodyKeyPoints.length];
                for (int i = 0; i < bodyKeyPoints.length; i++) {
                    rotatedBodyKeyPoints[i] = new PointF(bodyKeyPoints[i].getX(), bodyKeyPoints[i].getY());

                }
                //获取肢体关键点遮挡信息
                float[] visibles = body.getKeyPointsScore();

                if(!mCameraChanging){
                    STUtils.drawPointsAndLines(canvas, mPaint, rotatedBodyKeyPoints, visibles, width, height);
                }
            }
        }

    }

    private void addCurrentHumanActionInfoPoint(STHumanAction humanAction) {
        try{
            if (mCameraChanging || humanAction == null) {
                return;
            }
            //YYLog.info(TAG, "faceCount : " + humanAction.faceCount + " bodyCount : " + humanAction.bodyCount);
            FacePointInfo pointInfo = mFacePointInfoQueue.poll();
            if (pointInfo == null) {
                YYLog.error(this, Constant.MEDIACODE_PREPRO + "[face] not point info in queue!!!");
                return;
            }

            if (humanAction.faceCount > 0) {
                if (humanAction.faceCount != pointInfo.mFaceCount || pointInfo.mFrameData == null) {
                    pointInfo.mFrameData = new OrangeFilter.OF_FrameData();
                }
                if (humanAction.faceCount != pointInfo.mFaceCount || pointInfo.mFacePoints == null) {
                    pointInfo.mFacePoints = new float[humanAction.faceCount][212];
                }
                if (humanAction.faceCount != pointInfo.mFaceCount || pointInfo.mFrameData.faceFrameDataArr == null) {
                    pointInfo.mFrameData.faceFrameDataArr = new OrangeFilter.OF_FaceFrameData[humanAction.faceCount];
                    for (int i = 0; i < humanAction.faceCount; i++) {
                        pointInfo.mFrameData.faceFrameDataArr[i] = new OrangeFilter.OF_FaceFrameData();
                    }
                }

                pointInfo.mFaceCount = humanAction.faceCount;
                float[][] mTempFacePoints = pointInfo.mFacePoints;
                for (int i = 0; i < humanAction.faceCount; i++) {
                    STPoint[] points = humanAction.faces[i].getFace().getPointsArray();
                    for (int j = 0; j < 106; j++) {
                        mTempFacePoints[i][2 * j] = points[j].getX() / SCALE_WIDTH;
                        mTempFacePoints[i][2 * j + 1] = points[j].getY() / SCALE_HEIGHT;
                    }
                    pointInfo.mFrameData.faceFrameDataArr[i].facePoints = pointInfo.mFacePoints[i];
                    pointInfo.mFrameData.faceFrameDataArr[i].isBrowJump = (humanAction.faces[i].getFaceAction() & STMobileHumanActionNative.ST_MOBILE_BROW_JUMP) > 0;
                    pointInfo.mFrameData.faceFrameDataArr[i].isEyeBlink = (humanAction.faces[i].getFaceAction() & STMobileHumanActionNative.ST_MOBILE_EYE_BLINK) > 0;
                    pointInfo.mFrameData.faceFrameDataArr[i].isMouthOpen = (humanAction.faces[i].getFaceAction() & STMobileHumanActionNative.ST_MOBILE_MOUTH_AH) > 0;
                    pointInfo.mFrameData.faceFrameDataArr[i].isHeadYaw = (humanAction.faces[i].getFaceAction() & STMobileHumanActionNative.ST_MOBILE_HEAD_YAW) > 0;
                    pointInfo.mFrameData.faceFrameDataArr[i].isHeadPitch = (humanAction.faces[i].getFaceAction() & STMobileHumanActionNative.ST_MOBILE_HEAD_PITCH) > 0;
                }
            } else {
                pointInfo.mFaceCount = 0;
                if (pointInfo.mFrameData != null) {
                    pointInfo.mFrameData.faceFrameDataArr = null;
                }
            }

            // 拷贝肢体关键信息
            if (humanAction.bodyCount > 0) {
                if (humanAction.bodyCount != pointInfo.mBodyCount || pointInfo.mFrameData == null) {
                    pointInfo.mFrameData = new OrangeFilter.OF_FrameData();
                }
                if (humanAction.bodyCount != pointInfo.mBodyCount || pointInfo.mBodyPoints == null) {
                    pointInfo.mBodyPoints = new float[humanAction.bodyCount][14*2];
                }
                if (humanAction.bodyCount != pointInfo.mBodyCount || pointInfo.mBodySocres == null) {
                    pointInfo.mBodySocres = new float[humanAction.bodyCount][14];
                }

                if (humanAction.bodyCount != pointInfo.mBodyCount || pointInfo.mFrameData.bodyFrameDataArr == null)
                {
                    pointInfo.mFrameData.bodyFrameDataArr = new OrangeFilter.OF_BodyFrameData[humanAction.bodyCount];
                    for (int i = 0; i < humanAction.bodyCount; i++) {
                        pointInfo.mFrameData.bodyFrameDataArr[i] = new OrangeFilter.OF_BodyFrameData();
                    }
                }

                pointInfo.mBodyCount = humanAction.bodyCount;
                float[][] mTempBodyPoints = pointInfo.mBodyPoints;
                float[][] mTempBodyScores = pointInfo.mBodySocres;
                for (int i = 0; i < humanAction.bodyCount; i++) {
                    //获取肢体关键点信息
                    STPoint[] points = humanAction.bodys[i].getKeyPoints();
                    for (int j = 0; j < 14; j++) {
                        mTempBodyPoints[i][2 * j] = points[j].getX() / SCALE_WIDTH;
                        mTempBodyPoints[i][2 * j + 1] = points[j].getY() / SCALE_HEIGHT;
                    }
                    pointInfo.mFrameData.bodyFrameDataArr[i].bodyPoints = pointInfo.mBodyPoints[i];

                    //获取肢体关键点遮挡信息
                    float[] scores = humanAction.bodys[i].getKeyPointsScore();
                    for (int j = 0; j < 14; j++) {
                        mTempBodyScores[i][j] = scores[j] ;
                    }
                    pointInfo.mFrameData.bodyFrameDataArr[i].bodyPointsScore = pointInfo.mBodySocres[i];

                }
            } else {
                pointInfo.mBodyCount = 0;
                if (pointInfo.mFrameData != null) {
                    pointInfo.mFrameData.bodyFrameDataArr = null;
                }
            }

            FacePointInfo origPoint = null;
            synchronized (mLastUpdateLock) {
                origPoint = mLastUpdateFacePointInfo;
                mLastUpdateFacePointInfo = pointInfo;
            }

            if (origPoint != null) {
                mFacePointInfoQueue.add(origPoint);
            }
        } catch (Exception e) {
            e.printStackTrace();
            YYLog.error(this, "[face] exception:"+e.toString());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void deInitSTMobile() {
        synchronized (STMobileFaceDetectionWrapper.class) {
            if (mInited.get()) {
                YYLog.info(this, Constant.MEDIACODE_PREPRO + "[face] STMobileWrapper deInitSTMobile begin");
                if(mProcessHandler != null) {
                    mProcessHandler.removeCallbacksAndMessages(null);
                    mHandlerThread.quitSafely();
                    try {
                        mHandlerThread.join();
                    } catch (InterruptedException e) {
                        YYLog.error(this, Constant.MEDIACODE_PREPRO + "[face][exception] STMobileWrapper deInitSTMobile: "+e.toString());
                        e.printStackTrace();
                    }
                    mProcessHandler = null;
                    mHandlerThread = null;
                }

                if (mSubModelsManagerHandler != null) {
                    mSubModelsManagerHandler.removeCallbacksAndMessages(null);
                    mSubModelsManagerThread.quitSafely();
                    try {
                        mSubModelsManagerThread.join();
                    } catch (InterruptedException e) {
                        YYLog.error(this, Constant.MEDIACODE_PREPRO + "deInitSTMobile quit mDetectSceneThread error: " + e.toString());
                    }
                    mSubModelsManagerHandler = null;
                    mSubModelsManagerThread = null;
                }

                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                    mActionMsgThread.quitSafely();
                    try {
                        mActionMsgThread.join();
                    } catch (InterruptedException e) {
                        YYLog.error(this, Constant.MEDIACODE_PREPRO + "deInitSTMobile quit mDetectSceneThread error: " + e.toString());
                    }
                    mHandler = null;
                    mActionMsgThread = null;
                }

                if (mCameraHandler != null) {
                    mCameraHandler.removeCallbacksAndMessages(null);
                    mCameraCallbackThread.quitSafely();
                    try {
                        mCameraCallbackThread.join();
                    } catch (InterruptedException e) {
                        YYLog.error(this, Constant.MEDIACODE_PREPRO + "deInitSTMobile quit mCameraCallbackThread error: " + e.toString());
                    }
                    mCameraHandler = null;
                    mCameraCallbackThread = null;
                }

                synchronized (mHumanActionHandleLock) {
                    if (mSTMobileHumanActionNative != null) {
                        mSTMobileHumanActionNative.destroyInstance();
                        mSTMobileHumanActionNative = null;
                    }
                }

                YYLog.info(this, Constant.MEDIACODE_PREPRO + "[face] STMobileWrapper deInitSTMobile end");
            }
            mInited.set(false);
        }
    }



    private void handlePreviewCallbackWithBuffer() {
        YYLog.info(this,Constant.MEDIACODE_PREPRO + "[face] --handlePreviewCallbackWithBuffer");

        int cameraID = -1;
        synchronized (mCameraInfoLock) {
            if(mYMRCameraInfo != null) {
                cameraID = mYMRCameraInfo.getCameraID();
            }
        }

        if(cameraID != -1) {
            YMRCameraMgr.getInstance().setPreviewCallbackWithBuffer(cameraID, this);
            YYLog.info(this,Constant.MEDIACODE_PREPRO + "[face] handleSetPreviewCallback success, cameraId="+cameraID);
        } else {
            YYLog.info(this,Constant.MEDIACODE_PREPRO + "[face] handleSetPreviewCallback fail, camera is not open!!!");
        }
    }

    public void setPreviewCallbackWithBuffer() {
        if(mProcessHandler != null) {
            mProcessHandler.sendEmptyMessage(MESSAGE_SETPREVIEWCALLBACK);
            YYLog.info(this, "[face] setPreviewCallbackWithBuffer");
        } else {
            YYLog.error(this, "[face] setPreviewCallbackWithBuffer fail, mFaceDetectionHandler is null");
        }
    }

    public void setExternalPreviewCallback(ICameraPreviewCallbackListener listener) {
        YYLog.info(TAG, "setExternalPreviewCallbackListener " + listener);
        mPreviewCallbackExternal = listener;
    }

    public STMobileFaceDetectionWrapper.FacePointInfo getCurrentFacePointInfo() {
        if (!mInited.get() && mLastUpdateFacePointInfo != null) {
            mLastUpdateFacePointInfo.mFaceCount = 0;
            mLastUpdateFacePointInfo.mFacePoints = null;
        }

        STMobileFaceDetectionWrapper.FacePointInfo pointInfo = null;
        synchronized (mLastUpdateLock) {
            pointInfo = mLastUpdateFacePointInfo;
            mLastUpdateFacePointInfo = null;
        }

        return pointInfo;
    }

    public void releaseFacePointInfo(STMobileFaceDetectionWrapper.FacePointInfo facePoint) {
        STMobileFaceDetectionWrapper.FacePointInfo tmpPointInfo = null;
        synchronized (mLastUpdateLock) {
            if (mLastUpdateFacePointInfo == null) {
                mLastUpdateFacePointInfo = facePoint;
            } else {
                tmpPointInfo = facePoint;
            }
        }
        if (tmpPointInfo != null) {
            mFacePointInfoQueue.add(tmpPointInfo);
        }
    }

    public boolean isStMobileInitiated() {
        return mInited.get();
    }

    public void setThinFaceFlag(float value) {
        mNeedCheckFace = (value != 0);
    }

    public void setStickerFlag(String stickerFilePath) {
        mNeedCheckFace = (stickerFilePath != null);
    }

    public void setDetectOn(boolean isDetectOn) {
        mCameraDetectOn.set(isDetectOn);
    }

    public void resetFacePointInfo() {
        synchronized (mLastUpdateLock) {
            mImageData = null;
            mTmpBuffer = null;
            mScaleBuffer = null;
            if (mProcessHandler != null) {
                mProcessHandler.removeMessages(MESSAGE_PROCESS_IMAGE);
            }
            STMobileFaceDetectionWrapper.FacePointInfo tmpPointInfo = mLastUpdateFacePointInfo;
            if (tmpPointInfo != null) {
                mFacePointInfoQueue.add(tmpPointInfo);
            }
            mLastUpdateFacePointInfo = null;
            if (mSTMobileHumanActionNative != null) {
                mSTMobileHumanActionNative.reset();
            }
        }
    }

    public void initFaceDate() {
        YYLog.info(TAG, "initFaceDate");
        if (mProcessHandler == null) {
            return;
        }
        mProcessHandler.removeMessages(MESSAGE_PROCESS_IMAGE);
        mProcessHandler.sendEmptyMessage(MESSAGE_INIT_FACEDATE);
    }

    public void setIsCheckFace(boolean needCheckFace) {
        mNeedCheckFace = needCheckFace;
    }

    public boolean getIsCheckFace() {
        return mNeedCheckFace;
    }

    public void setFaceLimit(int num) {
        if (mProcessHandler != null) {
            mProcessHandler.removeMessages(MESSAGE_SET_FACE_LIMIT);
            Message msg = Message.obtain();
            msg.what = MESSAGE_SET_FACE_LIMIT;
            msg.obj = num;
            mProcessHandler.sendMessage(msg);
        } else {
            YYLog.info(TAG, "setFaceLimit mProcessHandler is null num=" + num);
        }
    }

    public void setBodyLimit(int num) {
        if (mProcessHandler != null) {
            mProcessHandler.removeMessages(MESSAGE_SET_BODY_LIMIT);
            Message msg = Message.obtain();
            msg.what = MESSAGE_SET_BODY_LIMIT;
            msg.obj = num;
            mProcessHandler.sendMessage(msg);
        } else {
            YYLog.info(TAG, "setBodyLimit mProcessHandler is null num=" + num);
        }
    }

    private void doSetBodyLimit(int num) {
        if (mSTMobileHumanActionNative != null) {
            mSTMobileHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_BODY_LIMIT, num);
            YYLog.info(TAG, "doSetBodyLimit num=" + num);
        }
    }

    public void resetFaceLimit() {
        if (mProcessHandler != null) {
            mProcessHandler.removeMessages(MESSAGE_SET_FACE_LIMIT);
            Message msg = Message.obtain();
            msg.what = MESSAGE_SET_FACE_LIMIT;
            msg.obj = mMaxFace;
            mProcessHandler.sendMessage(msg);
        } else {
            YYLog.info(TAG, "resetFaceLimit mFaceDetectionHandler is null");
        }
    }

    private void doSetFaceLimit(int num) {
        if (mSTMobileHumanActionNative != null) {
            mSTMobileHumanActionNative.setParam(STHumanActionParamsType.ST_HUMAN_ACTION_PARAM_FACELIMIT, num);
            YYLog.info(TAG, "doSetFaceLimit num=" + num);
        }
    }

    //获取摄像头相关信息
    public YMRCameraInfo getCameraInfo() {
        return mYMRCameraInfo;
    }

    //获取抠图返回的结果
    public Venus.VN_ImageData getVnImageData() {
        return mVnImageData;
    }

    //开启cpu抠图开关
    public void setNeedCpuSegment(boolean needCpuSegment) {
        mNeedCpuSegment = needCpuSegment;
    }
}
