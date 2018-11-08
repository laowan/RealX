package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.utils.Dupable;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/17.
 */

public class BaseFilter extends AbstractYYMediaFilter implements Dupable<BaseFilter> {

    // 纹理坐标乘以变换矩阵，无法做缩放，只能做旋转
    public String VERTEX_SHADER_BASE =
            "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition; \n" +
                    "attribute vec4 aTextureCoord; \n" +
                    "varying vec2 vTextureCoord; \n" +
                    "void main() \n" +
                    "{ \n" +
                    "	gl_Position = aPosition; \n" +
                    "	vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}";

    // 顶点坐标乘以变换矩阵，能做缩放和旋转
    public String VERTEX_SHADER_BASE_EX =
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition; \n" +
            "attribute vec2 aTextureCoord; \n" +
            "varying vec2 vTextureCoord; \n" +
            "void main() \n" +
            "{ \n" +
            "	gl_Position = uSTMatrix*aPosition; \n" +
            "	vTextureCoord = aTextureCoord;\n" +
            "}";

    public final static String FRAGMENT_SHADER_BASE =
            "precision lowp float; \n" +
            "varying mediump vec2 vTextureCoord; \n" +
            "uniform sampler2D sTexture; \n" +
            "void main() \n" +
            "{ \n" +
            "	gl_FragColor = texture2D(sTexture, vTextureCoord); \n" +
            "}";

    private final String TAG = "BaseFilter";

    // res
    protected int TEXTURE_NUM = 1;
    protected int[] mTextures;

    final protected int FRAMEBUFFER_NUM = 2;
    protected int[] mFrameBuffer;
    protected int[] mFrameBufferTexture;
    protected IntBuffer mOldFramebuffer;

    // program
    protected int mhProgram;
    protected int mhSTMatrixHandle;
    protected int mhVertexCoord;
    protected int mhTextureCoord;
    protected int mhTexture;

    protected int mTextureTarget;
    protected boolean mIsInit = false;

    protected FilterInfo mFilterInfo;
    protected OrangeFilter.OF_EffectInfo mEffectInfo = new OrangeFilter.OF_EffectInfo();
    protected int mOFContext = - 1; // 只是使用，不负责销毁
    protected int mFilterId = -1;

    protected int mOPType = 0;

    protected String mVertexShaderString = null;

    //是否使用filterGroup中共用的FBO与outputTexture
    protected boolean mFBOReuse = false;

    public BaseFilter() {
    }

    protected void initHandle() {
        mhSTMatrixHandle = GLES20.glGetUniformLocation(mhProgram, "uSTMatrix");
        mhVertexCoord = GLES20.glGetAttribLocation(mhProgram, "aPosition");
        mhTextureCoord = GLES20.glGetAttribLocation(mhProgram, "aTextureCoord");
        mhTexture = GLES20.glGetUniformLocation(mhProgram, "sTexture");
    }

    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;
        mTextureTarget = GLES20.GL_TEXTURE_2D;
        mOFContext = oFContext;

