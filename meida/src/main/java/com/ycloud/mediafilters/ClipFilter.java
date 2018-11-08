package com.ycloud.mediafilters;

import android.opengl.GLES20;
import android.util.Log;


import com.ycloud.common.Constant;
import com.ycloud.gles.Drawable2d;
import com.ycloud.gles.FullFrameRect;
import com.ycloud.gles.Texture2dProgram;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Locale;

/**
 * Created by Administrator on 2017/1/9.
 */
public class ClipFilter extends AbstractYYMediaFilter {
    private FullFrameRect mOffScreen = null;
    private int mOffScreenTextureId = -1;
    private int mOffScreenFrameBuffer = -1;

    private int mLastSampleWidth = 0;
    private int mLastSampleHeight = 0;

    private boolean mInited = false;

    public ClipFilter() {
        mInited = true;
    }

    public void init() {
        YYLog.info(this,Constant.MEDIACODE_PREPRO+"ClipFilter init");
        //mOffScreenTextureId = genTexture(GLES20.GL_TEXTURE_2D, mVideoConfig.mPreviewWidth, mVideoConfig.mPreviewHeight);
        mOffScreenTextureId = OpenGlUtils.createTexture(GLES20.GL_TEXTURE_2D, mOutputWidth, mOutputHeight);

        int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        mOffScreenFrameBuffer = frameBuffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOffScreenFrameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                mOffScreenTextureId, 0);

        mOffScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D_WITH_EXTRA_TXT_2),
                Drawable2d.Prefab.FULL_RECTANGLE,
                OpenGlUtils.createFloatBuffer(Drawable2d.FULL_RECTANGLE_TEX_COORDS),
                OpenGlUtils.createFloatBuffer(Drawable2d.FULL_RECTANGLE_TEX_COORDS));

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mInited = true;
    }

    public void deInit() {
        YYLog.info(this, Constant.MEDIACODE_PREPRO+"ClipFilter deInit");
        if (mOffScreenTextureId > 0) {
            int[] textures = new int[1];
            textures[0] = mOffScreenTextureId;
            GLES20.glDeleteTextures(1, textures, 0);
            mOffScreenTextureId = -1;
        }
        if (mOffScreenFrameBuffer > 0) {
            int[] framebuffers = new int[1];
            framebuffers[0] = mOffScreenFrameBuffer;
            GLES20.glDeleteFramebuffers(1, framebuffers, 0);
            mOffScreenFrameBuffer = -1;
        }

        if (mOffScreen != null) {
            mOffScreen.release(true);
            mOffScreen = null;
        }

        mInited = false;
    }

    /**
     * 确保剪切出来的图片的比例与编码的比例一致， 也就保证支持相同分辨率的手机,
     * 在观看端和预览端看到的图像的视野是一样的.
     *
     * @param sample
     */
    private boolean checkOutputChanged(YYMediaSample sample) {
        if (sample.mEncodeWidth == 0 && sample.mEncodeHeight == 0) {
            YYLog.error(this, Constant.MEDIACODE_PREPRO+"invalid encoder width or height");
            return false;
        }

        double origRation = mOutputWidth * 1.0 / mOutputHeight;
        double newRation = sample.mEncodeWidth * 1.0 / sample.mEncodeHeight;

        if (Math.abs(origRation - newRation) <= 0.001 && sample.mWidth == mLastSampleWidth && sample.mHeight == mLastSampleHeight) {
            return false;
        }
        YYLog.info(this, Constant.MEDIACODE_PREPRO+"input size(%d x %d), encode size(%d x %d)", sample.mWidth, sample.mHeight, sample.mEncodeWidth, sample.mEncodeHeight);

        //按照比例重现计算输出output
        StringBuilder logSb = new StringBuilder();
        logSb.append("checkOutputChanged, origOutputWidth=").append(mOutputWidth);
        logSb.append(",origOutputHeight=").append(mOutputHeight);
        /** 预览比例 */
        double inputRation = sample.mWidth * 1.0 / sample.mHeight;
        /** 编码尺寸比例 */
        double outputRation = newRation;

        if (inputRation > outputRation) {
            mOutputHeight = sample.mHeight;
            mOutputWidth = (int) (sample.mHeight * outputRation);
        } else {
            mOutputWidth = sample.mWidth;
            mOutputHeight = (int) (sample.mWidth / outputRation);
        }

        mLastSampleWidth = sample.mWidth;
        mLastSampleHeight = sample.mHeight;

        //只需要算出实际裁剪后的长宽，不需要实际生成纹理了
//        deInit();
//        init();
//        mOffScreen.adjustTexture(sample.mWidth, sample.mHeight, mOutputWidth, mOutputHeight);

        logSb.append(", newOutputWidth=").append(mOutputWidth);
        logSb.append(" newOutputHeight=").append(mOutputHeight);
        YYLog.info(this, Constant.MEDIACODE_PREPRO+logSb.toString());
        return true;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        checkOutputChanged(sample);
        if (mInited) {
            //1、根据计算出的长宽，实际作用到offScreen texture
//            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
//            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    // again, only really need to
//            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect
//
//            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOffScreenFrameBuffer);
//            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
//                    mOffScreenTextureId, 0);
//            mOffScreen.drawFrame(sample.mTextureId, sample.mTransform);

//            sample.mWidth = mOutputWidth;
//            sample.mHeight = mOutputHeight;
//            sample.mTextureId = mOffScreenTextureId;

//            System.arraycopy(Constant.mtxIdentity, 0, sample.mTransform, 0, sample.mTransform.length);
//
//            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            //2、传递计算出的裁剪长宽，由后面的filter真正作用到纹理，减少一次离屏渲染
            sample.mClipWidth = mOutputWidth;
            sample.mClipHeight = mOutputHeight;

            mLastSampleWidth = sample.mWidth;
            mLastSampleHeight = sample.mHeight;
        }

        deliverToDownStream(sample);
        return false;
    }
}
