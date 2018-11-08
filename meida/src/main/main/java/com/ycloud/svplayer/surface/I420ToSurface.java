package com.ycloud.svplayer.surface;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.ycloud.svplayer.MediaInfo;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by DZHJ on 2017/8/28.
 */

public class I420ToSurface {

    public static  final  String TAG = I420ToSurface.class.getSimpleName();

    public static final String VERTEX_SHADER =
            "precision mediump float;    \n" +
                    "precision mediump int;      \n" +
                    "attribute vec2 a_position;  \n" +
                    "attribute vec2 a_texCoord;  \n" +
                    "varying vec2 v_texCoord;    \n" +
                    "uniform mat4 u_modelView;   \n" +
                    "uniform mat4 u_projection;  \n" +
                    "void main()                 \n" +
                    "{                           \n" +
                    "   vec4 value = vec4(a_position, 0.0, 1.0); \n" +
                    "   gl_Position = u_modelView * value;       \n" +
                    "   v_texCoord = a_texCoord; \n" +
                    "}                           \n";

    public static final String I420_FRAGMENT_SHADER =
            "precision mediump float;  \n" +
                    "precision mediump int;    \n" +
                    "varying vec2 v_texCoord;  \n" +
                    "uniform sampler2D u_texY; \n" +
                    "uniform sampler2D u_texU; \n" +
                    "uniform sampler2D u_texV; \n" +
                    "const mat3 op = mat3(1.164, 1.164, 1.164, 0.0, -0.391, 2.018, 1.596, -0.813, 0.0); \n" +
                    "void main(void)   \n" +
                    "{                 \n" +
                    "   vec3 rgb, yuv; \n" +
                    "   yuv.x = texture2D(u_texY, v_texCoord).r - 0.0625; \n" +
                    "   yuv.y = texture2D(u_texU, v_texCoord).r - 0.5;    \n" +
                    "   yuv.z = texture2D(u_texV, v_texCoord).r - 0.5;    \n" +
                    "   rgb = op * yuv;                 \n" +
                    "   rgb.r = clamp(rgb.r, 0.0, 1.0); \n" +
                    "   rgb.g = clamp(rgb.g, 0.0, 1.0); \n" +
                    "   rgb.b = clamp(rgb.b, 0.0, 1.0); \n" +
                    "   gl_FragColor = vec4(rgb, 1.0);  \n" +
                    "}                                  \n";

    // rotation mode
    public static final int ROTATE_MODE_UNKNOWN = -1;
    public static final int ROTATE_MODE_NONE    = 0;
    public static final int ROTATE_MODE_90      = 1;
    public static final int ROTATE_MODE_180     = 2;
    public static final int ROTATE_MODE_270     = 3;


    private int mProgramId = -1;
    private int mPositionLoc = -1;
    private int mTexCoordLoc = -1;

    private int mModelViewUniform = -1;
    private int mProjectionUniform = -1;

    private int[] mTextureUniform = new int[3];
    private int[] mTextureIds = new int[3];
    private float[] mFrameModelViewVertexArray = new float[OpenGlUtils.MATRIX4FV_VERTEX.length];

    private boolean mInited = false;
    private int mRotateMode = ROTATE_MODE_UNKNOWN;
    // 0: offscreen texture, 1: frame texture.
    private FloatBuffer mVertexBuffers = null;

    private int mBufferId = 0;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    public I420ToSurface(int surfaceWidth,int surfaceHeight){
        mSurfaceWidth =surfaceWidth;
        mSurfaceHeight = surfaceHeight;
    }
    public void init() {
        if ((mProgramId = OpenGlUtils.createProgram(VERTEX_SHADER, I420_FRAGMENT_SHADER)) <= 0) {
            throw new RuntimeException("I420ToSurface created failed.");
        }
        GLES20.glUseProgram(mProgramId);
        mPositionLoc = GLES20.glGetAttribLocation(mProgramId, "a_position");
        mTexCoordLoc = GLES20.glGetAttribLocation(mProgramId, "a_texCoord");
        mModelViewUniform = GLES20.glGetUniformLocation(mProgramId, "u_modelView");
        mProjectionUniform = GLES20.glGetUniformLocation(mProgramId, "u_projection");
        mTextureUniform[0] = GLES20.glGetUniformLocation(mProgramId, "u_texY");
        mTextureUniform[1] = GLES20.glGetUniformLocation(mProgramId, "u_texU");
        mTextureUniform[2] = GLES20.glGetUniformLocation(mProgramId, "u_texV");
        GLES20.glUniform1i(mTextureUniform[0], 0);
        GLES20.glUniform1i(mTextureUniform[1], 1);
        GLES20.glUniform1i(mTextureUniform[2], 2);
        GLES20.glUseProgram(0);

        setupTexture();
        createBuffers();

        mInited = true;
    }

