package com.ycloud.mediarecord;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.config.RecordContants;
import com.ycloud.api.config.RecordDynamicParam;
import com.ycloud.api.config.ResolutionType;
import com.ycloud.api.config.TakePictureConfig;
import com.ycloud.api.config.TakePictureParam;
import com.ycloud.api.videorecord.IAudioRecordListener;
import com.ycloud.api.videorecord.ICameraPreviewCallbackListener;
import com.ycloud.api.videorecord.IChangeAspectRatioListener;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.api.videorecord.IOriginalPreviewSnapshotListener;
import com.ycloud.api.videorecord.IPreviewSnapshotListener;
import com.ycloud.api.videorecord.IVideoPreviewListener;
import com.ycloud.api.videorecord.IVideoRecordListener;
import com.ycloud.api.videorecord.MediaRecordErrorListener;
import com.ycloud.api.videorecord.VideoSurfaceView;
import com.ycloud.audio.AudioPlayEditor;
import com.ycloud.camera.utils.ICameraEventListener;
import com.ycloud.camera.utils.YMRCameraInfo;
import com.ycloud.camera.utils.YMRCameraMgr;
import com.ycloud.common.Constant;
import com.ycloud.common.FileUtils;
import com.ycloud.common.GlobalConfig;
import com.ycloud.common.OFLoader;
import com.ycloud.datamanager.AudioDataManager;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.gles.EglFactory;
import com.ycloud.gpuimagefilter.filter.FilterCenter;
import com.ycloud.gpuimagefilter.filter.RecordFilterGroup;
import com.ycloud.gpuimagefilter.filter.RecordFilterSessionWrapper;
import com.ycloud.mediacodec.MeidacodecConfig;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.mediacodec.videocodec.HardSurfaceEncoder;
import com.ycloud.mediafilters.AVSyncFilter;
import com.ycloud.mediafilters.AudioCaptureFilter;
import com.ycloud.mediafilters.AudioDataManagerFilter;
import com.ycloud.mediafilters.AudioEncoderFilter;
import com.ycloud.mediafilters.AudioFilterContext;
import com.ycloud.mediafilters.AudioProcessFilter;
import com.ycloud.mediafilters.AudioSpeedFilter;
import com.ycloud.mediafilters.CameraCaptureFilter;
import com.ycloud.mediafilters.ClipFilter;
import com.ycloud.mediafilters.IMediaSession;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediafilters.MediaFormatAdapterFilter;
import com.ycloud.mediafilters.MediaMuxerFilter;
import com.ycloud.mediafilters.PreviewFilter;
import com.ycloud.mediafilters.SnapshotFilter;
import com.ycloud.mediafilters.VideoDataManagerFilter;
import com.ycloud.mediafilters.VideoEncoderGroupFilter;
import com.ycloud.mediafilters.VideoEndPointFilter;
import com.ycloud.mediaprocess.OFColorTableFilterUtil;
import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.mediarecord.mediacodec.MediaCodecTester;
import com.ycloud.statistics.UploadStatManager;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.AbstractSurfaceInfo;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class NewVideoRecordSession implements ICameraEventListener, SurfaceHolder.Callback, IMediaSession {
    private String TAG = NewVideoRecordSession.class.getSimpleName();
    static {
        try {
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("audioengine");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            YYLog.error("NewVideoRecordSession", "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
        }
    }
    protected Context mContext = null;
    private VideoSurfaceView mSurfaceView = null;
    // video
    private int mSurfaceRotation = 0;
    private boolean mExposureCompensation = false;
    private String mCacheDir = null;

    protected RecordConfig mRecordConfig;
    protected OrientationEventListener mOrientationEventListener;
    protected YMRCameraInfo mYMRCameraInfo = null;
    protected AtomicLong mCurrentCameraLinkID = new AtomicLong(-1);
    private FocusAndMeteringDeal mFocusAndMeteringDeal;

    //private FilterSession mFilterSession = null;
    private RecordFilterSessionWrapper mRecordFilterSessionWrapper = null;

    CameraCaptureFilter mCameraCaptureFilter = null;
    private RecordFilterGroup mRecordFilterGroup = null;
    ClipFilter mClipFilter = null;
    VideoEncoderGroupFilter mVideoEncodeFilter = null;
    PreviewFilter mPreviewFilter = null;
    VideoEndPointFilter mVideoEndPointFilter = null;
    MediaFilterContext mVideoFilterContext = null;
    AudioFilterContext mAudioFilterContext = null;

    MediaMuxerFilter mMediaMuxerFilter = null;
    AudioCaptureFilter mAudioCaptureFilter = null;
    AudioEncoderFilter mAudioEncoderFilter = null;
    AudioProcessFilter mAudioProcessFilter = null;
    AudioSpeedFilter mAudioSpeedFilter = null;

    MediaFormatAdapterFilter mMediaFormatAdapterFilter = null;

    VideoDataManagerFilter mVideoDataManagerFilter = null;
    AudioDataManagerFilter mAudioDataManagerFilter = null;

    SnapshotFilter mSnapshotFilter = null;
    IBlurBitmapCallback mBlurBitmapCallback;
    IVideoRecordListener mRecordListener;
    MediaRecordErrorListener mErrorListener;

    private AtomicBoolean mIsRecord = new AtomicBoolean(false);

    private AtomicBoolean mRelease = new AtomicBoolean(false);

    //释放MediaMuxFilter的标识符，只有gl线程和audio线程都推出后才能释放
    private AtomicBoolean mReleaseMuxFilter = new AtomicBoolean(false);

    private EventHandler mEventHandler;
    private Object mRecordLock = new Object();

    private boolean mStoreDataInMemory = false;

    private Object mAudioPlayEditorLock = new Object();
    private AudioPlayEditor mAudioPlayEditor;
    private volatile int mBackgroundMusicID = -1;
    private Handler mAudioPlayEditorNotifyHandler;
    private float mAudioPlaySpeed = 1.0f;
    private volatile long mAudioPlayLenMS = 0;
    private AVSyncFilter mAVSyncFilter;
    private boolean mEnableAudioFrequencyCalculate;
    private ICameraEventListener mCameraEventListener = null;
    private boolean mEnableVideoRecord = true;

    public NewVideoRecordSession(Context context, VideoSurfaceView surfaceView, ResolutionType resolutionType) {
        //初始化全局参数
        GlobalConfig.getInstance().init(resolutionType);
        //根据服务端下发参数动态调整全局参数
        RecordDynamicParam.getInstance().applyParamToGlobalConf();

        mEventHandler = new EventHandler();
        FilterCenter.getInstance();

        //初始化本次record session的config参数
        mRecordConfig = new RecordConfig();

        mContext = context;
        mSurfaceView = surfaceView;

        mEnableVideoRecord = !SDKCommonCfg.getRecordModePicture();

        mVideoFilterContext = new MediaFilterContext(context);
        if (mEnableVideoRecord) {
            mAudioFilterContext = new AudioFilterContext();
        }

        mVideoFilterContext.setRecordConfig(mRecordConfig);
        if (mEnableVideoRecord) {
            mAudioFilterContext.setRecordConfig(mRecordConfig);
        }

        mCameraCaptureFilter = new CameraCaptureFilter(mVideoFilterContext);
        mRecordFilterSessionWrapper = new RecordFilterSessionWrapper(mRecordConfig, mVideoFilterContext);
        mRecordFilterGroup = new RecordFilterGroup(mRecordFilterSessionWrapper.getSessionID(), mVideoFilterContext.getGLManager().getLooper());
        mRecordFilterGroup.setMediaFilterContext(mVideoFilterContext);
        mRecordFilterGroup.startListen();

        mRecordFilterSessionWrapper.setRecordFilterGroup(mRecordFilterGroup);


        mClipFilter = new ClipFilter();
//        if (mEnableVideoRecord) {
        mVideoEncodeFilter = new VideoEncoderGroupFilter(mVideoFilterContext, true);
//        }

        mPreviewFilter = new PreviewFilter(mVideoFilterContext);

        if (mEnableVideoRecord) {
            mVideoEndPointFilter = new VideoEndPointFilter(mVideoFilterContext);
            mMediaMuxerFilter = new MediaMuxerFilter(mVideoFilterContext, true);
            mMediaMuxerFilter.setVideoAudioSync(true);
            mMediaMuxerFilter.setSingleStreamOfEndMode(true);

            mMediaFormatAdapterFilter = new MediaFormatAdapterFilter(mVideoFilterContext);
            mMediaFormatAdapterFilter.setNAL3ValidNAL4(true);

            mStoreDataInMemory = GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1 ? true : false;
            if (mStoreDataInMemory) {
                mVideoDataManagerFilter = new VideoDataManagerFilter(mVideoFilterContext);
                mAudioDataManagerFilter = new AudioDataManagerFilter(mVideoFilterContext);
            }

            mSnapshotFilter = new SnapshotFilter(mRecordConfig);
        }

        mVideoFilterContext.getGLManager().registerFilter(mCameraCaptureFilter);
        mVideoFilterContext.getGLManager().registerFilter(mRecordFilterGroup);
        mVideoFilterContext.getGLManager().registerFilter(mClipFilter);
        mVideoFilterContext.getGLManager().registerFilter(mPreviewFilter);
        if (mEnableVideoRecord) {
            mVideoFilterContext.getGLManager().registerFilter(mVideoEncodeFilter);
            mVideoFilterContext.getGLManager().registerFilter(mVideoEndPointFilter);
            mVideoFilterContext.getGLManager().registerFilter(mSnapshotFilter);
            mVideoFilterContext.getGLManager().registerFilter(mMediaMuxerFilter);
        }

        if (mEnableVideoRecord) {
            mAudioCaptureFilter = new AudioCaptureFilter(mAudioFilterContext);
            mAudioEncoderFilter = new AudioEncoderFilter(mAudioFilterContext);
            mAudioProcessFilter = new AudioProcessFilter(mAudioFilterContext);
            mAudioSpeedFilter = new AudioSpeedFilter();

            mAudioFilterContext.getAudioManager().registerFilter(mAudioCaptureFilter);
            mAudioFilterContext.getAudioManager().registerFilter(mAudioProcessFilter);
            mAudioFilterContext.getAudioManager().registerFilter(mAudioEncoderFilter);
            mAudioFilterContext.getAudioManager().registerFilter(mAudioSpeedFilter);
            mAVSyncFilter = new AVSyncFilter();
        }

        mCameraCaptureFilter.addDownStream(mRecordFilterGroup);
        if (mEnableVideoRecord) {
            mClipFilter.addDownStream(mVideoEncodeFilter).addDownStream(mSnapshotFilter);
            if (mStoreDataInMemory) {
                // TODO. Store video data in video memory manager filter.
                mVideoEncodeFilter.getOutputFilter().addDownStream(mMediaFormatAdapterFilter.addDownStream(mAVSyncFilter.addDownStream(mVideoDataManagerFilter).addDownStream(mMediaMuxerFilter)));
            } else {
                mVideoEncodeFilter.getOutputFilter().addDownStream(mMediaFormatAdapterFilter.addDownStream(mAVSyncFilter.addDownStream(mMediaMuxerFilter)));
            }
        }
        mRecordFilterGroup.setFilterGroupOutPath(mClipFilter, mPreviewFilter);

        if (mEnableVideoRecord) {
            if (mStoreDataInMemory) {
                // TODO. Store Audio data in Audio memory manager filter,
                mAudioCaptureFilter.addDownStream(mAudioProcessFilter.addDownStream(mAudioSpeedFilter.addDownStream(mAudioEncoderFilter.addDownStream(mAVSyncFilter.addDownStream(mAudioDataManagerFilter).addDownStream(mMediaMuxerFilter)))));
            } else {
                mAudioCaptureFilter.addDownStream(mAudioProcessFilter.addDownStream(mAudioSpeedFilter.addDownStream(mAudioEncoderFilter.addDownStream(mAVSyncFilter.addDownStream(mMediaMuxerFilter)))));
            }
        }
        mVideoFilterContext.getGLManager().setMediaSession(this);
        if (mEnableVideoRecord) {
            mAudioFilterContext.getAudioManager().setMediaSession(this);
        }

        mCacheDir = FileUtils.getDiskCacheDir(context);
        MeidacodecConfig.loadConfig(mContext);
        mSurfaceView.getHolder().addCallback(this);

        mOrientationEventListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int rotation) {
                if (((rotation >= 0) && (rotation < 45)) || (rotation >= 315)) {
                    mSurfaceRotation = 0;
                } else if ((rotation >= 45) && (rotation < 135)) {
                    mSurfaceRotation = 90;
                } else if ((rotation >= 135) && (rotation < 225)) {
                    mSurfaceRotation = 180;
                } else if ((rotation >= 225) && (rotation < 315)) {
                    mSurfaceRotation = 270;
                }
            }
        };

        YMRCameraMgr.getInstance().addCameraEventListener(this);
        mFocusAndMeteringDeal = new FocusAndMeteringDeal(mRecordConfig);
//        mSurfaceView.setOnTouchListener(mFocusAndMeteringDeal.getOnTouchListener());

        final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                if (mediaFilterContext != null) {
                    //这里并没有Prview相关的设置.
                    //保留当前的lowDelay信息，因为这个信息并不会随VideoEncoderConfig带入
                    VideoEncoderConfig config = new VideoEncoderConfig(mRecordConfig.getVideoWidth(), mRecordConfig.getVideoHeight(), mRecordConfig.getFrameRate(),
                            mRecordConfig.getBitRate(), VideoEncoderType.HARD_ENCODER_H264, "");

                    config.setBitRate(GlobalConfig.getInstance().getRecordConstant().RECORD_BITRATE);
                    config.setVideoEncoderType(GlobalConfig.getInstance().getRecordConstant().VIDEO_ENCODE_TYPE);
                    config.setEncodeParam(RecordDynamicParam.getInstance().getHardEncodeParameters());

                    YYLog.info(this, "encoder parameter=" + config.mEncodeParameter);

                    mediaFilterContext.setVideoEncodeConfig(config);
                    YYLog.info(this, "setEncoderConfig:" + config.toString());
                    mediaFilterContext.getDefaultVideoEncoderConfig().assign(config);

                    if (!mRelease.get()) {
                        mRecordFilterGroup.init(mediaFilterContext.getAndroidContext(), config.getEncodeWidth(), config.getEncodeHeight(), mRecordConfig.getOfDeviceLevel());
                        if (mEnableVideoRecord) {
                            mVideoEncodeFilter.init();
                        }
                        mPreviewFilter.init(config.getEncodeWidth(), config.getEncodeHeight());
                        OFColorTableFilterUtil.initFilePathUseContext(mediaFilterContext.getAndroidContext());
                    }
                }
            }
        });

        setFrameRate(mRecordConfig.getFrameRate());
        setBitRate(mRecordConfig.getBitRate());

        if (SDKCommonCfg.getRecordModePicture()) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    STMobileFaceDetectionWrapper.getPIctureInstance(mContext.getApplicationContext());
                }
            });
        }
        YYLog.info(this, "[tracer] NewVideoRecordSession end 2.8.1feature.......1380000000, phone model:" + DeviceUtil.getPhoneModel());
    }

    //set all component as null.
    public void glMgrCleanup() {
        if (mRelease.get()) {
            mCameraCaptureFilter = null;
            mRecordFilterGroup = null;
            mClipFilter = null;
            mVideoEncodeFilter = null;
            mPreviewFilter = null;
            mVideoEndPointFilter = null;
            mVideoFilterContext = null;
            mSnapshotFilter = null;

            if (mReleaseMuxFilter.get()) {
                YYLog.info(TAG, "glMgrCleanup set MediaMuxFilter null");
                mMediaMuxerFilter = null;
                mReleaseMuxFilter.set(false);
            } else {
                mReleaseMuxFilter.set(true);
            }

            mContext = null;
            mSurfaceView = null;

            YYLog.info(TAG, "VideoRecordSession glMgrCleanup");
        }
    }

    public void audioMgrCleanup() {
        if (mRelease.get()) {
            mAudioCaptureFilter = null;
            mAudioProcessFilter = null;
            mAudioEncoderFilter = null;

            if (mReleaseMuxFilter.get()) {
                YYLog.info(TAG, "audioMgrCleanup set MediaMuxFilter null");
                mMediaMuxerFilter = null;
                mReleaseMuxFilter.set(false);
            } else {
                mReleaseMuxFilter.set(true);
            }

            YYLog.info(TAG, "VideoRecordSession audioMgrCleanup");
        }
    }

    @Override
    public void setInputVideoFormat(MediaFormat mediaFromat) {

    }

    @Override
    public void setInputAudioFormat(MediaFormat mediaFormat) {

    }

    public void setVideoEncodeConfig(final VideoEncoderConfig config) {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                //sdk内部的适配参数.
                if (config.encodeParameterEmpty()) {
                    config.setEncodeParam(RecordDynamicParam.getInstance().getHardEncodeParameters());
                }
                mVideoFilterContext.setVideoEncodeConfig(config);

                YYLog.info(this, "setEncoderConfig:" + config.toString());
            }
        });
    }

    public void setEnableAudioRecord(boolean enable_audio) {
        mRecordConfig.setEnableAudioRecord(enable_audio);
        boolean caluFFT = enable_audio && mEnableAudioFrequencyCalculate;
        if (mAudioCaptureFilter != null) {
            mAudioCaptureFilter.enableAudioFrequencyCalculate(caluFFT);
        }
    }

    public void setCameraID(int id) {

        //对于只有单摄像头的手机，无论摄像头是否旋转为前置或者后置，cameraId 总为0
        if (Camera.getNumberOfCameras() == 1) {
            id = 0;
        }

        mRecordConfig.setCameraId(id);
        YMRCameraMgr.getInstance().addCameraEventListener(this);
    }

    /**
     * Start recording
     * <p>
     * return true is success, false is fail
     */
    public void startRecord(final boolean isSnapshot) throws VideoRecordException {
        long startTime = System.currentTimeMillis();
        YYLog.info(this, "[tracer] startRecord");
        if (mIsRecord.getAndSet(true)) {
            YYLog.info(this, "[tracer] startRecord, but it is record state, just return!!!");
            return;
        }

        VideoProcessTracer.RecordTracer recordTracer = new VideoProcessTracer.RecordTracer(mRecordConfig.getCameraId(), (int) (mRecordFilterSessionWrapper.getBeautyIntensity() * 100));
        recordTracer.setLutName(mRecordFilterSessionWrapper.getCurrentLutName());
        recordTracer.setExposureMode(YMRCameraMgr.getInstance().exposureMode);

        VideoProcessTracer.getInstace().addRecordTracer(recordTracer);
        VideoProcessTracer.getInstace().setResolution(mRecordConfig.getVideoWidth() + "x" + mRecordConfig.getVideoHeight());

        mSnapshotFilter.setSnapshotEnable(isSnapshot);
        //step 1:init filter
        mMediaFormatAdapterFilter.init();
        mMediaMuxerFilter.deInit();
        mMediaMuxerFilter.init();
        mVideoDataManagerFilter.deInit();
        mVideoDataManagerFilter.init();
        mAudioDataManagerFilter.deInit();
        mAudioDataManagerFilter.init();
        mAVSyncFilter.startRecord();
        synchronized (mRecordLock) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                        RecordConfig recordConfig = mediaFilterContext.getRecordConfig();
                        //step 2:启动视频encode
                        boolean res = mVideoEncodeFilter.startEncode(mediaFilterContext.getVideoEncoderConfig());
                        if (!res) {
                            if (recordConfig != null && recordConfig.getErrorListener() != null) {
                                mRecordConfig.getErrorListener().onVideoRecordError(MediaRecordErrorListener.MR_MSG_RECORD_START_ERROR, "encoder error!!");
                            }
                            YYLog.error(TAG, "start record.start encoder error!!!");
                            return;
                        }

                        mSnapshotFilter.startSnapshot();
                        mRecordFilterSessionWrapper.notifyEmojiStartRecord();
                        if (mStoreDataInMemory) {
                            mVideoDataManagerFilter.startRecord();
                        }
                    } catch (Exception e) {
                        YYLog.error(TAG, "video startRecord exception occur:" + e.getMessage());
                    } finally {
                        synchronized (mRecordLock) {
                            mRecordLock.notify();
                        }
                    }
                }
            });

            try {
                mRecordLock.wait();
            } catch (InterruptedException e) {
                YYLog.error(TAG, "video startRecord InterruptedException");
                e.printStackTrace();
            }

        }
        YYLog.info(TAG, "start record.video encoder start success");

        synchronized (mRecordLock) {
            final WeakReference<AudioFilterContext> wrAudioFilterContext = new WeakReference<>(mAudioFilterContext);
            mAudioFilterContext.getAudioManager().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        AudioFilterContext audioFilterContext = wrAudioFilterContext.get();
                        RecordConfig recordConfig = audioFilterContext.getRecordConfig();
                        if (recordConfig.getEnableAudioRecord()) {

                            //step 3:启动音频编码及处理filter
                            mAudioEncoderFilter.init();
                            mAudioProcessFilter.init();
                            mAudioSpeedFilter.init(AudioRecordConstant.SAMPLE_RATE, AudioRecordConstant.CHANNELS);
                            mAudioSpeedFilter.setRate(recordConfig.getRecordSpeed());

                            if (recordConfig.getEnableAudioRecord()) {
                                //step 4:启动音频encode
                                mAudioEncoderFilter.startAudioEncode();

                                if (mStoreDataInMemory) {
                                    mAudioDataManagerFilter.startRecord();
                                }
                            }
                        }
                    } catch (Exception e) {
                        YYLog.error(TAG, "audio startRecord exception occur:" + e.getMessage());
                    } finally {
                        synchronized (mRecordLock) {
                            mRecordLock.notify();
                        }
                    }
                }
            });

            try {
                mRecordLock.wait();
            } catch (InterruptedException e) {
                YYLog.error(TAG, "audio startRecord InterruptedException");
                e.printStackTrace();
            }
            YYLog.info(TAG, "start record.audio encoder start success");
        }

        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditorNotifyHandler = new Handler();
                mAudioPlayEditor.start();
            }
        }
        mAudioPlayLenMS = 0;

        // When there are background music, 'AudioPlayEditor' will notify camera to encode
        if (mBackgroundMusicID == -1) {
            mAudioCaptureFilter.setEncodeEnable(true);
            mCameraCaptureFilter.setEncodeEnable(true);
        }

        UploadStatManager.getInstance().startStat(mVideoFilterContext.getVideoEncoderConfig().mEncodeType.ordinal());
        RecordConfig recordConfig = mVideoFilterContext.getRecordConfig();
        if (recordConfig != null && recordConfig.getRecordListener() != null) {
            recordConfig.getRecordListener().onStart(true);
        }

        YYLog.info(TAG, "startRecord time:" + (System.currentTimeMillis() - startTime));
    }

    private AudioPlayEditor.IAudioPlayEditorListener mAudioPlayEditorListener = new AudioPlayEditor.IAudioPlayEditorListener() {
        @Override
        public void onAudioPlayStart() {
            if (mIsRecord.get()) {
                mAudioCaptureFilter.setEncodeEnable(true);
                mCameraCaptureFilter.setEncodeEnable(true);
                YYLog.info(TAG, " enable video record ");
            }
        }

        @Override
        public void onAudioPlayStop(long positionMS) {
            //mAudioPlayLenMS = positionMS;
            YYLog.info(TAG, "AudioPlay disable video record " + positionMS);
        }
    };

    public void pauseRecord() {
        long audioLen = AudioDataManager.instance().getDuration();
        boolean audioStop = false;
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditorNotifyHandler = new Handler();
                mAudioPlayEditor.pause();
                audioLen = mAudioPlayEditor.getCurrentPlayPositionMS() * 1000;
                audioStop = true;
                mAudioPlayLenMS = audioLen;
            }
        }

        //if (mBackgroundMusicID == -1) {
            mAVSyncFilter.stopRecord(new Runnable() {
                @Override
                public void run() {
                    stopRecord();
                }
            }, audioLen, audioStop);
            YYLog.info(this, "[tracer] pauseRecord!!!");
        //}
    }

    public void stopRecord() {
        long stopTime = System.currentTimeMillis();
        YYLog.info(this, "[tracer] stopRecord");
        if (!mIsRecord.get()) {
            YYLog.info(this, "[tracer] stopRecord, but it is not recording state, just return");
            return;
        }

        //控制capture的数据不再送入编码器
        if (mAudioCaptureFilter != null) {
            YYLog.info(TAG, "stop audio capture");
            mAudioCaptureFilter.setEncodeEnable(false);
        }
        if (mCameraCaptureFilter != null) {
            YYLog.info(TAG, "stop video capture");
            mCameraCaptureFilter.setEncodeEnable(false);
        }

        synchronized (mRecordLock) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        YYLog.info(this, "[tracer] do stopRecord  begin!!!====================");
                        UploadStatManager.getInstance().stopStat();

                        if (mSnapshotFilter != null && mSnapshotFilter.isSnaping()) {
                            mSnapshotFilter.stopSnapshot();
                        }

                        if (mBlurBitmapCallback != null) {
                            Message msg = Message.obtain();
                            msg.what = EventHandler.BLUR_BITMAP;
                            msg.obj = mPreviewFilter.getLastBitmap();
                            mEventHandler.sendMessage(msg);
                        }

                        if (mVideoEncodeFilter.isEnable()) {
                            mVideoEncodeFilter.stopEncode();
                        }

                        if (mStoreDataInMemory) {
                            mVideoDataManagerFilter.stopRecord();
                        }

                        YYLog.info(this, "[tracer] do stopRecord end!!!=========================");
                    } catch (Exception e) {
                        YYLog.error(TAG, "video stopRecord exception occur:" + e.getMessage());
                    } finally {
                        synchronized (mRecordLock) {
                            mRecordLock.notify();
                        }
                    }
                }
            });

            try {
                mRecordLock.wait();
            } catch (InterruptedException e) {
                YYLog.error(TAG, "video mVideoStopRecordLock ," + e.getMessage());
                e.printStackTrace();
            }
        }
        synchronized (mRecordLock) {
            mAudioFilterContext.getAudioManager().post(new Runnable() {
                @Override
                public void run() {
                    try {

                        //stop后不需要做deinit操作.
                        if (mAudioEncoderFilter != null) {
                            mAudioEncoderFilter.stopAudioEncode(false);
                        }
                        mAudioSpeedFilter.deInit();
                        //mAudioEncoderFilter.deInit();

                        mAudioProcessFilter.deInit();
                        if (mStoreDataInMemory) {
                            mAudioDataManagerFilter.stopRecord();
                        }
                    } catch (Exception e) {
                        YYLog.error(TAG, "audio stopRecord exception occur:" + e.getMessage());
                    } finally {
                        synchronized (mRecordLock) {
                            mRecordLock.notify();
                        }
                    }
                }
            });

            try {
                mRecordLock.wait();
            } catch (InterruptedException e) {
                YYLog.error(TAG, "video mAudioStopRecordLock ," + e.getMessage());
                e.printStackTrace();
            }
        }

        mMediaMuxerFilter.deInit();
        mIsRecord.set(false);
        YYLog.info(TAG, "stopRecord time:" + (System.currentTimeMillis() - stopTime));
    }

    /**
     * 业务层通知sdk删除了上一段录制，sdk更新保存的截图delta
     */
    public void deleteLastRecordSnapshot() {
        if (mSnapshotFilter != null) {
            mSnapshotFilter.deleteLastRecordSnapshot();
        }
    }

    /**
     * set file record speed for recording
     *
     * @param recordSpeed
     */
    public void setRecordSpeed(final float recordSpeed) {
        mRecordConfig.setRecordSpeed(recordSpeed);
        mAudioFilterContext.getAudioManager().post(new Runnable() {
            @Override
            public void run() {
                mAudioSpeedFilter.setRate(recordSpeed);
            }
        });

        YYLog.info(this, "[ymrsdk] set record speed:" + recordSpeed);
    }

    /**
     * set record orange filter level for different devices
     *
     * @param deviceLevel
     */
    public void setOfDeviceLevel(int deviceLevel) {
        YYLog.info(TAG, "setOfDeviceLevel:" + deviceLevel);
        mRecordConfig.setOfDeviceLevel(deviceLevel);
    }

    /**
     * set encode type for recording
     *
     * @param encodeType
     */
    public void setEncodeType(final int encodeType) {
        if (mVideoFilterContext != null) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                    if (mediaFilterContext != null) {
                        VideoEncoderConfig config = mediaFilterContext.getVideoEncoderConfig();
                        if (config != null) {
                            if (encodeType == RecordContants.RECORD_ENCODE_ONCE
                                    || encodeType == RecordContants.RECORD_SOFT_ENCODE_ONCE) {
                                int bitrate = 2500000;
                                int fps = GlobalConfig.getInstance().getRecordConstant().RECORD_FRAME_RATE;
                                int gopSize = 2;

                                bitrate = (int) (bitrate / mRecordConfig.getRecordSpeed());
                                fps = (int) (fps / mRecordConfig.getRecordSpeed());
                                gopSize = (int) (gopSize * mRecordConfig.getRecordSpeed());

                                YYLog.info(this, "set encode type:" + encodeType + ",bitrate:" + bitrate + ",fps:" + fps + ",gop:" + gopSize +
                                        ",record speed:" + mRecordConfig.getRecordSpeed());
                                config.setBitRate(bitrate); //更新码率
                                config.setFrameRate(fps); //更新帧率
                                config.setGopSize(gopSize); // gopSize*fps个帧中有一个i帧，默认为2
                                config.setBitRateModel(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR); //更新bitrate model

                                if (encodeType == RecordContants.RECORD_SOFT_ENCODE_ONCE) {
                                    config.setVideoEncoderType(VideoEncoderType.SOFT_ENCODER_X264);
                                    config.setIFrameMode(false);
                                }
                            }
                            //设置type为编码两次的情况
                            if (encodeType == RecordContants.RECORD_ENCODE_TWICE) {
                                YYLog.info(this, "set encode type:" + encodeType);
                                VideoEncoderConfig vConfig = new VideoEncoderConfig(mRecordConfig.getVideoWidth(), mRecordConfig.getVideoHeight(), mRecordConfig.getFrameRate(),
                                        mRecordConfig.getBitRate(), VideoEncoderType.HARD_ENCODER_H264, "");

                                vConfig.setBitRate(GlobalConfig.getInstance().getRecordConstant().RECORD_BITRATE);
                                vConfig.setVideoEncoderType(GlobalConfig.getInstance().getRecordConstant().VIDEO_ENCODE_TYPE);
                                vConfig.setEncodeParam(RecordDynamicParam.getInstance().getHardEncodeParameters());

                                mVideoFilterContext.setVideoEncodeConfig(vConfig);
                            }
                        }
                    }
                }
            });
        }
    }


    private void setEffectIsRestart(final boolean isRestart) {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                //TODO:wqm
//                mGPUPreprocessFilter.setEffectIsRestart(isRestart);
            }
        });
    }


    /**
     * set file path for recording
     *
     * @param file_name Should be full file path.
     */
    public void setOutputPath(String file_name) {
        YYLog.info(this, "[tracer] setOutputPath:" + file_name);
        mRecordConfig.setOutputPath(file_name);
        if (mRecordFilterGroup != null) {
            //String json_name = file_name+".json";
            // 暂时定一个json文件，等编辑页ui接入多段视频播放后，在生成多个json。录制多段时会覆盖旧的json文件。
            String json_name = "sdcard/filter.json";
            mRecordFilterGroup.setOutFileName(file_name, json_name);
        }
    }

    /**
     * Notify to user when when recording error occurs, or recording stopped.
     *
     * @param info_error see MediaRecordErrorListener for more details
     */
    public void setErrorListener(MediaRecordErrorListener info_error) {
        mErrorListener = info_error;

        mRecordConfig.setErrorListener(new MediaRecordErrorListener() {
            @Override
            public void onVideoRecordError(int what, String message) {
                Message msg = new Message();
                msg.what = EventHandler.RECORD_ERROR;
                msg.arg1 = what;
                msg.obj = message;
                mEventHandler.sendMessage(msg);
            }
        });
    }

    private void startPreview(final YMRCameraInfo cameraInfo) {
        YYLog.info(this, "startPreview mCameraFacingFront=" + cameraInfo.mCameraFacingFront);

        if (mVideoFilterContext.getGLManager().checkSameThread()) {
            mCameraCaptureFilter.init();
            YMRCameraMgr.getInstance().startPreview(cameraInfo.getCameraID(), mCameraCaptureFilter.getSurfaceTexture());
            STMobileFaceDetectionWrapper.getInstance(mVideoFilterContext.getAndroidContext()).setPreviewCallbackWithBuffer();


        } else {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    mCameraCaptureFilter.init();
                    YMRCameraMgr.getInstance().startPreview(cameraInfo.getCameraID(), mCameraCaptureFilter.getSurfaceTexture());
                    STMobileFaceDetectionWrapper.getInstance(mVideoFilterContext.getAndroidContext()).setPreviewCallbackWithBuffer();
                }
            });
        }
    }

    private void startAudioCapture() {
        RecordConfig recordConfig = mAudioFilterContext.getRecordConfig();
        if (recordConfig.getEnableAudioRecord()) {
            mAudioFilterContext.getAudioManager().post(new Runnable() {
                @Override
                public void run() {
                    if (mAudioCaptureFilter != null) {
                        mAudioCaptureFilter.init();
                        mAudioCaptureFilter.startCapture();
                        YYLog.info(TAG, Constant.MEDIACODE_CAP + "startAudioCapture");
                    }
                }
            });
        }
    }

    private void stopAudioCapture() {
        RecordConfig recordConfig = mAudioFilterContext.getRecordConfig();
        if (recordConfig.getEnableAudioRecord()) {
            mAudioFilterContext.getAudioManager().post(new Runnable() {
                @Override
                public void run() {
                    if (mAudioCaptureFilter != null) {
                        mAudioCaptureFilter.stopCapture();
                        mAudioCaptureFilter.deInit();
                        YYLog.info(TAG, Constant.MEDIACODE_CAP + "stopAudioCapture");
                    }
                }
            });
        }
    }


    public synchronized void onPause() {
        if (!mRelease.get()) {
            stopRecord();
            releaseCamera();
			if (mEnableVideoRecord) {
                stopAudioCapture();
            }
        } else {
            YYLog.info(TAG, "onPause after released , just return");
        }
    }

    public synchronized void onResume() throws VideoRecordException {
        if (!mRelease.get()) {
            OFLoader.setNeedResumeEffect(true);
            openCamera();
            if (mEnableVideoRecord) {
                startAudioCapture();
            }
            mPreviewFilter.setPreviewStart(false);
        }
    }

    private void openCamera() {
        //STMobileFaceDetectionWrapper.getInstance(mContext);
        //open the camera.
        //Running in main thread.
        long linkID = YMRCameraMgr.getInstance().open(mRecordConfig.getCameraId(), mRecordConfig, (Activity) mContext, YMRCameraMgr.CameraResolutionMode.CAMERA_RESOLUTION_PRECISE_MODE);
        mCurrentCameraLinkID.set(linkID);
        mFocusAndMeteringDeal.setCameraLinkID(linkID);
        if (linkID == -1) {
            YYLog.e("[camera]", "VideoRecordInternalCamDecouple openCamera fail");
            return;
        } else {
            if (mVideoEncodeFilter != null || !mEnableVideoRecord) {
                final WeakReference<MediaFilterContext> wrMediaFilterContext = new WeakReference<>(mVideoFilterContext);
                mVideoFilterContext.getGLManager().getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        MediaFilterContext mediaFilterContext = wrMediaFilterContext.get();
                        if (mediaFilterContext == null) {
                            return;
                        }
                        if (null != mOrientationEventListener)
                            mOrientationEventListener.enable();

                        if (mYMRCameraInfo == null) {
                            YMRCameraInfo cameraInfo = YMRCameraMgr.getInstance().getYMRCameraParameterInfo(mRecordConfig.getCameraId());
                        }

                        if (mYMRCameraInfo != null) {

                            YYLog.info(this, "openCamera  " + mYMRCameraInfo.toString());
                            YYLog.info(this, "startPreview mCameraFacingFront=" + mYMRCameraInfo.mCameraFacingFront);

                            mCameraCaptureFilter.init();
                            //mRecordFilterGroup.init(mVideoFilterContext, mYMRCameraInfo.mPreviewWidth, mYMRCameraInfo.mPreviewHeight);
                            YMRCameraMgr.getInstance().startPreview(mYMRCameraInfo.getCameraID(), mCameraCaptureFilter.getSurfaceTexture());
                        }
                    }
                });
            }
        }
    }

    protected void releaseCamera() {
        YMRCameraMgr.getInstance().releaseAll();
    }

    protected void releaseRecorder() {
        if (null != mOrientationEventListener)
            mOrientationEventListener.disable();
    }

    public void setBitRate(final int bitRate) {
        if (mVideoFilterContext != null) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                    if (mediaFilterContext != null) {
                        VideoEncoderConfig config = mediaFilterContext.getVideoEncoderConfig();
                        if (config != null) {
                            config.mBitRate = bitRate;
                        }
                    }
                }
            });
        }
    }

    public void setFrameRate(final int frameRate) {
        if (mVideoFilterContext != null) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                    if (mediaFilterContext != null) {
                        VideoEncoderConfig config = mediaFilterContext.getVideoEncoderConfig();
                        if (config != null) {
                            config.mFrameRate = frameRate;
                        }
                    }
                }
            });
        }
    }

    public void setVideoSize(final int width, final int height) {
        mRecordConfig.setVideoWidth(width);
        mRecordConfig.setVideoHeight(height);
        VideoProcessTracer.getInstace().setResolution(width + "x" + height);

        if (mVideoFilterContext != null) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                    if (mediaFilterContext != null) {
                        VideoEncoderConfig config = mediaFilterContext.getVideoEncoderConfig();
                        if (config != null) {
                            config.setEncodeSize(width, height);
                        }
                    }
                }
            });
        }
    }

    public void setYyVersion(String yyVersion) {
        VideoProcessTracer.getInstace().setYyVersion(yyVersion);
    }

    public void setAudioRecordListener(final IAudioRecordListener recordListener) {
        mRecordConfig.setAudioRecordListener(recordListener);
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        if (mRecordFilterGroup != null) {
            mRecordFilterGroup.setMediaInfoRequireListener(listener);
        }
    }

    /*设置进度回调*/
    public void setRecordListener(final IVideoRecordListener recordListener) {
        YYLog.info(TAG, "setRecordListener " + (recordListener == null ? "null" : ""));
        mRecordListener = recordListener;
        mRecordConfig.setRecordListener(new IVideoRecordListener() {
            @Override
            public void onProgress(float seconds) {
                Message msg = new Message();
                msg.what = EventHandler.RECORD_PROGRESS;
                msg.obj = new Float(seconds);
                mEventHandler.sendMessage(msg);

                /*
                if (mBackgroundMusicID != -1 ) {
                    long videoRecordLenMS = VideoDataManager.instance().getDuration();
                    if (mAudioPlayLenMS >0 && videoRecordLenMS >= mAudioPlayLenMS) {
                        YYLog.info(TAG, "AudioPlay " + mAudioPlayLenMS + " >> " + videoRecordLenMS);
                        mAudioPlayLenMS = 0;
                        VideoDataManager.instance().stopRecord();
                        if (mCameraCaptureFilter != null) {
                            mCameraCaptureFilter.setEncodeEnable(false);
                        }
                        if (mAudioPlayEditorNotifyHandler != null) {
                            mAudioPlayEditorNotifyHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    stopRecord();
                                }
                            });
                        }
                    }
                } */

            }

            @Override
            public void onStart(boolean successed) {
                Message msg = new Message();
                msg.what = EventHandler.RECORD_START;
                msg.obj = new Boolean(successed);
                mEventHandler.sendMessage(msg);
            }

            @Override
            public void onStop(boolean successed) {

                Message msg = new Message();
                msg.what = EventHandler.RECORD_STOP;
                msg.obj = new Boolean(successed);
                mEventHandler.sendMessage(msg);
                releaseRecorder();
            }
        });
    }

    public void setPreviewListener(final IVideoPreviewListener previewListener) {
        mRecordConfig.setPreviewListener(previewListener);
    }

    public void setFlashMode(final String flashMode) {
        if (mCurrentCameraLinkID.get() != -1) {
            YMRCameraMgr.getInstance().setFlashMode(mRecordConfig.getCameraId(), flashMode);
        }
    }

    public void setFaceDetectionListener(final IFaceDetectionListener listener) {
        if (mVideoFilterContext != null) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    if (mRecordFilterGroup != null) {
                        mRecordFilterGroup.setFaceDetectionListener(listener);
                    }
                }
            });
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
//        final AbstractSurfaceInfo surfaceInfo = EglFactory.newSurfaceInfo(holder, 0, 0);
//        if (mVideoFilterContext != null) {
//            mVideoFilterContext.getGLManager().post(new Runnable() {
//                @Override
//                public void run() {
//                    if (mPreviewFilter != null) {
//                        mPreviewFilter.onSurfaceChanged(surfaceInfo);
//                    }
//                }
//            });
//        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        final AbstractSurfaceInfo surfaceInfo = EglFactory.newSurfaceInfo(holder, width, height);
        if (mVideoFilterContext != null) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    if (mPreviewFilter != null) {
                        mPreviewFilter.onSurfaceChanged(surfaceInfo);
                        mPreviewFilter.setSurfaceValid(true);
                    }
                }
            });
        }

        mFocusAndMeteringDeal.surfaceChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPreviewFilter != null) {
            mPreviewFilter.setSurfaceValid(false);
        }

        if (mVideoFilterContext != null) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    if (mPreviewFilter != null) {
                        mPreviewFilter.onSurfaceDestroy();
                    }
                }
            });
        }
    }

    public void switchCamera() {
        YYLog.info(this, "[camera] [trace] switchCamera, current camera ID:" + mRecordConfig.getCameraId());

        //对于只有单摄像头的手机，无论摄像头是否旋转为前置或者后置，cameraId 总为0
        if (Camera.getNumberOfCameras() == 1) {
            return;
        }

        if (mRecordConfig.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mRecordConfig.setCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } else {
            mRecordConfig.setCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
        }

        releaseCamera();
        STMobileFaceDetectionWrapper.getInstance(mContext).initFaceDate(); // 重置人脸检测的数据
        openCamera();
        if (mVideoEncodeFilter != null) {
            mVideoFilterContext.getGLManager().getHandler().post(new Runnable() {
                @Override
                public void run() {
                    STMobileFaceDetectionWrapper.getInstance(mContext).setPreviewCallbackWithBuffer();
                }
            });
        }
    }

    public Parameters getCameraParameters() {
        if (mCurrentCameraLinkID.get() != -1) {
            return YMRCameraMgr.getInstance().getCameraParameters(mRecordConfig.getCameraId());
        }
        return null;
    }

    public boolean setCameraParameters(Parameters cameraParameters) {
        if (mCurrentCameraLinkID.get() != -1) {
            return YMRCameraMgr.getInstance().setCameraParameters(mRecordConfig.getCameraId(), cameraParameters);
        }
        return false;
    }

    public Camera.CameraInfo getCameraInfo() {
        if (mCurrentCameraLinkID.get() != -1) {
            return YMRCameraMgr.getInstance().getCameraInfo(mRecordConfig.getCameraId());
        }
        return null;
    }

    public synchronized void release() {
        mRelease.set(true);

        YYLog.info(this, "[tracer] release begin");
        if (null != mOrientationEventListener) {
            mOrientationEventListener.disable();
            mOrientationEventListener = null;
        }

        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.release();
                mAudioPlayEditor = null;
            }
            mBackgroundMusicID = -1;
        }

        releaseRecorder();

        if (mEnableVideoRecord) {
            stopAudioCapture();
        }

        YMRCameraMgr.getInstance().releaseAll();
        YMRCameraMgr.getInstance().removeCameraEventListener(this);

        stopRecord();
        HardSurfaceEncoder.releaseEncoder();
        //HardAudioEncoder.releaseEncoder();

        if (mVideoFilterContext != null) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    mRecordFilterGroup.destroy();
                    FilterCenter.getInstance().removeFilterObserver(mRecordFilterGroup, mRecordFilterSessionWrapper.getSessionID());
                    mRecordFilterGroup = null;
                    mRecordFilterSessionWrapper = null;
                }
            });

            mVideoFilterContext.getGLManager().quit();
            mVideoFilterContext = null;
        }

        if (mAudioFilterContext != null) {
            mAudioFilterContext.getAudioManager().post(new Runnable() {
                @Override
                public void run() {
                    mAudioCaptureFilter.deInit();
                    mAudioProcessFilter.deInit();
                    mAudioEncoderFilter.deInit();
                    mMediaMuxerFilter.deInit();
                }
            });
        }

        if (mAudioFilterContext != null) {
            mAudioFilterContext.getAudioManager().quit();
            mAudioFilterContext = null;
        }

        mBlurBitmapCallback = null;

        mRecordConfig.setRecordListener(null);
        mRecordConfig.setAudioRecordListener(null);

        mRecordConfig = null;


        YYLog.info(this, "[tracer] VideoRecordSession release end !!");

        MeidacodecConfig.unLoadConfig();
    }

    @Override
    public void onCameraOpenSuccess(final int cameraID) {
        YYLog.i("[camera]", "onCameraOpenSuccess cameraID=" + cameraID);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mCameraEventListener != null) {
                    mCameraEventListener.onCameraOpenSuccess(cameraID);
                }
            }
        }).start();
    }

    @Override
    public void onCameraOpenFail(final int cameraID, final String reason) {
        YYLog.i("[camera]", "onCameraOpenFail cameraID=" + cameraID + " reason=" + reason);
        //may run at main thread
        mRecordConfig.getErrorListener().onVideoRecordError(MediaRecordErrorListener.CAMERCA_ERROR, reason);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mCameraEventListener != null) {
                    mCameraEventListener.onCameraOpenFail(cameraID, reason);
                }
            }
        }).start();
    }

    @Override
    public void onCameraPreviewParameter(final int cameraID, final YMRCameraInfo cameraInfo) {
        //try to start preview....
        YYLog.i("[camera]", "onCameraPreviewParameter cameraID=" + cameraID);

        if (mVideoFilterContext != null && mVideoFilterContext.getGLManager() != null) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    mYMRCameraInfo = new YMRCameraInfo(cameraInfo);
                    MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                    if (mediaFilterContext != null) {
                        mediaFilterContext.setYMRCameraInfo(mYMRCameraInfo);
                        YYLog.info(this, "onCameraPreviewParameter, mYMRCameraInfo " + mYMRCameraInfo.toString());
                    }
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mCameraEventListener != null) {
                    mCameraEventListener.onCameraPreviewParameter(cameraID, cameraInfo);
                }
            }
        }).start();
    }

    @Override
    public void onCameraRelease(final int cameraID) {
        YYLog.i("[camera]", "onCameraRelease cameraID=" + cameraID);

        if (mVideoFilterContext != null && mVideoFilterContext.getGLManager() != null) {
            final WeakReference<MediaFilterContext> wrVideoFilterContext = new WeakReference<>(mVideoFilterContext);
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    if (mYMRCameraInfo != null && mYMRCameraInfo.getCameraID() == cameraID) {
                        YYLog.info(this, "onCameraRelease, mYMRCameraInfo " + mYMRCameraInfo.toString());
                        mYMRCameraInfo = null;

                        MediaFilterContext mediaFilterContext = wrVideoFilterContext.get();
                        if (mediaFilterContext != null) {
                            mediaFilterContext.setYMRCameraInfo(mYMRCameraInfo);
                        }
                    }
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mCameraEventListener != null) {
                    mCameraEventListener.onCameraRelease(cameraID);
                }
            }
        }).start();
    }

    public boolean isRecordEnabeled() {
        return MediaCodecTester.testHard264Enable();
    }

    public void setRecordSnapShot(String snapShotPath, String fileNamePrefix, float snapFrequency) {
        mRecordConfig.setSnapShotPath(snapShotPath);
        mRecordConfig.setSnapShotFileNamePrefix(fileNamePrefix);
        mRecordConfig.setSnapFrequency(snapFrequency);
    }

    public void setResolutionType(ResolutionType resolutionType) {
        mRecordConfig.setResolutionType(resolutionType);
    }

    public void setBlurBitmapCallBack(IBlurBitmapCallback blurBitmapCallBack) {
        mBlurBitmapCallback = blurBitmapCallBack;
    }

    public int getZoom() {
        if (mCurrentCameraLinkID.get() != -1) {
            return YMRCameraMgr.getInstance().getZoom(mRecordConfig.getCameraId());
        }
        return 0;
    }

    public int getMaxZoom() {
        if (mCurrentCameraLinkID.get() != -1) {
            return YMRCameraMgr.getInstance().getMaxZoom(mRecordConfig.getCameraId());
        }

        return 0;
    }

    public void setZoom(int zoom) {
        if (mCurrentCameraLinkID.get() != -1) {
            YMRCameraMgr.getInstance().setZoom(mRecordConfig.getCameraId(), zoom);
        }
    }

    public RecordFilterSessionWrapper getRecordFilterSessionWrapper() {
        return mRecordFilterSessionWrapper;
    }

    public void focusAndMetering(float x, float y, boolean autoCancel) {
        mFocusAndMeteringDeal.focusAndMetering(x, y, autoCancel);
    }

    private class EventHandler extends Handler {
        public static final int BLUR_BITMAP = 0;
        public static final int RECORD_START = 1;
        public static final int RECORD_PROGRESS = 2;
        public static final int RECORD_STOP = 3;
        public static final int RECORD_ERROR = 4;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BLUR_BITMAP:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    if (mBlurBitmapCallback != null) {
                        mBlurBitmapCallback.onBlurCallback(bitmap);
                    }
                    break;
                case  RECORD_START:
                    YYLog.info(TAG, "send onVideoRecordStart message");
                    boolean startSucceed  = ((Boolean) msg.obj).booleanValue();
                    if(mRecordListener != null) {
                        mRecordListener.onStart(startSucceed);
                    }
                    break;
                case  RECORD_STOP:
                    YYLog.info(TAG, "send onVideoRecordStop message");
                    boolean stopSucceed  = ((Boolean) msg.obj).booleanValue();
                    if(mRecordListener != null) {
                        mRecordListener.onStop(stopSucceed);
                    }
                    break;
                case RECORD_PROGRESS:
                    float seconds = ((Float) msg.obj).floatValue();
                    if (mRecordListener != null) {
                        mRecordListener.onProgress(seconds);
                    }
                    break;
                case RECORD_ERROR:
                    YYLog.info(TAG, "send onVideoRecordError message");
                    int what = msg.arg1;
                    String message = (String) msg.obj;
                    if (mErrorListener != null) {
                        mErrorListener.onVideoRecordError(what, message);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void getCameraBitmap(IBlurBitmapCallback blurBitmapCallback) {
        if (mSnapshotFilter != null) {
            mSnapshotFilter.setCaptureCallback(blurBitmapCallback);
        }
    }

    public int setBackgroundMusic(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long delayMS) {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.setListener(null);
                mAudioPlayEditor.pause();
            }
            mBackgroundMusicID = addAudioFileToPlay(path, beginReadPositionMS, endReadPositionMS, loop, delayMS, false);
            mAudioPlayEditor.setListener(mAudioPlayEditorListener);
            return mBackgroundMusicID;
        }
    }

    public int addAudioFileToPlay(String path, long beginReadPositionMS, long endReadPositionMS, boolean loop, long delayMS, boolean forceStart) {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor == null) {
                mAudioPlayEditor = new AudioPlayEditor();
                mAudioPlayEditor.prepare(mContext);
                mAudioPlayEditor.setPlaybackRate(mAudioPlaySpeed);
                mAudioPlayEditor.enableFrequencyCalculate(mEnableAudioFrequencyCalculate);
            }
            int id = mAudioPlayEditor.addPlayer(path, beginReadPositionMS, endReadPositionMS, loop, delayMS);
            if (forceStart) {
                mAudioPlayEditor.start();
            }
            return id;
        }
    }

    public void removeAudioFile(int ID) {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.removePlayer(ID);
            }
            if (mBackgroundMusicID == ID) {
                mBackgroundMusicID = -1;
                mAudioPlayEditor.setListener(null);
            }
        }
    }

    public void removeAllAudioFile() {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.clearPlayers();
                mAudioPlayEditor.release();
                mAudioPlayEditor = null;
            }
            mBackgroundMusicID = -1;
        }
    }

    public void seek(int positionMS) {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.seek(positionMS);
            }
        }
    }

    public long getAudioPlayPositionInMS() {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                return mAudioPlayEditor.getCurrentPlayPositionMS();
            }
        }
        return 0;
    }

    public void setAudioFileVolume(int ID, float volume) {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.setPlayerVolume(ID, volume);
            }
        }
    }

    public String[] getRecordAudioPaths() {
        return null;
    }

    public void setAudioPlaySpeed(float speed) {
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.setPlaybackRate(speed);
            }
            mAudioPlaySpeed = speed;
        }
    }

    public void enableAudioFrequencyCalculate(boolean enable) {
        mEnableAudioFrequencyCalculate = enable;
        if (mAudioCaptureFilter != null) {
            boolean caluFFT = mRecordConfig.getEnableAudioRecord() && mEnableAudioFrequencyCalculate;
            mAudioCaptureFilter.enableAudioFrequencyCalculate(caluFFT);
        }
        synchronized (mAudioPlayEditorLock) {
            if (mAudioPlayEditor != null) {
                mAudioPlayEditor.enableFrequencyCalculate(enable);
            }
        }
    }

    public int audioFrequencyData(float[] buffer, int len) {
        if (mRecordConfig == null || !mIsRecord.get()) {
            return 0;
        }
        if (mRecordConfig.getEnableAudioRecord()) {
            if (mAudioCaptureFilter != null) {
                return mAudioCaptureFilter.audioFrequencyData(buffer, len);
            }
        }else {
            synchronized (mAudioPlayEditorLock) {
                if (mAudioPlayEditor != null) {
                    return mAudioPlayEditor.frequencyData(buffer, len);
                }
            }
        }
        return 0;
    }

    public void delayInitSTMobile() {
        STMobileFaceDetectionWrapper.getInstance(mContext);
        if (mVideoFilterContext != null) {
            mVideoFilterContext.getGLManager().getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    STMobileFaceDetectionWrapper.getInstance(mContext).onCameraPreviewParameter(mYMRCameraInfo.getCameraID(), mYMRCameraInfo);
                    STMobileFaceDetectionWrapper.getInstance(mContext).setPreviewCallbackWithBuffer();
                }
            }, 200);
        }
    }
	
	public void setTakePictureConfig(TakePictureConfig config) {
        YMRCameraMgr.getInstance().setTakePictureConfig(config);
    }

    public void recoverPreview() {

        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                if (mYMRCameraInfo != null) {
                    YMRCameraMgr.getInstance().startPreview(mYMRCameraInfo.getCameraID(), mCameraCaptureFilter.getSurfaceTexture());
                }
            }
        });
    }

    public void autoFocus(Camera.AutoFocusCallback callback) {
        YMRCameraMgr.getInstance().autoFocus(mRecordConfig.getCameraId(), callback);
    }

    public void takePicture(TakePictureParam param) {
        YMRCameraMgr.getInstance().takePicture(mRecordConfig.getCameraId(), param);
    }

    public void setAspectRatioListener(IChangeAspectRatioListener listener) {
        if (mPreviewFilter != null) {
            mPreviewFilter.setAspectRatioListener(listener);
        }
    }

    public void setAspectWithDynamicEffect(boolean bEffect) {
        if (mPreviewFilter != null) {
            mPreviewFilter.setAspectWithDynamicEffect(bEffect);
        }
    }

    public void setAspectRatio(AspectRatioType aspectRatio, int x_offset, int y_offset) {
        if (mPreviewFilter != null) {
            mPreviewFilter.setAspectRatio(aspectRatio, x_offset, y_offset);
        }
        if (mRecordFilterGroup != null) {   // for transformfilter snapshot
            mRecordFilterGroup.setAspectRatio(aspectRatio);
        }
    }

    public Rect getCurrentVideoRect() {
        if (mPreviewFilter != null) {
            return mPreviewFilter.getCurrentVideoRect();
        }
        return null;
    }

    public void SyncFinalPreviewRect(int surfaceWidth, int surfaceHeight) {
        if (mPreviewFilter != null) {
            mPreviewFilter.SyncFinalPreviewRect(surfaceWidth, surfaceHeight);
        }
    }


    public Rect getFinalPreviewRectByAspect(AspectRatioType aspectRatio) {
        if (mPreviewFilter != null) {
            return mPreviewFilter.getFinalPreviewRectByAspect(aspectRatio);
        }
        return null;
    }

    public void setPreviewSnapshotListener(IPreviewSnapshotListener listener) {
        if (mPreviewFilter != null) {
            mPreviewFilter.setPreviewSnapshotListener(listener);
        }
    }

    public void setOriginalPreviewSnapshotListener(IOriginalPreviewSnapshotListener listener) {
        if (mRecordFilterGroup != null) {
            mRecordFilterGroup.setOriginalPreviewSnapshotListener(listener);
        }
    }

    public void takeOriginalPreviewSnapshot(final String path, final int width, final int height, final int type, final int quality, final boolean flipX) {
        if (mRecordFilterGroup != null) {
            mRecordFilterGroup.takeOriginalPreviewSnapshot( path,  width,  height,  type,  quality, flipX);
        }
    }

    public void takePreviewSnapshot(final String path, final int width, final int height, final int type, final int quality, final boolean flipX) {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                if (mPreviewFilter != null) {
                    mPreviewFilter.takePreviewSnapshot(path, width, height, type, quality, flipX);
                }
            }
        });
    }

    public void setPreviewRectOffset(int x_offset, int y_offset) {
        if (mPreviewFilter != null) {
            mPreviewFilter.setPreviewRectOffset(x_offset, y_offset);
        }
    }

    public void setPreviewFlipX() {
        if (mPreviewFilter != null) {
            mPreviewFilter.setPreviewFlipX();
        }
    }

    public void setCameraEventListener(ICameraEventListener listener) {
        mCameraEventListener = listener;
    }

    public void setPreviewCallbackListener(ICameraPreviewCallbackListener listener) {
        if (mVideoFilterContext == null) {
            YYLog.error(TAG, " setPreviewCallbackListener failed, mVideoFilterContext == null.");
            return;
        }
        STMobileFaceDetectionWrapper.getInstance(mVideoFilterContext.getAndroidContext()).setExternalPreviewCallback(listener);
    }
}
