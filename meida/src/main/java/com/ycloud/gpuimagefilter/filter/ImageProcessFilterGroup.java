package com.ycloud.gpuimagefilter.filter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.os.Looper;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.FilterType;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.common.GlobalConfig;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.facedetection.VenusSegmentWrapper;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.jpeg.YYJpeg;
import com.ycloud.jpeg.YYJpegFactory;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Created by liuchunyu on 2017/8/28.
 */

public class ImageProcessFilterGroup extends FilterGroup {
    private String TAG = "ImageProcessFilterGroup";

    private BaseFilter mTransformFilter;
    private BaseFilter mSquareFilter;
    private BaseFilter mImageViewFilter;

    private Context mContext;
    private int mImageTexture = OpenGlUtils.NO_TEXTURE;
    private YYMediaSample mSample;
    final protected int FRAMEBUFFER_NUM = 1;
    protected int[] mFrameBuffer;
    protected int[] mFrameBufferTexture;
    protected IntBuffer mOldFramebuffer;

    private ByteBuffer mBuffer;
    private Bitmap mBitmap;
    private String mImagePath = null;
    private int mHash = 0;
    private Bitmap mImageBitmap = null;
    private int mBitmapWidth = 0;
    private int mBitmapHeight = 0;
    private IFaceDetectionListener mFaceDetectionListener = null;
    private boolean mGestureDetectInited = false;
    private ImageProcessListener mImageProcessListener = null;
    private boolean mViewMode = false;


    public ImageProcessFilterGroup(Context context, int sessionID, Looper looper, boolean viewMode) {
        super(sessionID, looper);
        mViewMode = viewMode;
        mContext = context;
        mTransformFilter = new TransFormTextureFilter();
        if (SDKCommonCfg.getRecordModePicture() && mViewMode) {
            ((TransFormTextureFilter) mTransformFilter).setmUsedForPlayer(true);
        } else {
            ((TransFormTextureFilter) mTransformFilter).setmUsedForPlayer(false);
        }

        mSquareFilter = new SquareFilter();
        if (SDKCommonCfg.getRecordModePicture()) {
            if (mViewMode) {
                ((SquareFilter) mSquareFilter).setmUseForPlayer();
            } else {   // 非view模式，生成图片回调给业务端
                mImageViewFilter = new ImageViewFilter();
            }
        }
        mSample = new YYMediaSample();
    }

    public void setFaceDetectionListener(IFaceDetectionListener listener) {
        mFaceDetectionListener = listener;
    }

    public void setImageProcessListener(ImageProcessListener listener) {
        mImageProcessListener = listener;
    }

    public void init(int outputWidth, int outputHeight) {
        if (outputWidth <= 0 || outputHeight <= 0) {
            YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight + " is not legal");
            return;
        }
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;

        OpenGlUtils.checkGlError("init start");
        super.init();

        if (mTransformFilter != null) {
            if (SDKCommonCfg.getRecordModePicture() && mViewMode) {
                ((TransFormTextureFilter) mTransformFilter).setEnableRotate(true);
            }
            mTransformFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
        }

        if (mSquareFilter != null) {
            if (SDKCommonCfg.getRecordModePicture()) {
                if (mViewMode) {
                    ((SquareFilter) mSquareFilter).setEnableRotate(true);
                    mSquareFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
                }
            } else {
                mSquareFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
            }
        }