    private void createBuffers() {
        mVertexBuffers = ByteBuffer.allocateDirect(OpenGlUtils.VERTEXT_MATRIX4FV_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffers.put(OpenGlUtils.MATRIX4FV_VERTEX).rewind();

        int[] id = new int[1];
        GLES20.glGenBuffers(id.length, id, 0);
        mBufferId = id[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, OpenGlUtils.VERTEXT_MATRIX4FV_SIZE, mVertexBuffers, GLES20.GL_STATIC_DRAW);
        OpenGlUtils.checkGlError("glBufferData()");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void setupTexture() {

        GLES20.glGenTextures(mTextureIds.length, mTextureIds, 0);

        final int[] texArray = {GLES20.GL_TEXTURE0, GLES20.GL_TEXTURE1, GLES20.GL_TEXTURE2};

        for (int i = 0; i < 3; ++i) {
            GLES20.glActiveTexture(texArray[i]);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[i]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }
    }

    private void setupTextureData(int strideWidth, int strideHeight, final ByteBuffer[] data) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, strideWidth, strideHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data[0]);

        strideWidth >>= 1;
        strideHeight >>= 1;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, strideWidth, strideHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data[1]);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[2]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, strideWidth, strideHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data[2]);
    }

    public void setupModelView(final float[] data) {
        if(mModelViewUniform >= 0) {
            GLES20.glUniformMatrix4fv(mModelViewUniform, 1, false, data, 0);
        }
    }

    public void drawFrame(MediaInfo mediaInfo, final ByteBuffer[] data) {
        if (mInited == false) {
            YYLog.error(TAG, "drawFrame before Init");
        }

        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        GLES20.glClearColor(OpenGlUtils.CLEAR_COLOR[0],OpenGlUtils.CLEAR_COLOR[1],OpenGlUtils.CLEAR_COLOR[2],OpenGlUtils.CLEAR_COLOR[3]);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(mProgramId);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBufferId);
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

        setupTextureData(mediaInfo.planeWidth, mediaInfo.planeHeight,data);

        setupModelView(mFrameModelViewVertexArray);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        OpenGlUtils.checkGlError("glDrawArrays()");
        // clean all
        GLES20.glDisableVertexAttribArray(mPositionLoc);
        GLES20.glDisableVertexAttribArray(mTexCoordLoc);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glUseProgram(0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void printVertex(MediaInfo mMediaInfo, float[] vertexArray) {
        final String[] txtDisplayMode = {"Extend", "Scale", "Crop"};
        final String[] txtRotateMode = {"None", "Rotate90", "Rotate180", "Rotate270"};
        final String[] txtOrientateMode = {"Vertical", "Horizontal"};
        int mDisplayMode = 0;
        int mOrientateMode = 0;
        String text = String.format(" frameSize:%dx%d, planeSize:%dx%d, surfaceSize:%dx%d\ndisplayMode:%s, Rotation:%s, Orientation:%s,\nVertex =\n%.6f, %.6f, %.6f, %.6f\n%.6f, %.6f, %.6f, %.6f\n%.6f, %.6f, %.6f, %.6f\n%.6f, %.6f, %.6f, %.6f\n",
                mMediaInfo.width, mMediaInfo.height, mMediaInfo.planeWidth, mMediaInfo.planeHeight, 540, 960,
                (mDisplayMode < 0) ? "Unknown" : txtDisplayMode[mDisplayMode],
                (mRotateMode < 0) ? "Unknown" : txtRotateMode[mRotateMode],
                (mOrientateMode < 0) ? "Unknown" : txtOrientateMode[mOrientateMode],
                vertexArray[0], vertexArray[1], vertexArray[2], vertexArray[3], vertexArray[4], vertexArray[5], vertexArray[6], vertexArray[7],
                vertexArray[8], vertexArray[9], vertexArray[10], vertexArray[11], vertexArray[12], vertexArray[13], vertexArray[14], vertexArray[15]);
        YYLog.info(this, text);
    }

    public static void calculateVertexUV(float[] vertexArray,MediaInfo mediaInfo) {
        final float dx = (float) mediaInfo.width / (float) mediaInfo.planeWidth;
        final float dy = (float) mediaInfo.height / (float) mediaInfo.planeHeight;

        vertexArray[2] = 0.0f;
        vertexArray[3] = 0.0f;
        vertexArray[6] = dx;
        vertexArray[7] = 0.0f;
        vertexArray[10] = 0.0f;
        vertexArray[11] = dy;
        vertexArray[14] = dx;
        vertexArray[15] = dy;
    }

    public void updateVertexBuffer(MediaInfo mediaInfo) {

        float[] vertexArray = new float[OpenGlUtils.MATRIX4FV_VERTEX.length];
        mVertexBuffers.get(vertexArray).rewind();
        calculateVertexUV(vertexArray, mediaInfo);
        calculateVertexXY(vertexArray);
        mVertexBuffers.put(vertexArray).rewind();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, OpenGlUtils.VERTEXT_MATRIX4FV_SIZE, mVertexBuffers, GLES20.GL_STATIC_DRAW);
        OpenGlUtils.checkGlError("glBufferData()");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        printVertex(mediaInfo, vertexArray);
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

    public void setRotateMode(int mode) {
        switch (mode) {
            case 0:
                mode = ROTATE_MODE_NONE;
                break;
            case 90:
                mode=ROTATE_MODE_270;
                break;
            case 180:
                mode=ROTATE_MODE_180;
                break;
            case 270:
                mode=ROTATE_MODE_90;
                break;
        }
        if(mRotateMode != mode) {
            mRotateMode = mode;
            System.arraycopy(OpenGlUtils.MATRIX4FV_IDENTITY, 0, mFrameModelViewVertexArray, 0, mFrameModelViewVertexArray.length);
            if(mRotateMode == ROTATE_MODE_90) {
                Matrix.rotateM(mFrameModelViewVertexArray, 0, 90, 0, 0, 1.0f);
            } else if(mRotateMode ==ROTATE_MODE_180) {
                Matrix.rotateM(mFrameModelViewVertexArray, 0, 180, 0, 0, 1.0f);
            } else if(mRotateMode ==ROTATE_MODE_270) {
                Matrix.rotateM(mFrameModelViewVertexArray, 0, 270, 0, 0, 1.0f);
            }
        }
    }

    public void release(){
        for(int i = 0;i < mTextureIds.length;++i) {
            mTextureIds[i] = OpenGlUtils.safeDeleteTexture(mTextureIds[i]);
        }
        mInited = false;
    }
}
