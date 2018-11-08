/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.gles;

import android.annotation.TargetApi;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GL program and supporting functions for textured 2D shapes.
 */
public class Texture2dProgram {
    private static final String TAG = OpenGlUtils.TAG;

    public enum ProgramType {
        TEXTURE_2D, TEXTURE_2D_WITH_EXTRA_TXT, TEXTURE_2D_WITH_EXTRA_TXT_2, TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_FILT,TEXTURE_YUV
    }

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER = "" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private static final String VERTEX_SHADER_WITH_EXTRA_TXT = "" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "attribute vec4 aExtraTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vExtraTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vExtraTextureCoord = vec2(aExtraTextureCoord.x, aExtraTextureCoord.y);\n" +
            "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
            "}";

    private static final String VERTEX_SHADER_WITH_EXTRA_TXT_2 = "" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "attribute vec4 aExtraTextureCoord;\n" +
            "attribute vec4 aExtraTextureCoord2;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vExtraTextureCoord;\n" +
            "varying vec2 vExtraTextureCoord2;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vExtraTextureCoord = vec2(aExtraTextureCoord.x, aExtraTextureCoord.y);\n" +
            "    vExtraTextureCoord2 = vec2(aExtraTextureCoord2.x, aExtraTextureCoord2.y);\n" +
            "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
            "}";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D = "" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_2D_WITH_EXTRA_TXT = "" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vExtraTextureCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform sampler2D uExtraTexture;\n" +
            "uniform int uExtraTxtEnabled;\n" +
            "\n" +
            "void main() {\n" +
            "    if (uExtraTxtEnabled == 1) {\n" +
            "       vec4 base = texture2D(uTexture, vTextureCoord);\n" +
            "       vec4 overlay = texture2D(uExtraTexture, vExtraTextureCoord);\n" +
            "       vec4 outputColor;\n" +
            "       outputColor.r = overlay.r + base.r * base.a * (1.0 - overlay.a);\n" +
            "       outputColor.g = overlay.g + base.g * base.a * (1.0 - overlay.a);\n" +
            "       outputColor.b = overlay.b + base.b * base.a * (1.0 - overlay.a);\n" +
            "       outputColor.a = overlay.a + base.a * (1.0 - overlay.a);\n" +
            "       gl_FragColor = outputColor;\n" +
            "    }\n" +
            "    else {\n" +
            "        gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
            "    }\n" +
            "}";

    private static final String FRAGMENT_SHADER_2D_WITH_EXTRA_TXT_2 = "" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vExtraTextureCoord;\n" +
            "varying vec2 vExtraTextureCoord2;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform sampler2D uExtraTexture;\n" +
            "uniform sampler2D uExtraTexture2;\n" +
            "uniform int uExtraTxtEnabled;\n" +
            "uniform int uExtraTxt2Enabled;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 outputColor = texture2D(uTexture, vTextureCoord);\n" +
            "    if (uExtraTxtEnabled == 1) {\n" +
            "        vec4 overlay = texture2D(uExtraTexture, vExtraTextureCoord);\n" +
            "        outputColor.r = overlay.r + outputColor.r * outputColor.a * (1.0 - overlay.a);\n" +
            "        outputColor.g = overlay.g + outputColor.g * outputColor.a * (1.0 - overlay.a);\n" +
            "        outputColor.b = overlay.b + outputColor.b * outputColor.a * (1.0 - overlay.a);\n" +
            "        outputColor.a = overlay.a + outputColor.a * (1.0 - overlay.a);\n" +
            "    }\n" +
            "    if (uExtraTxt2Enabled == 1) {\n" +
            "        vec4 overlay2 = texture2D(uExtraTexture2, vExtraTextureCoord2);\n" +
            "        outputColor.r = overlay2.r + outputColor.r * outputColor.a * (1.0 - overlay2.a);\n" +
            "        outputColor.g = overlay2.g + outputColor.g * outputColor.a * (1.0 - overlay2.a);\n" +
            "        outputColor.b = overlay2.b + outputColor.b * outputColor.a * (1.0 - overlay2.a);\n" +
            "        outputColor.a = overlay2.a + outputColor.a * (1.0 - overlay2.a);\n" +
            "    }\n" +
            "    gl_FragColor = outputColor;\n" +
            "}";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    // Fragment shader that converts color to black & white with a simple transformation.
    private static final String FRAGMENT_SHADER_EXT_BW =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
                    "    float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;\n" +
                    "    gl_FragColor = vec4(color, color, color, 1.0);\n" +
                    "}\n";

