package com.ycloud.gpuimagefilter.filter;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.orangefilter.OrangeFilter;
import com.sensetime.stmobile.utils.Accelerometer;
import com.venus.Venus;
import com.ycloud.api.common.FilterType;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.videorecord.IOriginalPreviewSnapshotListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.facedetection.VenusGestureDetectWrapper;
import com.ycloud.gpuimagefilter.utils.FilterConfig;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediarecord.MediaPlayerWrapper;
import com.ycloud.svplayer.surface.InputSurface;
import com.ycloud.svplayer.surface.PlayerGLManager;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.MediaSampleExtraInfo;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.util.ListIterator;

/**
 * Created by Administrator on 2017/6/17.
 */

public class RecordFilterGroup extends FilterGroup {
    private final String TAG = "RecordFilterGroup";

    private BaseFilter mTransformFilter;
    private Accelerometer mAcc;
    private Context mContext;

    private boolean mGestureDetectInited = false;

    private AbstractYYMediaFilter mEncodeOutput;
    private AbstractYYMediaFilter mPreviewOutput;

    private IFaceDetectionListener mFaceDetectionListener = null;
    private long noFaceCount = 0;
    private boolean mDetecOn = false;

    private InputSurface mPlayerInputSurface;
    private PlayerGLManager.SurfaceWrapper mPlayerInputSurfaceWrapper;
    private MediaPlayerWrapper mediaPlayerWrapper = null;
    private MediaFilterContext mVideoFilterContext = null;

    private int mSegmentTextureId = OpenGlUtils.NO_TEXTURE;

    //录制过程中播放webm动画的速度
    private float mCurrentRecordSpeed;

    public static final int GL_OPEN_VIDEO = 0x01;
    public static final int GL_START_VIDEO = 0x02;
    public static final int GL_PAUSE_VIDEO = 0x03;
    public static final int GL_STOP_VIDEO = 0x04;
    public static final int GL_SEEK_VIDEO = 0x05;
    public static final int GL_SET_VIDEO_SPEED = 0x06;
    public static final int GL_UPDATE_VIDEO_SURFACE   = 0x07;
    public static final int GL_VIDEO_AUTO_LOOP        = 0x08;

    public RecordFilterGroup(int sessionID, Looper looper) {
        super(sessionID, looper);
        mTransformFilter = new TransFormTextureFilter();
        ((TransFormTextureFilter) mTransformFilter).setmUsedForPlayer(false);
    }

    public void setMediaFilterContext(MediaFilterContext VideoFilterContext) {
        mVideoFilterContext = VideoFilterContext;
    }

