package com.ycloud.svplayer.surface;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.view.Surface;

import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

/**
 * Created by DZHJ on 2017/8/12.
 */

public class InputSurface {
    public static  final String TAG = InputSurface.class.getSimpleName();

    private int mTextureId = OpenGlUtils.NO_TEXTURE;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;


    public void updateTexImage() {
        mSurfaceTexture.updateTexImage();
    }

    public void setup() {
        mTextureId = OpenGlUtils.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurface = new Surface(mSurfaceTexture);
    }

    public void release() {
        if (mTextureId != OpenGlUtils.NO_TEXTURE) {
            OpenGlUtils.deleteTexture(mTextureId);
            mTextureId = OpenGlUtils.NO_TEXTURE;
        }

        mSurface.release();
        mSurfaceTexture.release();
        mSurface = null;
        mSurfaceTexture = null;
    }

    public  int getTextureId(){
        return mTextureId;
    }

    public  int getTextureTarget() { return GLES11Ext.GL_TEXTURE_EXTERNAL_OES;}

    public Surface getSurface(){
        return mSurface;
    }

    public SurfaceTexture getSurfaceTexture(){
        return  mSurfaceTexture;
    }

    public void getTransformMatrix(float[] textureCoordMatrix){
        mSurfaceTexture.getTransformMatrix(textureCoordMatrix);
    }
}
