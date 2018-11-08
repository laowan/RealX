package com.ycloud.gpuimagefilter.filter;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import com.ycloud.api.process.ImageProcessListener;
import com.ycloud.gpuimage.adapter.GlTextureImageReader;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Administrator on 2018/5/21.
 */

public class ImageViewFilter extends BaseFilter {
    private static final String TAG = "ImageViewFilter";
    private Bitmap bitmap = null;
    private ImageProcessListener mImageProcessListener = null;
    private String mImagePath = null;
    private int mImageHash = 0;

    public void setImageProcessListener(ImageProcessListener listener) {
        mImageProcessListener = listener;
    }

    public void setImagePath(String path) {
        mImagePath = path;
    }

    public void setImageHash(int hash) {
        mImageHash = hash;
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
    }

    private void preProcessMultiPliedAlpha(byte[] pixBuf) {
        float a = 0, r = 0, g = 0, b = 0;
        for(int i = 0; i < pixBuf.length - 4; i += 4) {
            // 1. convert range from -127 ~ 128 to range 0 ~ 255
            r  = pixBuf[i] & 0xff;
            g  = pixBuf[i+1] & 0xff;
            b  = pixBuf[i+2] & 0xff;
            a  = pixBuf[i+3] & 0xff;

            // 2. alpha preMultiply
            a /= 255;
            r *= a;
            g *= a;
            b *= a;

            // 3. set back the preMultiply value.
            pixBuf[i] = (byte)r;
            pixBuf[i + 1] = (byte)g;
            pixBuf[i + 2] = (byte)b;
        }
    }

    public void Transform2DTextureToBitmap(int textureId, int width, int height, Bitmap bitmap, boolean preMultiAlpha) {
        int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        OpenGlUtils.checkGlError("glGenFramebuffers ");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);
        OpenGlUtils.checkGlError("glBindFramebuffer ");
        if(frameBuffers[0] != 0 && textureId != 0 && width > 0 && height > 0) {
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
            OpenGlUtils.checkGlError("glFramebufferTexture2D ");
            ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
            mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
            OpenGlUtils.checkGlError("glReadPixels ");
            if (bitmap != null) {
                /** 带Alpha通道的特效，要先把RGB数据与Alpha数据预乘，Android位图默认是开启预乘的，使用预乘的格式。*/
                if (preMultiAlpha) {
                    preProcessMultiPliedAlpha(mByteBuffer.array());
                }
                bitmap.copyPixelsFromBuffer(mByteBuffer);
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
    }


    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {

        OpenGlUtils.checkGlError("SquareFilter processMediaSample start");
        storeOldFBO();

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(sample.mWidth, sample.mHeight, Bitmap.Config.ARGB_8888);
        }
        Transform2DTextureToBitmap(sample.mTextureId, sample.mWidth, sample.mHeight, bitmap, sample.mPreMultiplyAlpha);

        OpenGlUtils.checkGlError("processMediaSample SquareFilter end");
        recoverOldFBO();

        if (mImageProcessListener != null) {
            mImageProcessListener.onProcessFinish(bitmap, mImagePath, mImageHash);
        }

        return true;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (bitmap != null) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }
        YYLog.info(TAG, "destroy");
    }
}