    public void init(Context context, int outputWidth, int outputHeight, int deviceLevel) {
        if (null == context || outputHeight <= 0 || outputHeight <= 0) {
            YYLog.info(TAG, "init context=" + context + " outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
            return;
        }

        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;

        OpenGlUtils.checkGlError("init start");
        super.init();
        mContext = context;

        if (mTransformFilter != null) {
            mTransformFilter.init(mOutputWidth, mOutputHeight, true, mOFContext);
        }

        // 开启重力传感器监听
        mAcc = new Accelerometer(mContext.getApplicationContext());
        mAcc.start();

        // 初始化人脸肢体检测
        STMobileFaceDetectionWrapper.getInstance(mContext).resetFacePointInfo();

        mLayout.addPathInFilter(FilterLayout.kAllPathFlag, mTransformFilter);
        performLayout();

        initMediaPlayer();

        mCurrentRecordSpeed = 1.0f;

        mInited = true;
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    @Override
    public void destroy() {
        if (!mInited) {
            return;
        }
        mInited = false;

        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mTransformFilter != null) {
            mTransformFilter.destroy();
            mTransformFilter = null;
        }

        mAcc.stop();

        if (mGestureDetectInited) {
            mGestureDetectWrapper.deInit();
            mGestureDetectInited = false;
            mGestureDetectWrapper = null;
        }

        destroyOFContext();

        if (mediaPlayerWrapper != null) {
            mediaPlayerWrapper.release();
            mediaPlayerWrapper = null;
        }

        if (mPlayerInputSurface != null) {
            mPlayerInputSurface.release();
            mPlayerInputSurface = null;
            YYLog.info(TAG, "releaseInternal mPlayerInputSurface release");
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }


    public AbstractYYMediaFilter setFilterGroupOutPath(AbstractYYMediaFilter encodeOutput, AbstractYYMediaFilter previewOutput) {
        mEncodeOutput = encodeOutput;
        mPreviewOutput = previewOutput;

        mLayout.addPathOutFilter(FilterLayout.kEncodePathFlag, mEncodeOutput);
        mLayout.addPathOutFilter(FilterLayout.kPreviewPathFlag, mPreviewOutput);
        mLayout.performLayout(null);
        return this;
    }

    @Override
    public void performLayout() {
        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        mLayout.performSimpleTwoGraphLayout(res.mFilterList);
    }

    // 根据新的宽高重新初始化好filter
    private void updateFilterResource(int newWidth, int newHeight) {
        YYLog.info(TAG, "updateFilterResource newWidth=" + newWidth + " newHeight=" + newHeight
                + " mOutputWidth" + mOutputWidth + " mOutputHeight" + mOutputHeight);
        mOutputWidth = newWidth;
        mOutputHeight = newHeight;

        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        for (int i = 0; res.mFilterList != null && i < res.mFilterList.size(); i++) {
            res.mFilterList.get(i).changeSize(mOutputWidth, mOutputHeight);
        }
    }

    // 只有双色表filter要写入配置文件
    @Override
    public String marshall() {
        //编译各个filter, 得到对应的json信息.
        FilterConfig config = new FilterConfig();
        config.setMP4Name(m_mp4Name.get());

        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        if (res.mFilterList != null) {
            ListIterator<BaseFilter> it = res.mFilterList.listIterator();
            while (it.hasNext()) {
                BaseFilter filter = it.next();
                if (filter.getFilterInfo() != null && filter instanceof OFDoubleColorTableFilter) {
                    config.addFilterInfo(filter.getFilterInfo());
                }
            }
        }
        String ret = config.marshall();
        YYLog.info(this, "FilterGroup.marshall: " + (ret == null ? "null" : ret));
        return ret;
    }


    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (!mInited) {
            return false;
        }

        restoreOutputTexture();
        if (mUseFilterSelector) {
            mVideoFilterSelector.processMediaSample(sample, upstream);
        }

        //传递手机方向给orangeFilter，用于确定美腿，瘦身等拉伸方向
        int dir = Accelerometer.getDirection();
        if (dir == 0 || dir == 2) {
            OrangeFilter.setConfigInt(mOFContext, OrangeFilter.OF_ConfigKey_ScreenOrientation, 1);
        } else {
            OrangeFilter.setConfigInt(mOFContext, OrangeFilter.OF_ConfigKey_ScreenOrientation, 0);
        }

        //传递录制速度给orangeFilter
        if (mVideoFilterContext != null) {
            float playSpeed = mVideoFilterContext.getRecordConfig().getRecordSpeed();
            if (playSpeed != mCurrentRecordSpeed) {
                YYLog.info(TAG, "set play speed of animation:" + playSpeed);
                mCurrentRecordSpeed = playSpeed;
                OrangeFilter.setConfigFloat(mOFContext, OrangeFilter.OF_ConfigKey_Animation2DPlaySpeed, 1 / playSpeed);
            }
        }

        sample.mDisplayRotation = dir;
        int requiredFrameData = getRequiredFrameData(sample);

        //通过回调附加业务层传递的信息
        boolean needRhythm = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_AudioBeat) > 0;
        if (mMediaInfoRequireListener != null && needRhythm) {
            MediaSampleExtraInfo info = new MediaSampleExtraInfo();
            mMediaInfoRequireListener.onRequireMediaInfo(info);
            if (info != null) {
                sample.mAudioFrameData.beat = info.getRhythmQuality();
                sample.mAudioFrameData.loudness = info.getRhythmStrengthRatio();
                sample.mAudioFrameData.loudnessSmooth = info.getRhythmSmoothRatio();
                sample.mAudioFrameData.frequencyData = info.getRhythmFrequencyData();
            }
        }

        //人脸信息
        mNeedCheckFace = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_FaceLandmarker) > 0;
        if (STMobileFaceDetectionWrapper.getInstance(mContext).getIsCheckFace() != mNeedCheckFace) {
            STMobileFaceDetectionWrapper.getInstance(mContext).setIsCheckFace(mNeedCheckFace);
        }