        if (mImageViewFilter != null && SDKCommonCfg.getRecordModePicture() && !mViewMode) {
            ((ImageViewFilter)mImageViewFilter).setImageProcessListener(mImageProcessListener);
            mImageViewFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);
        }

        mFrameBuffer = new int[FRAMEBUFFER_NUM];
        mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
        OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);

        mOldFramebuffer = IntBuffer.allocate(1);

        if (!SDKCommonCfg.getRecordModePicture()) {  // save memory usage
            mBuffer = ByteBuffer.allocate(mOutputWidth * mOutputHeight * 4);
            mBuffer.order(ByteOrder.nativeOrder());
            mBitmap = Bitmap.createBitmap(mOutputWidth, mOutputHeight, Bitmap.Config.ARGB_8888);
        }

        mLayout.addPathInFilter(FilterLayout.kAllPathFlag, mTransformFilter);
        if (SDKCommonCfg.getRecordModePicture() && !mViewMode) {
            mLayout.addPathOutFilter(FilterLayout.kPreviewPathFlag, mImageViewFilter);
        } else {
            mLayout.addPathOutFilter(FilterLayout.kPreviewPathFlag, mSquareFilter);
        }
        mLayout.defaultLayout();

        mInited = true;
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    public void destroy() {
        if (false == mInited) {
            YYLog.info(TAG, "destroy mIsInit is false");
            return;
        }

        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        mContext = null;

        if (mTransformFilter != null) {
            mTransformFilter.destroy();
            mTransformFilter = null;
        }

        if (mSquareFilter != null) {
            mSquareFilter.destroy();
            mSquareFilter = null;
        }

        if (mImageViewFilter != null) {
            mImageViewFilter.destroy();
            mImageViewFilter = null;
        }

        if (mImageTexture != OpenGlUtils.NO_TEXTURE) {
            OpenGlUtils.deleteTexture(mImageTexture);
            mImageTexture = OpenGlUtils.NO_TEXTURE;
        }

        if (mFrameBufferTexture != null && mFrameBuffer != null) {
            OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
            mFrameBufferTexture = null;
            mFrameBuffer = null;
        }

        if (mOldFramebuffer != null) {
            mOldFramebuffer.clear();
            mOldFramebuffer = null;
        }

        destroyOFContext();

        mInited = false;
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

    public boolean isDetectFace(STMobileFaceDetectionWrapper.FacePointInfo facePointInfo){
        return mNeedCheckFace && !(facePointInfo == null || facePointInfo.mFaceCount <= 0);
    }

    public void onFaceDetectCallback(boolean isFaceDetect) {
        if (mFaceDetectionListener == null) {
            return;
        }
        FilterDataStore.OperResult<Integer, BaseFilter> effectRes = mFilterStore.getFilterInfoByType(FilterType.GPUFILTER_EFFECT, kFilterStoreID);
        if (effectRes.mFilterCopyOnWriteList != null && !effectRes.mFilterCopyOnWriteList.isEmpty()) {
            boolean flag = STMobileFaceDetectionWrapper.getPictureInstance(mContext).isStMobileInitiated();
            if (flag && isFaceDetect) {
                mFaceDetectionListener.onFaceStatus(IFaceDetectionListener.HAS_FACE);
            }
        } else {
            mFaceDetectionListener.onFaceStatus(IFaceDetectionListener.NO_MATTER);
        }
    }

    private STMobileFaceDetectionWrapper.FacePointInfo bodyInfoDetectPicture(Context context, YYMediaSample sample) {
        STMobileFaceDetectionWrapper.FacePointInfo humanBodyPointInfo = STMobileFaceDetectionWrapper.getPictureInstance(context).getCurrentFacePointInfo();
        if (mNeedCheckBody) {
            sample.mBodyFrameDataArr = new OrangeFilter.OF_BodyFrameData[0];
        }
        if (mNeedCheckFace) {
            sample.mFaceFrameDataArr = new OrangeFilter.OF_FaceFrameData[0];
        }

        if (humanBodyPointInfo != null && humanBodyPointInfo.mFrameData != null) {
            if (humanBodyPointInfo.mBodyCount > 0) {
                sample.mBodyFrameDataArr = humanBodyPointInfo.mFrameData.bodyFrameDataArr;
            }
            if (humanBodyPointInfo.mFaceCount > 0) {
                sample.mFaceFrameDataArr = humanBodyPointInfo.mFrameData.faceFrameDataArr;
            }
        }

        return humanBodyPointInfo;
    }

    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        sample.mShouldUpsideDown = false;
        if (SDKCommonCfg.getRecordModePicture()) {
            restoreOutputTexture();

            if (mUseFilterSelector) {
                mVideoFilterSelector.processMediaSample(sample, upstream);
            }

            int requiredFrameData = getRequiredFrameData(sample);

            //人脸信息
            boolean needCheckFaceBefore = mNeedCheckFace;
            mNeedCheckFace = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_FaceLandmarker) > 0;
            if (needCheckFaceBefore != mNeedCheckFace) {
                STMobileFaceDetectionWrapper.getPictureInstance(mContext).setIsCheckFace(mNeedCheckFace);
            }

            //肢体信息
            mNeedCheckBody = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_Body) > 0;
            if (STMobileFaceDetectionWrapper.getPictureInstance(mContext).getEnableBodyDetect() != mNeedCheckBody) {
                STMobileFaceDetectionWrapper.getPictureInstance(mContext).setEnableBodyDetect(mNeedCheckBody);
            }

            //手势信息
            mNeedCheckGesture = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_Gesture) > 0;

            //抠图信息
            boolean needSegmentBefore = mNeedSegment;
            mNeedSegment = (requiredFrameData & OrangeFilter.OF_RequiredFrameData_BackgroundSegment) > 0;
            if (needSegmentBefore != mNeedSegment) {
                if (mNeedSegment) {
                    OrangeFilter.setConfigInt(mOFContext, OrangeFilter.OF_ConfigKey_DeviceLevel, 2);
                } else {
                    OrangeFilter.setConfigInt(mOFContext, OrangeFilter.OF_ConfigKey_DeviceLevel, 0);
                }
            }

            STMobileFaceDetectionWrapper.FacePointInfo faceAndBodyPointInfo = null;

            //填充人脸和肢体信息
            if(mNeedCheckFace || mNeedCheckBody) {
//                STMobileFaceDetectionWrapper.getInstance(mContext).setDetectOn(false);  // close preview detect.
//                STMobileFaceDetectionWrapper.enableSTMobilePlayerMode();
                if (sample.mFaceFrameDataArr == null) { // 一张图片已经识别过人脸就不再重复识别
                    int tryCount = 1;                   // 商汤图片模型识别一次就够了，一次识别不了，多次也是白搭
                    boolean isDetectFace = false;
                    while (tryCount > 0) {
                        STMobileFaceDetectionWrapper.getPictureInstance(mContext).setIsCheckFace(true);
                        STMobileFaceDetectionWrapper.getPictureInstance(mContext).onVideoFrameEx(sample.mRgbaBytes, sample.mWidth, sample.mHeight, true,true);
                        faceAndBodyPointInfo = bodyInfoDetectPicture(mContext, sample);
                        isDetectFace = isDetectFace(faceAndBodyPointInfo);
                        tryCount--;
                        if (isDetectFace) {
                            break;
                        }
                        STMobileFaceDetectionWrapper.getPictureInstance(mContext).releaseFacePointInfo(faceAndBodyPointInfo);
                    }
                    YYLog.info(TAG, "isDetectFace : " + isDetectFace);
                    if (mNeedCheckFace) {
                        onFaceDetectCallback(isDetectFace);
                    }
                    STMobileFaceDetectionWrapper.getPictureInstance(mContext).releaseFacePointInfo(faceAndBodyPointInfo);
                } else {
                    YYLog.info(TAG, "Human action detecting have done before.");
                }
//                STMobileFaceDetectionWrapper.enableSTMobileCameraMode();
//                STMobileFaceDetectionWrapper.getInstance(mContext).setDetectOn(true);	// open preview detect.
            }

            //填充抠图信息
            if (mNeedSegment) {
                if (!mSegmentInited) {
                    mVenusSegmentWrapper = new VenusSegmentWrapper(mContext, mOutputWidth, mOutputHeight);
                    mVenusSegmentWrapper.init();
                    mSegmentInited = true;
                }

                if (mVenusSegmentWrapper != null) {
                    mVenusSegmentWrapper.updateSegmentDataWithCache(sample, null, null);
                }
            }
        } else {
            sample.mRgbaBytes = null;
        }
        mTransformFilter.processMediaSample(sample, upstream);