        if (!mFBOReuse) {
            mTextures = new int[TEXTURE_NUM];
            for (int i = 0; i < TEXTURE_NUM; i++) {
                mTextures[i] = OpenGlUtils.createTexture(mOutputWidth, mOutputHeight);
            }

            mFrameBuffer = new int[FRAMEBUFFER_NUM];
            mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
            OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);
        }

        mOldFramebuffer = IntBuffer.allocate(1);

        String fragmentShader = new String(FRAGMENT_SHADER_BASE);
        if (isExtTexture) {

            fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
                    fragmentShader.replace("uniform sampler2D sTexture;",
                            "uniform samplerExternalOES sTexture;");
            mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        }

        mhProgram = OpenGlUtils.createProgram(VERTEX_SHADER_BASE, fragmentShader);
        mVertexShaderString = VERTEX_SHADER_BASE;
        initHandle();

        mIsInit = true;
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight + " isExtTexture" + isExtTexture);
    }

    public void changeTextureTarget(int textureTarget) {
        YYLog.info(this, "[target] gpu filter change texture target: " + textureTarget);
        if(mTextureTarget != textureTarget && textureTarget != -1) {
            GLES20.glDeleteProgram(mhProgram);
            mTextureTarget = textureTarget;

            String fragmentShader = new String(FRAGMENT_SHADER_BASE);
            if(textureTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
                fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
                        fragmentShader.replace("uniform sampler2D sTexture;",
                                "uniform samplerExternalOES sTexture;");
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
            }

            mhProgram = OpenGlUtils.createProgram(mVertexShaderString, fragmentShader);
            initHandle();
        }
    }

    // 与 init 的唯一区别在于 VERTEX_SHADER_BASE_EX
    public void initExt(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;
        mTextureTarget = GLES20.GL_TEXTURE_2D;
        mOFContext = oFContext;

        if (!mFBOReuse) {
            mTextures = new int[TEXTURE_NUM];
            for (int i = 0; i < TEXTURE_NUM; i++) {
                mTextures[i] = OpenGlUtils.createTexture(mOutputWidth, mOutputHeight);
            }

            mFrameBuffer = new int[FRAMEBUFFER_NUM];
            mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
            OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);
        }

        mOldFramebuffer = IntBuffer.allocate(1);

        String fragmentShader = new String(FRAGMENT_SHADER_BASE);
        if (isExtTexture) {
            fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
                    fragmentShader.replace("uniform sampler2D sTexture;",
                            "uniform samplerExternalOES sTexture;");
            mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        }
        mhProgram = OpenGlUtils.createProgram(VERTEX_SHADER_BASE_EX, fragmentShader);
        mVertexShaderString = VERTEX_SHADER_BASE_EX;
        initHandle();

        mIsInit = true;
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight + " isExtTexture" + isExtTexture);
    }

    /**
     * 将filterGroup中公用的outputTexture传递给filter，filter使用filterGroup公用的outputTexture，不需要在内部额外创建outputTexture
     *
     * @param textures 由filterGroup传递的公用的outputTexture
     */
    public void setOutputTextures(int[] textures) {
        if (mFBOReuse) {
            mTextures = textures;
        }
    }

    /**
     * 将filterGroup中公用的FBO及附加在上面的texture传递给filter，filter使用filterGroup公用的FBO资源，不需要在内部额外创建
     *
     * @param frameBuffer        由filterGroup传递的公用的FBO
     * @param frameBufferTexture 由filterGroup传递的公用的FBO上附加的texture
     */
    public void setCacheFBO(int[] frameBuffer, int[] frameBufferTexture) {
        if (mFBOReuse) {
            mFrameBuffer = frameBuffer;
            mFrameBufferTexture = frameBufferTexture;
        }
    }

    public void destroy() {
        if (!mFBOReuse) {
            if (mTextures != null) {
                for (int i = 0; i < mTextures.length; i++) {
                    OpenGlUtils.deleteTexture(mTextures[i]);
                }
                mTextures = null;
            }

            if (mFrameBufferTexture != null && mFrameBuffer != null) {
                OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
                mFrameBufferTexture = null;
                mFrameBuffer = null;
            }
        }

        if (mOldFramebuffer != null) {
            mOldFramebuffer.clear();
            mOldFramebuffer = null;
        }

        GLES20.glDeleteProgram(mhProgram);
        mIsInit = false;
        YYLog.info(TAG, "destroy");
    }

    public void changeSize(int newWidth, int newHeight) {
        YYLog.info(this, "BaseFilter change size: newWidth=" + newWidth
                + " newHeight=" + newHeight + "origWidth=" + mOutputWidth + " origHeight=" + mOutputHeight);
        mOutputWidth = newWidth;
        mOutputHeight = newHeight;

        //如果fbo复用，不用在width和height发生变化的时候重新为每个filter创建texture和fbo
        if (!mFBOReuse) {
            if (mTextures != null) {
                for (int i = 0; i < mTextures.length; i++) {
                    OpenGlUtils.deleteTexture(mTextures[i]);
                    mTextures[i] = OpenGlUtils.createTexture(mOutputWidth, mOutputHeight);
                }
            }

            if (mFrameBufferTexture != null && mFrameBuffer != null) {
                OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
                OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);
            }
        }
    }

    public boolean getIsInit() {
        return mIsInit;
    }

    protected void drawSquare(int texture, FloatBuffer vertexCoordBuffer, FloatBuffer textureCoordBuffer, float[] textureCoordMatrix) {
        GLES20.glUseProgram(mhProgram);
        GLES20.glVertexAttribPointer(mhVertexCoord, 2, GLES20.GL_FLOAT, false, 0, vertexCoordBuffer);
        GLES20.glEnableVertexAttribArray(mhVertexCoord);
        GLES20.glVertexAttribPointer(mhTextureCoord, 2, GLES20.GL_FLOAT, false, 0, textureCoordBuffer);
        GLES20.glEnableVertexAttribArray(mhTextureCoord);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, texture);
        GLES20.glUniform1i(mhTexture, 0);

        GLES20.glUniformMatrix4fv(mhSTMatrixHandle, 1, false, textureCoordMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mhVertexCoord);
        GLES20.glDisableVertexAttribArray(mhTextureCoord);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    protected void drawToFrameBuffer(YYMediaSample sample) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        drawSquare(mTextures[0], OpenGlUtils.VERTEXCOORD_BUFFER, OpenGlUtils.TEXTURECOORD_BUFFER, OpenGlUtils.IDENTITY_MATRIX);

        sample.mTextureId = mFrameBufferTexture[0];
        sample.mFrameBufferId = mFrameBuffer[0];
    }


    protected void drawTextureToFrameBuffer(YYMediaSample sample, int textureID) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        drawSquare(textureID, OpenGlUtils.VERTEXCOORD_BUFFER, OpenGlUtils.TEXTURECOORD_BUFFER, OpenGlUtils.IDENTITY_MATRIX);

        sample.mTextureId = mFrameBufferTexture[0];
        sample.mFrameBufferId = mFrameBuffer[0];
    }

    /**
     * 交换输入与输出纹理，of输出的纹理作为下一个of filter的输入纹理，同时输入纹理作为下一个ofFilter的输出纹理
     *
     * @param sample YYMediaSample
     */
    protected void swapTexture(YYMediaSample sample) {
        int tmpTextureId = sample.mTextureId;
        sample.mTextureId = mTextures[0];
        mTextures[0] = tmpTextureId;
    }

    public void setFrameBufferReuse(boolean isReuse) {
        mFBOReuse = isReuse;
    }

    public boolean getFrameBufferReuse() {
        return mFBOReuse;
    }

    public String getFilterName() {
        return TAG;
    }

    public FilterInfo getFilterInfo() {
        return mFilterInfo;
    }

    public void setFilterInfo(FilterInfo filterInfo) {
        mFilterInfo = filterInfo;
        if (mFilterInfo == null || mFilterInfo.mFilterConfigs == null) {
            YYLog.error(TAG, "setFilterInfo:filterInfo is valid!");
            return;
        }
        updateParams();
    }

    protected void storeOldFBO() {
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, mOldFramebuffer);
    }

    protected void recoverOldFBO() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOldFramebuffer.get(0));
    }

    //should overrite this.
    protected void updateParams() {
        // do nothing
    }

    @Override
    public boolean isDupable() {
        return false;
    }

    @Override
    public BaseFilter duplicate() {
        return null;
    }

    //设置of的ui config参数
    public void setFilterUIConf(Map<String, Object> uiConf) {
        OrangeFilter.getEffectInfo(mOFContext, mFilterId, mEffectInfo);

        for (Map.Entry<String, Object> entry : uiConf.entrySet()) {
            String[] uiConfKey = entry.getKey().split(":");
            int filterId = mEffectInfo.filterList[Integer.parseInt(uiConfKey[0])];

            OrangeFilter.OF_Param ofParam = OrangeFilter.getFilterParamData(mOFContext, filterId, uiConfKey[1]);
            if (ofParam != null) {
                switch (ofParam.getType()) {
                    case OrangeFilter.OF_ParamType_Float:
                        //反序列化出来的是Double类型
                        if (entry.getValue() instanceof Double) {
                            ((OrangeFilter.OF_Paramf) ofParam).val = ((Double) entry.getValue()).floatValue();
                        } else if (entry.getValue() instanceof Integer) {
                            //反序列化出来的是Integer类型
                            ((OrangeFilter.OF_Paramf) ofParam).val = ((Integer) entry.getValue()).floatValue();
                        } else {
                            //业务层添加时候传入的是Float类型
                            ((OrangeFilter.OF_Paramf) ofParam).val = (float) entry.getValue();
                        }
                        break;
                    case OrangeFilter.OF_ParamType_Int:
                        ((OrangeFilter.OF_Parami) ofParam).val = (int) entry.getValue();
                        break;
                    case OrangeFilter.OF_ParamType_String:
                        ((OrangeFilter.OF_ParamString) ofParam).val = (String) entry.getValue();
                        break;
                }
                OrangeFilter.setFilterParamData(mOFContext, filterId, uiConfKey[1], ofParam);
            } else {
                YYLog.error(TAG, "ofParam is null, maybe effect file error");
            }
        }
    }
}
