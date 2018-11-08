package com.ycloud.gles;

import android.opengl.GLES20;

import com.ycloud.gpuimage.adapter.GlTextureImageReader;
import com.ycloud.svplayer.MediaInfo;
import com.ycloud.utils.ImageStorageUtil;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Created by kele on 20/6/2018.
 */

public class I420ToRgbRender {

    private GLShaderProgram mGLShaderProgram = new GLShaderProgram();

    private int[]   mTextureIds = new int[3];
    private int     mVertextBufferId = -1;

    private FloatBuffer  mVertexBuffers = null;

    protected int mFrameBuffer;
    protected int mFrameBufferTexture;

    private int mWidth = 0;
    private int mHeight = 0;

    private int mPlanWidth = 0;
    private int mPlanHeight = 0;

    private int mPositionLoc = -1;
    private int mTexCoordLoc = -1;

    private int mModelViewUniform = -1;
    private int mProjectionUniform = -1;
    protected IntBuffer mOldFramebuffer;

    protected ByteBuffer mUByteBuffer= null;
    protected ByteBuffer mVByteBuffer = null;

    protected int mDebugCnt = 0;

    //TODO[debug]
    protected GlTextureImageReader mImagerReader = null;

    public I420ToRgbRender() {
        for (int i = 0; i < mTextureIds.length; ++i) {
            mTextureIds[i] = -1;
        }

        mVertexBuffers = ByteBuffer.allocateDirect(OpenGlUtils.VERTEXT_MATRIX4FV_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffers.put(OpenGlUtils.MATRIX4FV_VERTEX).rewind();
    }

    public void init(int width, int height) {
        mWidth = width;
        mHeight = height;

        mPlanWidth = width;
        mPlanHeight = height;

        mUByteBuffer = ByteBuffer.allocate((mPlanWidth* mPlanHeight) >> 2);
        mVByteBuffer = ByteBuffer.allocate((mPlanWidth* mPlanHeight) >> 2);

        mGLShaderProgram.setProgram(VertexShader.VERTEX_SHADER, I420FragShader.I420_FRAGMENT_SHADER);
        mGLShaderProgram.useProgram();

        mPositionLoc = mGLShaderProgram.getHandle("a_position");

        mTexCoordLoc = mGLShaderProgram.getHandle("a_texCoord");
        mModelViewUniform = mGLShaderProgram.getHandle( "u_modelView");
        mProjectionUniform = mGLShaderProgram.getHandle( "u_projection");

        mGLShaderProgram.setUniform1i("u_texY",0);
        mGLShaderProgram.setUniform1i("u_texU", 1);
        mGLShaderProgram.setUniform1i("u_texV", 2);
        GLES20.glUseProgram(0);

        createTextures();
        createBufferIds();
        createFrameBuffers();

        mOldFramebuffer = IntBuffer.allocate(1);

        mImagerReader = new GlTextureImageReader(mWidth, mHeight, true);
    }

    private void createTextures() {
        GLES20.glGenTextures(mTextureIds.length, mTextureIds, 0);

        final int[] texArray = {GLES20.GL_TEXTURE0, GLES20.GL_TEXTURE1, GLES20.GL_TEXTURE2};

        for (int i = 0; i < 3; ++i) {
            GLES20.glActiveTexture(texArray[i]);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[i]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private void destroyTextures() {
        for(int i = 0;i < mTextureIds.length;++i) {
            mTextureIds[i] = OpenGlUtils.safeDeleteTexture(mTextureIds[i]);
        }
    }

    private void createFrameBuffers() {
        int[] frameBufferArrays = new int[1];
        int[] frameBufferTextureArrays = new int[1];
        OpenGlUtils.createFrameBuffer(mWidth, mHeight, frameBufferArrays, frameBufferTextureArrays, 1);

        mFrameBuffer = frameBufferArrays[0];
        mFrameBufferTexture = frameBufferTextureArrays[0];
    }

    private void destroyFramebuffer() {
        if (mFrameBufferTexture != -1 && mFrameBuffer != -1) {
            int[] frameBufferArrays = new int[1];
            int[] frameBufferTextureArrays = new int[1];

            frameBufferArrays[0] = mFrameBuffer;
            frameBufferTextureArrays[0] = mFrameBufferTexture;
            OpenGlUtils.releaseFrameBuffer(1, frameBufferTextureArrays, frameBufferArrays);

            mFrameBufferTexture = -1;
            mFrameBuffer = -1;
        }
    }

    private void createBufferIds() {
        int[] id = new int[1];
        GLES20.glGenBuffers(id.length, id, 0);
        mVertextBufferId = id[0];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertextBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, OpenGlUtils.VERTEXT_MATRIX4FV_SIZE, mVertexBuffers, GLES20.GL_STATIC_DRAW);
        OpenGlUtils.checkGlError("glBufferData()");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void destroyBufferIds() {
        if(mVertextBufferId != -1) {
            int[] id = new int[1];
            id[0] = mVertextBufferId;
            GLES20.glDeleteBuffers(id.length, id, 0);
            mVertextBufferId = -1;
        }
    }

    public void destroy() {

        if (mOldFramebuffer != null) {
            mOldFramebuffer.clear();
            mOldFramebuffer = null;
        }

        destroyBufferIds();
        destroyFramebuffer();
        destroyTextures();
    }

    public int getFrameBufferTexture() {
        return mFrameBufferTexture;
    }


    public void render(YYMediaSample sample) {

        //check the planWidth modify
        if(mPlanWidth != sample.mPlanWidth || mPlanHeight != sample.mPlanHeight) {
            updateVertexBuffer(sample);

            mPlanWidth = sample.mPlanWidth;
            mPlanHeight = sample.mPlanHeight;

            mUByteBuffer = ByteBuffer.allocate((mPlanWidth* mPlanHeight) >> 2);
            mVByteBuffer = ByteBuffer.allocate((mPlanWidth* mPlanHeight) >> 2);
        }

        storeOldFBO();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        mGLShaderProgram.useProgram();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertextBufferId);
        // (x, y)
        GLES20.glVertexAttribPointer(mPositionLoc, 2, GLES20.GL_FLOAT, false, OpenGlUtils.VERTEXT_STRIDE, 0);
        OpenGlUtils.checkGlError("glVertexAttribPointer()");
        GLES20.glEnableVertexAttribArray(mPositionLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray()");
        // (u, v)
        GLES20.glVertexAttribPointer(mTexCoordLoc, 2, GLES20.GL_FLOAT, false, OpenGlUtils.VERTEXT_STRIDE, OpenGlUtils.VERTEXT_UV_OFFSET);
        OpenGlUtils.checkGlError("glVertexAttribPointer()");
        GLES20.glEnableVertexAttribArray(mTexCoordLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray()");

        setupTextureData(sample.mPlanWidth, sample.mPlanHeight, sample.mDataByteBuffer);
        OpenGlUtils.checkGlError("setupTextureData()");
        GLES20.glUniformMatrix4fv(mModelViewUniform, 1, false, OpenGlUtils.MATRIX4FV_IDENTITY, 0);

        OpenGlUtils.checkGlError("glUniformMatrix4fv(mModelViewUniform)");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        OpenGlUtils.checkGlError("glDrawArrays()");
        // clean all
        GLES20.glDisableVertexAttribArray(mPositionLoc);
        GLES20.glDisableVertexAttribArray(mTexCoordLoc);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glUseProgram(0);
        //download the iamge.
//        if(mDebugCnt++ < 5) {
//            ImageStorageUtil.saveToFile(ImageStorageUtil.createImgae(mImagerReader.read(mFrameBufferTexture, mWidth, mHeight), mWidth, mHeight));
//        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        recoverOldFBO();
    }

    protected void storeOldFBO() {
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, mOldFramebuffer);
    }

    protected void recoverOldFBO() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOldFramebuffer.get(0));
    }

    public static void calculateVertexUV(float[] vertexArray, YYMediaSample sample) {
        final float dx = (float) sample.mWidth / (float) sample.mPlanWidth;
        final float dy = (float) sample.mHeight / (float) sample.mPlanHeight;

        vertexArray[2] = 0.0f;
        vertexArray[3] = 0.0f;
        vertexArray[6] = dx;
        vertexArray[7] = 0.0f;
        vertexArray[10] = 0.0f;
        vertexArray[11] = dy;
        vertexArray[14] = dx;
        vertexArray[15] = dy;
    }

    private void printVertex(YYMediaSample sample, float[] vertexArray) {
        final String[] txtDisplayMode = {"Extend", "Scale", "Crop"};
        final String[] txtRotateMode = {"None", "Rotate90", "Rotate180", "Rotate270"};
        final String[] txtOrientateMode = {"Vertical", "Horizontal"};
        int mDisplayMode = 0;
        String text = String.format("[I420ToRgbRender] frameSize:%dx%d, planeSize:%dx%d,\nVertex =\n%.6f, %.6f, %.6f, %.6f\n%.6f, %.6f, %.6f, %.6f\n%.6f, %.6f, %.6f, %.6f\n%.6f, %.6f, %.6f, %.6f\n",
                sample.mWidth, sample.mHeight, sample.mPlanWidth, sample.mPlanHeight,
                vertexArray[0], vertexArray[1], vertexArray[2], vertexArray[3], vertexArray[4], vertexArray[5], vertexArray[6], vertexArray[7],
                vertexArray[8], vertexArray[9], vertexArray[10], vertexArray[11], vertexArray[12], vertexArray[13], vertexArray[14], vertexArray[15]);
        YYLog.info(this, text);
    }

    public void updateVertexBuffer(YYMediaSample sample) {

        float[] vertexArray = new float[OpenGlUtils.MATRIX4FV_VERTEX.length];
        mVertexBuffers.get(vertexArray).rewind();
        calculateVertexUV(vertexArray, sample);
        calculateVertexXY(vertexArray);
        mVertexBuffers.put(vertexArray).rewind();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertextBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, OpenGlUtils.VERTEXT_MATRIX4FV_SIZE, mVertexBuffers, GLES20.GL_STATIC_DRAW);
        OpenGlUtils.checkGlError("glBufferData()");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        printVertex(sample, vertexArray);
    }

    public static void calculateVertexXY(float[] vertexArray) {
        vertexArray[0]  = -1.0f;
        vertexArray[1]  =  1.0f;
        vertexArray[4]  =  1.0f;
        vertexArray[5]  =  1.0f;
        vertexArray[8]  = -1.0f;
        vertexArray[9]  = -1.0f;
        vertexArray[12] =  1.0f;
        vertexArray[13] = -1.0f;

    }

    private void setupTextureData(int strideWidth, int strideHeight, final ByteBuffer data) {

        //assume plane size = strideWidth * strideHeight.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        int origPos = data.position();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, strideWidth, strideHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data);
        origPos = data.position();
        data.position(origPos + strideWidth*strideHeight);

        strideWidth >>= 1;
        strideHeight >>= 1;

        mUByteBuffer.put(data.array(), data.position(), strideHeight*strideWidth);
        mUByteBuffer.position(0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);

        origPos = data.position();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, strideWidth, strideHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mUByteBuffer);
        data.position(origPos+strideHeight*strideWidth);

        mVByteBuffer.put(data.array(), data.position(), strideHeight*strideWidth);
        mVByteBuffer.position(0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[2]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, strideWidth, strideHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mVByteBuffer);
    }
}
