package com.ycloud.mediafilters;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.opengl.GLES20;

import com.ycloud.mediarecord.IBlurBitmapCallback;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.ByteBufferPool;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by DZHJ on 2017/4/29.
 */

public class SnapshotFilter extends  AbstractYYMediaFilter {
    static private int SNAPSHOT_POOL_SIZE = 2;
    private int mSnapIndex;
    private AtomicBoolean mSnapshotStarted;
    private RecordConfig mRecordConfig;

    private long mLastSnapShotTime = 0;
    private long mSnapshotStartTime = 0;
    private long mSnapshotDelta = 0;
    private Stack<Long> mPreRecordSnapshotDeltaList = new Stack<>();

    private static ExecutorService mThreadPool = Executors.newSingleThreadExecutor();
    private ByteBufferPool mSnapshotPool = null;
    private Bitmap mBitmap = null;

    private boolean mTakeCurrentSnapshot;
    private IBlurBitmapCallback mBlurBitmapCallback;
    private AtomicBoolean mInited = new AtomicBoolean(false);

    public SnapshotFilter(RecordConfig recordConfig){
        mSnapshotStarted = new AtomicBoolean(false);
        mRecordConfig = recordConfig;
        mTakeCurrentSnapshot = false;
        init();
    }

    public void init() {
        YYLog.info(TAG, "SnapshotFilter init, mSnapshotPool width:" + mRecordConfig.getVideoWidth() + " height:" + mRecordConfig.getVideoHeight());
        File file = new File(mRecordConfig.getSnapShotPath());
        if (!file.exists() || (file.exists() && !file.isDirectory())) {
            file.mkdirs();
        }
        mInited.set(true);
    }

    public void startSnapshot(){
        mSnapIndex = 0;
        mLastSnapShotTime = 0;
        mSnapshotStartTime = 0;
        mSnapshotDelta = 0;
    }

