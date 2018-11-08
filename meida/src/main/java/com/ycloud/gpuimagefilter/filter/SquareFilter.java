package com.ycloud.gpuimagefilter.filter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.abs;

/**
 * Created by liuchunyu on 2017/7/29.
 */

public class SquareFilter extends BaseFilter {
    private String TAG = "SquareFilter";
	
	private FloatBuffer vertexPosBuf;
    private FloatBuffer vertexTexCoordsBuf;
    private float[] mMVPMatrix = new float[16];
    private float[] mScaleMatrix = new float[16];
    private float[] mRotateMatrix = new float[16];
    private float[] mTranslateMatrix = new float[16];
    private float[] mTmpTextureCord = new float[8];
    private float mRotateScaleW = 1.0f; // 从大到小的最小缩放值，刚好横屏居中
    private float mRotateScaleWTmp = 1.0f;
    private float mRotateScaleH = 1.0f;
    private float mRotateScaleHTmp = 1.0f;
    private float mScaleValueW = 1.0f;       // 当前一帧要缩放的值
    private float mSCaleValueH = 1.0f;
    private float mRotateAngleStep = 18.0f; // 每一帧旋转增加的角度
    private float mRotateAngle = 0.0f;      // 当前一帧要旋转的角度值
    private float mCurrentAngle = 0.0f;     // 用户点击旋转的角度，不包含源视频自带的旋转角度，传递给转码模块使用
    private boolean mStopRotate = true;     // 一次旋转完成 横屏->竖屏  竖屏->横屏
    private boolean mRotated = false;
    private boolean mEnableRotate = false;
    private boolean mClockWise = false;
    private boolean mUseForPlayer = false;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mViewWidth;
    private int mViewHeight;
    private float mWidthRatio;
    private float mHeightRatio;
    private float mInputAspect;
    private float mInputAspectAfterRotate;  // 视频旋转 90 / 270 度后的输入宽高比
    private float mOutputAspect;
    private int viewPortWidth;
    private int viewPortHeight;
    private float mVideoOrignalRotateAngle = 0;
    private int mLayout = 0; // 0: aspectfit , 1  cut to fit full screen

    private Timer mTimer = null;
    private TimerTask mTimerTask = null;
    private boolean mTimerStop = true;

    // 颜色背景和位图背景只能二选一,位图背景优先级高于颜色背景
    private int mBackGroundColor = -1;
    private Bitmap mBackgroundBitmap = null;
    private int mTexturesBackground = -1;

    private AtomicBoolean mDuringRotateWithoutEffect = new AtomicBoolean(false);