    // Fragment shader with a convolution filter.  The upper-left half will be drawn normally,
    // the lower-right half will have the filter applied, and a thin red line will be drawn
    // at the border.
    //
    // This is not optimized for performance.  Some things that might make this faster:
    // - Remove the conditionals.  They're used to present a half & half view with a red
    //   stripe across the middle, but that's only useful for a demo.
    // - Unroll the loop.  Ideally the compiler does this for you when it's beneficial.
    // - Bake the filter kernel into the shader, instead of passing it through a uniform
    //   array.  That, combined with loop unrolling, should reduce memory accesses.
    public static final int KERNEL_SIZE = 9;
    private static final String FRAGMENT_SHADER_EXT_FILT =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "#define KERNEL_SIZE " + KERNEL_SIZE + "\n" +
                    "precision highp float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform float uKernel[KERNEL_SIZE];\n" +
                    "uniform vec2 uTexOffset[KERNEL_SIZE];\n" +
                    "uniform float uColorAdjust;\n" +
                    "void main() {\n" +
                    "    int i = 0;\n" +
                    "    vec4 sum = vec4(0.0);\n" +
                    "    if (vTextureCoord.x < vTextureCoord.y - 0.005) {\n" +
                    "        for (i = 0; i < KERNEL_SIZE; i++) {\n" +
                    "            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n" +
                    "            sum += texc * uKernel[i];\n" +
                    "        }\n" +
                    "    sum += uColorAdjust;\n" +
                    "    } else if (vTextureCoord.x > vTextureCoord.y + 0.005) {\n" +
                    "        sum = texture2D(sTexture, vTextureCoord);\n" +
                    "    } else {\n" +
                    "        sum.r = 1.0;\n" +
                    "    }\n" +
                    "    gl_FragColor = sum;\n" +
                    "}\n";


    public static final String FRAGMENT_SHADER_YUV =
            "precision highp float;" +
                    "varying vec2 vTextureCoord;" +
                    "uniform sampler2D tex_y;" +
                    "uniform sampler2D tex_u;" +
                    "uniform sampler2D tex_v;" +
                    "void main() {" +
                    "    vec3 yuv;" +
                    "    yuv.x = texture2D(tex_y, vTextureCoord).r;" +
                    "    yuv.y = texture2D(tex_u, vTextureCoord).r - 0.5;" +
                    "    yuv.z = texture2D(tex_v, vTextureCoord).r - 0.5;" +
                    "    yuv.x = 1.1643 * yuv.x - 0.0728;" +
                    "    vec3 rgb = vec3(" +
                    "        yuv.x + 1.5958 * yuv.z," +
                    "        yuv.x - 0.39173 * yuv.y - 0.8129 * yuv.z," +
                    "        yuv.x + 2.017 * yuv.y" +
                    "    );" +
                    "    gl_FragColor = vec4(rgb, 1);" +
                    "}";

    private ProgramType mProgramType;

    // Handles to the GL program and various components of it.
    private int mProgramHandle;
    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int muKernelLoc;
    private int muTexOffsetLoc;
    private int muColorAdjustLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private int muExtraTextureEnableLoc;
    private int maExtraTextureCoordLoc;
    private int muExtraTextureLoc;
    private int muExtraTexture2EnableLoc;
    private int maExtraTextureCoord2Loc;
    private int muExtraTexture2Loc;

    private int mWaterTextureLoc;
    private int mWaterEnabledLoc;
    private int mSampleYLoc;
    private int mSampleULoc;
    private int mSampleVLoc;

    private int mTextureTarget;

    private float[] mKernel = new float[KERNEL_SIZE];
    private float[] mTexOffset;
    private float mColorAdjust;

    private int mProgramIndex = 0;


    /**用来统计花费了多少opengl的资源 */
    protected static AtomicInteger mProgramTotalIndex = new AtomicInteger(0);
    protected static AtomicInteger mProgramRemains = new AtomicInteger(0);

