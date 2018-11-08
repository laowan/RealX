package com.yy.mediaframeworks.gpuimage.adapter;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;

import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Download pixel buffer from GPU to CPU with PBO.
 * Only works with GL ES 3.0 
 * Created By Huang Chengzong
 * 2016/11/4
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GlPboReader {

    private final static String TAG = "GlPboReader";

    private final static int DEFUALT_NUMBER_PBOS = 2;
    private int[] mPboIds;
    private int mPboIndex = 0;
    private int mPboNumember = DEFUALT_NUMBER_PBOS;
    private int mPboBufferSize;
    private int mPboDownloadCount;
    private AtomicBoolean mIsInit = new AtomicBoolean(false);

    private long mMAPWaitTimeMs = 0;
    private long mReadMapTimeMs =0;
    private long mDownloadTimeMs = 0;
    private long mPboInitTimeMs = 0;
    private long mGLFinishTime = 0;

    private int mWidth, mHeight;
    private static boolean mIsPboSupport = false;

    public GlPboReader(int width, int height) {
        init(width, height);
    }

    public static void checkPboSupport(final Context context) {
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        mIsPboSupport = configurationInfo.reqGlEsVersion >= 0x30000;
    }

    public static boolean isPboSupport() {
        return mIsPboSupport;
    }

    private void init(int width, int height) {
        mWidth = width;
        mHeight = height;
        mPboBufferSize = mWidth * height * 4;
        initPBO();
    }

    private void initPBO() {
        long begin = System.currentTimeMillis();
        mPboIds = new int[DEFUALT_NUMBER_PBOS];
        GLES30.glGenBuffers(DEFUALT_NUMBER_PBOS, mPboIds, 0);

        for (int i = 0; i < DEFUALT_NUMBER_PBOS; i++) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds[i]);
            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, mPboBufferSize, null, GLES30.GL_STREAM_DRAW);
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
        mIsInit.set(true);
        mPboInitTimeMs += (System.currentTimeMillis() - begin);
    }

    public void deInitPBO() {
        YYLog.info(this, "[pbo] mMAPWaitTimeMs = "+mMAPWaitTimeMs + " mReadWaitTimeMs="+mReadMapTimeMs
                                    + " downloadTims=" + mDownloadTimeMs
                                    + " pboInitTimes=" + mPboInitTimeMs
                                    +"  glFinishTimes=" +mGLFinishTime);
        GLES30.glDeleteBuffers(DEFUALT_NUMBER_PBOS, mPboIds, 0);
    }

    public ByteBuffer downloadGpuBufferWithPbo() {
        long downBegin = System.currentTimeMillis();

        ByteBuffer pboBuffer = null;
        mPboIndex = (mPboIndex + 1) % mPboNumember;

        int nextPboIndex = (mPboIndex + 1) % mPboNumember;

        if (mPboDownloadCount < mPboNumember) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds[mPboIndex]);
            GLESNativeTools.glReadPixelWithJni(0, 0, mWidth, mHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0);
        } else {
            long readBeginMs = System.currentTimeMillis();
           // GLES20.glFinish();
            mGLFinishTime += (System.currentTimeMillis() - readBeginMs);


            long beginMs = System.currentTimeMillis();
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds[mPboIndex]);
            GLESNativeTools.glReadPixelWithJni(0, 0, mWidth, mHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0);
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, mPboIds[nextPboIndex]);

            long mapBefore = System.currentTimeMillis();
            mReadMapTimeMs += mapBefore - beginMs;

            pboBuffer = (ByteBuffer) GLES30.glMapBufferRange(GLES30.GL_PIXEL_PACK_BUFFER, 0, mPboBufferSize, GLES30.GL_MAP_READ_BIT);
            mMAPWaitTimeMs += (System.currentTimeMillis() - mapBefore);

            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);

        }
        mPboDownloadCount++;
        if (mPboDownloadCount == Integer.MAX_VALUE) {
            mPboDownloadCount = mPboNumember;
        }
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);

        mDownloadTimeMs += (System.currentTimeMillis() - downBegin);
        return pboBuffer;
    }

    public void onImageSizeUpdate(int width, int height) {
        if (mWidth == width && mHeight == height) {
            return;
        }

        this.deInitPBO();
        this.init(width, height);
    }
}
