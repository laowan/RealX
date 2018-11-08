package com.ycloud.gpuimagefilter.filter;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.videorecord.IOriginalPreviewSnapshotListener;
import com.ycloud.gpuimage.adapter.GlTextureImageReader;
import com.ycloud.utils.ImageSizeUtil;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by liuchunyu on 2017/7/24.
 */

public class TransFormTextureFilter extends BaseFilter {
    private String TAG = "TransFormTextureFilter";
    private FloatBuffer mClipInputTextureCoordBuffer = null; // 输入纹理经过第一个filter时进行裁剪的参数
    private boolean mNeedUpataBuffer = false;
    private boolean mUseCilpBuffer = false;
    private boolean mUsedForPlayer = false;
    private boolean mEnableRotate = false;
    private GlTextureImageReader mGlImageReader = null;
    private int mRotateAngle = 0;

    private int[] mVideoFrameBuffer;
    private int[] mVideoFrameBufferTexture;
    private int mVideoWidth;
    private int mVideoHeight;

    private ExecutorService mSingleThreadExecutor = null;
    ConcurrentLinkedQueue<SnapshotParam> mSnapshotParamQueue = new ConcurrentLinkedQueue<SnapshotParam>();
    private class SnapshotParam{
        String path;
        int width;
        int height;
        int type;
        int quality;
        boolean flipX;
        AspectRatioType aspect;
    }
    private IOriginalPreviewSnapshotListener mOriginalPreviewSnapshotListener = null;
    private AspectRatioType mAspect = AspectRatioType.ASPECT_RATIO_4_3;

    public TransFormTextureFilter() {
        super();
    }

    public void setmUsedForPlayer(boolean bUseForPlayer) {
        YYLog.info(TAG, "setmUsedForPlayer " + bUseForPlayer);
        mUsedForPlayer = bUseForPlayer;
    }

    public void setEnableRotate(boolean enable) {
        mEnableRotate = enable;
    }

    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        if (mEnableRotate && mUsedForPlayer) {
            super.initExt(outputWidth, outputHeight, isExtTexture, oFContext);
        } else {
            super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        }
        mNeedUpataBuffer = true;
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    public void initVideoTexture(int width, int height) {
        mVideoFrameBuffer = new int[1];
        mVideoFrameBufferTexture = new int[1];

        mVideoWidth = width;
        mVideoHeight = height;

        OpenGlUtils.checkGlError("initVideoTexture begin");
        OpenGlUtils.createFrameBuffer(mVideoWidth, mVideoHeight, mVideoFrameBuffer, mVideoFrameBufferTexture, 1);
        OpenGlUtils.checkGlError("initVideoTexture end");
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mClipInputTextureCoordBuffer != null) {
            mClipInputTextureCoordBuffer.clear();
            mClipInputTextureCoordBuffer = null;
        }
        if (mGlImageReader != null) {
            mGlImageReader.destroy();
            mGlImageReader = null;
        }

        if (mVideoFrameBufferTexture != null && mVideoFrameBuffer != null) {
            OpenGlUtils.releaseFrameBuffer(1, mVideoFrameBufferTexture, mVideoFrameBuffer);
            mVideoFrameBufferTexture = null;
            mVideoFrameBuffer = null;
        }

