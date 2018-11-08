package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.FadeBlendFilterParameter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by DZHJ on 2017/7/24.
 */

public class FadeBlendFilter extends BaseFilter {
    public String VERTEX_SHADER_FADE =
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition; \n" +
            "attribute vec4 aTextureCoord; \n" +
            "varying vec2 vTextureCoord; \n" +
            "void main() \n" +
            "{ \n" +
            "	gl_Position = aPosition; \n" +
            "	vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}";

    public String FRAGMENT_SHADER_FADE =
            "precision lowp float; \n" +
            "varying mediump vec2 vTextureCoord; \n" +
            "uniform float uTweenFactor; \n" +
            "uniform sampler2D sTexture; \n" +
            "uniform sampler2D sTexture1; \n" +
            "void main() \n" +
            "{ \n" +
            "   gl_FragColor = mix(texture2D(sTexture, vTextureCoord), texture2D(sTexture1, vTextureCoord), uTweenFactor); \n" +
            "}";

    private final String TAG = "FadeBlendFilter";

    private int mhFadeBlendProgram;
    private int mhFadeBlendSTMatrixHandle;
    private int mhFadeBlendVertexCoord;
    private int mhFadeBlendTextureCoord;
    private int mhFadeBlendTexture1;
    private int mhFadeBlendTexture2;
    private int mhFadeBlendTweenFactor;

    private float mTweenFactor = 0.5f; // 默认参数暂时

    public FadeBlendFilter() {
        super();
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);

        mhFadeBlendProgram = OpenGlUtils.createProgram(VERTEX_SHADER_FADE, FRAGMENT_SHADER_FADE);
        mhFadeBlendSTMatrixHandle = GLES20.glGetUniformLocation(mhFadeBlendProgram, "uSTMatrix");
        mhFadeBlendVertexCoord = GLES20.glGetAttribLocation(mhFadeBlendProgram, "aPosition");
        mhFadeBlendTextureCoord = GLES20.glGetAttribLocation(mhFadeBlendProgram, "aTextureCoord");
        mhFadeBlendTexture1 = GLES20.glGetUniformLocation(mhFadeBlendProgram, "sTexture");
        mhFadeBlendTexture2 = GLES20.glGetUniformLocation(mhFadeBlendProgram, "sTexture1");
        mhFadeBlendTweenFactor = GLES20.glGetUniformLocation(mhFadeBlendProgram, "uTweenFactor");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight + " isExtTexture" + isExtTexture);
    }

    @Override
    public void destroy() {
        super.destroy();
        GLES20.glDeleteProgram(mhProgram);
        mIsInit = false;
        YYLog.info(TAG, "destroy");
    }

    @Override
    protected void updateParams() {
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            BaseFilterParameter param = it.next().getValue();
            float tweenFactor = ((FadeBlendFilterParameter) (param)).mTweenFactor;
            if (tweenFactor >= 0 && tweenFactor <= 1) {
                mTweenFactor = tweenFactor;
            }

            YYLog.info(TAG, "updateParams tweenFactor=" + tweenFactor);
        }
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (sample.mTextureId1 != OpenGlUtils.NO_TEXTURE) {

            storeOldFBO();

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);

            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            GLES20.glUseProgram(mhFadeBlendProgram);
            GLES20.glVertexAttribPointer(mhFadeBlendVertexCoord, 2, GLES20.GL_FLOAT, false, 0, OpenGlUtils.VERTEXCOORD_BUFFER);
            GLES20.glEnableVertexAttribArray(mhFadeBlendVertexCoord);
            GLES20.glVertexAttribPointer(mhFadeBlendTextureCoord, 2, GLES20.GL_FLOAT, false, 0, OpenGlUtils.TEXTURECOORD_BUFFER);
            GLES20.glEnableVertexAttribArray(mhFadeBlendTextureCoord);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(mTextureTarget, sample.mTextureId);
            GLES20.glUniform1i(mhFadeBlendTexture1, 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(mTextureTarget, sample.mTextureId1);
            GLES20.glUniform1i(mhFadeBlendTexture2, 1);

            GLES20.glUniformMatrix4fv(mhFadeBlendSTMatrixHandle, 1, false, OpenGlUtils.IDENTITY_MATRIX, 0);

            GLES20.glUniform1f(mhFadeBlendTweenFactor, mTweenFactor);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(mhFadeBlendVertexCoord);
            GLES20.glDisableVertexAttribArray(mhFadeBlendTextureCoord);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            sample.mTextureId = mFrameBufferTexture[0];
            sample.mFrameBufferId = mFrameBuffer[0];
            recoverOldFBO();
        }

        deliverToDownStream(sample);
        return true;
    }
}