    /**
     * Prepares the program in the current EGL context.
     */
    public Texture2dProgram(ProgramType programType) {
        mProgramType = programType;

        switch (programType) {
            case TEXTURE_2D:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
                break;
            case TEXTURE_2D_WITH_EXTRA_TXT:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER_WITH_EXTRA_TXT, FRAGMENT_SHADER_2D_WITH_EXTRA_TXT);
                break;
            case TEXTURE_2D_WITH_EXTRA_TXT_2:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER_WITH_EXTRA_TXT_2, FRAGMENT_SHADER_2D_WITH_EXTRA_TXT_2);
                break;
            case TEXTURE_EXT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);
                break;
            case TEXTURE_EXT_BW:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_BW);
                break;
            case TEXTURE_EXT_FILT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_FILT);
                break;
            case TEXTURE_YUV:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = OpenGlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_YUV);
                break;
            default:
                throw new RuntimeException("Unhandled type " + programType);
        }
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Log.d(TAG, "Created program " + mProgramHandle + " (" + programType + ")");

        mProgramIndex = mProgramTotalIndex.addAndGet(1);
        YYLog.info(this, "[OpenGlUtils] create a new Program, index="+ mProgramIndex + " totalRemains="+mProgramRemains.addAndGet(1));

        // get locations of attributes and uniforms

        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        OpenGlUtils.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        OpenGlUtils.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        OpenGlUtils.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        OpenGlUtils.checkLocation(muTexMatrixLoc, "uTexMatrix");
        muExtraTextureEnableLoc = GLES20.glGetUniformLocation(mProgramHandle, "uExtraTxtEnabled");
        maExtraTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aExtraTextureCoord");
        muExtraTextureLoc = GLES20.glGetUniformLocation(mProgramHandle, "uExtraTexture");
        muExtraTexture2EnableLoc = GLES20.glGetUniformLocation(mProgramHandle, "uExtraTxt2Enabled");
        maExtraTextureCoord2Loc = GLES20.glGetAttribLocation(mProgramHandle, "aExtraTextureCoord2");
        muExtraTexture2Loc = GLES20.glGetUniformLocation(mProgramHandle, "uExtraTexture2");
        muKernelLoc = GLES20.glGetUniformLocation(mProgramHandle, "uKernel");
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1;
            muTexOffsetLoc = -1;
            muColorAdjustLoc = -1;
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexOffset");
            OpenGlUtils.checkLocation(muTexOffsetLoc, "uTexOffset");
            muColorAdjustLoc = GLES20.glGetUniformLocation(mProgramHandle, "uColorAdjust");
            OpenGlUtils.checkLocation(muColorAdjustLoc, "uColorAdjust");

            // initialize default values
            setKernel(new float[] {0f, 0f, 0f,  0f, 1f, 0f,  0f, 0f, 0f}, 0f);
            setTexSize(256, 256);
        }

        mWaterTextureLoc = GLES20.glGetUniformLocation(mProgramHandle, "waterTexture");
        mWaterEnabledLoc = GLES20.glGetUniformLocation(mProgramHandle, "waterEnabled");

        mSampleYLoc = GLES20.glGetUniformLocation(mProgramHandle, "tex_y");
        mSampleULoc = GLES20.glGetUniformLocation(mProgramHandle, "tex_u");
        mSampleVLoc = GLES20.glGetUniformLocation(mProgramHandle, "tex_v");
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        YYLog.info(this, "[OpenGlUtils] deleting program " + mProgramHandle);
        GLES20.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;

        YYLog.info(this, "[OpenGlUtils] release Program, totalRemains="+mProgramRemains.decrementAndGet());
    }

    /**
     * Returns the program type.
     */
    public ProgramType getProgramType() {
        return mProgramType;
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    public int createTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        OpenGlUtils.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);
        OpenGlUtils.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        OpenGlUtils.checkGlError("glTexParameter");

        return texId;
    }

    /**
     * Configures the convolution filter values.
     *
     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    public void setKernel(float[] values, float colorAdj) {
        if (values.length != KERNEL_SIZE) {
            throw new IllegalArgumentException("Kernel size is " + values.length +
                    " vs. " + KERNEL_SIZE);
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE);
        mColorAdjust = colorAdj;
        //Log.d(TAG, "filt kernel: " + Arrays.toString(mKernel) + ", adj=" + colorAdj);
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    public void setTexSize(int width, int height) {
        float rw = 1.0f / width;
        float rh = 1.0f / height;

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = new float[] {
                -rw, -rh,   0f, -rh,    rw, -rh,
                -rw, 0f,    0f, 0f,     rw, 0f,
                -rw, rh,    0f, rh,     rw, rh
        };
        //Log.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
        draw(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex, vertexStride, texMatrix, texBuffer, textureId, texStride, null, -1);
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride, FloatBuffer extraTxtBuffer, int extraTextureId) {
        draw(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex, vertexStride, texMatrix, texBuffer, textureId, texStride, extraTxtBuffer, extraTextureId, null, -1);
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride,
                     FloatBuffer extraTxtBuffer, int extraTextureId, FloatBuffer extraTxtBuffer2, int extraTextureId2) {
        OpenGlUtils.checkGlError("draw start");

        boolean hasExtra = false;

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        OpenGlUtils.checkGlError("glUseProgram");

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        OpenGlUtils.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        OpenGlUtils.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0);
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0);
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust);
        }

        if (muExtraTextureEnableLoc >= 0) {
            if (extraTextureId >= 0) {
                GLES20.glUniform1i(muExtraTextureEnableLoc, 1);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, extraTextureId);
                GLES20.glUniform1i(muExtraTextureLoc, 1);

                GLES20.glEnableVertexAttribArray(maExtraTextureCoordLoc);
                GLES20.glVertexAttribPointer(maExtraTextureCoordLoc, 2, GLES20.GL_FLOAT, false, texStride, extraTxtBuffer);

                OpenGlUtils.checkGlError("glUniformMatrix4fv");
            } else {
                GLES20.glUniform1i(muExtraTextureEnableLoc, 0);
                GLES20.glUniform1i(muExtraTextureLoc, 0);
            }
        }

        if (muExtraTexture2EnableLoc >= 0) {
            if (extraTextureId2 >= 0) {
                GLES20.glUniform1i(muExtraTexture2EnableLoc, 1);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, extraTextureId2);
                GLES20.glUniform1i(muExtraTexture2Loc, 2);

                GLES20.glEnableVertexAttribArray(maExtraTextureCoord2Loc);
                GLES20.glVertexAttribPointer(maExtraTextureCoord2Loc, 2, GLES20.GL_FLOAT, false, texStride, extraTxtBuffer2);

                OpenGlUtils.checkGlError("glUniformMatrix4fv");
            } else {
                GLES20.glUniform1i(muExtraTexture2EnableLoc, 0);
                GLES20.glUniform1i(muExtraTexture2Loc, 0);
            }
        }

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        OpenGlUtils.checkGlError("glDrawArrays");

        if(muExtraTextureEnableLoc >=0 && extraTextureId >=0) {
            GLES20.glDisableVertexAttribArray(maExtraTextureCoordLoc);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            hasExtra = true;
        }

        if(muExtraTexture2EnableLoc >=0 && extraTextureId2>=0) {
            GLES20.glDisableVertexAttribArray(maExtraTextureCoord2Loc);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            hasExtra = true;
        }

        if(hasExtra) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);
        GLES20.glUseProgram(0);
    }



    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride, int waterMarkTextureId,
                     int ytextureId, int utextureId, int vtextureId) {
        OpenGlUtils.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        OpenGlUtils.checkGlError("glUseProgram");

        // Set the texture.
        if (mSampleYLoc >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ytextureId);
            GLES20.glUniform1i(mSampleYLoc, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, utextureId);
            GLES20.glUniform1i(mSampleULoc, 1);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, vtextureId);
            GLES20.glUniform1i(mSampleVLoc, 2);
        } else {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(mTextureTarget, textureId);
        }

        if (mWaterEnabledLoc >= 0) {
            if (waterMarkTextureId != -1) {
                GLES20.glUniform1i(mWaterEnabledLoc, 1);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waterMarkTextureId);
                GLES20.glUniform1i(mWaterTextureLoc, 2);
            } else {
                GLES20.glUniform1i(mWaterEnabledLoc, 0);
                GLES20.glUniform1i(mWaterTextureLoc, 0);
            }
        }

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        OpenGlUtils.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        OpenGlUtils.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        OpenGlUtils.checkGlError("glDrawArrays");
        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);
        GLES20.glUseProgram(0);
    }
}