        if (mSingleThreadExecutor != null) {
            mSingleThreadExecutor.shutdown();
            mSingleThreadExecutor = null;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    @Override
    public void setImageSize(int imageWidth, int imageHeight) {
        super.setImageSize(imageWidth, imageHeight);
        mNeedUpataBuffer = true;
    }

    private void clipInputTextureWithTextureCoordBuffer(YYMediaSample sample) {
        if (mImageWidth != sample.mWidth || mImageHeight != sample.mHeight) {
            setImageSize(sample.mWidth, sample.mHeight);
        }

        if (mNeedUpataBuffer) {
            if (mImageWidth != mOutputWidth || mImageHeight != mOutputHeight) {
                mImageWidth = sample.mWidth;
                mImageHeight = sample.mHeight;

                FloatBuffer floatBuffer = ByteBuffer.allocateDirect(OpenGlUtils.COORD_LEN * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
                floatBuffer.put(sample.mShouldUpsideDown ? OpenGlUtils.TEXTURE_COORD_UPDOWN : OpenGlUtils.TEXTURE_COORD).position(0);
                if (mClipInputTextureCoordBuffer != null) {
                    mClipInputTextureCoordBuffer.clear();
                }
                mClipInputTextureCoordBuffer = OpenGlUtils.adjustTexture(floatBuffer, mImageWidth, mImageHeight, mOutputWidth, mOutputHeight);
                mUseCilpBuffer = true;
                YYLog.info(TAG, "clipInputTextureWithTextureCoordBuffer mImageWidth=" + mImageWidth + " mImageHeight=" + mImageHeight
                        + " mOutputWidth" + mOutputWidth + " mOutputHeight" + mOutputHeight);
            } else {
                mUseCilpBuffer = false;
            }
            mNeedUpataBuffer = false;
        }
    }

    private ByteBuffer mRgbaByteBuffer = null;
    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        return processMediaSample(sample, upstream, true);
    }

    public void setRotateAngle(int angle) {
        mRotateAngle = angle;
    }

    public boolean processMediaSample(YYMediaSample sample, Object upStream, boolean shouldDeliver) {

        //may be sample.mTextureTarget is not assigned with value, so just temporary condition.
        if((sample.mTextureTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES && mTextureTarget == GLES20.GL_TEXTURE_2D)
            || (sample.mTextureTarget == GLES20.GL_TEXTURE_2D && mTextureTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES)) {
            changeTextureTarget(sample.mTextureTarget);
        }

        OpenGlUtils.checkGlError("TransformTexture filter processMediaSample start");
        clipInputTextureWithTextureCoordBuffer(sample);
        storeOldFBO();

        // 处理texture1，总是有效的
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);

        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        FloatBuffer textureCoordBuffer = sample.mShouldUpsideDown ? OpenGlUtils.TEXTURECOORD_BUFFER_UPDOWN : OpenGlUtils.TEXTURECOORD_BUFFER;

        if (!mEnableRotate || !mUsedForPlayer) {
            drawSquare(sample.mTextureId, OpenGlUtils.VERTEXCOORD_BUFFER,
                    mUseCilpBuffer == true ? mClipInputTextureCoordBuffer : textureCoordBuffer, sample.mTransform);
        } else {
            if (SDKCommonCfg.getRecordModePicture() && sample.mShouldUpsideDown && mRotateAngle == 180) {  // for YOYI rotate 180
                drawSquare(sample.mTextureId, OpenGlUtils.VERTEXCOORD_BUFFER, OpenGlUtils.TEXTURECOORD_BUFFER_UPDOWN, OpenGlUtils.IDENTITY_MATRIX);
            } else {
                drawSquare(sample.mTextureId, OpenGlUtils.VERTEXCOORD_BUFFER, OpenGlUtils.TEXTURECOORD_BUFFER, OpenGlUtils.IDENTITY_MATRIX);
            }
        }
        System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, sample.mTransform, 0, sample.mTransform.length);
        sample.mTextureId = mFrameBufferTexture[0];
        sample.mFrameBufferId = mFrameBuffer[0];
        sample.mTextureTarget = GLES20.GL_TEXTURE_2D;
        sample.mWidth = mOutputWidth;
        sample.mHeight = mOutputHeight;

        if (mSnapshotParamQueue != null && mSnapshotParamQueue.size() > 0) {
            takeSnapshot(sample, mSnapshotParamQueue.poll());
        }

