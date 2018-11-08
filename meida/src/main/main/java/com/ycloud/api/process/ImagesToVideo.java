package com.ycloud.api.process;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Message;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.ImageInformation;
import com.ycloud.mediaprocess.ImagesSpliceVideo;
import com.ycloud.mediaprocess.OpenGLFilter;
import com.ycloud.mediaprocess.OpenglContext;
import com.ycloud.mediaprocess.OpenglContext10;
import com.ycloud.mediaprocess.OpenglContext14;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YMRThread;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2017/2/21.
 */

public class ImagesToVideo implements YMRThread.Callback {
    private final String TAG = ImagesToVideo.class.getSimpleName();
    private OpenglContext mOpenglContext = null;
    private SurfaceTexture mSurfaceTexture = null;
    private ImagesSpliceVideo mImgToVideo = null;
    private OpenGLFilter mImgProFilter = null;
    private List<ImageInformation> mImgInfo = null;
    private String IMG_TEMP_DIR = null;
    private final String IMG_FORMAT = "jpg";
    private final String IMG_RELUAR = "%05d";
    private final String IMG_INFO = "imageInfo";
    private IMediaListener mMediaListener = null;
    private float mFrameRate = 25;
    private int mFrameCount = 0;
    private float mVideoDuration = 0;
    private float mPreprocessImageProgressRatio = 0.5f;
    private YMRThread mThread;
    private final int MSG_PROCESS = 1;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private boolean mReleased = false;
    private int[] mTexture = null;

    public ImagesToVideo(Context context) {
        IMG_TEMP_DIR = FileUtils.getDiskCacheDir(context) + File.separator + "tempimages" + File.separator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mOpenglContext = new OpenglContext14();
        }
        else {
            mOpenglContext = new OpenglContext10();
        }

