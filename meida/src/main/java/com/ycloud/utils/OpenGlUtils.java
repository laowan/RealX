/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.hardware.Camera.Size;
import android.opengl.ETC1Util;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;

import com.ycloud.gles.EglCore;
import com.ycloud.gles.EglFactory;
import com.ycloud.gles.IEglCore;
import com.ycloud.gles.IEglSurfaceBase;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

public class OpenGlUtils {
    public static String VERTEX_SHADER_BASE =
            "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition; \n" +
                    "attribute vec4 aTextureCoord; \n" +
                    "varying vec2 vTextureCoord; \n" +
                    "void main() \n" +
                    "{ \n" +
                    "	gl_Position = aPosition; \n" +
                    "	vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}";


    public static String FRAGMENT_SHADER_BASE =
            "precision lowp float; \n" +
                    "varying mediump vec2 vTextureCoord; \n" +
                    "uniform sampler2D sTexture; \n" +
                    "void main() \n" +
                    "{ \n" +
                    "	gl_FragColor = texture2D(sTexture, vTextureCoord); \n" +
                    "}";

    public static final int COORD_LEN = 8;
    public static final int NO_TEXTURE = -1;
    public static final int NO_PROGRAM = -1;
    public static final int SIZEOF_FLOAT = 4;
    public static final int SIZEOF_INT = 4;
    public static final int SIZEOF_SHORT = 2;
    public static final int SIZEOF_BYTE = 1;
    public static final String TAG = "[GLUtil]";

    //gl texture compress format type
    public static final int TEXTURE_FORMAT_NONE = 0;
    public static final int TEXTURE_FORMAT_ETC1 = 2;
    public static final int TEXTURE_FORMAT_ETC2 = 4;

    //gl version support
    public static final int GL_VERSION_NONE = 0;
    public static final int GL_VERSION_31 = 2;

    //gl texture format result
    private static int mGLSupportTextureFormat = TEXTURE_FORMAT_NONE;

    private static int mGLSupportVersion = GL_VERSION_NONE;

    //has check gl env or not
    private static boolean mGLEnvChecked = false;

    public static final float[] IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    // buffer
    public static final float VERTEX_COORD[] = {
            -1.0f, -1.0f, // 0 bottom left
            1.0f, -1.0f, // 1 bottom right
            -1.0f, 1.0f, // 2 top left
            1.0f, 1.0f, // 3 top right
    };
    public static final float VERTEX_COORD_UPDOWN[] = {
            -1.0f, 1.0f, // 2 top left
            1.0f, 1.0f, // 3 top right
            -1.0f, -1.0f, // 0 bottom left
            1.0f, -1.0f, // 1 bottom right
    };
    public static final float TEXTURE_COORD[] = {
            0.0f, 0.0f, // 0 bottom left
            1.0f, 0.0f, // 1 bottom right
            0.0f, 1.0f, // 2 top left
            1.0f, 1.0f // 3 top right
    };
    public static final float TEXTURE_COORD_UPDOWN[] = {
            0.0f, 1.0f, // 0 bottom left
            1.0f, 1.0f, // 1 bottom right
            0.0f, 0.0f, // 2 top left
            1.0f, 0.0f // 3 top right
    };

    public static FloatBuffer VERTEXCOORD_BUFFER = createFloatBuffer(VERTEX_COORD);
    public static FloatBuffer VERTEXCOORD_BUFFER_UPDOWN = createFloatBuffer(VERTEX_COORD_UPDOWN);
    public static FloatBuffer TEXTURECOORD_BUFFER = createFloatBuffer(TEXTURE_COORD);
    public static FloatBuffer TEXTURECOORD_BUFFER_UPDOWN = createFloatBuffer(TEXTURE_COORD_UPDOWN);
    public static float[] TEXTURECOORD_IDENTITY_MATRIX = new float[16];