        // 处理texture2
        if (sample.mTextureId1 != OpenGlUtils.NO_TEXTURE) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[1]);

            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            drawSquare(sample.mTextureId1, OpenGlUtils.VERTEXCOORD_BUFFER,
                    mUseCilpBuffer == true ? mClipInputTextureCoordBuffer : textureCoordBuffer, sample.mTransform1);
            System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, sample.mTransform1, 0, sample.mTransform1.length);
            sample.mTextureId1 = mFrameBufferTexture[1];
        }

        // 处理video texture, oes texture to 2d texture
        if (sample.mExtraTextureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mVideoFrameBuffer[0]);

            GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);
            GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            drawSquare(sample.mExtraTextureId, OpenGlUtils.VERTEXCOORD_BUFFER,
                    mUseCilpBuffer == true ? mClipInputTextureCoordBuffer : textureCoordBuffer, sample.mExtraTextureTransform);
            System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, sample.mExtraTextureTransform, 0, sample.mExtraTextureTransform.length);
            sample.mExtraTextureId = mVideoFrameBufferTexture[0];
        }

        recoverOldFBO();

        OpenGlUtils.checkGlError("processMediaSample TransformTextureFitler end");


        if(shouldDeliver) {
            deliverToDownStream(sample);
        }
        return true;
    }

    public void setOriginalPreviewSnapshotListener(IOriginalPreviewSnapshotListener listener) {
        mOriginalPreviewSnapshotListener = listener;
    }

    public void setAspectRatio(AspectRatioType aspect) {
        mAspect = aspect;
        YYLog.info(TAG, "setAspectRatio " + aspect);
    }

    public void takeOriginalPreviewSnapshot(String path, int width, int height, int type, int quality, boolean flipX) {
        SnapshotParam param = new SnapshotParam();
        param.path = path;
        param.width = width;
        param.height = height;
        param.type = type;
        param.quality = quality;
        param.flipX = flipX;
        param.aspect = mAspect;
        mSnapshotParamQueue.offer(param);
    }

    private void takeSnapshot(YYMediaSample sample, SnapshotParam param) {
        int width = sample.mWidth;
        int height = sample.mHeight;
        ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        mByteBuffer.order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
        OpenGlUtils.checkGlError("glReadPixels ");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (bitmap != null) {
            bitmap.copyPixelsFromBuffer(mByteBuffer);
        }
        saveBitmap(bitmap, param);
    }

    private Bitmap getMirrorBitmap(Bitmap src, int newWidth, int newHeight, boolean flipX, AspectRatioType aspect) {
        int width = src.getWidth();
        int height = src.getHeight();

        Matrix matrix = new Matrix();
        //matrix.preScale(1, -1);
        if (flipX) {
            matrix.preScale(-1.0f, 1.0f);   // mirror by X axis
        }

        Rect r = ImageSizeUtil.getCropRect(width, height, aspect);

        int adjustWidth = (r.right-r.left);
        int adjustHeight = ((r.bottom-r.top));
        float scaleWidth = ((float) newWidth) / adjustWidth;
        float scaleHeight = ((float) newHeight) / adjustHeight;
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        Bitmap result = Bitmap.createBitmap(
                src, r.left, r.top, (r.right-r.left), (r.bottom-r.top), matrix, true);
        src.recycle();
        return result;
    }

    private void notifyResult(int result, String path) {
        if (mOriginalPreviewSnapshotListener != null) {
            mOriginalPreviewSnapshotListener.onScreenSnapshot(result, path);
        }
    }

    private void CreateDirectoryIfNeed(String path) {
        int lastIndex = path.lastIndexOf(File.separatorChar);
        if (lastIndex < 0) {
            YYLog.error(TAG, "path " + path +" not available !");
            return;
        }
        try {
            String directory = path.substring(0, lastIndex);
            File file = new File(directory);
            if (!file.exists() || !file.isDirectory()) {
                if (!file.mkdirs()) {
                    YYLog.error(TAG, "mkdirs " + directory + " failed !");
                }
            }
        }catch (Exception e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }
    }


    private void saveBitmap(final Bitmap bmp, final SnapshotParam param) {
        if (mSingleThreadExecutor == null) {
            mSingleThreadExecutor = Executors.newSingleThreadExecutor();
        }

        mSingleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (null == bmp || param == null) {
                    YYLog.error(TAG, "takePicture error ! bmp == null. ");
                    notifyResult(-1, param == null ? " " : param.path);
                    return;
                }

                Bitmap bitmap = getMirrorBitmap(bmp, param.width, param.height, param.flipX, param.aspect);
                if (null == bitmap) {
                    notifyResult(-1, param.path);
                    return;
                }

                long time = System.currentTimeMillis();
                FileOutputStream out = null;
                try {
                    CreateDirectoryIfNeed(param.path);
                    out = new FileOutputStream(param.path);
                } catch (FileNotFoundException e) {
                    YYLog.error(TAG, String.format(Locale.getDefault(), "%s not found: %s", param.path, e.toString()));
                }
                if (out == null) {
                    notifyResult(-1, param.path);
                    bitmap.recycle();
                    return;
                }
                Bitmap.CompressFormat format = (param.type == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG);
                boolean ret = bitmap.compress(format, param.quality, out);
                try {
                    out.flush();
                    out.close();
                    notifyResult(0, param.path);
                } catch (IOException e) {
                    YYLog.error(TAG, "save to file failed: IOException happened:" + e.toString());
                    ret = false;
                    notifyResult(-1, param.path);
                } finally {
                    bitmap.recycle();
                }
                YYLog.info(TAG, "takeSnapshot " + param.path + " ret : " + ret + " cost :" + (System.currentTimeMillis() -  time));
            }
        });
    }

    /* test code,用于验证同一个时间戳,画面有所不同 */
    private int mCurTracedStride = 0;
    private void saveFrame(YYMediaSample sample) {
        if (mGlImageReader == null) {
            mGlImageReader = new GlTextureImageReader(mOutputWidth, mOutputHeight);
        }

        mCurTracedStride++;
        byte[] readerRbgaBytes = mGlImageReader.read(sample.mTextureId, mOutputWidth, mOutputHeight);
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream("/sdcard/ztest/" + mCurTracedStride + "_" + sample.mTimestampMs + ".png"));
            Bitmap bmp = Bitmap.createBitmap(mOutputWidth, mOutputHeight, Bitmap.Config.ARGB_8888);
            ByteBuffer pixelBuf = ByteBuffer.wrap(readerRbgaBytes);
            bmp.copyPixelsFromBuffer(pixelBuf);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            bmp.recycle();
        } catch (Exception ex) {
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (Exception ex) {

            }
        }
    }

    private void saveFrame2(YYMediaSample sample) {
        if (mRgbaByteBuffer == null) {
            mRgbaByteBuffer = ByteBuffer.allocateDirect(mOutputWidth * mOutputHeight * 4);
        }
        mCurTracedStride++;
        mRgbaByteBuffer.rewind();
        GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mRgbaByteBuffer);

        BufferedOutputStream bos = null;
        try {
            mRgbaByteBuffer.rewind();
            bos = new BufferedOutputStream(new FileOutputStream("/sdcard/ztest/" + mCurTracedStride + "_" + sample.mTimestampMs + ".png"));
            Bitmap bmp = Bitmap.createBitmap(mOutputWidth, mOutputHeight, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(mRgbaByteBuffer);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            bmp.recycle();
        } catch (Exception ex) {
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (Exception ex) {

            }
        }
    }
}