//        mTransformFilter.deliverToDownStream(sample);
        return true;
    }

    public void processImage(String path, int hash, boolean preMultiplyAlpha) {
        if (!mInited) {
            return;
        }
        YYJpeg mYYJpeg = null;
        boolean useYYJpeg = false;
        long time = System.currentTimeMillis();
        // 图片改变才解码与更新纹理数据
        if (!path.equalsIgnoreCase(mImagePath) || hash != mHash) {
            mImagePath = path;
            mHash = hash;
            if (path.endsWith("jpg") || path.endsWith("jpeg") ||
                    path.endsWith("JPG") || path.endsWith("JPEG") ) {
                useYYJpeg = true;
            }

            if (useYYJpeg) {
                mYYJpeg = YYJpegFactory.decodeFile(path);
                if (mYYJpeg == null) {
                    YYLog.error(TAG, "processImages YYJpeg decodeFile :" + path + " failed.");
                    return;
                }
            } else {
                mImageBitmap = BitmapFactory.decodeFile(path);
                if (mImageBitmap == null) {
                    YYLog.error(TAG, "processImages decodeFile :" + path + " failed.");
                    return;
                }
            }

            mSample.reset();

            if (useYYJpeg) {
                mBitmapWidth = mYYJpeg.getWidth();
                mBitmapHeight = mYYJpeg.getHeight();
            } else {
                mBitmapWidth = mImageBitmap.getWidth();
                mBitmapHeight = mImageBitmap.getHeight();
            }

            YYLog.info(TAG, "decode " + path + " success. hash " + mHash + "  useYYJpeg " + useYYJpeg);
            System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, mSample.mTransform, 0, mSample.mTransform.length);

            if (useYYJpeg) {
                mBuffer = ByteBuffer.allocate(mBitmapWidth * mBitmapHeight * 4);
                mYYJpeg.copyPixelsToBuffer(mBuffer);
                mImageTexture = OpenGlUtils.loadTexture(mBuffer, mBitmapWidth, mBitmapHeight, GLES20.GL_RGBA, mImageTexture);
                if (mYYJpeg != null) {
                    mYYJpeg.recycle();
                }
            } else {
                mImageTexture = OpenGlUtils.loadTexture(mImageBitmap, mImageTexture, false);
                mBuffer = ByteBuffer.allocate(mBitmapWidth * mBitmapHeight * 4);
                mImageBitmap.copyPixelsToBuffer(mBuffer);
                if (mImageBitmap != null && !mImageBitmap.isRecycled()) {
                    mImageBitmap.recycle();
                    mImageBitmap = null;
                }
            }

            if (mImageViewFilter != null) {
                ((ImageViewFilter)mImageViewFilter).setImagePath(mImagePath);
            }
        }
        // 图片文件不改变的情况下会改变hash值，要回调不同的值给业务层做区分。
        if (mImageViewFilter != null) {
            ((ImageViewFilter)mImageViewFilter).setImageHash(hash);
        }
        mSample.mRgbaBytes = mBuffer.array();   // for real time face detection
        mSample.mWidth = mBitmapWidth;
        mSample.mHeight = mBitmapHeight;
        mSample.mTextureId = mImageTexture;
        mSample.mPreMultiplyAlpha = preMultiplyAlpha;
        if (mViewMode && mSquareFilter != null) {
            ((SquareFilter) mSquareFilter).setVideoSize(mSample.mWidth, mSample.mHeight);
        }
        processMediaSample(mSample, this);
        YYLog.info(TAG, "processImage " + path + " Finish! cost " + (System.currentTimeMillis() - time));
    }

    public void processImages(String imageBasePath, int imageRate) {
        if (false == mInited) {
            YYLog.info(TAG, "processImages mIsInit is false");
            return;
        }

        String imgType = imageBasePath.substring(imageBasePath.indexOf(".") + 1);
        String basePath = imageBasePath.substring(0, imageBasePath.indexOf("%"));
        int timeStep = 1000 / imageRate;
        YYLog.info(TAG, "imgType=" + imgType + " basePath=" + basePath + " timeStep=" + timeStep);

        File file = new File(basePath);
        String[] fileList = file.list();
        for (int i = 0; i < fileList.length; i++) {
            String imagePath = basePath + (i + 1) + "." + imgType;

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                YYLog.error(TAG, "processImages imagePath not exist:" + imagePath);
                continue;
            }

            mSample.mWidth = bitmap.getWidth();
            mSample.mHeight = bitmap.getHeight();
            System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, mSample.mTransform, 0, mSample.mTransform.length);
            mImageTexture = OpenGlUtils.loadTexture(bitmap, mImageTexture, true);
            mSample.mTextureId = mImageTexture;
            mSample.mTimestampMs = i * timeStep;

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
            processMediaSample(mSample, this);
            mBuffer.clear();
            mBuffer.order(ByteOrder.LITTLE_ENDIAN);
            GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mBuffer);
            saveToFile(mBuffer, imagePath);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
    }

    private void saveToFile(ByteBuffer buffer, String imagePath) {
        mBitmap.copyPixelsFromBuffer(buffer);
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(imagePath);
        } catch (FileNotFoundException e) {
            YYLog.e(TAG, "saveToFile " + imagePath + "not found:" + e.toString());
        }
        if (out == null) {
            return;
        }
        mBitmap.compress(Bitmap.CompressFormat.JPEG, GlobalConfig.getInstance().getRecordConstant().SNAPSHOT_QUALITY, out);
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            YYLog.e(TAG, "save to file failed: IOException happened:" + e.toString());
        } finally {
            out = null;
        }
    }
}
