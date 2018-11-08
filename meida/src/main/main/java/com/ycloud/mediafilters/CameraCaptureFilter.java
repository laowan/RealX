package com.ycloud.mediafilters;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.ycloud.common.Constant;
import com.ycloud.statistics.UploadStatManager;
import com.ycloud.utils.TimeUtil;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.util.concurrent.atomic.AtomicBoolean;


@SuppressLint("NewApi")
public class CameraCaptureFilter extends AbstractYYMediaFilter implements SurfaceTexture.OnFrameAvailableListener/*, CameraListner*/ {
    //TODO. remove debug later.

    public int mCaptureTextureId = -1;
    public SurfaceTexture mCaptureSurfaceTexture = null;
    public long mTextureCreatedThreadId = -1;
    AtomicBoolean mInited = new AtomicBoolean(false);
    MediaFilterContext mFilterContext = null;

    private long mCurrentFrameTimeDeltaTickcountNanos = 0;

    private long mRecordStartTime = 0;
    private AtomicBoolean mIsFirstEncodedFrame = new AtomicBoolean(false);

    AtomicBoolean mEncodeEnable = new AtomicBoolean(false);

    public CameraCaptureFilter(MediaFilterContext filterContext) {
        //mCameraStaff = new CameraStaff(filterContext.getAndroidContext());
        mFilterContext = filterContext;

        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] CameraCaptureFilter ctor");
    }

    private void doInit() {
        //mEnvSurface = mGlManager.getEglCore().createOffscreenSurface(mVideoEncoderConfig.mEncodeWidth, mVideoEncoderConfig.mEncodeHeight);
        //mGlManager.getEglCore().makeCurrent(mEnvSurface);
        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] CameraCaptureFilter.doInit begin");
        synchronized (mInited) {
            if (mInited.get()) {
                return;
            }

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mCaptureTextureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCaptureTextureId);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            mCaptureSurfaceTexture = new SurfaceTexture(mCaptureTextureId);
//            mCaptureSurfaceTexture.setDefaultBufferSize(mFilterContext.getCameraPreviewConfig().getWidth(), mFilterContext.getCameraPreviewConfig().getHeight());
            mCaptureSurfaceTexture.setOnFrameAvailableListener(this);

            mTextureCreatedThreadId = Thread.currentThread().getId();
            mInited.set(true);
            mInited.notifyAll();
        }

        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] CameraCaptureFilter.doInit end");
    }

    //running in gl thread.
    public void init() {
        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] CameraCaptureFilter.init begin");
        if (mFilterContext.getGLManager().checkSameThread()) {
            doInit();
        } else {
            mFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    doInit();
                }
            });
            //wait for implement.
        }
        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] CameraCaptureFilter.init end");
    }

    public void deInit() {
        if (mFilterContext.getGLManager().checkSameThread()) {
            doDeInit();
        } else {
            mFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    doDeInit();
                }
            });
            //wait for implement.
        }
    }

    private void doDeInit() {
        if (!mInited.getAndSet(false)) {
            YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] doDeInit: no Initalized state, so return");
            return;
        }

        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] doDeInit begin");
        if (mCaptureTextureId > 0) {
            int[] textures = { mCaptureTextureId };
            GLES20.glDeleteTextures(1, textures, 0);
            mCaptureTextureId = -1;
        }

        try {
            if (mCaptureSurfaceTexture != null) {
                mCaptureSurfaceTexture.detachFromGLContext();
                mCaptureSurfaceTexture.release();
                mCaptureSurfaceTexture = null;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        YYLog.info(this, Constant.MEDIACODE_CAP + "[procedure] doDeInit end");
    }


    public SurfaceTexture getSurfaceTexture() {
        return mCaptureSurfaceTexture;
    }


    @Override
    public boolean processMediaSample(YYMediaSample sammple, Object upstream) {
        // TODO Auto-generated method stub
        return false;
    }


    /**
     * 设置是否采集数据,送入编码器的标识。开始录制应该立即设为true，停止录制应该立即设为false
     */
    public void setEncodeEnable(boolean encodeEnable) {
        YYLog.info(TAG, "setEncodeEnable:" + encodeEnable);
        mEncodeEnable.set(encodeEnable);
        mRecordStartTime = System.nanoTime();
        mIsFirstEncodedFrame.set(encodeEnable);
    }

    private void handleFrameAvailble(SurfaceTexture surfaceTexture) {
        try {
            if (!mInited.get() || !surfaceTexture.equals(mCaptureSurfaceTexture)) {
                //switch 摄像头的时候mCaptureSurfacewTexture可能会变化.
                YYLog.error(this, Constant.MEDIACODE_CAP + "[tracer] handleFrameAvailble, not same surfaceTexture or not initialized");
                return;
            }

            if (mCurrentFrameTimeDeltaTickcountNanos == 0 && surfaceTexture.getTimestamp() != 0) {
                mCurrentFrameTimeDeltaTickcountNanos = surfaceTexture.getTimestamp() -  TimeUtil.getTickCountLong() * 1000000;
                YYLog.info(this, Constant.MEDIACODE_CAP + "onFrameAvailable timestamp " + surfaceTexture.getTimestamp() +
                        " tickcount " + TimeUtil.getTickCountLong() +
                        " delta " + mCurrentFrameTimeDeltaTickcountNanos);
            }

            UploadStatManager.getInstance().fps(UploadStatManager.CAP_FPS);

            //尽量使用采集摄像头回调的pts， 数据会精确一些
            surfaceTexture.updateTexImage();
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mWidth = mFilterContext.getYMRCameraInfo().mPreviewWidth;
            sample.mHeight = mFilterContext.getYMRCameraInfo().mPreviewHeight;

            long curTime = System.nanoTime();

            //这里要在设置sample的pts之前先设置deliverToEncode标识，否则由于多线程问题，可能出现设置了一个非法pts的sample被送入encode的情况
            //表现是会直接回调一个异常大的pts
            sample.mDeliverToEncoder = mEncodeEnable.get();
            if (mIsFirstEncodedFrame.get()) {
                mRecordStartTime = curTime;
                mIsFirstEncodedFrame.set(false);
            }
            sample.mAndoridPtsNanos = curTime - mRecordStartTime;

//            YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "video pts capture:" + sample.mAndoridPtsNanos / 1000000);

            sample.mYYPtsMillions = (mCurrentFrameTimeDeltaTickcountNanos == 0 ? TimeUtil.getTickCountLong() : (sample.mAndoridPtsNanos - mCurrentFrameTimeDeltaTickcountNanos) / 1000000);
            sample.mResMode = mFilterContext.getYMRCameraInfo().mResolutionMode;

            //hardcode nowing
            sample.mImageFormat = ImageFormat.NV21; //hardcode now
            sample.mCameraFacingFront = mFilterContext.getYMRCameraInfo().mCameraFacingFront;
            sample.mDisplayRotation = mFilterContext.getYMRCameraInfo().mDisplayRotation;

            //TODO. change into preivew config.
            sample.mVideoStabilization = mFilterContext.getVideoEncoderConfig().videoStabilization;

            //TODO optimize the memory. compare the transform
            //sample.mTransform = mLastTransform;
            surfaceTexture.getTransformMatrix(sample.mTransform);
            //TODO.
            //YYLog.debug(this, "CameraCaptureFilter.handleFrameAvailble: tranform-"+DumpUtil.toString(sample.mTransform));
            sample.mTextureId = mCaptureTextureId;
            sample.mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
            sample.mTextureCreatedThreadId = mTextureCreatedThreadId;

            sample.mEncodeWidth = mFilterContext.getVideoEncoderConfig().getEncodeWidth();
            sample.mEncodeHeight = mFilterContext.getVideoEncoderConfig().getEncodeHeight();
            sample.mEncoderType = mFilterContext.getVideoEncoderConfig().mEncodeType;
            sample.mCameraCapture = true;

            deliverToDownStream(sample);
            sample.decRef();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onFrameAvailable(final SurfaceTexture surfaceTexture) {

        if (mFilterContext.getGLManager().checkSameThread()) {
            handleFrameAvailble(surfaceTexture);
        } else {
            //usually, it cannot reach this branch, just in case that some
            //device system is not same as ASOP
            mFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    handleFrameAvailble(surfaceTexture);
                }
            });
            //wait for implement.
        }
    }
}
