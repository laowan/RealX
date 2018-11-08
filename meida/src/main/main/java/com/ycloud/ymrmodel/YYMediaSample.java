package com.ycloud.ymrmodel;

import android.media.MediaFormat;

import com.orangefilter.OrangeFilter;
import com.venus.Venus;
import com.ycloud.api.common.SampleType;
import com.ycloud.camera.utils.YMRCameraMgr;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class YYMediaSample extends YYMediaSampleBase
{
    public static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    public static final float TEXTURE_BUFFER[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };


    public int  mSampleId = -1;
    //
    /**
     * 实际的图片信息的长，宽
     */
    public int mWidth = 0;
    public int mHeight = 0;

    //16字节对齐后的strideWidth
    public int mPlanWidth = 0;

    //16字节对齐后的strideHeight
    public int mPlanHeight = 0;

    /**
     * 经过裁剪后，需要输出的图片长宽
     */
    public int mClipWidth = 0;
    public int mClipHeight = 0;

    /**
     * 来自android系统的camera采集系统打的时间戳,
     * YY传输系统的音视频同步 "不是" 基于此时间戳.
     */
    public long mAndoridPtsNanos = 0;

    /**
     * camera采集系统回调onFrameAvailable函数后, YY媒体框架会获取系统时间戳
     * YY传输系统的音视频同步是基于此时间戳, 单位是毫秒. .
     */
    public long mYYPtsMillions = 0;

    /**
     * 编码时间戳, 单位毫秒
     */
    public long mDtsMillions = 0;

    /**
     * 标识当前sample是否最终要被送到编码器进行编码
     */
    public boolean mDeliverToEncoder = false;

    public boolean mDeliverToPreview = true;

    /*** 是否可用来做截图使用, 因为一些特效有输出2个texture，一个需要用来截图， 一个专门用来预览，不能截图*/
    public boolean mDeliverToSnapshot = true;

    /** for YOYI effect width alpha channel */
    public boolean mPreMultiplyAlpha = false;

    /**
     * Capture information
     */
    public int mImageFormat;
    public boolean mCameraFacingFront = false;

    public boolean  mEndOfStream = false;

    /**
     * 摄像头在何种displayRotation下设置的setDisplayOrientation的.
     */
    public int mDisplayRotation;
    public boolean mVideoStabilization;
    public float[] mTransform = new float[16];
    /**
     * 决定当前帧是否需要翻转(目前ffmpeg解码出来是颠倒的,orangefilter也需要颠倒的.硬解码是正向的)
     */
    public boolean mShouldUpsideDown = true;

    //[Notice!!! 拷贝赋值，不要直接reference赋值.
    public FloatBuffer mGLCubeBuffer = (FloatBuffer) (ByteBuffer.allocateDirect(CUBE.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()).put(CUBE).position(0);

    public FloatBuffer mGLTextureBuffer = (FloatBuffer) ByteBuffer.allocateDirect(TEXTURE_BUFFER.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEXTURE_BUFFER).position(0);

    public YMRCameraMgr.CameraResolutionMode mResMode;
    /**
     *
     */

    public boolean mCameraCapture = false;

    /**
     * Codec Information
     */
    public MediaFormat mMediaFormat = null;
    public int mColorFormat;
    /*
     * Buffer flags associated with the buffer.  A combination of
     * {@link #BUFFER_FLAG_KEY_FRAME} and {@link #BUFFER_FLAG_END_OF_STREAM}.
     *
     * <p>Encoded buffers that are key frames are marked with
     * {@link #BUFFER_FLAG_KEY_FRAME}.
     */
    public int mFrameFlag = 0;
    public int mFrameType = VideoConstant.VideoFrameType.kVideoUnknowFrame;


    /**
     * 为true则表示数据在texutre中， false则数据在byteBuffer中.
     */
    public boolean mTextureValid = true;

    /**
     * Video Data
     */
    public ByteBuffer mDataByteBuffer = null;
    public int mBufferOffset = 0;
    public int mBufferSize = 0;
    public int mBufferFlag = 0; //用于传递mediacodec中的buffinfo中的flag

    /**
     * 有时视频数据的传递需要直接用byte数组.
     */
    public byte[] mDataBytes = null;

    //摄像头采集的图片纹理ID.
    public int mTextureId = -1;

    //其他由外部生成的纹理ID，比如说video player解码生成的纹理，作为当前帧渲染的附加信息
    public int mExtraTextureId = OpenGlUtils.NO_TEXTURE;
    public float[] mExtraTextureTransform = new float[16];

    public int mFrameBufferId = -1;

    //TODO:add extra textureIds
    /**
     * 取值EGL中的GLES20.GL_TEXTURE_EXTERNAL_OES(外部纹理，可以跨线程使用，
     * 例如摄像头采集一般用外部纹理), GLES20.GL_TEXTURE_2D(内部纹理，不能跨线程使用，
     * 只能在创建线程中使用), 两者使用的shader不一样.
     */
    public int mTextureTarget;
    public long mTextureCreatedThreadId = -1;

    //texture纹理被人为的垂直方向flip了， 因为软编码的时候，需要从gpu中把纹理图像读取处理，
    //而android的坐标系统与纹理的坐标系统的Y轴是反.
    //人为反过后，已经更新了mTransform域， 这个标志位只是用于在获取截屏，软编码读数据时候判断.
    public boolean mTextureFlipVertical = false;

    //图片在编码处理中的编码分辨率大小.
    public int mEncodeWidth = 0;
    public int mEncodeHeight = 0;
    public int mCodecId = 0;

    //默认h264的硬编码.
    public VideoEncoderType mEncoderType = VideoEncoderType.HARD_ENCODER_H264;

    /**
     * extentend information
     */
    public HashMap<String, String> mExtInfo = null;

    /**
     * debug the memory leak
     */
    public String mStackTraceInfo = null;
    public boolean mBAllocFromPool = false;

    // filter参数
    public long mTimestampMs = 0L; // 当前帧的时间戳
    public int mTextureId1 = OpenGlUtils.NO_TEXTURE; // 第二个texture，不作为输出
    public float[] mTransform1 = new float[16];


    /*当前sample使用的filter列表*/
    public List<Integer> mApplyFilterIDs = new ArrayList<>();

    /**
     * 保存当前帧的rgba数据,如果存在,则后续处理的filter就不需要再去取
     */
    public byte[] mRgbaBytes = null;

    public boolean mHasPrepareFrameData = false;

    public OrangeFilter.OF_AudioFrameData mAudioFrameData = new OrangeFilter.OF_AudioFrameData();
    public OrangeFilter.OF_FaceFrameData[] mFaceFrameDataArr;
    public OrangeFilter.OF_BodyFrameData[] mBodyFrameDataArr;
    public Venus.VN_GestureFrameDataArr mGestureFrameDataArr;
    public OrangeFilter.OF_SegmentFrameData mSegmentFrameData = new OrangeFilter.OF_SegmentFrameData();
    public int mAvatarId = -1;

    /**
     * 这里的内存管理，没有严格的多线程安全， 不然每个addRef/decRef都要加锁.
     * <p>
     * 1. 对于不在多线程中传递(非异步filter), 在filter的开头加addRef, 结束运行decRef.
     * 2. 对于upFilter需要传递到异步downFilter时，downFilter在processMediaSample开始处addRef,
     * 然后在处理结束时候运行decRef即可.
     * 3. sample从Allocator中alloc分配出来， 已经自带了一个refercent cnt.
     * 4. 注意exception可能会把sample.defCnt跳过的情况.
     */
    AtomicInteger mRefCnt = new AtomicInteger(0);

    @Override
    public int addRef() {
        return mRefCnt.addAndGet(1);
    }

    @Override
    public int decRef() {
        int ret = mRefCnt.decrementAndGet();
        if (ret > 0) {
            return ret;
        } else if (ret < 0) {
            YYLog.info(this, "YYMediaSample.decRef, reference cnt <0, take notice.....");
            return ret;
        } else {
            YYMediaSampleAlloc.instance().free(this);
            return 0;
        }
    }

    public void reset() {

        mSampleId = -1;

        mWidth = 0;
        mHeight = 0;
        mClipWidth = 0;
        mClipHeight = 0;
        mAndoridPtsNanos = 0;
        mTimestampMs = 0L;
        mYYPtsMillions = 0;
        mDtsMillions = 0;

        mPlanHeight = 0;
        mPlanWidth = 0;


        /**Capture information
         public int 		mImageFormat;
         public int 		mCameraFacing;
         public int 		mDisplayRotation;
         public boolean 	mVideoStabilization;
         */

        //TODO set default value;
        mMediaFormat = null;
        mColorFormat = 0;
        mFrameFlag = 0;
        mFrameType = VideoConstant.VideoFrameType.kVideoUnknowFrame;

        mDataByteBuffer = null;
        mBufferOffset = 0;
        mBufferSize = 0;
        mBufferFlag = 0;
        mDataBytes = null;

        mTextureId = -1;
        mFrameBufferId = -1;
        mTextureTarget = -1;
        mTextureCreatedThreadId = -1;
        mTextureFlipVertical = false;

        mExtraTextureId = -1;

        mDeliverToEncoder = false;
        mDeliverToPreview = true;
        mDeliverToSnapshot = true;

        mCameraCapture = false;
        mEndOfStream = false;

        mEncodeWidth = 0;
        mEncodeHeight = 0;

        mEncoderType = VideoEncoderType.HARD_ENCODER_H264;
        mGLCubeBuffer.clear().position(0);
        mGLCubeBuffer.put(CUBE).position(0);
        mGLTextureBuffer.clear().position(0);
        mGLTextureBuffer.put(TEXTURE_BUFFER).position(0);
        mExtInfo = null;

        mSampleType = SampleType.UNKNOWN;

        mStackTraceInfo = null;
        mBAllocFromPool = false;

        System.arraycopy(VideoConstant.mtxIdentity, 0, mTransform, 0, mTransform.length);
        System.arraycopy(VideoConstant.mtxIdentity, 0, mExtraTextureTransform, 0, mExtraTextureTransform.length);
        mShouldUpsideDown = true;
        mRgbaBytes = null;
        mRefCnt.set(0);

        mApplyFilterIDs.clear();
        mAudioFrameData = new OrangeFilter.OF_AudioFrameData();
        mFaceFrameDataArr = null;
        mBodyFrameDataArr = null;
        mGestureFrameDataArr = null;
        mSegmentFrameData = new OrangeFilter.OF_SegmentFrameData();
        mPreMultiplyAlpha = false;
        mAvatarId = -1;
    }

    public void assigne(YYMediaSample sample) {
        this.mSampleId = sample.mSampleId;
        this.mSampleType = sample.mSampleType;

        this.mWidth = sample.mWidth;
        this.mHeight = sample.mHeight;
        this.mClipWidth = sample.mClipWidth;
        this.mClipHeight = sample.mClipHeight;
        this.mAndoridPtsNanos = sample.mAndoridPtsNanos;
        this.mTimestampMs = sample.mTimestampMs;
        this.mYYPtsMillions = sample.mYYPtsMillions;
        this.mDtsMillions = sample.mDtsMillions;
        this.mResMode = sample.mResMode;

        this.mPlanWidth = sample.mPlanWidth;
        this.mPlanHeight = sample.mPlanHeight;

        this.mImageFormat = sample.mImageFormat;
        this.mCameraFacingFront = sample.mCameraFacingFront;
        this.mDisplayRotation = sample.mDisplayRotation;
        this.mVideoStabilization = sample.mVideoStabilization;


        System.arraycopy(sample.mTransform, 0, this.mTransform, 0, mTransform.length);
        System.arraycopy(sample.mExtraTextureTransform, 0, this.mExtraTextureTransform, 0, mExtraTextureTransform.length);

        this.mShouldUpsideDown = sample.mShouldUpsideDown;
        this.mGLCubeBuffer.clear().position(0);
        this.mGLCubeBuffer.put(sample.mGLCubeBuffer);

        this.mGLTextureBuffer.clear().position(0);
        this.mGLTextureBuffer.put(sample.mGLTextureBuffer);

        this.mMediaFormat = sample.mMediaFormat;  //TODO. memcpy?
        this.mColorFormat = sample.mColorFormat;
        this.mFrameFlag = sample.mFrameFlag;
        this.mFrameType = sample.mFrameType;

        this.mDataByteBuffer = sample.mDataByteBuffer; //TODO. memcpy
        this.mBufferOffset = sample.mBufferOffset;
        this.mBufferSize = sample.mBufferSize;
        this.mBufferFlag = sample.mBufferFlag;
        this.mDataBytes = sample.mDataBytes;

        this.mEndOfStream = sample.mEndOfStream;
        this.mDeliverToEncoder = sample.mDeliverToEncoder;
        this.mDeliverToPreview = sample.mDeliverToPreview;
        this.mDeliverToSnapshot = sample.mDeliverToSnapshot;

        this.mTextureId = sample.mTextureId;
        this.mFrameBufferId = sample.mFrameBufferId;
        this.mTextureTarget = sample.mTextureTarget;
        this.mTextureCreatedThreadId = sample.mTextureCreatedThreadId;
        this.mExtInfo = sample.mExtInfo; //TODO. memcpy.

        this.mExtraTextureId = sample.mExtraTextureId;

        this.mTextureFlipVertical = sample.mTextureFlipVertical;
        this.mEncodeWidth = sample.mEncodeWidth;
        this.mEncodeHeight = sample.mEncodeHeight;
        this.mEncoderType = sample.mEncoderType;

        this.mCameraCapture = sample.mCameraCapture;

        this.mApplyFilterIDs = sample.mApplyFilterIDs;

        this.mAudioFrameData = sample.mAudioFrameData;
        this.mFaceFrameDataArr = sample.mFaceFrameDataArr;
        this.mBodyFrameDataArr = sample.mBodyFrameDataArr;
        this.mGestureFrameDataArr = sample.mGestureFrameDataArr;
        this.mSegmentFrameData = sample.mSegmentFrameData;
        this.mPreMultiplyAlpha = sample.mPreMultiplyAlpha;
        this.mAvatarId = sample.mAvatarId;
}

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" mSampleId:").append(mSampleId);
        sb.append(" mWidth:").append(mWidth);
        sb.append(" mHeight:").append(mHeight);
        sb.append(" mEncodeWidth:").append(mEncodeWidth);
        sb.append(" mEncodeHeight:").append(mEncodeHeight);
        sb.append(" mFrameType:").append(mFrameType);
        sb.append(" mTextureId:").append(mTextureId);
        sb.append(" mFrameBufferId:").append(mFrameBufferId);
        sb.append(" mEncoderType:").append(mEncoderType);
        sb.append(" mCameraFacingFront:").append(mCameraFacingFront);
        sb.append(" mDisplayRotation:").append(mDisplayRotation);
        sb.append(" mClipWidth:").append(mClipWidth);
        sb.append(" mClipHeight:").append(mClipHeight);
        sb.append(" mEnOfStream: ").append(mEndOfStream);
        sb.append(" mDeliverToPreview:").append(mDeliverToPreview);
        sb.append(" mDeliveToSnapshot:").append(mDeliverToSnapshot);
        sb.append(" mPlanWidth:").append(mPlanWidth);
        sb.append(" mPlanHeight:").append(mPlanHeight);
        if (mStackTraceInfo != null) {
            sb.append(" mStackTraceInfo:").append(mStackTraceInfo);
        }

        return sb.toString();
    }

    static YYMediaSample allocSample() {
        return YYMediaSampleAlloc.instance().alloc();
    }


    /**
     * 复制sample中保存的肢体识别数据，随mux写入到文件或内存中
     * 1.body data不为null且包含肢体数据（length>0)，copy数据到dataCopy
     * 2.body data不为null不包含肢体数据（length==0)，返回空的dataCopy
     * 3.body data为null,说明当前sample没有经过肢体检测，dataCopy返回null
     * @return
     */
    public OrangeFilter.OF_BodyFrameData[] bodyFrameDataArrClone() {
        if (mBodyFrameDataArr != null) {
            OrangeFilter.OF_BodyFrameData[] bodyFrameDataArrCopy = new OrangeFilter.OF_BodyFrameData[mBodyFrameDataArr.length];

            for (int i = 0; i < mBodyFrameDataArr.length; i++) {
                bodyFrameDataArrCopy[i] = new OrangeFilter.OF_BodyFrameData();
                bodyFrameDataArrCopy[i].bodyPointsScore = new float[mBodyFrameDataArr[i].bodyPointsScore.length];
                for (int j = 0; j < mBodyFrameDataArr[i].bodyPointsScore.length; j++) {
                    bodyFrameDataArrCopy[i].bodyPointsScore[j] = mBodyFrameDataArr[i].bodyPointsScore[j];
                }
                bodyFrameDataArrCopy[i].bodyPoints = new float[mBodyFrameDataArr[i].bodyPoints.length];
                for (int j = 0; j < mBodyFrameDataArr[i].bodyPoints.length; j++) {
                    bodyFrameDataArrCopy[i].bodyPoints[j] = mBodyFrameDataArr[i].bodyPoints[j];
                }
            }
            return bodyFrameDataArrCopy;
        } else {
            return null;
        }
    }

    /**
     * 复制sample中保存的人体识别数据，随mux写入到文件或内存中
     * 1.face data不为null且包含人脸数据（length>0)，copy数据到dataCopy
     * 2.face data不为null不包含人脸数据（length==0)，返回空的dataCopy
     * 3.face data为null,说明当前sample没有经过人脸检测，dataCopy返回null
     * @return
     */
    public OrangeFilter.OF_FaceFrameData[] faceFrameDataArrClone() {
        if (mFaceFrameDataArr != null) {
            OrangeFilter.OF_FaceFrameData[] faceFrameDataArrCopy = new OrangeFilter.OF_FaceFrameData[mFaceFrameDataArr.length];

            for (int i = 0; i < mFaceFrameDataArr.length; i++) {
                faceFrameDataArrCopy[i] = new OrangeFilter.OF_FaceFrameData();
                faceFrameDataArrCopy[i].facePoints = new float[mFaceFrameDataArr[i].facePoints.length];
                for (int j = 0; j < mFaceFrameDataArr[i].facePoints.length; j++) {
                    faceFrameDataArrCopy[i].facePoints[j] = mFaceFrameDataArr[i].facePoints[j];
                }
            }
            return faceFrameDataArrCopy;
        } else {
            return null;
        }
    }
}
