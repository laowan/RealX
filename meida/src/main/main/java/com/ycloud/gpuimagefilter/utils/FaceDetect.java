package com.ycloud.gpuimagefilter.utils;

import android.content.Context;
import android.opengl.GLES20;
import com.orangefilter.OrangeFilterApi;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Administrator on 2017/6/22.
 */

public class FaceDetect {
    private final String TAG = "FaceDetect";

    // 检测脸部动作：张嘴、眨眼、抬眉、点头、摇头
    private static final int ST_MOBILE_TRACKING_MULTI_THREAD = 0x00000000; ///< 多线程，功耗较多，卡顿较少
    private static final int ST_MOBILE_TRACKING_SINGLE_THREAD = 0x00010000;  ///< 单线程，功耗较少，对于性能弱的手机，会偶尔有卡顿现象
    private static final int ST_MOBILE_TRACKING_ENABLE_DEBOUNCE = 0x00000010; ///< 打开去抖动
    private static final int ST_MOBILE_TRACKING_ENABLE_FACE_ACTION = 0x00000020; ///< 检测脸部动作：张嘴、眨眼、抬眉、点头、摇头
    private static final int ST_MOBILE_TRACKING_DEFAULT_CONFIG = ST_MOBILE_TRACKING_MULTI_THREAD | ST_MOBILE_TRACKING_ENABLE_DEBOUNCE;

    private int REVERSE_TABLE[] = {
            32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20,
            19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
            42, 41, 40, 39, 38, 37, 36, 35, 34, 33,
            43, 44, 45, 46, 51, 50, 49, 48, 47,
            61, 60, 59, 58, 63, 62, 55, 54, 53, 52, 57,
            56, 71, 70, 69, 68, 67, 66, 65, 64, 75, 76, 77, 72, 73,
            74, 79, 78, 81, 80, 83, 82, 90, 89, 88, 87, 86, 85, 84,
            95, 94, 93, 92, 91, 100, 99, 98, 97, 96, 103, 102, 101, 105, 104};

    private OrangeFilterApi.OF_FrameData mFaceFrameData;
    private int mFaceCount = 1;
    private int mDir = 2;
    //private STMobileMultiTrack106 mMultiTrack106 = null;
    private Context mContext = null;

    final private int FRAMEBUFFER_NUM = 1;
    private int[] mFrameBuffer;
    private int[] mFrameBufferTexture;

    private int mOutputWidth;
    private int mOutputHeight;
    private int mScaleWidth;
    private int mScaleHeight;

    private ByteBuffer mSrcBuffer;
    private ByteBuffer mScaleBuffer;

    public FaceDetect() {
        mFaceFrameData = new OrangeFilterApi.OF_FrameData();
        mFaceFrameData.faceFrameDataArr = new OrangeFilterApi.OF_FaceFrameData[mFaceCount];
        mFrameBuffer = new int[FRAMEBUFFER_NUM];
        mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
    }

    public void init(Context context, int outputWidth, int outputHeight) {
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;
        mScaleWidth = mOutputWidth / 2;
        mScaleHeight = mOutputHeight / 2;
        mContext = context.getApplicationContext();
        OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);
        mSrcBuffer = ByteBuffer.allocate(mOutputWidth * mOutputHeight * 4).order(ByteOrder.nativeOrder());
        mScaleBuffer = ByteBuffer.allocate(mScaleWidth * mScaleHeight * 4).order(ByteOrder.nativeOrder());
        initSTMobile();
        YYLog.info(TAG, "init mOutputWidth=" + mOutputWidth + " mOutputHeight" + mOutputHeight);
    }

    public void destroy() {
        if (mFrameBufferTexture != null && mFrameBuffer != null) {
            OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
            mFrameBufferTexture = null;
            mFrameBuffer = null;
        }

        if (mSrcBuffer != null) {
            mSrcBuffer.clear();
            mSrcBuffer = null;
        }

        if (mScaleBuffer != null) {
            mScaleBuffer.clear();
            mScaleBuffer = null;
        }

/*        if (mMultiTrack106 != null) {
            mMultiTrack106.destory();
            mMultiTrack106 = null;
        }*/

        YYLog.info(TAG, "destroy");
    }

    private void initSTMobile() {
        /*if (mMultiTrack106 == null) {
            AuthCallback authCallback = new AuthCallback() {
                @Override
                public void onAuthResult(boolean succeed, String errMessage) {
                    if (!TextUtils.isEmpty(errMessage)) {
                        if (succeed) {
                            YYLog.info(TAG, "[face] STMobile have been authorized...");
                        } else {
                            YYLog.error(TAG, "[face] STMobile authorized fail....");
                        }
                    }
                }
            };
            int config = ST_MOBILE_TRACKING_DEFAULT_CONFIG | ST_MOBILE_TRACKING_ENABLE_FACE_ACTION;
            if (authCallback != null && mContext != null) {
                mMultiTrack106 = new STMobileMultiTrack106(mContext, config, authCallback);
            }
            int max = 5;
            mMultiTrack106.setMaxDetectableFaces(max);
        }*/
    }


    public OrangeFilterApi.OF_FrameData detect(int framebuffer) {
        mSrcBuffer.clear();
        mSrcBuffer.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mSrcBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        /*// 调用实时人脸检测函数，返回当前人脸信息
        STMobileFaceAction[] faceActions = mMultiTrack106.trackFaceAction(mScaleBuffer.array(), STImageFormat.ST_PIX_FMT_RGBA8888,
                mOutputWidth, mOutputHeight, mOutputWidth * 4, mDir);

        if (faceActions != null && faceActions.length > 0) {
            if (faceActions.length != mFaceCount) {
                mFaceCount = faceActions.length;
                mFaceFrameData.faceFrameDataArr = new OrangeFilterApi.OF_FaceFrameData[mFaceCount];
            }

            for (int i = 0; i < faceActions.length; i++) {

                for (int j = 0; j < 106; j++) {
                    mFaceFrameData.faceFrameDataArr[i].facePoints[2 * j] = 1.0f - faceActions[i].face.points_array[2 * j + 1] / mOutputHeight;
                    mFaceFrameData.faceFrameDataArr[i].facePoints[2 * j + 1] = 1.0f - faceActions[i].face.points_array[2 * j] / mOutputWidth;
                }

                // 以人脸中轴线对称反转
                float arr[] = new float[106 * 2];
                for (int j = 0; j < 106; j++) {
                    int index = REVERSE_TABLE[j];
                    arr[2 * j] = mFaceFrameData.faceFrameDataArr[i].facePoints[2 * index];
                    arr[2 * j + 1] = mFaceFrameData.faceFrameDataArr[i].facePoints[2 * index + 1];
                }

                for (int j = 0; j < 106; j++) {
                    mFaceFrameData.faceFrameDataArr[i].facePoints[2 * j] = arr[2 * j];
                    mFaceFrameData.faceFrameDataArr[i].facePoints[2 * j + 1] = arr[2 * j + 1];
                }
            }
            return mFaceFrameData;
        } else {
            return null;
        }*/
        return null;
    }
}
