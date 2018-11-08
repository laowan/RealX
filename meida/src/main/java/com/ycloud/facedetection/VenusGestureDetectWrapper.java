package com.ycloud.facedetection;

import android.content.Context;
import android.opengl.GLES20;

import com.venus.Venus;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

/**
 * Created by jinyongqing on 2018/1/24.
 */

public class VenusGestureDetectWrapper {
    private final String TAG = VenusGestureDetectWrapper.class.getSimpleName();
    private int mGestureID = -1;
    private String mModelPath;

    public VenusGestureDetectWrapper(Context context) {
        mModelPath = context.getApplicationContext().getFilesDir().getPath() + "/gesture.dat";
    }

    public void init() {
        YYLog.info(TAG, "init");
        OpenGlUtils.checkGlError("init start");
        mGestureID = Venus.createGesture(mModelPath);
        OpenGlUtils.checkGlError("init end");
    }

    public void deInit() {
        YYLog.info(TAG, "deInit");
        OpenGlUtils.checkGlError("destroy start");
        Venus.destoryGesture(mGestureID);
        OpenGlUtils.checkGlError("destroy end");
        mGestureID = -1;
    }

    public void updateGestureData(YYMediaSample sample, int textureWidth, int textureHeight) {
//        YYLog.info(TAG, "applyGesture gestureID=" + mGestureID + ",ofContext=" + mOfContext);
        sample.mGestureFrameDataArr = new Venus.VN_GestureFrameDataArr();
        Venus.applyGesture(mGestureID, sample.mTextureId, GLES20.GL_TEXTURE_2D, textureWidth, textureHeight, sample.mGestureFrameDataArr);
    }
}