        //肢体信息
        mNeedCheckBody = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_Body) > 0;
        if (STMobileFaceDetectionWrapper.getInstance(mContext).getEnableBodyDetect() != mNeedCheckBody) {
            STMobileFaceDetectionWrapper.getInstance(mContext).setEnableBodyDetect(mNeedCheckBody);
        }

        //手势信息
        mNeedCheckGesture = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_Gesture) > 0;

        //抠图信息
        mNeedSegment = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_BackgroundSegment) > 0;

        //avatar信息
        mNeedAvatar = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_Avatar) > 0;

        STMobileFaceDetectionWrapper.FacePointInfo faceAndBodyPointInfo = null;

        //填充播放器解码数据
        if(mNeedPlayerVideoData) {
            sample.mExtraTextureId = mPlayerInputSurface.getTextureId();
            mPlayerInputSurface.getTransformMatrix(sample.mExtraTextureTransform);
        }

        if (mTransformFilter instanceof TransFormTextureFilter) {
            ((TransFormTextureFilter) mTransformFilter).processMediaSample(sample, upstream, false);
        }

        //填充人脸和肢体信息
        if (mNeedCheckFace || mNeedCheckBody || (mNeedSegment && SDKCommonCfg.getUseCpuSegmentMode())) {
            STMobileFaceDetectionWrapper.getInstance(mContext).setDetectOn(true);
            faceAndBodyPointInfo = bodyInfoDetect(mContext, sample);
            boolean isDetectFace = isDetectFace(faceAndBodyPointInfo);
            if (mNeedCheckFace) {
                onFaceDetectCallback(isDetectFace);
            }
            STMobileFaceDetectionWrapper.getInstance(mContext).releaseFacePointInfo(faceAndBodyPointInfo);
            mDetecOn = true;
        } else {
            if (mDetecOn) {
                STMobileFaceDetectionWrapper.getInstance(mContext).setDetectOn(false);
            }
            mDetecOn = false;
        }

        //填充手势信息
        if (mNeedCheckGesture) {
            //初始化
            if (!mGestureDetectInited) {
                mGestureDetectWrapper = new VenusGestureDetectWrapper(mContext);
                mGestureDetectWrapper.init();
                mGestureDetectInited = true;
            }
            //GPU
            mGestureDetectWrapper.updateGestureData(sample, mOutputWidth, mOutputHeight);
        }

        //填充抠图信息
        if (mNeedSegment) {
            //初始化
            if (!mSegmentInited) {
                initSegment(mContext);
                mSegmentInited = true;
            }
            //GPU同步抠图
            if (!SDKCommonCfg.getUseCpuSegmentMode()) {
                if (mVenusSegmentWrapper != null) {
                    mVenusSegmentWrapper.updateSegmentDataWithCache(sample, null, null);
                }
            } else {
                //CPU异步抠图
                STMobileFaceDetectionWrapper.getInstance(mContext).setNeedCpuSegment(true);
                Venus.VN_ImageData vnImageData = STMobileFaceDetectionWrapper.getInstance(mContext).getVnImageData();
                mSegmentTextureId = OpenGlUtils.loadTexture(ByteBuffer.wrap(vnImageData.data),
                        vnImageData.width, vnImageData.height, GLES20.GL_LUMINANCE, mSegmentTextureId);
                sample.mSegmentFrameData.segmentTextureID = mSegmentTextureId;
                sample.mSegmentFrameData.segmentTextureTarget = GLES20.GL_TEXTURE_2D;
                sample.mSegmentFrameData.segmentTextureWidth = vnImageData.width;
                sample.mSegmentFrameData.segmentTextureHeight = vnImageData.height;
            }
        } else {
            if (SDKCommonCfg.getUseCpuSegmentMode()) {
                STMobileFaceDetectionWrapper.getInstance(mContext).setNeedCpuSegment(false);
            }
        }

        //填充avatar信息
        if (mNeedAvatar) {
            if (mFilterGroupAvatarId == -1) {
                mFilterGroupAvatarId = OrangeFilter.createAvatar(mOFContext, "", OrangeFilter.OF_AvatarMode_FaceBlendshape);
            }
            sample.mAvatarId = mFilterGroupAvatarId;
        } else {
            if (mFilterGroupAvatarId != -1) {
                OrangeFilter.destroyAvatar(mOFContext, mFilterGroupAvatarId);
                mFilterGroupAvatarId = -1;
            }
        }

        if (mOutputWidth != sample.mEncodeWidth || mOutputHeight != sample.mEncodeHeight) {
            updateFilterResource(sample.mEncodeWidth, sample.mEncodeHeight);
        }

        mTransformFilter.deliverToDownStream(sample);

        return true;
    }

    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        mFaceDetectionListener = listener;
    }

    public boolean isDetectFace(STMobileFaceDetectionWrapper.FacePointInfo facePointInfo){
        return mNeedCheckFace && !(facePointInfo == null || facePointInfo.mFaceCount <= 0);
    }

    public void onFaceDetectCallback(boolean isFaceDetect) {
        if (mFaceDetectionListener == null) {
            return;
        }
        FilterDataStore.OperResult<Integer, BaseFilter> effectRes = mFilterStore.getFilterInfoByType(FilterType.GPUFILTER_EFFECT, kFilterStoreID);
        if (effectRes.mFilterCopyOnWriteList != null && !effectRes.mFilterCopyOnWriteList.isEmpty()) {
            boolean flag = STMobileFaceDetectionWrapper.getInstance(mContext).isStMobileInitiated();
            if (flag) {
                if (isFaceDetect) {
                    mFaceDetectionListener.onFaceStatus(IFaceDetectionListener.HAS_FACE);
                } else {
                    noFaceCount++;
                    if (noFaceCount > 10) {       //避免使用动态加载商汤库方式之后，首次加载贴纸会出现闪烁的问题
                        mFaceDetectionListener.onFaceStatus(IFaceDetectionListener.NO_FACE);
                    }
                }
            } else {
                noFaceCount = 0;
            }
        } else {
            mFaceDetectionListener.onFaceStatus(IFaceDetectionListener.NO_MATTER);
        }
    }

    public void startListen() {
        if (!mStartListen.getAndSet(true)) {
            FilterCenter.getInstance().addFilterObserver(this, mLooper, mSessionID);
            mFilterHandler = new Handler(mLooper, null) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case GL_OPEN_VIDEO:
                            initMediaPlayer();
                            mediaPlayerWrapper.setVideoPath((String) msg.obj);

                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.setRenderMSGHandle(mFilterHandler);
                            }

                            break;
                        case GL_START_VIDEO:
                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.start();
                            }
                            break;
                        case GL_PAUSE_VIDEO:
                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.pause();
                            }
                            break;
                        case GL_STOP_VIDEO:
                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.stopPlayback();
                            }
                            break;
                        case GL_SET_VIDEO_SPEED:
                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.setPlaybackSpeed((float) msg.obj);
                            }
                            break;
                        case GL_SEEK_VIDEO:
                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.seekTo((int) msg.obj);
                            }
                            break;
                        case GL_UPDATE_VIDEO_SURFACE:
                            mPlayerInputSurface.updateTexImage();
                            if (!mNeedPlayerVideoData) {
                                ((TransFormTextureFilter) mTransformFilter).initVideoTexture(msg.arg1, msg.arg2);
                            }
                            mNeedPlayerVideoData = true;
                            break;
                        case GL_VIDEO_AUTO_LOOP:
                            if (mediaPlayerWrapper != null) {
                                mediaPlayerWrapper.setAutoLoop((Boolean) msg.obj);
                            }
                            break;
                        default:
                            //post into sync data task into looper.
                            FilterDataStore.OperResult<Integer, FilterInfo> res = FilterCenter.getInstance().getFilterSnapshot(mSessionID);
                            doFilterBatchAdd(res.mFilterList);
                            mFilterCenterSnapshotVer = res.mSnapshotVer;
                    }
                }
            };

            mFilterHandler.sendEmptyMessage(100);
        }
    }

    public void initMediaPlayer() {
        if (mPlayerInputSurface == null) {
            mPlayerInputSurface = new InputSurface();
            mPlayerInputSurface.setup();
            mPlayerInputSurfaceWrapper = new PlayerGLManager.SurfaceWrapper(mPlayerInputSurface.getSurface(),
                    mPlayerInputSurface.getSurfaceTexture(),
                    1, mPlayerInputSurface.getTextureId());
        }
        if (mediaPlayerWrapper == null) {
            mediaPlayerWrapper = new MediaPlayerWrapper(mContext);
            mediaPlayerWrapper.setMediaFilterContext(mVideoFilterContext);
            mediaPlayerWrapper.setInputSurface(mPlayerInputSurfaceWrapper);
        }
    }

    public void setAspectRatio(AspectRatioType aspect) {
        if (mTransformFilter != null) {
            ((TransFormTextureFilter)mTransformFilter).setAspectRatio(aspect);
        }
    }

    public void setOriginalPreviewSnapshotListener(IOriginalPreviewSnapshotListener listener) {
        if (mTransformFilter != null) {
            ((TransFormTextureFilter)mTransformFilter).setOriginalPreviewSnapshotListener(listener);
        }
    }

    public void takeOriginalPreviewSnapshot(String path, int width, int height, int type, int quality, boolean flipX){
        if (mTransformFilter != null) {
            ((TransFormTextureFilter)mTransformFilter).takeOriginalPreviewSnapshot(path, width, height, type, quality, flipX);
        }
    }
}
