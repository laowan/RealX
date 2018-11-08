package com.ycloud.gpuimage.adapter;

import android.opengl.GLES20;

import com.ycloud.utils.YYLog;
import com.yy.mediaframeworks.gpuimage.adapter.GlPboReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by kele on 2016/11/18.
 */

public class GlTextureImageReader {

    private byte[] mPixBytes = null;
    private ByteBuffer mPixBuffer = null;
    private boolean mGlPboSupported = false;
    private GlPboReader mGlPboReader = null;
    private int mReaderFrameBuffer = -1;

    private int mWidth = 0;
    private int mHeight = 0;

    public GlTextureImageReader(int width, int height) {
        mGlPboSupported = GlPboReader.isPboSupport();
        YYLog.info(this, "[GlUtil] pbo support=" + mGlPboSupported);
        init(width, height);
    }

    /**
     * 可强制选择使用哪种模式(如果选择PBO,则需要看实际是否支持)
     * @param width
     * @param height
     * @param choosePboSupported
     */
    public GlTextureImageReader(int width, int height, boolean choosePboSupported) {
        if (choosePboSupported == false) {
            mGlPboSupported = false;
        } else {
            mGlPboSupported = GlPboReader.isPboSupport();
        }

        YYLog.info(this, "[GlUtil] pbo support=" + mGlPboSupported);
        init(width, height);
    }

    private void init(int width, int height) {
        int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        mReaderFrameBuffer = frameBuffers[0];

        YYLog.info(this, "[reader] init width=" + width + " height=" + height + " readerFrameBuffer=" + mReaderFrameBuffer);

        mPixBuffer = ByteBuffer.allocateDirect(width * height * 4);

        if (mGlPboSupported) {
            mGlPboReader = new GlPboReader(width, height);
        } else {
            mPixBuffer.order(ByteOrder.nativeOrder());
        }
        mPixBytes = new byte[width * height * 4];

        mWidth = width;
        mHeight = height;
    }


    public void destroy() {
        if (mReaderFrameBuffer > 0) {
            int[] framebuffers = new int[1];
            framebuffers[0] = mReaderFrameBuffer;
            GLES20.glDeleteFramebuffers(1, framebuffers, 0);
            mReaderFrameBuffer = -1;
        }

        if (mGlPboSupported && mGlPboReader != null) {
            mGlPboReader.deInitPBO();
            mGlPboReader = null;
        }
        YYLog.info(this, "[pbo] readTotalTime=" + mReadTotalTime
                                    + " mByteExtractTime=" + mByteExtractTimes
                                    + " mNoPboCount=" + mNoPboCount
                                    + " mDirectBufferTime="+mDirectBufferExtractTime
                                    + " mGLFinishTime="+mGLFinishTime);
    }

    public void checkImageSize(int widht, int height) {
        if (mWidth == widht && mHeight == height) {
            return;
        }

        destroy();
        init(widht, height);
    }


    private long mReadTotalTime = 0;
    private long mNoPboCount = 0;
    private long mByteExtractTimes = 0;
    private long mDirectBufferExtractTime = 0;
    private long mGLFinishTime = 0;

    public ByteBuffer readByteBuffer(int texture, int width, int height) {

        ByteBuffer ret = null;

        long readBeginMs = System.currentTimeMillis();

        if (mReaderFrameBuffer < 0) {
            YYLog.error(this, "[reader] read fail, mReaderFrameBuffer =" + mReaderFrameBuffer);
            return null;
        }
        checkImageSize(width, height);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mReaderFrameBuffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                texture, 0);

        //TODO pbo crash..
        if (mGlPboSupported && mGlPboReader != null) {
            ByteBuffer pixBuffer = mGlPboReader.downloadGpuBufferWithPbo();
            long dataExtractBeginMs = System.currentTimeMillis();

            if (pixBuffer == null) {
                mPixBuffer.clear();
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixBuffer);
                ret = mPixBuffer;
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                mNoPboCount++;
                return ret;
            } else {
                ret = pixBuffer;
            }
            mByteExtractTimes += (System.currentTimeMillis() - dataExtractBeginMs);
        } else {
            mPixBuffer.clear();
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixBuffer);
            ret = mPixBuffer;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mReadTotalTime += (System.currentTimeMillis() - readBeginMs);
        return ret;
    }

    //不是线程安全.
    public byte[] read(int texture, int width, int height) {
        long readBeginMs = System.currentTimeMillis();
        if (mReaderFrameBuffer < 0) {
            YYLog.error(this, "[reader] read fail, mReaderFrameBuffer =" + mReaderFrameBuffer);
            return null;
        }

        checkImageSize(width, height);

        byte[] ret = null;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mReaderFrameBuffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                texture, 0);

        //TODO pbo crash..
        if (mGlPboSupported && mGlPboReader != null) {
            ByteBuffer pixBuffer = mGlPboReader.downloadGpuBufferWithPbo();
            long dataExtractBeginMs = System.currentTimeMillis();

            if (pixBuffer == null) {
                mPixBuffer.clear();
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixBuffer);
                //ret = mPixBuffer.array();
                mPixBuffer.position(0);
                mPixBuffer.get(mPixBytes);
                ret = mPixBytes;
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                mNoPboCount++;
                return ret;
            }

            if (pixBuffer.hasArray()) {
                ret = pixBuffer.array();
            } else {
                //YYLog.info(this, "[debug] tmpPixBuff.remain/pos"+tmpPixelBuf.remaining() + " " + tmpPixelBuf.position() + " pixBytes.len="+mPixBytes.length);
                long beginDirectTime = System.currentTimeMillis();
                pixBuffer.position(0);
                pixBuffer.get(mPixBytes);
                ret = mPixBytes;
                mDirectBufferExtractTime += (System.currentTimeMillis() - beginDirectTime);
            }

            mByteExtractTimes += (System.currentTimeMillis() - dataExtractBeginMs);
        } else {
            mPixBuffer.clear();
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixBuffer);
//            ret = mPixBuffer.array();
            mPixBuffer.position(0);
            mPixBuffer.get(mPixBytes);
            ret = mPixBytes;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mReadTotalTime += (System.currentTimeMillis() - readBeginMs);
        return ret;
    }
}
