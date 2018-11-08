package com.ycloud.gpuimage.adapter;

import android.opengl.GLES20;

import com.ycloud.common.Constant;
import com.ycloud.gles.Drawable2d;
import com.ycloud.gles.FullFrameRect;
import com.ycloud.gles.Texture2dProgram;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import static com.ycloud.gles.FullFrameRect.MAIN_TEXTURE;

/**
 * Created by Administrator on 2018/1/9.
 */

public class GlCliper {
    private FullFrameRect mOffScreen = null;
    private int mOffScreenTextureId = -1;
    private int mOffScreenFrameBuffer = -1;

    private int mLastSampleWidth = 0;
    private int mLastSampleHeight = 0;

    private boolean mVerticalFlip = false;

    protected int     mImageWidth = 0;
    protected int     mImageHeight = 0;

    protected int     mOutputWidth = 0;
    protected int     mOutputHeight = 0;

    private boolean mInited = false;

    public GlCliper() {
        mInited = true;
    }

    public void init() {
        YYLog.info(this, Constant.MEDIACODE_PREPRO+"ClipFilter init");
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

        mVerticalFlip = false;
        mInited = false;
    }

    /**
     * 确保剪切出来的图片的比例与编码的比例一致， 也就保证支持相同分辨率的手机,
     * 在观看端和预览端看到的图像的视野是一样的.
     *
     * @param sample
     */
    private boolean checkOutputChanged(YYMediaSample sample, int dstWidth, int dstHeight, boolean verticalFlip) {
        if (dstWidth == 0 && dstHeight == 0) {
            YYLog.error(this, Constant.MEDIACODE_PREPRO+"invalid encoder width or height");
            return false;
        }

        if(dstHeight == mOutputHeight && dstWidth == mOutputWidth && mVerticalFlip == verticalFlip)
            return false;

        //只需要算出实际裁剪后的长宽，不需要实际生成纹理了
        deInit();
        mOutputWidth = dstWidth;
        mOutputHeight = dstHeight;
        mVerticalFlip = verticalFlip;
        init();
        mOffScreen.adjustTexture(sample.mWidth, sample.mHeight, mOutputWidth, mOutputHeight);
        if(mVerticalFlip) {
            mOffScreen.setTextureFlipY(MAIN_TEXTURE);
        }
        return true;
    }

    public void clip(YYMediaSample sample, int dstWidth, int dstHeight, boolean verticalFlip) {
        if(sample.mWidth == dstWidth && sample.mHeight == dstHeight && verticalFlip == mVerticalFlip)
            return;

        checkOutputChanged(sample, dstWidth, dstHeight, verticalFlip);
        if (mInited) {
            //1、根据计算出的长宽，实际作用到offScreen texture
            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    // again, only really need to
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOffScreenFrameBuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                    mOffScreenTextureId, 0);
            mOffScreen.drawFrame(sample.mTextureId, sample.mTransform);

            sample.mWidth = mOutputWidth;
            sample.mHeight = mOutputHeight;
            sample.mTextureId = mOffScreenTextureId;

            System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, sample.mTransform, 0, sample.mTransform.length);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            //2、传递计算出的裁剪长宽，由后面的filter真正作用到纹理，减少一次离屏渲染
            sample.mClipWidth = mOutputWidth;
            sample.mClipHeight = mOutputHeight;

            mLastSampleWidth = sample.mWidth;
            mLastSampleHeight = sample.mHeight;
        }
    }
}