    public void stopSnapshot(){
        mSnapshotStarted.set(false);
        mPreRecordSnapshotDeltaList.push(mSnapshotDelta);
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (mBitmap != null) {
                    mBitmap.recycle();
                    mBitmap = null;
                }
            }
        });
        mSnapshotPool = null;
        YYLog.info(TAG, "snapshot delta of record:" + mSnapshotDelta);
    }

    //业务删除一段录制的时候，需要同步更新snapshot保存的上次录制结束时间点与最后截图的时间间隔，防止截图不准确
    public void deleteLastRecordSnapshot() {
        if (mPreRecordSnapshotDeltaList != null && !mPreRecordSnapshotDeltaList.empty()) {
            long delta = mPreRecordSnapshotDeltaList.pop();
            YYLog.info(TAG, "update snapshot delta list, remove delta=" + delta + " of this segment");
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream){

        if (!mInited.get()) {
            return false;
        }

        //此纹理不能用来截图.
        if(!sample.mDeliverToSnapshot) {
            return false;
        }

        if (!mSnapshotStarted.get()) {
            deliverToDownStream(sample);
            return true;
        }

        if(mTakeCurrentSnapshot) {
            takeSnapShot(sample);
            mTakeCurrentSnapshot = false;
        }

        long currentTime = System.currentTimeMillis();
        if(mSnapshotStartTime == 0) {
            mSnapshotStartTime = currentTime;
        }

        /*如果录制速度大于1的情况下，当前生成视频时采用丢帧策略，截图也应该相应丢帧，录制速度小于1则不用*/
        float recordSpeed =  ((mRecordConfig.getRecordSpeed() > 1.0) ? mRecordConfig.getRecordSpeed() : 1.0f);

        /*当前时间点与上次截图的时间间隔，如果是本次录制的首张截图，需要考虑record session的上次录制结束时间点与最后截图的时间间隔*/
        if(mLastSnapShotTime == 0) {
            /*判断是否是一次video record session的第一段录制*/
            if(!mPreRecordSnapshotDeltaList.isEmpty()) {
                mSnapshotDelta = currentTime - mSnapshotStartTime + mPreRecordSnapshotDeltaList.peek();
            } else {
                mSnapshotDelta = currentTime - mSnapshotStartTime + (long)((1000.0 * recordSpeed)/mRecordConfig.getSnapFrequency());
            }
//            YYLog.info(TAG, "start take snapshot, first delta:" + mSnapshotDelta);
        } else {
            mSnapshotDelta = currentTime - mLastSnapShotTime;
        }

        if(mSnapshotDelta > (long)((1000.0 * recordSpeed)/mRecordConfig.getSnapFrequency())) {
            mLastSnapShotTime = currentTime;
//            YYLog.info(TAG, "take snapshot,delta is:" + mSnapshotDelta);
            snapShot(sample);
        }

        deliverToDownStream(sample);

        return true;
    }

    private void snapShot(YYMediaSample sample) {
        int frameBufferId = sample.mFrameBufferId;
        if(frameBufferId<0){
            return;
        }
        if (mSnapshotPool == null) {
            mSnapshotPool = new ByteBufferPool(SNAPSHOT_POOL_SIZE, mRecordConfig.getVideoWidth() * mRecordConfig.getVideoHeight() * 4);
        }

        try {
            ByteBuffer snapByteBf = mSnapshotPool.newByteBuffer();
            if (snapByteBf != null){
                snapByteBf.clear();
                snapByteBf.order(ByteOrder.LITTLE_ENDIAN);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId);
                GLES20.glReadPixels(0, 0, sample.mWidth, sample.mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, snapByteBf);
                saveToFile(snapByteBf, sample.mDisplayRotation);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            }else{
                YYLog.i(TAG,"snapShot snapshotPool is empty");
            }

        } catch (Throwable e) {
            YYLog.e(TAG, "snapshot error:" + e.toString());
        }
    }

    /**
     * 图片反转
     *
     * @param bmp
     * @param flag
     *            0为水平反转，1为垂直反转
     * @return
     */
    public static Bitmap reverseBitmap(Bitmap bmp, int flag) {
        float[] floats = null;
        switch (flag) {
            case 0: // 水平反转
                floats = new float[] { -1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f };
                break;
            case 1: // 垂直反转
                floats = new float[] { 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f };
                break;
        }

        if (floats != null) {
            Matrix matrix = new Matrix();
            matrix.setValues(floats);
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        }

        return null;
    }

    private void saveToFile(final ByteBuffer snap, final int rotation) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (mBitmap == null) {
                    mBitmap = Bitmap.createBitmap(mRecordConfig.getVideoWidth(), mRecordConfig.getVideoHeight(), Bitmap.Config.ARGB_8888);
                }
                mBitmap.copyPixelsFromBuffer(snap);
                //在极慢速度下，每张图片保存多张截图
                float recordSpeed =  ((mRecordConfig.getRecordSpeed() < 1.0) ? mRecordConfig.getRecordSpeed() : 1.0f);
                int snapshotNum = (int)(1 / recordSpeed);

                for(int i = 0; i < snapshotNum; i++) {
                    mSnapIndex++;
                    FileOutputStream out = null;
                    String indexStr = String.format("%03d", mSnapIndex);
                    String finalFilePath = mRecordConfig.getSnapShotPath() + File.separator + mRecordConfig.getSnapShotFileNamePrefix() + indexStr + ".jpg";
                    try {
                        out = new FileOutputStream(finalFilePath);
                    } catch (FileNotFoundException e) {
                        YYLog.error(TAG, "snapshot saveToFile "+finalFilePath + "not found:" + e.toString());
                    }
                    if (out == null) {
                        return;
                    }
                    mBitmap.compress(Bitmap.CompressFormat.JPEG, mRecordConfig.getSnapshotQuality(), out);
                    try {
                        out.flush();
                        out.close();
                    } catch (IOException e) {
                        YYLog.error(TAG, "save to file failed: IOException happened:" + e.toString());
                    }

                    //write metadata with exif to first snapshot jpeg file
                    if (mSnapIndex == 1) {
                        try {
                            ExifInterface exif = new ExifInterface(finalFilePath);
//                            YYLog.info(TAG, "jyq test filePath=" + finalFilePath);
                            String rotateExif = sample2ExifRotate(rotation);
                            //为了兼容业务层，这里做了一个hack，使用TAG_FLASH来保存图片拍摄时候的屏幕方向
                            exif.setAttribute(ExifInterface.TAG_FLASH, rotateExif);
                            exif.saveAttributes();
                        } catch (IOException e) {
                            YYLog.error(TAG, "save metadata with Exif error");
                        }
                    }
                }
                if(mSnapshotPool != null) {
                    mSnapshotPool.freeByteBuffer(snap);
                }
            }
        });
    }

    @Override
    public void deInit() {
        YYLog.info(TAG, "SnapshotFilter deInit");
        stopSnapshot();
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (mBitmap != null) {
                    mBitmap.recycle();
                    mBitmap = null;
                }
            }
        });
        mSnapshotPool = null;
        mInited.set(false);
    }

    public boolean isSnaping() {
        return mSnapshotStarted.get();
    }

    public void setSnapshotEnable(boolean encodeEnable) {
        mSnapshotStarted.set(encodeEnable);
    }

    /*提供外部获取截图*/
    public void setCaptureCallback(IBlurBitmapCallback blurBitmapCallback) {
        mBlurBitmapCallback = blurBitmapCallback;

        mTakeCurrentSnapshot = true;
    }

    private void takeSnapShot(YYMediaSample sample) {
        int frameBufferId = sample.mFrameBufferId;
        if (frameBufferId < 0) {
            return;
        }

        if (mSnapshotPool == null) {
            mSnapshotPool = new ByteBufferPool(SNAPSHOT_POOL_SIZE, mRecordConfig.getVideoWidth() * mRecordConfig.getVideoHeight() * 4);
        }

        try {
            ByteBuffer snapByteBf = mSnapshotPool.newByteBuffer();
            if (snapByteBf != null) {
                snapByteBf.clear();
                snapByteBf.order(ByteOrder.LITTLE_ENDIAN);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId);
                GLES20.glReadPixels(0, 0, sample.mWidth, sample.mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, snapByteBf);

                Bitmap bitmap = Bitmap.createBitmap(sample.mWidth, sample.mHeight, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(snapByteBf);
                mSnapshotPool.freeByteBuffer(snapByteBf);

                if(mBlurBitmapCallback != null) {
                    mBlurBitmapCallback.onBlurCallback(bitmap);
                }

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            } else {
                YYLog.i(TAG, "snapShot snapshotPool is empty");
            }

        } catch (Throwable e) {
            YYLog.e(TAG, "snapshot error:" + e.toString());
        }

        mTakeCurrentSnapshot = false;
    }

    private String sample2ExifRotate(int sampleRotation) {
        switch (sampleRotation) {
            case 1:
                return "0";
            case 2:
                return "90";
            case 3:
                return "180";
            case 0:
                return "270";
        }
        return "0";
    }
}
