package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.Rotation;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.FloatBuffer;

/**
 * Created by Administrator on 2018/10/8.
 *
 */

public class RotateFilter extends BaseFilter {
    private static final String TAG = "RotateFilter";
    private int mRotateAngle = 0;

    public static final float TEXTURE_NO_ROTATION[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    public static final float TEXTURE_ROTATED_90[] = {
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f,
    };
    public static final float TEXTURE_ROTATED_180[] = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };
    public static final float TEXTURE_ROTATED_270[] = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
    };

    private static float flip(final float i) {
        if (i == 0.0f) {
            return 1.0f;
        }
        return 0.0f;
    }

    public static float[] getRotation(final Rotation rotation, final boolean flipHorizontal,
                                      final boolean flipVertical) {
        float[] rotatedTex;
        switch (rotation) {
            case ROTATION_90:
                rotatedTex = TEXTURE_ROTATED_90;
                break;
            case ROTATION_180:
                rotatedTex = TEXTURE_ROTATED_180;
                break;
            case ROTATION_270:
                rotatedTex = TEXTURE_ROTATED_270;
                break;
            case NORMAL:
            default:
                rotatedTex = TEXTURE_NO_ROTATION;
                break;
        }
        if (flipHorizontal) {
            rotatedTex = new float[]{
                    flip(rotatedTex[0]), rotatedTex[1],
                    flip(rotatedTex[2]), rotatedTex[3],
                    flip(rotatedTex[4]), rotatedTex[5],
                    flip(rotatedTex[6]), rotatedTex[7],
            };
        }
        if (flipVertical) {
            rotatedTex = new float[]{
                    rotatedTex[0], flip(rotatedTex[1]),
                    rotatedTex[2], flip(rotatedTex[3]),
                    rotatedTex[4], flip(rotatedTex[5]),
                    rotatedTex[6], flip(rotatedTex[7]),
            };
        }
        return rotatedTex;
    }

    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.initExt(outputHeight, outputWidth, isExtTexture, oFContext);   // !! switch width and height
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    public void setRotateAngle(int angle) {
        mRotateAngle = angle;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {

        OpenGlUtils.checkGlError("Rotate filter processMediaSample start");
        storeOldFBO();

        // 处理texture1，总是有效的
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);

        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        FloatBuffer textureCoordBuffer = OpenGlUtils.createFloatBuffer(getRotation(Rotation.fromInt(mRotateAngle), false, false));

        drawSquare(sample.mTextureId, OpenGlUtils.VERTEXCOORD_BUFFER, textureCoordBuffer, OpenGlUtils.IDENTITY_MATRIX);

        System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, sample.mTransform, 0, sample.mTransform.length);
        sample.mTextureId = mFrameBufferTexture[0];
        sample.mFrameBufferId = mFrameBuffer[0];
        sample.mTextureTarget = GLES20.GL_TEXTURE_2D;
        sample.mWidth = mOutputWidth;
        sample.mHeight = mOutputHeight;
        recoverOldFBO();
        OpenGlUtils.checkGlError("processMediaSample TransformTextureFitler end");
        return true;
    }

}