    private void drawBackground(Bitmap bitmap) {
        if (mTexturesBackground == -1) {
            int width = bitmap.getWidth() ;
            int height = bitmap.getHeight() ;
            mTexturesBackground = OpenGlUtils.createTexture(width, height);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexturesBackground);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        }
        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        drawSquare(mTexturesBackground, OpenGlUtils.VERTEXCOORD_BUFFER, OpenGlUtils.TEXTURECOORD_BUFFER, OpenGlUtils.IDENTITY_MATRIX);
    }


    public SquareFilter() {
        super();
    }

    /** alloc ByteBuffer filled with data */
    private FloatBuffer allocBuffer(float[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(data.length * 4);
        buf.order(ByteOrder.nativeOrder());
        FloatBuffer buffer;
        buffer = buf.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);
        return buffer;
    }

    public void setmUseForPlayer() {
        mUseForPlayer = true;
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");

        YYLog.info(TAG, "init mEnableRotate " + mEnableRotate + " mUseForPlayer " + mUseForPlayer);
        if (mEnableRotate && mUseForPlayer) {
            super.initExt(outputWidth, outputHeight, isExtTexture, oFContext);
            viewPortWidth = outputWidth;
            viewPortHeight = outputHeight;
        } else {
            super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        }
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init");

        if (mEnableRotate && mUseForPlayer) {
            mViewWidth = outputWidth;
            mViewHeight = outputHeight;
            mOutputAspect = (float) mViewWidth / (float) mViewHeight;
            /** load vertex data into FloatBuffer */
            vertexPosBuf = allocBuffer(YYMediaSample.CUBE);
            /** load texture coordinate data into FloatBuffer */
            vertexTexCoordsBuf = allocBuffer(YYMediaSample.TEXTURE_BUFFER);
            initRotateTimer();
        }
    }

    public void setVideoSize(int w, int h){
        mVideoWidth = w;
        mVideoHeight = h;
        mInputAspect = (float) mVideoWidth / (float) mVideoHeight;
        mInputAspectAfterRotate = (float) mVideoHeight / (float) mVideoWidth;

        // 用于非旋转状态时，视频按宽高比显示在一个固定不变的视口中。
        if (mInputAspect < mOutputAspect) {
            mWidthRatio = mInputAspect / mOutputAspect;
            mScaleValueW = mWidthRatio;
            mRotateScaleWTmp = mScaleValueW;
        } else {
            mHeightRatio = mOutputAspect / mInputAspect;
            mSCaleValueH = mHeightRatio;
            mRotateScaleHTmp = mSCaleValueH;
        }
        YYLog.info(TAG, "setVideoSize mVideoWidth " + mVideoWidth + " mVideoHeight " + mVideoHeight + " mInputAspect " + mInputAspect);
        YYLog.info(TAG, "setVideoSize mViewWidth " + mViewWidth + " mViewHeight " + mViewHeight + " mOutputAspect " + mOutputAspect);
        YYLog.info(TAG, "setVideoSize mScaleValueW " + mScaleValueW + " mSCaleValueH " + mSCaleValueH);

        // 用于旋转90/270度状态时，视频边旋转边缩放在一个固定不变的视口中。
        if (mInputAspectAfterRotate < mOutputAspect) {
            mRotateScaleW = mInputAspectAfterRotate / mOutputAspect;
            mRotateScaleH = mRotateScaleW;
        } else {
            mRotateScaleH = mOutputAspect / mInputAspectAfterRotate;
            mRotateScaleW = mRotateScaleH;
        }

        YYLog.info(TAG, "setVideoSize mRotateScaleW " + mRotateScaleW + " mRotateScaleH " + mRotateScaleH + "  mInputAspectAfterRotate " + mInputAspectAfterRotate);
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();
        vertexPosBuf = null;
        vertexTexCoordsBuf = null;
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mTexturesBackground != -1) {
            OpenGlUtils.deleteTexture(mTexturesBackground);
        }
        if (mBackgroundBitmap != null && !mBackgroundBitmap.isRecycled()) {
            mBackgroundBitmap.recycle();
            mBackgroundBitmap = null;
        }
        vertexPosBuf = null;
        vertexTexCoordsBuf = null;
        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }


    private boolean updataRotateScaleValue(boolean rotateVideoOriginalAngle) {
        if (!mStopRotate) {
            mRotateAngle += mRotateAngleStep;
            mRotateAngle %= 360;

            // 完成一次旋转需要变换的角度次数（等同于视频帧数量）
            float angleSteps = 90/mRotateAngleStep;

            if (mInputAspect < mOutputAspect) {
                // 计算每次旋转视频缩放倍数, mRotateScaleW 为竖屏视频缩放到横屏时，Y轴缩放的目标值
                float delta = abs(mScaleValueW - mRotateScaleW);
                float step = delta / angleSteps;
                float max;

                if ((mRotateAngle > 0 && mRotateAngle <= 90) || (mRotateAngle > 180 && mRotateAngle <= 270)) {
                    max = mRotateScaleW;
                } else {
                    max = mScaleValueW;
                }

                if (mRotateScaleWTmp < max) {
                    mRotateScaleWTmp += step;
                } else {
                    mRotateScaleWTmp -= step;
                }

                YYLog.info(TAG, "mRotateScaleWTmp " + mRotateScaleWTmp);
            } else {
                float delta = abs(mSCaleValueH - mRotateScaleH);
                float step = delta / angleSteps;
                float max ;
                if ((mRotateAngle > 0 && mRotateAngle <= 90) || (mRotateAngle > 180 && mRotateAngle <= 270)) {
                    max = mRotateScaleH;
                } else {
                    max = mSCaleValueH;
                }

                if (mRotateScaleHTmp < max) {
                    mRotateScaleHTmp += step;
                } else {
                    mRotateScaleHTmp -= step;
                }
                YYLog.info(TAG, "mRotateScaleHTmp " + mRotateScaleHTmp);
            }

            if (mRotateAngle % 90 == 0) {
                mStopRotate = true;
                if (!rotateVideoOriginalAngle) {
                    mCurrentAngle += 90;
                    mCurrentAngle %= 360;
                }
                YYLog.info(TAG, "Current rotate angle " + mCurrentAngle + " mRotateAngle " + mRotateAngle + " rotateVideoOriginalAngle " + rotateVideoOriginalAngle);
                return true;
            }
        }
        return false;
    }


    private float[] setUpMatrix() {
        float mTmp[];
        /** Translate */
        Matrix.setIdentityM(mTranslateMatrix, 0);
        Matrix.translateM(mTranslateMatrix, 0, 0, 0.0f, 0 );

        /** Rotate*/
        Matrix.setIdentityM(mRotateMatrix, 0);
        if (!mClockWise) {
            Matrix.rotateM(mRotateMatrix, 0, mRotateAngle, 0, 0, 1);    // Z  轴旋转
        } else {
            Matrix.rotateM(mRotateMatrix, 0, -mRotateAngle, 0, 0, 1);    // Z  轴旋转
        }
        mTmp = mRotateMatrix.clone();
        Matrix.multiplyMM(mRotateMatrix, 0,mTranslateMatrix , 0,mTmp , 0);

        /** Scale */
        Matrix.setIdentityM(mScaleMatrix, 0);
        if (!mRotated) {  // 未做任何旋转的初始状态
            if (mInputAspect < mOutputAspect) {
                Matrix.scaleM(mScaleMatrix, 0, mScaleValueW, 1.0f, 1.0f);
            } else {
                Matrix.scaleM(mScaleMatrix, 0, 1.0f, mSCaleValueH, 1.0f);
            }
        } else if (mInputAspect == mInputAspectAfterRotate) {

            if ((mRotateAngle > 0 && mRotateAngle <= 90) || (mRotateAngle > 180 && mRotateAngle <= 270)) {
                Matrix.scaleM(mScaleMatrix, 0, mSCaleValueH, 1.0f, 1.0f);
            } else {
                Matrix.scaleM(mScaleMatrix, 0, 1.0f, mSCaleValueH, 1.0f);
            }

        } else {
            if (mInputAspect < mOutputAspect) {
                Matrix.scaleM(mScaleMatrix, 0, mRotateScaleWTmp, 1.0f, 1.0f);
            } else {
                if (mInputAspectAfterRotate > mOutputAspect &&   // 竖屏视频，但是需要上下加黑边， 如 490x562
                        ((mRotateAngle > 0 && mRotateAngle <= 90) || (mRotateAngle > 180 && mRotateAngle <= 270))) {
                    Matrix.scaleM(mScaleMatrix, 0, mRotateScaleHTmp, 1.0f, 1.0f);
                } else {
                    Matrix.scaleM(mScaleMatrix, 0, 1.0f, mRotateScaleHTmp, 1.0f);
                }
            }
        }
        mTmp = mScaleMatrix.clone();
        Matrix.multiplyMM(mScaleMatrix, 0,mRotateMatrix , 0,mTmp , 0);

        return mScaleMatrix;
    }

    public void StartRotate() {
        mStopRotate = false;
        mRotated = true;
        startRotateTimer();
    }

    public float getCurrentRotateAngle() {
        YYLog.info(TAG, "getCurrentRotateAngle " + mCurrentAngle);
        return mCurrentAngle;
    }

    public void setBackGroundColor(int color) {
        if (mEnableRotate && mUseForPlayer) {
            mBackGroundColor = color;
            YYLog.info(TAG, "setBackGroundColor " + color);
        }
    }

    public void setBackGroundBitmap(Bitmap bitmap) {
        if (mEnableRotate && mUseForPlayer) {
            mBackgroundBitmap = bitmap;
            YYLog.info(TAG, "setBackGroundBitmap OK.");
        }
    }

    public RectF getCurrentVideoRect() {
        RectF rect = new RectF(0,0, mViewWidth, mViewHeight); // 默认整个View大小
        if (mInputAspect < mOutputAspect) {   // 左右加黑边
            float widthOffset = (mViewWidth - (mViewWidth * mWidthRatio)) / 2;
            rect.left = widthOffset;
            rect.top = 0;
            rect.right = mViewWidth - widthOffset;
            rect.bottom = mViewHeight;
        } else {                              // 上下加黑边
            float heightOffset = (mViewHeight - (mViewHeight * mHeightRatio)) / 2;
            rect.left = 0;
            rect.top = heightOffset;
            rect.right = mViewWidth;
            rect.bottom = mViewHeight - heightOffset;
        }
        return rect;
    }

    public void setEnableRotate(boolean enable ) {
        mEnableRotate = enable;
        YYLog.info(TAG, "setEnableRotate " + mEnableRotate);
    }

    public void setRotateDirection(boolean clockwise) {
        mClockWise = clockwise;
        YYLog.info(TAG, " setRotateDirection " + clockwise);
    }

    /**
     * 用于设置原始视频metadata中的旋转角度
     * @param rotateAngle
     */
    public void setVideoRotate(int rotateAngle) {
        mVideoOrignalRotateAngle = rotateAngle;
        YYLog.info(TAG, "setVideoRotate " + rotateAngle);
        rotateWithOutEffect(rotateAngle,true);
    }

    /**
     * 用于设置用户上次设置的旋转角度，再次进入剪切页面时，恢复上次用户旋转的角度
     * 不论当前是什么旋转角度，强制旋转到 rotateAngle 度状态
     * @param rotateAngle
     */
    public void setLastVideoRotate(int rotateAngle) {
        YYLog.info(TAG, "setLastVideoRotate " + rotateAngle + " mVideoOrignalRotateAngle " + mVideoOrignalRotateAngle + " mRotateAngle " + mRotateAngle);
        int CurrentRotateAngle = (int)mRotateAngle;
        int targetRotateAngle;

        if (mVideoOrignalRotateAngle != 0) {
            rotateAngle += mVideoOrignalRotateAngle;
        }

        if (CurrentRotateAngle == rotateAngle) {
            YYLog.info(TAG, "Already rotate the target angle " + rotateAngle);
            return;
        }

        targetRotateAngle = 360 + rotateAngle - CurrentRotateAngle; // 从当前旋转角度达到目标旋转角度还需要旋转的角度数
        targetRotateAngle %= 360;
        mDuringRotateWithoutEffect.set(true);
        rotateWithOutEffect(targetRotateAngle, false);
        mDuringRotateWithoutEffect.set(false);
    }

    private void rotateWithOutEffect(int rotateAngle, boolean bOriginalInMetadata) {
        if (rotateAngle != 90 && rotateAngle != 180 && rotateAngle != 270) {
            return;
        }
        stopRotateTimer();
        if (bOriginalInMetadata && (rotateAngle == 90 || rotateAngle == 270) ) {
            setVideoSize(mVideoHeight, mVideoWidth);
        }

        if (!mClockWise) {
            // 旋转方向为逆时针旋转
            rotateAngle = 360 - rotateAngle;
        }

        if (bOriginalInMetadata) {
            while (mRotateAngle < rotateAngle) {
                mStopRotate = false;
                updataRotateScaleValue(true);
            }
        } else {
            while (rotateAngle > 0) {
                mStopRotate = false;
                boolean finishRotate = updataRotateScaleValue(false);
                if (finishRotate) {
                    rotateAngle -= 90;
                }
            }
        }
        mRotated = true;
        startRotateTimer();
    }

    public void setLayoutMode(int mode) {
        mLayout = mode;
        YYLog.info(TAG, "setLayoutMode " + mode);
    }


    public void setViewPortSize(int w, int h) {

        viewPortWidth = w;
        viewPortHeight = h;
		
        mViewWidth = w;
        mViewHeight = h;
        mOutputAspect = (float)mViewWidth/(float)mViewHeight;

        // updata scale value
        setVideoSize(mVideoWidth, mVideoHeight);

        YYLog.info(TAG, "setViewPortSize w " + w + " h " + h );
    }

    private void updataVertext(int videoWidth, int cutSize) {
        System.arraycopy(YYMediaSample.TEXTURE_BUFFER, 0, mTmpTextureCord, 0, YYMediaSample.TEXTURE_BUFFER.length);
        float offset = (float)cutSize/(float)videoWidth;
        mTmpTextureCord[2] -= offset;
        mTmpTextureCord[6] -= offset;
        vertexTexCoordsBuf = allocBuffer(mTmpTextureCord);
    }

    private void updateVertexForLayout(int layoutMode) {
        System.arraycopy(YYMediaSample.TEXTURE_BUFFER, 0, mTmpTextureCord, 0, YYMediaSample.TEXTURE_BUFFER.length);

        if (mInputAspect > mOutputAspect) {         // 上下加黑边 或者 左右裁剪， 此处左右裁剪
            float newW = mOutputAspect * mVideoHeight;
            float cutSize = (1 - newW / mVideoWidth);

            mTmpTextureCord[0] += cutSize/2;        // 左边裁剪
            mTmpTextureCord[4] += cutSize/2;

            mTmpTextureCord[2] -= cutSize/2;        // 右边裁剪
            mTmpTextureCord[6] -= cutSize/2;

            YYLog.info(TAG, "cut left and right !!!");

        } else {                                    // 左右加黑边 或者 上下裁剪，此处上下裁剪
            float newH = mVideoWidth / mOutputAspect;
            float cutSize = (1 - newH / mVideoHeight);

            mTmpTextureCord[1] += cutSize/2;        // 底部裁剪
            mTmpTextureCord[3] += cutSize/2;

            mTmpTextureCord[5] -= cutSize/2;        // 顶部裁剪
            mTmpTextureCord[7] -= cutSize/2;

            YYLog.info(TAG, "cut bottom and up !!!");
        }

        vertexTexCoordsBuf = allocBuffer(mTmpTextureCord);
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        OpenGlUtils.checkGlError("SquareFilter processMediaSample start");

        if (mDuringRotateWithoutEffect.get()) {
            YYLog.info(TAG, "during rotate without effect. just return.");
            return true;
        }

        storeOldFBO();
        if (mBackgroundBitmap != null && mEnableRotate && mUseForPlayer) {
            drawBackground(mBackgroundBitmap); // 位图背景
        }

        if (mEnableRotate && mUseForPlayer) {
            GLES20.glViewport(0, 0, viewPortWidth, viewPortHeight);
        } else {
            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        }

        float clearColorR = 0.0f, clearColorG = 0.0f, clearColorB = 0.0f, clearColorA = 0.0f;
        // 颜色背景;
        if (mBackGroundColor != -1) {
            clearColorB = Color.blue(mBackGroundColor);
            clearColorG = Color.green(mBackGroundColor);
            clearColorR =  Color.red(mBackGroundColor);
            clearColorA =  Color.alpha(mBackGroundColor);
        }

        if (mBackgroundBitmap == null) {  // 防止清掉位图背景，位图背景优先级高于颜色背景
            GLES20.glClearColor(clearColorR/0xFF, clearColorG/0xFF, clearColorB/0xFF, clearColorA/0xFF);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }

        FloatBuffer textureCoordBuffer = sample.mShouldUpsideDown ? OpenGlUtils.TEXTURECOORD_BUFFER_UPDOWN : OpenGlUtils.TEXTURECOORD_BUFFER;
        if (mEnableRotate && mUseForPlayer) {
            if (sample.mWidth % 16 != 0) { // 宽度非16倍数的视频，如540，硬解码出来的纹理有绿边，裁掉~！
                updataVertext(sample.mWidth, sample.mWidth % 16);
            }

            if (false && mLayout == 1) {  // 用于发布页，裁剪全屏显示
                Matrix.setIdentityM(mMVPMatrix, 0);
                updateVertexForLayout(1);
            } else {             // 用于本地导入页，按宽高比缩放到视口中
                mMVPMatrix = setUpMatrix();
            }
            drawSquare(sample.mTextureId, vertexPosBuf, vertexTexCoordsBuf, mMVPMatrix);
        } else {
            drawSquare(sample.mTextureId, OpenGlUtils.VERTEXCOORD_BUFFER, textureCoordBuffer, OpenGlUtils.IDENTITY_MATRIX);
        }
        OpenGlUtils.checkGlError("processMediaSample SquareFilter end");
        recoverOldFBO();
        return true;
    }

    private void initRotateTimer() {
        if (mTimer == null) {
            mTimer = new Timer();
            mTimerTask = new TimerTask() {
                public void run() {
                    if (!mTimerStop) {
                        updataRotateScaleValue(false);
                    }
                }
            };
            mTimer.schedule(mTimerTask, 1000, 30);
        }
    }

    private void startRotateTimer() {
        mTimerStop = false;
    }

    private void stopRotateTimer() {
        mTimerStop = true;
    }

}
