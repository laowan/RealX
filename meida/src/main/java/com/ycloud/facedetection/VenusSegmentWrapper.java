package com.ycloud.facedetection;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES31;

import com.venus.Venus;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.nio.ByteBuffer;

/**
 * Created by jinyongqing on 2018/4/3.
 */

public class VenusSegmentWrapper {
    private final String TAG = "VenusSegmentWrapper";

    private String[] mModelPath;
    private int mSegmentID = -1;
    private int mSegmentTextureId = OpenGlUtils.NO_TEXTURE;
    private int mTextureWidth = 0;
    private int mTextureHeight = 0;

    private int mSegmentMode = SEGMENT_GPU_MODE;

    public static final int SEGMENT_GPU_MODE = 1;
    public static final int SEGMENT_CPU_MODE = 2;

    //抠图input数据格式
    public static final int SEGMENT_INPUT_YUV_FRONT = 0;
    public static final int SEGMENT_INPUT_YUV_BACK = 1;
    public static final int SEGMENT_INPUT_RGBA = 2;

    private Venus.VN_ImageData mVnImageData;


    public VenusSegmentWrapper(Context context, int width, int height) {
        mModelPath = new String[2];
        mModelPath[0] = context.getApplicationContext().getFilesDir().getPath() + "/cache_seg.dat0";
        mModelPath[1] = context.getApplicationContext().getFilesDir().getPath() + "/cache_seg.dat1";
        mTextureWidth = width;
        mTextureHeight = height;
    }


    public void init() {
        YYLog.info(TAG, "init with GPU");
        mSegmentID = Venus.createSegmentCache(mModelPath[0], mModelPath[1]);

        OpenGlUtils.checkGlError("init begin");
        mSegmentTextureId = OpenGlUtils.createTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_RGBA, GLES31.GL_RGBA16F, mTextureWidth, mTextureHeight);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init segmentId=" + mSegmentID + ",textureId=" + mSegmentTextureId);
    }

    public void initWithCpu() {
        YYLog.info(TAG, "init with CPU");
        mSegmentMode = SEGMENT_CPU_MODE;
        OpenGlUtils.checkGlError("init begin");
        mSegmentID = Venus.createSegmentCacheCpu(mModelPath[0], mModelPath[1]);
        OpenGlUtils.checkGlError("init end");
        mVnImageData = new Venus.VN_ImageData();
    }

    public void deInit() {
        YYLog.info(TAG, "deInit segmentId=" + mSegmentID + ",textureId=" + mSegmentTextureId);
        Venus.destorySegment(mSegmentID);
        mSegmentID = -1;
        OpenGlUtils.checkGlError("destroy begin");
        OpenGlUtils.deleteTexture(mSegmentTextureId);
        OpenGlUtils.checkGlError("destroy end");
        mSegmentTextureId = OpenGlUtils.NO_TEXTURE;
    }

    public void deInitWithCpu() {
        Venus.destorySegmentCpu(mSegmentID);
        mSegmentID = -1;
        OpenGlUtils.checkGlError("destroy begin");
        OpenGlUtils.deleteTexture(mSegmentTextureId);
        OpenGlUtils.checkGlError("destroy end");
        mSegmentTextureId = OpenGlUtils.NO_TEXTURE;
    }

    /**
     * 根据抠图缓存data，获取当前帧的抠图结果，填充到sample到frameData中
     * 1.当前帧有对应的抠图缓存data，将抠图缓存data做为input，获取抠图结果
     * 2.当前帧没有对应的抠图缓存data，获取抠图结果后，将中间数据做为缓存data保存到outCacheData中
     *
     * @param sample       当前帧
     * @param inCacheData  缓存中间数据的输入
     * @param outCacheData 缓存中间数据的输出
     */
    public void updateSegmentDataWithCache(YYMediaSample sample, Venus.VN_SegmentCacheData inCacheData, Venus.VN_SegmentCacheData outCacheData) {
        /*YYLog.info(TAG, "updateSegmentDataWithCache segmentId=" + mSegmentID + ",textureId="
                + mSegmentTextureId + ",textureWidth=" + mTextureWidth + ",textureHeight=" + mTextureHeight);*/
        OpenGlUtils.checkGlError("updateSegmentDataWithCache begin");
        Venus.applySegmentCache(mSegmentID, sample.mTextureId, GLES20.GL_TEXTURE_2D,
                inCacheData, mSegmentTextureId, GLES20.GL_TEXTURE_2D, mTextureWidth, mTextureHeight, outCacheData);
        OpenGlUtils.checkGlError("updateSegmentDataWithCache end");

        sample.mSegmentFrameData.segmentTextureID = mSegmentTextureId;
        sample.mSegmentFrameData.segmentTextureTarget = GLES20.GL_TEXTURE_2D;
        sample.mSegmentFrameData.segmentTextureWidth = mTextureWidth;
        sample.mSegmentFrameData.segmentTextureHeight = mTextureHeight;
    }

    public void updateSegmentDataWithCacheCpu(YYMediaSample sample, byte[] inputImageData, Venus.VN_ImageData segmentOutData, int width, int height, int inputFormat) {
        Venus.applySegmentCacheCpu(mSegmentID, inputFormat, width, height, 3, inputImageData, null, segmentOutData, null);
        processSegmentDataCpu(sample, segmentOutData.data, segmentOutData.width, segmentOutData.height);
    }

    public void processSegmentDataCpu(YYMediaSample sample, byte[] data, int width, int height) {
        //创建一个单通道纹理，存放cpu抠图的结果
        OpenGlUtils.checkGlError("updateSegmentDataWithCacheCpu begin");
        mSegmentTextureId = OpenGlUtils.loadTexture(ByteBuffer.wrap(data), width, height, GLES20.GL_LUMINANCE, mSegmentTextureId);
        OpenGlUtils.checkGlError("updateSegmentDataWithCacheCpu end");

        sample.mSegmentFrameData.segmentTextureID = mSegmentTextureId;
        sample.mSegmentFrameData.segmentTextureTarget = GLES20.GL_TEXTURE_2D;
        sample.mSegmentFrameData.segmentTextureWidth = mVnImageData.width;
        sample.mSegmentFrameData.segmentTextureHeight = mVnImageData.height;
    }


    public Venus.VN_ImageData updateSegmentDataWithCacheCpu(byte[] inputImageData, int width, int height, int inputFormat, Venus.VN_SegmentCacheData inCacheData, Venus.VN_SegmentCacheData outCacheData) {
        Venus.applySegmentCacheCpu(mSegmentID, inputFormat, width, height, 3, inputImageData, inCacheData, mVnImageData, outCacheData);

        return mVnImageData;
    }
}