    public static final float[] CLEAR_COLOR = {0.0f, 0.0f, 0.0f, 1.0f};
    public static final float[] MATRIX4FV_VERTEX = {
            -1.0f,  1.0f, 0.0f, 0.0f,
            1.0f,  1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 1.0f,
            1.0f, -1.0f, 1.0f, 1.0f,
    };
    public static final float[] MATRIX4FV_IDENTITY = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f,
    };

    public static final int VERTEXT_STRIDE = 16;
    public static final int VERTEXT_UV_OFFSET = 8;
    public static final int VERTEXT_MATRIX4FV_SIZE = (VERTEXT_STRIDE * 4);

    private static final int PKM_HEADER_SIZE = 16;
    private static final int PKM_HEADER_WIDTH_OFFSET = 8;
    private static final int PKM_HEADER_HEIGHT_OFFSET = 10;
    private static final String PKM_TEST_FILE = "test_red.pkm";

    static {
        Matrix.setIdentityM(TEXTURECOORD_IDENTITY_MATRIX, 0);
    }

    public static void updateTexture(int texture, Bitmap img, boolean recycle) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        if (recycle) {
            img.recycle();
        }
    }

    public static void updateTexture(int texture, Bitmap img) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public static int createTexture() {
        int textures[] = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return textures[0];
    }

    public static int createTexture(int width, int height) {
        int textures[] = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return textures[0];
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static int createTexture(int target, int format, int type, int width, int height) {
        int textures[] = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(target, textures[0]);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        if (type == GLES20.GL_UNSIGNED_BYTE) {
            GLES20.glTexImage2D(target, 0, format, width, height, 0,
                    format, GLES20.GL_UNSIGNED_BYTE, null);
        } else if (type == GLES31.GL_RGBA16F && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            GLES31.glTexStorage2D(target, 1, GLES31.GL_RGBA16F, width, height);
        }
        GLES20.glBindTexture(target, 0);

        return textures[0];
    }

    public static int createTexture(int target, int width, int height) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(target, textures[0]);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(target, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return textures[0];
    }

    public static int createTexture(int target) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(target, textures[0]);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        return textures[0];
    }

    public static int loadTexture(final Bitmap img, final int usedTexId) {
        return loadTexture(img, usedTexId, true);
    }

    public static int loadTexture(final Bitmap img, final int usedTexId, final boolean recycle) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, img);
            textures[0] = usedTexId;
        }
        if (recycle) {
            img.recycle();
        }
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return textures[0];
    }

    public static int loadTexture(final ByteBuffer data, final int width, final int height, int format, final int usedTexId) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height,
                    0, format, GLES20.GL_UNSIGNED_BYTE, data);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width,
                    height, format, GLES20.GL_UNSIGNED_BYTE, data);
            textures[0] = usedTexId;
        }
        return textures[0];
    }
    
    public static int loadTexture(final IntBuffer data, final Size size, final int usedTexId) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size.width, size.height,
                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, size.width,
                    size.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
            textures[0] = usedTexId;
        }
        return textures[0];
    }

    public static void deleteTexture(int texture) {
        int[] textureHandles = new int[1];
        textureHandles[0] = texture;
        GLES20.glDeleteTextures(1, textureHandles, 0);
    }

    public static int loadTextureAsBitmap(final IntBuffer data, final Size size, final int usedTexId) {
        Bitmap bitmap = Bitmap
                .createBitmap(data.array(), size.width, size.height, Config.ARGB_8888);
        return loadTexture(bitmap, usedTexId);
    }

    public static int loadShader(final String strSource, final int iType) {
        int[] compiled = new int[1];
        int iShader = GLES20.glCreateShader(iType);
        GLES20.glShaderSource(iShader, strSource);
        GLES20.glCompileShader(iShader);
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.d("Load Shader Failed", "Compilation\n" + GLES20.glGetShaderInfoLog(iShader));
            return 0;
        }
        return iShader;
    }

    public static int loadProgram(final String strVSource, final String strFSource) {
        int iVShader;
        int iFShader;
        int iProgId;
        int[] link = new int[1];
        iVShader = loadShader(strVSource, GLES20.GL_VERTEX_SHADER);
        if (iVShader == 0) {
            Log.d("Load Program", "Vertex Shader Failed");
            return 0;
        }
        iFShader = loadShader(strFSource, GLES20.GL_FRAGMENT_SHADER);
        if (iFShader == 0) {
            Log.d("Load Program", "Fragment Shader Failed");
            return 0;
        }

        iProgId = GLES20.glCreateProgram();

        GLES20.glAttachShader(iProgId, iVShader);
        GLES20.glAttachShader(iProgId, iFShader);

        GLES20.glLinkProgram(iProgId);

        GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.d("Load Program", "Linking Failed");
            return 0;
        }
        GLES20.glDeleteShader(iVShader);
        GLES20.glDeleteShader(iFShader);
        return iProgId;
    }

    public static float rnd(final float min, final float max) {
        float fRandNum = (float) Math.random();
        return min + (max - min) * fRandNum;
    }

    private static String glGetErrorStr(int err)
    {
        switch (err)
        {
            case GL10.GL_STACK_OVERFLOW:
                return "stack overflow";

            case GL10.GL_STACK_UNDERFLOW:
                return "stack underflow";

            case GLES20.GL_NO_ERROR:
                return "GL_NO_ERROR";

            case GLES20.GL_INVALID_ENUM:
                return "GL_INVALID_ENUM";

            case GLES20.GL_INVALID_VALUE:
                return "GL_INVALID_VALUE";

            case GLES20.GL_INVALID_OPERATION:
                return "GL_INVALID_OPERATION";

            case GLES20.GL_OUT_OF_MEMORY:
                return "GL_OUT_OF_MEMORY";

            case GLES20.GL_INVALID_FRAMEBUFFER_OPERATION:
                return "GL_INVALID_FRAMEBUFFER_OPERATION";

            default:
                return "unknown";
        }
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error) + ":" + glGetErrorStr(error);
            YYLog.error(TAG, msg);
            //throw new RuntimeException(msg);
            //TODO:暂时屏蔽抛出异常，部分手机glGetError有时会获取到错误信息，但是运行正常，怀疑是硬件问题。
        }
    }

    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    public static FloatBuffer createFloatBuffer(float[] coords) {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
    }

    public static FloatBuffer createFloatBuffer(int length) {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        return fb;
    }

    public static FloatBuffer createFloatBuffer(double[] coords) {
        float buffer[] = new float[coords.length];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (float)(coords[i]);
        }
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(buffer.length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(buffer);
        fb.position(0);
        return fb;
    }

    public static void updateFloatBuffer(FloatBuffer fb, float[] coords) {
        fb.clear();
        fb.put(coords);
        fb.position(0);
    }

    public static IntBuffer createIntBuffer(int[] coords) {
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_INT);
        bb.order(ByteOrder.nativeOrder());
        IntBuffer ib = bb.asIntBuffer();
        ib.put(coords);
        ib.position(0);
        return ib;
    }
    
    public static ShortBuffer createShortBuffer(short[] coords) {
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_SHORT);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer ib = bb.asShortBuffer();
        ib.put(coords);
        ib.position(0);
        return ib;
    }
    
    public static ByteBuffer createByteBuffer(byte[] coords) {
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_BYTE);
        bb.order(ByteOrder.nativeOrder());
        bb.put(coords);
        bb.position(0);
        return bb;
    }

    public static void createFrameBuffer(int width, int height, int[] frameBuffer, int[] frameBufferTexture, int frameBufferSize) {
        for (int i = 0; i < frameBufferSize; i++) {

            GLES20.glGenTextures(1, frameBufferTexture, i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glGenFramebuffers(1, frameBuffer, i);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, frameBufferTexture[i], 0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
    }

    public static void drawTextureToFrameBuffer(int width, int height, int textureID, int[] frameBuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0]);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        drawSquare(textureID, OpenGlUtils.VERTEXCOORD_BUFFER, OpenGlUtils.TEXTURECOORD_BUFFER, OpenGlUtils.IDENTITY_MATRIX);
    }

    public static void drawSquare(int inputTexture, FloatBuffer vertexCoordBuffer, FloatBuffer textureCoordBuffer, float[] textureCoordMatrix) {
        int hProgram = OpenGlUtils.createProgram(VERTEX_SHADER_BASE, FRAGMENT_SHADER_BASE);
        int hVertexCoord = GLES20.glGetAttribLocation(hProgram, "aPosition");
        int hTextureCoord = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");
        int hTexture = GLES20.glGetUniformLocation(hProgram, "sTexture");
        int hStMatrixHandle = GLES20.glGetUniformLocation(hProgram, "uSTMatrix");

        GLES20.glUseProgram(hProgram);
        GLES20.glVertexAttribPointer(hVertexCoord, 2, GLES20.GL_FLOAT, false, 0, vertexCoordBuffer);
        GLES20.glEnableVertexAttribArray(hVertexCoord);
        GLES20.glVertexAttribPointer(hTextureCoord, 2, GLES20.GL_FLOAT, false, 0, textureCoordBuffer);
        GLES20.glEnableVertexAttribArray(hTextureCoord);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(hTexture, 0);

        GLES20.glUniformMatrix4fv(hStMatrixHandle, 1, false, textureCoordMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(hVertexCoord);
        GLES20.glDisableVertexAttribArray(hTextureCoord);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public static void releaseFrameBuffer(int num, int[] hTextureArray, int[] hFrameBufferArray) {
        GLES20.glDeleteTextures(num, hTextureArray, 0);
        GLES20.glDeleteFramebuffers(num, hFrameBufferArray, 0);
    }

    public static void saveFrameBuffer(int frameBuffer, ByteBuffer ouputBuffer, int outputWidth, int outputHeight) {
        ouputBuffer.clear();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glReadPixels(0, 0, outputWidth, outputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ouputBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        ouputBuffer.rewind();
    }

    public static int createNoSizeTexture() {
        int textures[] = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return textures[0];
    }

    public static void updataTexture(ByteBuffer data, int width, int height, int texture) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        if (data != null) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public static void saveFrame(String filename, Buffer data, int width, int height) {
        BufferedOutputStream bos = null;
        try {
            try {
                bos = new BufferedOutputStream(new FileOutputStream(filename));
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(data);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.recycle();
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

    /**
     * Creates a new program from the supplied vertex and fragment shaders.
     *
     * @return A handle to the program, or 0 on failure.
     */
    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            YYLog.error(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            YYLog.error(TAG, "Could not link program: ");
            YYLog.error(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(pixelShader);
        return program;
    }

    /**
     * Compiles the provided shader source.
     *
     * @return A handle to the shader, or 0 on failure.
     */
    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            YYLog.error(TAG, "Could not compile shader " + shaderType + ":");
            YYLog.error(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }



    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     * <p>
     * Throws a RuntimeException if the location is invalid.
     */
    public static void checkLocation(int location, String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }

    /**
     * Creates a texture from raw data.
     *
     * @param data Image data, in a "direct" ByteBuffer.
     * @param width Texture width, in pixels (not bytes).
     * @param height Texture height, in pixels.
     * @param format Image data format (use constant appropriate for glTexImage2D(), e.g. GL_RGBA).
     * @return Handle to texture.
     */
    public static int createImageTexture(ByteBuffer data, int width, int height, int format) {
        int[] textureHandles = new int[1];
        int textureHandle;

        GLES20.glGenTextures(1, textureHandles, 0);
        textureHandle = textureHandles[0];
        OpenGlUtils.checkGlError("glGenTextures");

        // Bind the texture handle to the 2D texture target.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);

        // Configure min/mag filtering, i.e. what scaling method do we use if what we're rendering
        // is smaller or larger than the source image.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        OpenGlUtils.checkGlError("loadImageTexture");

        // Load the data from the buffer into the texture handle.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, /*level*/ 0, format,
                width, height, /*border*/ 0, format, GLES20.GL_UNSIGNED_BYTE, data);
        OpenGlUtils.checkGlError("loadImageTexture");

        return textureHandle;
    }



    /**
     * Writes GL version info to the log.
     */
    @TargetApi(18)
    public static void logVersionInfo() {
        YYLog.info(TAG, "vendor  : " + GLES20.glGetString(GLES20.GL_VENDOR));
        YYLog.info(TAG, "renderer: " + GLES20.glGetString(GLES20.GL_RENDERER));
        YYLog.info(TAG, "version : " + GLES20.glGetString(GLES20.GL_VERSION));

        if (false) {
            int[] values = new int[1];
            GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, values, 0);
            int majorVersion = values[0];
            GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, values, 0);
            int minorVersion = values[0];
            if (GLES30.glGetError() == GLES30.GL_NO_ERROR) {
                YYLog.info(TAG, "iversion: " + majorVersion + "." + minorVersion);
            }
        }
    }


    //TODO. 应该把坐标系的这些常用操作封装起来.
    public static  FloatBuffer  setFlipX(final FloatBuffer texCoordArray) {
        float[] textureCords = new float[] {
                texCoordArray.get(2), texCoordArray.get(3),
                texCoordArray.get(0), texCoordArray.get(1),
                texCoordArray.get(6), texCoordArray.get(7),
                texCoordArray.get(4), texCoordArray.get(5)
        };
        return createFloatBuffer(textureCords);
    }

    public static FloatBuffer setFlipY(final FloatBuffer texCoordArray) {
        float[] textureCords = new float[] {
                texCoordArray.get(4), texCoordArray.get(5),
                texCoordArray.get(6), texCoordArray.get(7),
                texCoordArray.get(0), texCoordArray.get(1),
                texCoordArray.get(2), texCoordArray.get(3),
        };
        return createFloatBuffer(textureCords);
    }

    public static FloatBuffer adjustTexture(final FloatBuffer texCoordArray, float incomingWidth, float incomingHeight, float outputWidth, float outputHeight) {

        float ratioMax = Math.max(outputWidth / incomingWidth, outputHeight / incomingHeight);
        int imageWidthNew = Math.round(incomingWidth * ratioMax);
        int imageHeightNew = Math.round(incomingHeight * ratioMax);

        float ratioWidth = imageWidthNew / outputWidth;
        float ratioHeight = imageHeightNew / outputHeight;

        float distHorizontal;
        float distVertical;
        distHorizontal = (1 - 1 / ratioWidth) / 2;
        distVertical = (1 - 1 / ratioHeight) / 2;

        float[] textureCords = new float[] {
                addDistance(texCoordArray.get(0), distHorizontal),
                addDistance(texCoordArray.get(1), distVertical),
                addDistance(texCoordArray.get(2), distHorizontal),
                addDistance(texCoordArray.get(3), distVertical),
                addDistance(texCoordArray.get(4), distHorizontal),
                addDistance(texCoordArray.get(5), distVertical),
                addDistance(texCoordArray.get(6), distHorizontal),
                addDistance(texCoordArray.get(7), distVertical), };

        StringBuilder sb = new StringBuilder("adjustTexture textureCords:");
        for (int i = 0; i < textureCords.length; i ++) {
            if (i % 2 == 0) {
                sb.append("\n");
            }
            sb.append(textureCords[i]);
            if (i != textureCords.length - 1) {
                sb.append(", ");
            }
        }
        YYLog.info("GlUtil", sb.toString());

        return createFloatBuffer(textureCords);
    }

    private static float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    public static String copyAssetsResToSdcard(Context context, String strFileName) {
        String strBasePath = FileUtils.getDiskCacheDir(context) + File.separator;
        FileUtils.createDir(strBasePath);
        File file = new File(strBasePath, strFileName);
        try {
            ResourceUtils.copyToSdcard(context, file.getName(), file.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return strBasePath + strFileName;
    }

    public static int imgPathToTexture(String imgPath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imgPath);
        return OpenGlUtils.loadTexture(bitmap, OpenGlUtils.NO_TEXTURE, true);
    }

    public static int safeDeleteTexture(int texture){
        if(texture > 0) {
            int[] id = new int[1];
            id[0] = texture;
            GLES20.glDeleteTextures(1, id, 0);
        }
        return -1;
    }


    /**
     * 判断gl支持的版本号
     *
     * @param majorNum
     * @param minorNum
     * @return
     */
    public static boolean glVersionGreaterThan(char majorNum, char minorNum) {
        String glVer = GLES20.glGetString(GLES20.GL_VERSION);
        if (null == glVer) {
            YYLog.info(TAG, "glVersionGreaterThan:" + majorNum + "." + minorNum + " false, glVer is null");
            return false;
        }
        int dotPos = glVer.indexOf('.');
        if (dotPos != -1 && dotPos > 0) {
            int dMaj = glVer.charAt(dotPos - 1) - majorNum;
            int dMin = glVer.charAt(dotPos + 1) - minorNum;
            if (dMaj < 0) {
                YYLog.info(TAG, "glVersionGreaterThan:" + majorNum + "." + minorNum + " false, major version is small");
                return false;
            } else if (dMaj == 0) {
                if (dMin > 0) {
                    YYLog.info(TAG, "glVersionGreaterThan:" + majorNum + "." + minorNum + " true");
                    return true;
                } else {
                    YYLog.info(TAG, "glVersionGreaterThan:" + majorNum + "." + minorNum + " false, minor version is small");
                    return false;
                }
            }
            YYLog.info(TAG, "glVersionGreaterThan:" + majorNum + "." + minorNum + " true");
            return true;
        }
        return false;
    }

    /**
     * 判断gl支持的版本号
     *
     * @param majorNum
     * @param minorNum
     * @return
     */
    public static boolean glVersionEqualOrGreaterThan(char majorNum, char minorNum) {
        String glVer = GLES20.glGetString(GLES20.GL_VERSION);
        if (null == glVer) {
            return false;
        }
        int dotPos = glVer.indexOf('.');
        if (dotPos != -1 && dotPos > 0) {

            int dMaj = glVer.charAt(dotPos - 1) - majorNum;
            int dMin = glVer.charAt(dotPos + 1) - minorNum;
            if (dMaj < 0) {
                return false;
            } else if (dMaj == 0) {
                return dMin >= 0 ? true : false;
            }

            return true;
        }
        return false;
    }

    /**
     * gl环境兼容性检测
     */
    private static void checkGLEnv(Context context) {
        //创建openGL环境
        IEglCore eglCore;
        IEglSurfaceBase envSurface;
        try {
            checkGlError("checkGLEnv: gl env init begin");
            eglCore = EglFactory.createEGL(null, EglCore.FLAG_RECORDABLE);
            envSurface = eglCore.createSurfaceBase();
            envSurface.createOffscreenSurface(64, 64);
            eglCore.makeCurrent(envSurface);
            checkGlError("checkGLEnv: gl env init end");
        } catch (Exception e) {
            if (EglFactory.isUseEgl14()) {
                YYLog.warn(TAG, "switch to egl10 for retry.");
                EglFactory.setForceUseEgl10(true);

                checkGlError("checkGLEnv: gl env init begin");
                eglCore = EglFactory.createEGL(null, EglCore.FLAG_RECORDABLE);
                envSurface = eglCore.createSurfaceBase();
                envSurface.createOffscreenSurface(64, 64);
                eglCore.makeCurrent(envSurface);
                checkGlError("checkGLEnv: gl env init end");
            } else {
                YYLog.error(TAG, "checkGLEnv exception:" + e.getMessage());
                return;
            }
        }

        //纹理压缩格式判断：是否支持etc1
        boolean etc1Support = ETC1Util.isETC1Supported();
        if (etc1Support) {
            mGLSupportTextureFormat |= TEXTURE_FORMAT_ETC1;
        }

        //纹理压缩格式判断：是否支持etc2
        boolean etc2Support =isETC2Supported(context);
        if (etc2Support) {
            mGLSupportTextureFormat |= TEXTURE_FORMAT_ETC2;
        }

        //gl版本支持判断
        boolean glSupport31 = glVersionGreaterThan('3', '0');
        if (glSupport31) {
            mGLSupportVersion |= GL_VERSION_31;
        }

        //销毁openGL环境
        checkGlError("checkGLEnv: gl env release begin");
        if (envSurface != null) {
            eglCore.makeNothingCurrent();
            envSurface.releaseEglSurface();
        }
        if (eglCore != null) {
            eglCore.release();
        }
        checkGlError("checkGLEnv: gl env release end");
    }

    /**
     * 获取gl支持的版本号
     *
     * @return mGLSupportVersion
     */
    public static int getGLSupportVersion(Context context) {
        if (!mGLEnvChecked) {
            checkGLEnv(context);
            mGLEnvChecked = true;
        }
        return mGLSupportVersion;
    }

    /**
     * 获取支持的压缩纹理的格式
     *
     * @return mGLSupportTextureFormat
     */
    public static int getGLSupportTextureFormat(Context context) {
        if (!mGLEnvChecked) {
            checkGLEnv(context);
            mGLEnvChecked = true;
        }

        return mGLSupportTextureFormat;
    }

    /**
     * 判断是否支持etc2格式的纹理:1.通过glVersion来判断 2.通过绘制一个小的pkm文件来判断
     *
     * @return support or not
     */
    public static boolean isETC2Supported(Context context) {
        if (glVersionEqualOrGreaterThan('3', '0')) {
            //1.load pkm文件到etc2纹理
            ETC2TextureWrapper etc2Texture = loadETC2Texture(context, PKM_TEST_FILE, GLES30.GL_COMPRESSED_RGB8_ETC2);

            //如果加载测试纹理失败，没法深入判断，可能是文件或者context之类的问题，这种情况下，还是认为是支持的
            if (etc2Texture == null) {
                YYLog.warn(TAG, "not really test success, regard as support");
                return true;
            }

            //2.draw texture to fbo
            int[] frameBuffer = new int[1];
            int[] frameBufferTexture = new int[1];
            int inputTextureWidth = etc2Texture.width;
            int inputTextureHeight = etc2Texture.height;
            createFrameBuffer(inputTextureWidth, inputTextureHeight, frameBuffer, frameBufferTexture, 1);
            drawTextureToFrameBuffer(inputTextureWidth, inputTextureWidth, etc2Texture.textures[0], frameBuffer);

//            ImageStorageUtil.save2DTextureToJPEG(frameBufferTexture[0], inputTextureWidth, inputTextureHeight);

            //3.read rgba from texture
            ByteBuffer rgbaBuffer = ByteBuffer.allocateDirect(inputTextureWidth * inputTextureHeight * 4).order(ByteOrder.nativeOrder());
            saveFrameBuffer(frameBuffer[0], rgbaBuffer, inputTextureWidth, inputTextureHeight);

            //4.释放资源
            releaseFrameBuffer(1, frameBufferTexture, frameBuffer);
            GLES20.glDeleteTextures(1, etc2Texture.textures, 0);

            //5.判断rgba数据是否正确,这里原图片用一张纯红色的图，所以判断像素是否是红色
            int colorRed = rgbaBuffer.get(0) & 0xFF;
            if (colorRed > 200) {
                YYLog.info(TAG, "isETC2Supported true,color red value=" + colorRed);
                return true;
            } else {
                YYLog.info(TAG, "isETC2Supported false,color red value=" + colorRed);
                return false;
            }
        }

        YYLog.info(TAG, "isETC2Supported false,low edition gl version");
        return false;
    }

    /**
     * 从pkm加载etc2纹理
     *
     * @param filename
     * @param compression
     * @return ETC2TextureWrapper
     */
    private static ETC2TextureWrapper loadETC2Texture(Context context, String filename, int compression) {
        if (context == null) {
            YYLog.error(TAG, "loadETC2Texture context is null");
            return null;
        }

        ETC2TextureWrapper etc2Texture = new ETC2TextureWrapper();

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int textureID = textures[0];
        etc2Texture.textures = textures;

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        InputStream is = null;
        try {
            is = context.getApplicationContext().getAssets().open(filename);
//            is = new FileInputStream("/sdcard/Noizz/red.pkm");
        } catch (IOException e) {
            YYLog.error(TAG, "loadETC2Texture error: open file failed, filename=" + filename);
            return null;
        }

        try {
            byte[] tmp = new byte[8192];
            int bytesRead;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while ((bytesRead = is.read(tmp)) != -1) {
                output.write(tmp, 0, bytesRead);
            }
            byte[] data = output.toByteArray();

            ByteBuffer buffer = ByteBuffer.allocateDirect(data.length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(data).position(PKM_HEADER_SIZE);

            ByteBuffer header = ByteBuffer.allocateDirect(PKM_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
            header.put(data, 0, PKM_HEADER_SIZE).position(0);

            int width = header.getShort(PKM_HEADER_WIDTH_OFFSET);
            int height = header.getShort(PKM_HEADER_HEIGHT_OFFSET);

            etc2Texture.width = width;
            etc2Texture.height = height;

            GLES20.glCompressedTexImage2D(GLES20.GL_TEXTURE_2D, 0, compression, width, height, 0, data.length - PKM_HEADER_SIZE, buffer);
            checkGlError("Loading of ETC2 texture; call to glCompressedTexImage2D()");
        } catch (Exception e) {
            YYLog.error(TAG, "Could not load ETC2 texture: " + e);
            return null;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                YYLog.error(TAG, "loadETC2Texture error:" + e.getMessage());
            }
        }
        return etc2Texture;
    }

    private static class ETC2TextureWrapper {
        int[] textures;
        int width;
        int height;
    }
}
