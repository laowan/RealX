package com.ycloud.gpuimagefilter.filter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Looper;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.facedetection.VenusSegmentWrapper;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.gpuimagefilter.utils.FaceDetectWrapper;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.gpuimagefilter.utils.HumanBodyDetectWrapper;
import com.ycloud.gpuimagefilter.utils.SegmentCacheDetectWrapper;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.MediaSampleExtraInfo;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by liuchunyu on 2017/7/29.
 */

public class PlayerFilterGroup extends FilterGroup {
    private String TAG = "PlayerFilterGroup";

    private BaseFilter mTransformFilter;
    private BaseFilter mSquareFilter;
    private BaseFilter mRotateFilter;
    private int mRotateAngle = 0;
    private AtomicBoolean mNeedUpdataTextureResource = new AtomicBoolean(false);

    private Context mContext;

    private long mLastDetectTimeStamp = 0; //上一次进行肢体检测的数据的时间戳

    private boolean mFirstFrame = false;
    public PlayerFilterGroup(Context context, int sessionID, Looper looper) {
        super(sessionID, looper);
        mContext = context;
        mTransformFilter = new TransFormTextureFilter();
        ((TransFormTextureFilter) mTransformFilter).setmUsedForPlayer(true);
        mSquareFilter = new SquareFilter();
        ((SquareFilter) mSquareFilter).setmUseForPlayer();

        mRotateFilter = new RotateFilter();
    }

    public void init(int outputWidth, int outputHeight, boolean isExtTexutre, int videoViewW,
                     int videoViewH, boolean enableRotate, boolean clockwise, int color, Bitmap bitmap) {
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;

        OpenGlUtils.checkGlError("init start");
        super.init();

        //清空之前filterGroup中保存的人脸识别，肢体识别，抠图缓存等信息
        mHumanBodyDetectWrapper.bodiesDetectInfoList.clear();
        mFaceDetectWrapper.facesDetectInfoList.clear();
        mSegmentCacheDetectWrapper.segmentCacheDataList.clear();

        TimeEffectParameter.instance().clear();

        STMobileFaceDetectionWrapper.getInstance(mContext).resetFacePointInfo();

        if (mTransformFilter != null) {
            if (enableRotate) {
                ((TransFormTextureFilter) mTransformFilter).setEnableRotate(enableRotate);
            }
            mTransformFilter.init(mOutputWidth, mOutputHeight, isExtTexutre, mOFContext);
        }

        if (SDKCommonCfg.getRecordModePicture() && mRotateFilter != null) {
            mRotateFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
        }

        if (mSquareFilter != null) {
            if (!enableRotate) {
                mSquareFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
            } else {
                ((SquareFilter) mSquareFilter).setEnableRotate(enableRotate);  // video size in sps
                ((SquareFilter) mSquareFilter).setRotateDirection(clockwise);
                mSquareFilter.init(videoViewW, videoViewH, false, mOFContext);
                ((SquareFilter) mSquareFilter).setVideoSize(mOutputWidth, mOutputHeight);  // video size in sps
                ((SquareFilter) mSquareFilter).setBackGroundColor(color);
                ((SquareFilter) mSquareFilter).setBackGroundBitmap(bitmap);
            }
        }

        mLayout.addPathInFilter(FilterLayout.kAllPathFlag, mTransformFilter);
        mLayout.addPathOutFilter(FilterLayout.kPreviewPathFlag, mSquareFilter);
        mLayout.defaultLayout();
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);

        mFirstFrame = true;

        //packet中保存的肢体数据保存到bodiesDetectInfoList，将录制时候等人脸，肢体识别信息传递到编辑模块使用
        VideoDataManager.instance().getBodyDetectInfo(mHumanBodyDetectWrapper.bodiesDetectInfoList);

