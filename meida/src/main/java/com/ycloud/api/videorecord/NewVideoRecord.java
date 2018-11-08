package com.ycloud.api.videorecord;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.ycloud.Version;
import com.ycloud.VideoProcessTracer;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.config.ResolutionType;
import com.ycloud.api.config.TakePictureConfig;
import com.ycloud.api.config.TakePictureParam;
import com.ycloud.camera.utils.ICameraEventListener;
import com.ycloud.datamanager.AudioDataManager;
import com.ycloud.datamanager.MediaDataExtractor;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gpuimagefilter.filter.RecordFilterSessionWrapper;
import com.ycloud.mediarecord.IBlurBitmapCallback;
import com.ycloud.mediarecord.NewVideoRecordSession;
import com.ycloud.mediarecord.VideoRecordException;
import com.ycloud.statistics.HiidoStatistics;
import com.ycloud.statistics.IHiidoStatisticsSettings;
import com.ycloud.utils.Timestamp;
import com.ycloud.utils.YYLog;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * 视频录制
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NewVideoRecord implements IVideoRecord, IVideoPreviewListener {
    private String TAG = NewVideoRecord.class.getSimpleName();
    private NewVideoRecordSession mVideoRecord;

    private final  static int MSG_RECORD_START = 1;
    private final static int MSG_RECORD_STOP = 2;
    private final static int MSG_SWITCH_CAMERA = 3;
    private final static int MSG_RECORD_RELEASE = 4;
    private final static int MSG_ON_PAUSE = 5;
    private final static int MSG_ON_RESUME = 6;
    private final static int MSG_ON_START_PREVIEW = 7;
    private final static int MSG_RECORD_PAUSE = 8;

    private Object mReleaseSyncLock;
    private AtomicBoolean mIsRelease = new AtomicBoolean(false);

    private IVideoPreviewListener mVideoPreviewListener;

    /*涉及到camera的open，release操作在这个线程*/
    private Handler mCameraHandler;
    private Handler.Callback mCameraCallBack = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            int what = msg.what;
            switch (what) {
                case MSG_SWITCH_CAMERA:
                    if (mCameraHandler != null) {
                        mCameraHandler.removeMessages(MSG_SWITCH_CAMERA);
                    }
                    if (mVideoRecord != null) {
                        mVideoRecord.switchCamera();
                    }
                    break;
                case MSG_ON_PAUSE:
                    handleOnPause();
                    break;
                case MSG_ON_RESUME:
                    try {
                        handleOnResume();
                    } catch (VideoRecordException e) {
                        YYLog.error(TAG, "VideoRecordException " + e.toString());
                    }
                    break;
                case MSG_ON_START_PREVIEW:
                    IVideoPreviewListener previewListener = (IVideoPreviewListener) msg.obj;
                    try {
                        handleOnStartPreview(previewListener);
                    } catch (VideoRecordException e) {
                        YYLog.error(TAG, "VideoRecordException " + e.toString());
                    }
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    /*涉及到录制的start，stop操作在这个线程*/
    private Handler mVideoRecordHandler;
    private Handler.Callback mRecordCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RECORD_START:
                    if (mVideoRecordHandler != null) {
                        mVideoRecordHandler.removeMessages(MSG_RECORD_START);
                    }
                    boolean isSnapshot = (boolean)msg.obj;
                    try {
                        HiidoStatistics.startRecordTime = Timestamp.getCurTimeInMillSencods();
                        mVideoRecord.startRecord(isSnapshot);
                    } catch (VideoRecordException e) {
                        e.printStackTrace();
                    }
                    break;
                case MSG_RECORD_STOP:
                    if (mVideoRecordHandler != null) {
                        mVideoRecordHandler.removeMessages(MSG_RECORD_STOP);
                    }
                    if (mVideoRecord != null) {
                        mVideoRecord.stopRecord();
                    }
                    break;
                case MSG_RECORD_PAUSE:
                    if (mVideoRecordHandler != null) {
                        mVideoRecordHandler.removeMessages(MSG_RECORD_PAUSE);
                    }
                    if (mVideoRecord != null) {
                        mVideoRecord.pauseRecord();
                    }
                    break;
                case MSG_RECORD_RELEASE:
                    if (mVideoRecordHandler != null) {
                        mVideoRecordHandler.removeMessages(MSG_RECORD_RELEASE);
                        mVideoRecordHandler.getLooper().quitSafely();
                    }
                    if (mVideoRecord != null) {
                        mVideoRecord.release();
                        synchronized (mReleaseSyncLock) {
                            if (mReleaseSyncLock != null) {
                                mReleaseSyncLock.notify();
                                mReleaseSyncLock = null;
                            }
                        }
                        YYLog.info(TAG, " VideoRecordPresentor release handler thread safely!");
                    }
                    break;
            }
            return false;
        }
    };

    /**
     * 视频录制对象
     * @param context             activity context
     * @param videoSurfaceView    视频录制画面预览的view
     * @param resolutionType      录制使用的编码分辨率
     */
    public NewVideoRecord(Context context, VideoSurfaceView videoSurfaceView,ResolutionType resolutionType) {

        VideoProcessTracer.getInstace().reset();

        YYLog.info(TAG, "VideoRecord begin, SDK version : " + Version.getVersion());

        HandlerThread cameraThread = new HandlerThread(SDK_NAME_PREFIX +"camera");
        cameraThread.start();
        mCameraHandler = new Handler(cameraThread.getLooper(), mCameraCallBack);

        HandlerThread recordThread = new HandlerThread(SDK_NAME_PREFIX + "record");
        recordThread.start();
        mVideoRecordHandler = new Handler(recordThread.getLooper(), mRecordCallback);

        mIsRelease.set(false);

        if (HiidoStatistics.Enable()) {
            /*创建*/
            HiidoStatistics.create(context, null, null);
            /*模块*/
            HiidoStatistics.saveModuleType(IHiidoStatisticsSettings.MODULE_TYPE.RECORD);
        }

        mVideoRecord = new NewVideoRecordSession(context, videoSurfaceView,resolutionType);
    }
    @Override
    public void setOutputPath(String outputPath) {
        mVideoRecord.setOutputPath(outputPath);
    }

    /* 设置进度回调*/
    @Override
    public void setRecordListener(IVideoRecordListener listener) {
        YYLog.info(TAG, " setRecordListener!!!");
        mVideoRecord.setRecordListener(listener);
    }

    @Override
    public void setAudioRecordListener(IAudioRecordListener listener) {
        YYLog.info(TAG, "setAudioRecordListener!!!");
        mVideoRecord.setAudioRecordListener(listener);
    }

    @Override
    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        YYLog.info(TAG, "setMediaInfoRequireListener!!!");
        mVideoRecord.setMediaInfoRequireListener(listener);
    }

    @Override
    public void startRecord(boolean isSnapShot) {
        YYLog.info(TAG, " startRecord!!! isSnapShot:" + isSnapShot);
        if (mVideoRecordHandler != null) {
            Message msg = Message.obtain();
            msg.what = MSG_RECORD_START;
            msg.obj = isSnapShot;
            mVideoRecordHandler.sendMessageDelayed(msg, 100);
        }
    }

    @Override
    public void setRecordSpeed(float recordSpeed) {
        mVideoRecord.setRecordSpeed(recordSpeed);
    }

    @Override
    public void setOfDeviceLevel(int deviceLevel) {
        mVideoRecord.setOfDeviceLevel(deviceLevel);
    }

    @Override
    public void stopRecord() {
        YYLog.info(TAG, " stopRecord!!!");
        if (mVideoRecordHandler != null) {
            mVideoRecordHandler.sendEmptyMessageDelayed(MSG_RECORD_STOP, 120);
        }
    }

    @Override
    public void setCameraID(int Id){
        mVideoRecord.setCameraID(Id);
    }

    @Override
    public void setFlashMode(String flashMode) {
        mVideoRecord.setFlashMode(flashMode);
    }

    @Override
    public void onPause() {
        YYLog.info(TAG, " VideoRecord onPause!");
        mCameraHandler.sendEmptyMessage(MSG_ON_PAUSE);
    }

    private void handleOnPause() {
        mVideoRecord.onPause();
    }

    @Override
    public void onResume() throws VideoRecordException {
        YYLog.info(TAG, " VideoRecord onResume!");
        mCameraHandler.sendEmptyMessage(MSG_ON_RESUME);
    }


    public void startPreview(IVideoPreviewListener listener) throws VideoRecordException {
        YYLog.info(TAG, " VideoRecord startPreview!");

        Message msg = Message.obtain();
        msg.what = MSG_ON_START_PREVIEW;
        msg.obj = listener;
        mCameraHandler.sendMessage(msg);
    }

    private void handleOnResume() throws VideoRecordException {
        mVideoRecord.onResume();
    }

    public void onStart() {
        if (mVideoPreviewListener != null) {
            mVideoPreviewListener.onStart();
        }

        //Delay init STMobileFace
        mVideoRecordHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mVideoRecord != null) {
                    mVideoRecord.delayInitSTMobile();
                }
            }
        });
    }

    private void handleOnStartPreview(IVideoPreviewListener listener) throws VideoRecordException  {
        if (listener != null) {
            mVideoRecord.setPreviewListener(this);
        }else {
            mVideoRecord.setPreviewListener(null);
        }
        mVideoPreviewListener = listener;
        mVideoRecord.onResume();
    }

    public void setOnInfoErrorListener(MediaRecordErrorListener info_error) {
        if (mVideoRecord != null) {
            mVideoRecord.setErrorListener(info_error);
            //TODO. remove this.
        }
    }

    /*不录制音频*/
    @Override
    public void setEnableAudioRecord(boolean enable_audio) {
        mVideoRecord.setEnableAudioRecord(enable_audio);
        HiidoStatistics.saveMuteSetting((enable_audio ? 0 : 1));
    }

    @Override
    public void switchCamera() {
        mCameraHandler.sendEmptyMessageDelayed(MSG_SWITCH_CAMERA, 100);
    }

    @Override

    public Camera.Parameters getCameraParameters() {
        return mVideoRecord.getCameraParameters();
    }

    @Override
    public boolean setCameraParameters(Camera.Parameters parameters) {
        return mVideoRecord.setCameraParameters(parameters);
    }

    @Override
    public Camera.CameraInfo getCameraInfo() {
        return mVideoRecord.getCameraInfo();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void release() {
        YYLog.info(TAG, " VideoRecord release begin!");
        if(mCameraHandler != null) {
            mCameraHandler.getLooper().quitSafely();
        }

        if (mVideoRecordHandler != null && !mIsRelease.get()) {
            mIsRelease.set(true);
            mReleaseSyncLock = new Object();
            synchronized (mReleaseSyncLock) {
                mVideoRecordHandler.sendEmptyMessage(MSG_RECORD_RELEASE);
                try {
                    mReleaseSyncLock.wait();
                    YYLog.info(TAG, " VideoRecord release end!");
                } catch (InterruptedException e) {
                    YYLog.error(TAG, "release wait is interrupt!");
                }
            }
        }
    }

    @Override
    public void setVideoSize(int width, int height) {
        mVideoRecord.setVideoSize(width,height);
    }
    @Override
    public boolean isRecordEnabeled(){
        return mVideoRecord.isRecordEnabeled();
    }

    @Override
    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        YYLog.info(TAG, " setFaceDetectionListener");
        mVideoRecord.setFaceDetectionListener(listener);
    }

    @Override
    public void setRecordSnapShot(String snapShotPath, String fileNamePrefix,float snapFrequency) {
        mVideoRecord.setRecordSnapShot(snapShotPath,fileNamePrefix,snapFrequency);
    }

    @Override
    public void setYyVersion(String yyVersion) {
        mVideoRecord.setYyVersion(yyVersion);
    }

    @Override
    public void setEncodeType(int encodeType) {
        mVideoRecord.setEncodeType(encodeType);
    }



    @Override
    public void setBlurBitmapCallBack(IBlurBitmapCallback blurBitmapCallBack) {
        mVideoRecord.setBlurBitmapCallBack(blurBitmapCallBack);

    }

    @Override
    public int getZoom() {
       return mVideoRecord.getZoom();
    }

    @Override
    public int getMaxZoom() {
        return mVideoRecord.getMaxZoom();
    }

    @Override
    public void setZoom(int factor) {
        mVideoRecord.setZoom(factor);
    }

    @Override
    public void focusAndMetering(float x, float y, boolean autoCancel) {
        mVideoRecord.focusAndMetering(x, y, autoCancel);
    }

    @Override
    public RecordFilterSessionWrapper getRecordFilterSessionWrapper() {
        return mVideoRecord.getRecordFilterSessionWrapper();
    }

    @Override
    public void getCameraBitmap(IBlurBitmapCallback blurBitmapCallback) {
        mVideoRecord.getCameraBitmap(blurBitmapCallback);
    }

    @Override
    public void deleteLastRecordSnapshot() {
        if (mVideoRecord != null) {
            mVideoRecord.deleteLastRecordSnapshot();
        }

        VideoProcessTracer.getInstace().deleteLastRecordTracer();
    }

    public void removeMemMediaSegment(int mediaSegmentIndex) {
        YYLog.info(TAG, "removeMemMediaSegment index=" + mediaSegmentIndex);
        VideoDataManager.instance().removeSegmentByIndex(mediaSegmentIndex);
        AudioDataManager.instance().removeSegmentByIndex(mediaSegmentIndex);
    }

    public void resetMemMediaData() {
        YYLog.info(TAG, "resetMemMediaData");
        VideoDataManager.instance().reset();
        AudioDataManager.instance().reset();
    }

    /**
     * 导入开始前，先调resetMemMediaData清理一下内存中数据，如果导入多段MP4文件，只需要在最开始的时候清理一次。
     * @param MP4Path
     */
    public void importMediaDataToMemory(String MP4Path) {
        MediaDataExtractor extractor = new MediaDataExtractor();

        if (extractor.init(MP4Path) == 0 ) {
            VideoDataManager.instance().startRecord();
            extractor.ExtractorMediaData(MediaDataExtractor.MediaDataType.MEDIA_DATA_TYPE_VIDEO);
            VideoDataManager.instance().stopRecord();

            AudioDataManager.instance().startRecord();
            extractor.ExtractorMediaData(MediaDataExtractor.MediaDataType.MEDIA_DATA_TYPE_AUDIO);
            AudioDataManager.instance().stopRecord();
        }
        extractor.deInit();
    }

    public void pauseRecord() {
        YYLog.info(this, "[tracer] pauseRecord!!!");
        if (mVideoRecordHandler != null) {
            mVideoRecordHandler.sendEmptyMessage(MSG_RECORD_PAUSE);
        }
    }

    public int setBackgroundMusic(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long delayMS) {
        if (mVideoRecord != null) {
            return mVideoRecord.setBackgroundMusic(path, beginReadPositionMS, endReadPositionMS, loop, delayMS);
        }
        return -1;
    }

    public int addAudioFileToPlay(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long delayMS, boolean forceStart) {
        if (mVideoRecord != null) {
            return mVideoRecord.addAudioFileToPlay(path, beginReadPositionMS, endReadPositionMS, loop, delayMS, forceStart);
        }
        return -1;
    }

    public void removeAudioFile(int ID) {
        if (mVideoRecord != null) {
            mVideoRecord.removeAudioFile(ID);
        }
    }

    public void removeAllAudioFile() {
        if (mVideoRecord != null) {
            mVideoRecord.removeAllAudioFile();
        }
    }

    public void seek(int positionMS) {
        if (mVideoRecord != null) {
            mVideoRecord.seek(positionMS);
        }
    }

    public long getAudioPlayPositionInMS() {
        if (mVideoRecord != null) {
            return mVideoRecord.getAudioPlayPositionInMS();
        }
        return 0;
    }

    public void setAudioPlaySpeed(float speed) {
        if (mVideoRecord != null) {
            mVideoRecord.setAudioPlaySpeed(speed);
        }
    }

    public void enableAudioFrequencyCalculate(boolean enable) {
        if (mVideoRecord != null) {
            mVideoRecord.enableAudioFrequencyCalculate(enable);
        }
    }

    public int audioFrequencyData(float[] buffer, int len) {
        if (mVideoRecord != null) {
            return mVideoRecord.audioFrequencyData(buffer, len);
        }
        return  0;
    }
	
	
	/* for YOYI */
    @Override
    public void setTakePictureConfig(TakePictureConfig config) {
        if (config == null) {
            YYLog.error(TAG, " setTakePictureConfig error! config == null.");
            return;
        }
        if (mVideoRecord != null) {
            mVideoRecord.setTakePictureConfig(config);
        }
    }

    @Override
    public void recoveryPreview() {
        if (mVideoRecord != null) {
            mVideoRecord.recoverPreview();
        }
    }

    @Override
    public void takePicture(TakePictureParam param) {
        if (param == null) {
            YYLog.error(TAG, " takePicture error! param == null.");
            return;
        }
        if (param.mQuality < 1 || param.mQuality > 100) {
            param.mQuality = 100;
        }
        if (mVideoRecord != null) {

            mVideoRecord.takePicture(param);

//            final TakePictureParam p = param;
//            mVideoRecord.autoFocus(new Camera.AutoFocusCallback() {
//                @Override
//                public void onAutoFocus(boolean success, Camera camera) {
//                    mVideoRecord.takePicture(p);
//                }
//            });
        } else {
            YYLog.error(TAG, "takePicture error ! mVideoRecord == null.");
        }
    }

    @Override
    public void setAspectRatioListener(IChangeAspectRatioListener listener) {
        if (mVideoRecord != null) {
            mVideoRecord.setAspectRatioListener(listener);
        }
    }

    @Override
    public void setAspectWithDynamicEffect(boolean bEffect) {
        if (mVideoRecord != null) {
            mVideoRecord.setAspectWithDynamicEffect(bEffect);
        }
    }

    @Override
    public void setAspectRatio(AspectRatioType aspectRatio, int x_offset, int y_offset) {
        if (mVideoRecord != null) {
            mVideoRecord.setAspectRatio(aspectRatio, x_offset, y_offset);
        }
    }

    @Override
    public Rect getCurrentVideoRect() {
        if (mVideoRecord != null) {
            return mVideoRecord.getCurrentVideoRect();
        }
        return null;
    }

    @Override
    public void SyncFinalPreviewRect(int surfaceWidth, int surfaceHeight) {
        if (mVideoRecord != null) {
            mVideoRecord.SyncFinalPreviewRect(surfaceWidth, surfaceHeight);
        }
    }

    @Override
    public Rect getFinalPreviewRectByAspect(AspectRatioType aspectRatio) {
        if (mVideoRecord != null) {
            return mVideoRecord.getFinalPreviewRectByAspect(aspectRatio);
        }
        return null;
    }

    @Override
    public void setPreviewSnapshotListener(IPreviewSnapshotListener listener) {
        if (mVideoRecord != null) {
            mVideoRecord.setPreviewSnapshotListener(listener);
        }
    }

    @Override
    public void takePreviewSnapshot(String path, int width, int height, int type, int quality, boolean bFlipX) {
        if (mVideoRecord != null) {
            mVideoRecord.takePreviewSnapshot(path, width, height, type, quality, bFlipX);
        }
    }

    @Override
    public void setOriginalPreviewSnapshotListener(IOriginalPreviewSnapshotListener listener) {
        if (mVideoRecord != null) {
            mVideoRecord.setOriginalPreviewSnapshotListener(listener);
        }
    }

    @Override
    public void takeOriginalPreviewSnapshot(String path, int width, int height, int type, int quality, boolean bFlipX) {
        if (mVideoRecord != null) {
            mVideoRecord.takeOriginalPreviewSnapshot(path, width, height, type, quality, bFlipX);
        }
    }

    @Override
    public void setPreviewRectOffset(int x_offset, int y_offset) {
        if (mVideoRecord != null) {
            mVideoRecord.setPreviewRectOffset(x_offset, y_offset);
        }
    }

    @Override
    public void setPreviewFlipX() {
        if (mVideoRecord != null) {
            mVideoRecord.setPreviewFlipX();
        }
    }

    @Override
    public void setCameraEventListener(ICameraEventListener listener) {
        if (mVideoRecord != null) {
            mVideoRecord.setCameraEventListener(listener);
        }
    }

    @Override
    public void setPreviewCallbackListener(ICameraPreviewCallbackListener listener) {
        if (mVideoRecord != null) {
            mVideoRecord.setPreviewCallbackListener(listener);
        }
    }
}