        mImgToVideo = new ImagesSpliceVideo();
        mImgProFilter = new OpenGLFilter();
        mThread = new YMRThread("ymrsdk_Img2video");
        mThread.setCallback(this);
        mThread.start();
        YYLog.info(TAG, " ImagesToVideo Construct OK. ");
    }


    /**
     * 设置小于视频大小的图片是居中显示，还是拉伸自适应上下或者左右加黑边
     * @param b
     */
    public void setSmallPictureScale(boolean b) {
        if (mImgProFilter != null) {
            mImgProFilter.setSmallPictureScale(b);
        }
    }

    /**
     * 设置图片是否裁剪为全屏显示， 默认为按宽高比上下/左右加黑边
     * @param bValue
     */
    public void setImageClipFullScreen(boolean bValue) {
        if (mImgProFilter != null) {
            mImgProFilter.setImageClipFullScreen(bValue);
        }
    }

    /**
     * 设置图片路径以及显示时长等信息
     * @param imgInfo
     */
    public void setImageInfo(List<ImageInformation> imgInfo) {
        mImgInfo = imgInfo;
    }

    /**
     * 设置输出视频路径
     *
     * @param outputFile
     */
    public void setOutputFile(String outputFile) {
        if (mImgToVideo != null) {
            mImgToVideo.setOutputFile(outputFile);
            YYLog.info(TAG, " setOutputFile " + outputFile);
        }
    }

    /**
     * 设置输出视频大小
     *
     * @param width
     * @param height
     */
    public void setOutputSize(int width, int height) {
        if (mImgProFilter != null) {
            mImgProFilter.setOutputSize(width, height);
            YYLog.info(TAG, " setOutputSize width " + width + " height " + height);
        }
    }


    /**
     * 设置进度及错误监听
     *
     * @param mediaListener
     */
    public void setMediaListener(IMediaListener mediaListener) {
        if (mImgToVideo != null) {
            mImgToVideo.setMediaListener(mediaListener);
        }
        mMediaListener = mediaListener;
    }

    /**
     * 执行
     */
    public void execute() {
        YYLog.info(TAG, "execute enter, mInited " + mInited + " mThread " + mThread + " mReleased " + mReleased);
        synchronized (mInited) {
            if (mThread != null && !mReleased) {  // do not receive execute message after release
                YYLog.info(TAG, "sendMessage MSG_PROCESS .");
                mThread.sendMessage(Message.obtain(mThread.getHandler(), MSG_PROCESS));
            }
        }
    }

    private void startProcess() {

        if (null != mMediaListener) {
            mMediaListener.onProgress(0.0f);
        }

        if (mImgInfo == null || mImgInfo.size() == 0) {
            if (mMediaListener != null) {
                mMediaListener.onError(-1, "Parameter Exception, Picture file list is null.");
            }
            return;
        }

        // 创建临时文件夹
        if (!FileUtils.createDir(IMG_TEMP_DIR)) {
            if (mMediaListener != null) {
                mMediaListener.onError(-1, "Create directory " + IMG_TEMP_DIR + " failed.");
            }
            YYLog.error(TAG, "Create directory " + IMG_TEMP_DIR + " failed.");
            return;
        }

        long time = System.currentTimeMillis();
        int imageCount = mImgInfo.size();
        mFrameCount = 0;
        // imgPathArray给filter处理
        for (int i = 0; i < imageCount; i++) {
            ImageInformation info = mImgInfo.get(i);
            if (info != null) {
                String imgPathIn = info.mImagePath;
                String imgPathOut = getOutPutImgPath(i);

                if (!FileUtils.checkPath(imgPathIn)) {
                    YYLog.error(TAG, " File " + imgPathIn + " NOT Exist! ");
                    continue;
                }

                mImgProFilter.imgProcess(imgPathIn, imgPathOut);

                if (!FileUtils.checkPath(imgPathOut)) {
                    YYLog.error(TAG, " File " + imgPathOut + " NOT Exist! ");
                    continue;
                }

                info.mTmpPath = imgPathOut;

//                int tmp = (int)(info.mDuration * 1000) % 40;
//                if (tmp != 0) {   // 按每帧显示时间严格对齐
//                    info.mDuration += ((float)(40 - tmp) / (float) 1000);
//                }
                mVideoDuration += info.mDuration;
                mFrameCount++;
                YYLog.info(TAG, "[" + i +"]" + " duration " + info.mDuration + " : " + imgPathIn);

                if (null != mMediaListener) {
                    float progress = (float) i / (float)imageCount * mPreprocessImageProgressRatio;
                    mMediaListener.onProgress(progress);
                    YYLog.info(TAG, "progress " + progress);
                }
            }
        }

        String path = IMG_TEMP_DIR + IMG_INFO + "_" + Thread.currentThread().getId() + ".txt";
        if (!initImageInformation(path)) {
            if (mMediaListener != null) {
                mMediaListener.onError(-1, "Create file " + path + " failed.");
            }
            FileUtils.deleteFileSafely(new File(path));
            return;
        }

        //如果图片总张数太少，使用默认帧率，提高视频中总的帧数。总帧数太低影响一些特效效果
        if (mFrameCount > 50) {
            mFrameRate = (float) mFrameCount / mVideoDuration;
        }
        YYLog.info(TAG, "Video duration: " + mVideoDuration + " total Frame: " + mFrameCount + " frameRate " + mFrameRate);

        mImgToVideo.setConfigFile(path);
        if (SDKCommonCfg.getRecordModePicture()) {
        mImgToVideo.setTotalFrameCount(mFrameCount);
        mImgToVideo.setFrameRate((int)mFrameRate);
        mImgToVideo.setGop(1); // full I frame
        } else {  // Noizz
            mFrameRate = 25;
            mImgToVideo.setTotalFrameCount((int)(mFrameRate * mVideoDuration));
            mImgToVideo.setFrameRate((int) mFrameRate);
            mImgToVideo.setGop(1);
        }
        mImgToVideo.setInitializeProgress(mPreprocessImageProgressRatio);
        long time1 = System.currentTimeMillis();
        // 将filter的结果给ImagesToVideo处理，生成视频(使用同步方法)
        mImgToVideo.excuteSync();
        // 释放临时文件
        if(!FileUtils.deleteFileSafely(new File(path))) {
            YYLog.warn(TAG, "Delete tmp file " + path + " Failed.");
        }
        if (null != mMediaListener) {
            mMediaListener.onProgress(1.0f);
        }
        YYLog.info(TAG, "ImagesToVideo end, Preprocess cost: " + (time1 - time) + " encode cost: " + (System.currentTimeMillis() - time));

    }

    // 格式"%05d"
    private String getOutPutImgPath(int index) {
        String path = IMG_TEMP_DIR;
        path = path + String.format(IMG_RELUAR, index + 1) + "." + IMG_FORMAT;
        return path;
    }


    private void init() {
        if (!mInited.get()) {
            mTexture = new int[1];
            GLES20.glGenTextures(1, mTexture, 0);
            mSurfaceTexture = new SurfaceTexture(mTexture[0]);
            mOpenglContext.init(mSurfaceTexture);
            mImgProFilter.init();
            mInited.set(true);
            YYLog.info(TAG, "init success.");
        } else {
            YYLog.warn(TAG, "have inited yet! ");
        }
    }

    private void deinit() {
        if (mInited.get()) {
            if (mOpenglContext != null) {
                mOpenglContext.release();
                mOpenglContext = null;
            }
            if (mImgProFilter != null) {
                mImgProFilter.release();
                mImgProFilter = null;
            }
            if (mImgToVideo != null) {
                mImgToVideo.release();
                mImgToVideo = null;
            }
            if (mTexture != null) {
                GLES20.glDeleteTextures(1, mTexture, 0);
                mTexture = null;
            }
        }
        mInited.set(false);
        YYLog.info(TAG, "deinit success.");
    }

    public void release() {
        YYLog.info(TAG, "release .");
        synchronized (mInited) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mThread != null) {
                        mThread.stop();
                        mThread = null;
                    }
                }
            }).start();
            mReleased = true;
        }
    }

    private boolean initImageInformation(String path) {
        int ret = 0;
        try {
            FileWriter writer = new FileWriter(path);
            for (ImageInformation info : mImgInfo) {
                writer.write("file '" + info.mTmpPath+"'");
                writer.write("\r\n");
                writer.write("duration " + Float.toString(info.mDuration));
                writer.write("\r\n");
            }
            ImageInformation lastInfo = mImgInfo.get(mImgInfo.size()-1);
            writer.write("file '" + lastInfo.mTmpPath+"'");
            writer.write("\r\n");
            writer.write("duration " + Float.toString(lastInfo.mDuration));
            writer.write("\r\n");
            writer.close();
        } catch (IOException e) {
            YYLog.error(TAG, "Exception : " + e.getMessage());
            ret = -1;
        }
        return ret == 0;
    }

    @Override
    public void onStart() {
        YYLog.info(TAG, "Thread Start.");
        init();
    }

    @Override
    public void onStop() {
        deinit();
        YYLog.info(TAG, "Thread Exit.");
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PROCESS:
                YYLog.info(TAG, "Receive MSG_PROCESS ...");
                startProcess();
                break;
            default:
                break;
        }
    }
}