        VideoDataManager.instance().getFaceDetectInfo(mFaceDetectWrapper.facesDetectInfoList);
        mInited = true;
    }

    public void destroy() {
        if (!mInited) {
            return;
        }
        mInited = false;

        STMobileFaceDetectionWrapper.getInstance(mContext).resetFacePointInfo();

        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mTransformFilter != null) {
            mTransformFilter.destroy();
            mTransformFilter = null;
        }

        if (mRotateFilter != null) {
            mRotateFilter.destroy();
            mRotateFilter = null;
        }

        if (mSquareFilter != null) {
            mSquareFilter.destroy();
            mSquareFilter = null;
        }

        if (mContext != null) {
//            STMobileFaceDetectionWrapper.getInstance(mContext).deInit();
            mContext = null;
        }

        destroyOFContext();
        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    protected void afterFilterRemove(BaseFilter filter) {
        super.afterFilterRemove(filter);
        if (filter != null) {
            OpenGlUtils.checkGlError("removeFilter end");
        }
    }

    @Override
    protected void afterFilterModify(BaseFilter filter) {
        super.afterFilterModify(filter);
    }

    private void doHumanActionDetect(YYMediaSample sample) {
        byte[] pixBuf = getVideoImageData(sample);

        //如果即将送入检测的时间戳与之前检测的时间戳差别较大，需要清除识别数据的缓存
        if (Math.abs(sample.mTimestampMs - mLastDetectTimeStamp) > 1000) {
            STMobileFaceDetectionWrapper.getInstance(mContext).resetFacePointInfo();
        }
        mLastDetectTimeStamp = sample.mTimestampMs;

        boolean flag = STMobileFaceDetectionWrapper.getInstance(mContext).onVideoFrame(pixBuf, 0, sample.mWidth, sample.mHeight, mFirstFrame);

        //true表示人脸数据已经成功送stmobile检测，false表示数据异常，在检测前返回
        if (flag) {
            mFirstFrame = false;
        }
    }

    // 根据新的宽高重新初始化好filter
    private void updateFilterResource(int newWidth, int newHeight) {
        YYLog.info(TAG, "updateFilterResource newWidth=" + newWidth + " newHeight=" + newHeight
                + " mOutputWidth" + mOutputWidth + " mOutputHeight" + mOutputHeight);
        //如果width或者height有变化，重新设置filterGroup的width和height(否则旋转后在filterGroup中新建filter会异常）,
        //并且创建filterGroup中的texture,fbo以及frameBufferTexture
        if ((newWidth != mOutputWidth || newHeight != mOutputHeight) && mTextures != null) {
            mOutputWidth = newWidth;
            mOutputHeight = newHeight;
            for (int i = 0; i < mTextures.length; i++) {
                OpenGlUtils.deleteTexture(mTextures[i]);
                mTextures[i] = OpenGlUtils.createTexture(newWidth, newHeight);
                mOriginTextures[i] = mTextures[i];
            }
            if (mFrameBufferTexture != null && mFrameBuffer != null) {
                OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
                OpenGlUtils.createFrameBuffer(newWidth, newHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);
            }

        }

        //如果width或者height有变化，重新设置filterList中每个filter的宽高，以及texture
        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        for (int i = 0; res.mFilterList != null && i < res.mFilterList.size(); i++) {
            if (res.mFilterList.get(i).getFrameBufferReuse()) {
                res.mFilterList.get(i).setOutputTextures(mTextures);
                res.mFilterList.get(i).setCacheFBO(mFrameBuffer, mFrameBufferTexture);
            }
            res.mFilterList.get(i).changeSize(newWidth, newHeight);
        }
    }

    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (!mInited) {
            return false;
        }

        restoreOutputTexture();

        if (mUseFilterSelector) {
            mVideoFilterSelector.processMediaSample(sample, upstream);
        }

        //如果上层设置了sample处理的监听，则process每一帧前要通过回调通知上层，供上层对当前帧填充附加信息，或者做额外处理
        MediaSampleExtraInfo info = new MediaSampleExtraInfo();
        if (mMediaInfoRequireListener != null) {
            mMediaInfoRequireListener.onRequireMediaInfo(info, sample.mTimestampMs);
        }

        int requiredFrameData = getRequiredFrameData(sample);
        boolean needRhythm = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_AudioBeat) > 0;
        //通过回调附加业务层传递的信息
        if (needRhythm && info != null) {
            sample.mAudioFrameData.beat = info.getRhythmQuality();
            sample.mAudioFrameData.loudness = info.getRhythmStrengthRatio();
            sample.mAudioFrameData.loudnessSmooth = info.getRhythmSmoothRatio();
            sample.mAudioFrameData.frequencyData = info.getRhythmFrequencyData();
        }

        //附加肢体信息
        mNeedCheckBody = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_Body) > 0;
        if (STMobileFaceDetectionWrapper.getInstance(mContext).getEnableBodyDetect() != mNeedCheckBody) {
            STMobileFaceDetectionWrapper.getInstance(mContext).setEnableBodyDetect(mNeedCheckBody);
        }

        //附加人脸信息
        mNeedCheckFace = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_FaceLandmarker) > 0;
        if (STMobileFaceDetectionWrapper.getInstance(mContext).getIsCheckFace() != mNeedCheckFace) {
            STMobileFaceDetectionWrapper.getInstance(mContext).setIsCheckFace(mNeedCheckFace);
        }

        //抠图信息
        mNeedSegment = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_BackgroundSegment) > 0;

        if (mTransformFilter instanceof TransFormTextureFilter) {

            if (SDKCommonCfg.getRecordModePicture() && (mRotateAngle == 180)) {                   // for YOYI rotate 180
                ((TransFormTextureFilter) mTransformFilter).setRotateAngle(mRotateAngle);
            } else {
                ((TransFormTextureFilter) mTransformFilter).setRotateAngle(0);                    // reset
            }

            ((TransFormTextureFilter) mTransformFilter).processMediaSample(sample, upstream, false);
        }

        // 添加特效前水平旋转，垂直旋转放在transform里面做
        if (SDKCommonCfg.getRecordModePicture()) {  // for YOYI rotate 90 270
            if (mNeedUpdataTextureResource.get()) {
                updateFilterResource(mOutputHeight, mOutputWidth);
                mNeedUpdataTextureResource.set(false);
            }
            if  (mRotateAngle == 90 || mRotateAngle == 270) {
                ((RotateFilter) mRotateFilter).setRotateAngle(mRotateAngle);
                mRotateFilter.processMediaSample(sample, upstream);
            }
        }

        //填充抠图信息
        if (mNeedSegment) {
            if (!mSegmentInited) {
                mVenusSegmentWrapper = new VenusSegmentWrapper(mContext, mOutputWidth, mOutputHeight);
                if (!SDKCommonCfg.getUseCpuSegmentMode()) {
                    mVenusSegmentWrapper.init();
                } else {
                    mVenusSegmentWrapper.initWithCpu();
                }
                mSegmentInited = true;
            }

            //读取抠图缓存到data，并且还原抠图数据
            if (mVenusSegmentWrapper != null) {
                if (!SDKCommonCfg.getUseCpuSegmentMode()) {
                    SegmentCacheDetectWrapper.SegmentCacheDetectRes detectRes = segmentCacheSearchAndDataRestore(sample);
                    if (!detectRes.isFound) {
                        segmentDataDetectAndCacheSave(sample);
                    }
                } else {
                    SegmentCacheDetectWrapper.SegmentCacheDetectRes detectRes = segmentCacheSearchAndDataRestoreCpu(sample, 10);
                    if (!detectRes.isFound) {
                        byte[] imageData = getVideoImageData(sample);
                        segmentDataDetectAndCacheSaveCpu(sample, imageData, detectRes.pos);
                    }
                }
            }
        }

        Boolean HaveSendVideoFrameToSTMobile = false;
        STMobileFaceDetectionWrapper.FacePointInfo humanBodyPointInfo = null;
        if (mNeedCheckBody) {
            HumanBodyDetectWrapper.HumanBodyDetectRes detectRes = bodyInfoSearch(sample);
            //没有从body data list里面查找到对应pts的肢体识别数据，需要在编辑页用播放器数据重新识别
            if (!detectRes.isFound) {
                doHumanActionDetect(sample);
                humanBodyPointInfo = bodyInfoDetectAndSave(mContext, sample, detectRes.pos);
                if (humanBodyPointInfo != null) {
                    HaveSendVideoFrameToSTMobile = true;
                }
            }
        }

        if (mNeedCheckFace) {
            FaceDetectWrapper.FaceDetectRes detectRes = faceInfoSearch(sample);
            if (!detectRes.isFound) {

                if (!HaveSendVideoFrameToSTMobile) {  // 已经在肢体识别中做过识别了，就不用再次识别人脸了
                    doHumanActionDetect(sample);
                }

                humanBodyPointInfo = faceInfoDetectAndSave(mContext, sample, detectRes.pos);
            }
        }

        sample.mRgbaBytes = null;
        sample.mShouldUpsideDown = true;
        sample.mHasPrepareFrameData = false;

        mTransformFilter.deliverToDownStream(sample);

        if (mNeedCheckBody || mNeedCheckFace) {
            STMobileFaceDetectionWrapper.getInstance(mContext).releaseFacePointInfo(humanBodyPointInfo);
        }

        return true;
    }

    public void StartRotate() {
        if (mSquareFilter instanceof SquareFilter) {
            ((SquareFilter) mSquareFilter).StartRotate();
        }
    }

    public void setFlutterRotateAngel(int angle) {
        YYLog.info(TAG, "setFlutterRotateAngel :" + angle);
        if (angle == 0 || angle == 90 || angle == 180 || angle == 270) {
            mRotateAngle = angle;
            mNeedUpdataTextureResource.set(true);
        } else {
            YYLog.warn(TAG, "setFlutterRotateAngel parameter : " + angle + " invalid. ");
        }
    }

    public float getCurrentRotateAngle() {
        if (mSquareFilter instanceof SquareFilter) {
            return ((SquareFilter) mSquareFilter).getCurrentRotateAngle();
        }
        return 0;
    }

    public RectF getCurrentVideoRect() {
        if (mSquareFilter instanceof SquareFilter) {
            return ((SquareFilter) mSquareFilter).getCurrentVideoRect();
        }
        return null;
    }
    public void setViewPortSize(int w, int h) {
        if (mSquareFilter instanceof SquareFilter) {
            ((SquareFilter) mSquareFilter).setViewPortSize(w, h);
        }
    }

    public void setLayoutMode(int mode) {
        if (mSquareFilter instanceof SquareFilter) {
            ((SquareFilter) mSquareFilter).setLayoutMode(mode);
        }
    }

    public void setVideoRotate(int rotateAngle) {
        if (mSquareFilter instanceof SquareFilter) {
            ((SquareFilter) mSquareFilter).setVideoRotate(rotateAngle);
        }
    }

    public void setLastVideoRotate(int rotateAngle) {
        if (mSquareFilter instanceof SquareFilter) {
            ((SquareFilter) mSquareFilter).setLastVideoRotate(rotateAngle);
        }
    }

}
