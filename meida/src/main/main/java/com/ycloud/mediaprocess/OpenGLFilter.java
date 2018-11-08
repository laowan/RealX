package com.ycloud.mediaprocess;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.opengl.GLES20;
import android.util.Log;

import com.ycloud.ymrmodel.Rotation;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.TextureRotationUtil;
import com.ycloud.utils.YYLog;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class OpenGLFilter {
    private static final String TAG = "OpenGLFilter";
    // shader
    private final String mVertexShader = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "}";
    private final String mFragmentShader = "" +
            "varying highp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "}";

    // program
    protected int mGLProgId;
    protected int mGLAttribPosition;
    protected int mGLUniformTexture;
    protected int mGLAttribTextureCoordinate;
    protected int mOutputWidth;
    protected int mOutputHeight;
    private boolean mIsInitialized;
    private boolean mSmallPicScale = true;

    // framebuffer texture
    final private int FRAMEBUFFER_NUM = 1;
    private int[] mFrameBuffer = null;
    private int[] mFrameBufferTexture = null;
    private int mImgTexture = OpenGlUtils.NO_TEXTURE;

    // buffer
    final int VERTEXCOORDS_LEN = 8;
    float mVertexCoords[] = new float[VERTEXCOORDS_LEN];

    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLSlipTextureCoordBuffer;
    private ByteBuffer mOutputByteBuffer = null;

    private boolean mResIsBeUpdate = false;
    private int mTextureMaxSize = -1;
    private boolean mLayoutClipToFill = false;
    private Bitmap bmp = null;

    public OpenGLFilter() {
    }

    public void init() {
        initProgram();
        initTexture();
        initBuffer();
        mTextureMaxSize = getTextureMaxSize();
        mIsInitialized = true;
    }

    public void release() {
        GLES20.glDeleteProgram(mGLProgId);
        destroyFramebuffers();
        destroyTextures();
        if (mOutputByteBuffer != null) {
            mOutputByteBuffer.clear();
        }
        if (bmp != null) {
            bmp.recycle();
            bmp = null;
        }
        mIsInitialized = false;
    }

    private void initProgram() {
        mGLProgId = OpenGlUtils.loadProgram(mVertexShader, mFragmentShader);
        mGLAttribPosition = GLES20.glGetAttribLocation(mGLProgId, "position");
        mGLUniformTexture = GLES20.glGetUniformLocation(mGLProgId, "inputImageTexture");
        mGLAttribTextureCoordinate = GLES20.glGetAttribLocation(mGLProgId,
                "inputTextureCoordinate");
        mIsInitialized = true;
    }

    private void initTexture() {
        mImgTexture = OpenGlUtils.createTexture();
    }

    private void initBuffer() {
        mGLCubeBuffer = ByteBuffer.allocateDirect(mVertexCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] flipTexture = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true);
        mGLSlipTextureCoordBuffer = ByteBuffer.allocateDirect(flipTexture.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mGLSlipTextureCoordBuffer.put(flipTexture).position(0);
    }

    private void destroyTextures() {
        int[] texture = new int[1];
        texture[0] = mImgTexture;
        GLES20.glDeleteTextures(1, texture, 0);
    }

    // 保存处理后的结果
    private void saveProcessedImg(String imgPathOut) {
        mOutputByteBuffer.clear();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mOutputByteBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        BufferedOutputStream bos = null;
        try {
            try {
                bos = new BufferedOutputStream(new FileOutputStream(imgPathOut));
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (bmp != null && !bmp.isRecycled()) {
                bmp.copyPixelsFromBuffer(mOutputByteBuffer);
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            }
        }catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        } finally {
            if (bos != null)
                try {
                    bos.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }
    }

    // 计算图片显示坐标
    private void calcCoord(float viewWidth, float viewHeight, float imgWidth, float imgHeight) {
        float up = 1, down = -1, left = -1, right = 1;
        float widthRatio = imgWidth / viewWidth;
        float heightRatio = imgHeight / viewHeight;
        if (!mSmallPicScale && imgWidth <= viewWidth && imgHeight <= viewHeight) {
            // 居中显示
            up = 1 * heightRatio;
            down = -1 * heightRatio;
            left = -1 * widthRatio;
            right = 1 * widthRatio;
        }
        else {
            float viewRatio = viewHeight / viewWidth;
            float imgRatio = imgHeight / imgWidth;
            if (!(viewRatio == imgRatio)) {
                if (viewRatio > imgRatio) {
                    // 左右都取，上下居中
                    float imgDscHeight = imgHeight / widthRatio;
                    float tmpRatio = imgDscHeight / viewHeight;
                    up = 1 * tmpRatio;
                    down = -1 * tmpRatio;
                }
                else {
                    // 上下都取，左右居中
                    float imgDscWidth = imgWidth / heightRatio;
                    float tmpRatio = imgDscWidth / viewWidth;
                    left = -1 * tmpRatio;
                    right = 1 * tmpRatio;
                }
            }
            else {
                // 全屏显示
            }
        }

        mVertexCoords[0] = left; mVertexCoords[1] = down;
        mVertexCoords[2] = right; mVertexCoords[3] = down;
        mVertexCoords[4] = left; mVertexCoords[5] = up;
        mVertexCoords[6] = right; mVertexCoords[7] = up;
        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(mVertexCoords).position(0);
    }

    public void setImageClipFullScreen(boolean bValue) {
        mLayoutClipToFill = bValue;
    }

    public void setSmallPictureScale(boolean b) {
        mSmallPicScale = b;
    }

    private void calcCoordClip(float viewWidth, float viewHeight, float imgWidth, float imgHeight) {
        float up = 1, down = -1, left = -1, right = 1;

        float viewRatio = viewWidth / viewHeight ;
        float imgRatio = imgWidth / imgHeight ;

        if (imgRatio > viewRatio) {  // 图片宽高比 大于 输出view宽高比,上下拉伸，左右裁剪

            float tmpRatio = imgRatio / viewRatio;
            left = -1 * tmpRatio;
            right = 1 * tmpRatio;
        } else {                     // 图片宽高比 小于 输出view宽高比， 左右拉伸，上下裁剪

            float tmpRatio = viewRatio / imgRatio;
            up = 1 * tmpRatio;
            down = -1 * tmpRatio;
        }

        mVertexCoords[0] = left; mVertexCoords[1] = down;
        mVertexCoords[2] = right; mVertexCoords[3] = down;
        mVertexCoords[4] = left; mVertexCoords[5] = up;
        mVertexCoords[6] = right; mVertexCoords[7] = up;
        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(mVertexCoords).position(0);
    }

    private void draw(final int textureId, final FloatBuffer cubeBuffer,
                      final FloatBuffer textureBuffer) {
        GLES20.glUseProgram(mGLProgId);
        if (!mIsInitialized) {
            return;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    // 设置输出图片大小
    public void setOutputSize(int width, int height) {
        if (width < 0 || height < 0) {
            return;
        }

        if (mOutputWidth != width || mOutputHeight != height) {
            mOutputWidth = width;
            mOutputHeight = height;
            mResIsBeUpdate = true;
        }
    }

    private void updateResource() {
        if (false == mResIsBeUpdate) {
            return;
        }

        destroyFramebuffers();
        mFrameBuffer = new int[FRAMEBUFFER_NUM];
        mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
        OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);

        if (mOutputByteBuffer != null) {
            mOutputByteBuffer.clear();
            mOutputByteBuffer = null;
        }
        mOutputByteBuffer = ByteBuffer.allocateDirect(mOutputWidth * mOutputHeight * 4).order(ByteOrder.nativeOrder());
        if (bmp == null) {
            bmp = Bitmap.createBitmap(mOutputWidth, mOutputHeight, Bitmap.Config.ARGB_8888);
        }
        mResIsBeUpdate = false;
    }

    private int getImgRotationAngle(String imgPath) {
        int angle = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(imgPath);
        } catch (IOException ex) {
            Log.e("liucy", "cannot read exif" + ex);
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        angle = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        angle = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        angle = 270;
                        break;
                }
            }
        }
        return angle;
    }

    Bitmap adjustImg(Bitmap src, int angle, float scale)
    {
        if (angle != 0 || scale != 1.0f) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(angle);
            try {
                Bitmap res = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);
                src.recycle();
                return res;
            } catch (OutOfMemoryError ex) {
                ex.printStackTrace();
                return null;
            }
        }
        else {
            return src;
        }
    }

    private Bitmap imgPreProcess(String imgPath) {
        Bitmap imgSrc = BitmapFactory.decodeFile(imgPath);
        if (imgSrc == null) {
            YYLog.error(this, "Decode file " + imgPath + " failed !");
            return null;
        }

        Bitmap imgDsc = null;
        int imgWidth = imgSrc.getWidth();
        int imgHeight = imgSrc.getHeight();
        int maxSize = Math.max(imgWidth, imgHeight);
        float scale = 1.0f;
        if (maxSize > mTextureMaxSize) {
            scale =  (float)mTextureMaxSize / (float)maxSize;
        }

        int imgRotationAngle = getImgRotationAngle(imgPath);
        imgDsc = adjustImg(imgSrc, imgRotationAngle, scale);
        return imgDsc;
    }

    // 处理图片接口
    public void imgProcess(String imgPathIn, String imgPathOut) {
        if (null == imgPathIn || null == imgPathOut) {
            return;
        }

        // 根据输出的宽高重新申请资源
        updateResource();

        // 图片预处理
        Bitmap img = imgPreProcess(imgPathIn);
        if (img == null) {
            return;
        }

        // 计算坐标
        if (!mLayoutClipToFill) {
            calcCoord(mOutputWidth, mOutputHeight, img.getWidth(), img.getHeight());
        } else {
            calcCoordClip(mOutputWidth, mOutputHeight, img.getWidth(), img.getHeight());
        }

        OpenGlUtils.updateTexture(mImgTexture, img);
        img.recycle();

        // 绘制
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        draw(mImgTexture, mGLCubeBuffer, mGLSlipTextureCoordBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // 保存
        saveProcessedImg(imgPathOut);
    }

    private void destroyFramebuffers() {
        if (mFrameBufferTexture != null) {
            GLES20.glDeleteTextures(mFrameBufferTexture.length, mFrameBufferTexture, 0);
            mFrameBufferTexture = null;
        }
        if (mFrameBuffer != null) {
            GLES20.glDeleteFramebuffers(mFrameBuffer.length, mFrameBuffer, 0);
            mFrameBuffer = null;
        }
    }

    /**
     * 获取TextureMaxSize的值
     * @return
     */
    private int getTextureMaxSize() {
        int textureMaxSize = 0;
        IntBuffer buffer = IntBuffer.allocate(4);
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, buffer);
        textureMaxSize = buffer.get(0);
        return textureMaxSize;
    }
}
