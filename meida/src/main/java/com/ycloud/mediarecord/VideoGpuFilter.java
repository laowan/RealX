package com.ycloud.mediarecord;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.ycloud.api.common.TransitionInfo;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.gpuimagefilter.filter.FFmpegFilterGroup;
import com.ycloud.gpuimagefilter.filter.FFmpegFilterSessionWrapper;
import com.ycloud.gpuimagefilter.filter.FilterCenter;
import com.ycloud.player.TransitionPts;
import com.ycloud.player.annotations.CalledByNative;
import com.ycloud.utils.TransitionTimeUtils;
import com.ycloud.svplayer.surface.MediacodecVideoDecoderForTransition;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Created by dzhj on 17/7/28.
 */

public class VideoGpuFilter {
    public final static String TAG = VideoGpuFilter.class.getSimpleName();
    private int mOutputWidth;
    private int mOutputHeight;

    private InputSurface mInputSurface;
    private SurfaceTexture mSurfaceTexture;
    private List<TransitionInfo> mTransitionList;
    private MediacodecVideoDecoderForTransition mCurrentVideoDecoder;
    private MediacodecVideoDecoderForTransition mNextVideoDecoder;
    private int mCurrentVideoIndex;

    private Object mDrawFrameClock = new Object();

    private FfmpegGLThread mGlThread;

    // filter 参数
    private ByteBuffer mRGBAByteBuffer;
    private FFmpegFilterSessionWrapper mFilterSession;
    private FFmpegFilterGroup mFFmpegFilterGroup;
    private YYMediaSample mSample;
    private String mFilterJson;
    private int mTexture = OpenGlUtils.NO_TEXTURE;
    private Object mInitReady = new Object();

    public VideoGpuFilter(int width, int height, Context context, int sessionID, List<TransitionInfo> transitionInfoList) {
        mOutputWidth = width;
        mOutputHeight = height;
        mTransitionList = transitionInfoList;
        mCurrentVideoIndex = -1;
        mGlThread = new FfmpegGLThread(TAG);
        mGlThread.start();
        mFilterSession = new FFmpegFilterSessionWrapper(sessionID);
        mFFmpegFilterGroup = new FFmpegFilterGroup(context, mFilterSession.getSessionID(), mGlThread.getLooper());
        mFFmpegFilterGroup.startListen();
        mSample = new YYMediaSample();
        mRGBAByteBuffer = ByteBuffer.allocateDirect(mOutputWidth * mOutputHeight * 4).order(ByteOrder.nativeOrder());
        mGlThread.init((null == transitionInfoList || 0 == transitionInfoList.size()) ? false : true);

        synchronized (mInitReady) {
            try {
                mInitReady.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.e(TAG, "VideoGpuFilter ready");
    }

    public void setBgmMusicRhythmInfo(String path ,int start) {
        mFFmpegFilterGroup.setRhythmInfo(path, start);
    }

    public void release() {
        mRGBAByteBuffer = null;
    }

    @CalledByNative
    public void unInit() {
        mGlThread.unInit();
    }

    @CalledByNative
    public void drawFrame(long unityPts) {
        synchronized (mDrawFrameClock) {
            mGlThread.drawFrame(unityPts);
            try {
                mDrawFrameClock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class FfmpegGLThread extends HandlerThread implements Handler.Callback {
        private static final int GL_DRAW_FRAME = 1;
        private static final int GL_RELEASE = 2;
        private static final int GL_INIT = 3;

        private static final String UNITY_PTS_US = "unityPtsUs";
        private Handler mHandler;

        public FfmpegGLThread(String name) {
            super(name);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case GL_DRAW_FRAME:
                    Bundle bundle = msg.getData();
                    long unityPtsUs = bundle.getLong(UNITY_PTS_US);
                    drawFrameInternal(unityPtsUs);
                    break;
                case GL_RELEASE:
                    // post interrupt to avoid all further execution of messages/events in the queue
                    interrupt();
                    // quit message processing and exit thread
                    quit();
                    unInitInternal();
                    break;
                case GL_INIT:
                    boolean isExtTexutre = (boolean)msg.obj;
                    initInternal(isExtTexutre);
                    break;
                default:
                    break;
            }

            return false;
        }

        @Override
        public synchronized void start() {
            super.start();
            // Create the handler that will process the messages on the handler thread
            mHandler = new Handler(this.getLooper(), this);
        }

        public void init(boolean isExtTexutre) {
            Message msg = Message.obtain();
            msg.what = GL_INIT;
            msg.obj = isExtTexutre;
            mHandler.sendMessage(msg);
        }

        public void unInit() {
            mHandler.sendEmptyMessage(GL_RELEASE);
        }

        public void drawFrame(long unityPtsUs) {
            Message msg = Message.obtain();
            msg.what = GL_DRAW_FRAME;

            Bundle bundle = new Bundle();
            bundle.putLong(UNITY_PTS_US, unityPtsUs);

            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }

    private void initInternal(boolean isExtTexutre) {
        OpenGlUtils.checkGlError("init begin");
        // 创建环境
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        mSurfaceTexture = new SurfaceTexture(texture[0]);
        Surface surface = new Surface(mSurfaceTexture);
        mInputSurface = new InputSurface(surface);
        mInputSurface.makeCurrent();

        if (mFFmpegFilterGroup != null) {
            mFFmpegFilterGroup.init(mOutputWidth, mOutputHeight, isExtTexutre);
            if (mFilterJson != null) {
                mFilterSession.setFilterJson(mFilterJson);
            }
        }
        mTexture = OpenGlUtils.createNoSizeTexture();

        synchronized(mInitReady) {
            mInitReady.notify();
        }
    }

    public void unInitInternal() {
        YYLog.info(TAG, "uninit begin");
        if (mFFmpegFilterGroup != null){
            mFFmpegFilterGroup.destroy();
            FilterCenter.getInstance().removeFilterObserver(mFFmpegFilterGroup, mFilterSession.getSessionID());
            mFFmpegFilterGroup = null;
        }

        mFilterSession = null;

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        YYLog.info(TAG, "uninit end");
    }

    private void drawFrameInternal(long unityPts) {
        // 转场特效
        TransitionPts transitionPts = TransitionTimeUtils.unityPtsToPts(unityPts, mTransitionList);
        if (transitionPts != null) {
            try {
                if (transitionPts.videoIndex != mCurrentVideoIndex) {
                    mCurrentVideoIndex = transitionPts.videoIndex;

                    if (mCurrentVideoDecoder != null) {
                        mCurrentVideoDecoder.release();
                    }
                    if (mNextVideoDecoder != null) {
                        mCurrentVideoDecoder = mNextVideoDecoder;
                        mCurrentVideoDecoder.resetPosition();
                    } else {
                        mCurrentVideoDecoder = new MediacodecVideoDecoderForTransition(mTransitionList.get(mCurrentVideoIndex).mVideoPath);
                    }
                    if (mCurrentVideoIndex + 1 < mTransitionList.size()) {
                        mNextVideoDecoder = new MediacodecVideoDecoderForTransition(mTransitionList.get(mCurrentVideoIndex + 1).mVideoPath);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                YYLog.e(TAG, "drawFrame:" + e.getMessage());
            }

            while ((!mCurrentVideoDecoder.isDecoderEOS()) && transitionPts.currentPts > mCurrentVideoDecoder.getCurrentTimeUs()) {
                mCurrentVideoDecoder.decodeFrame();
            }

            if (transitionPts.nextPts != -1) {
                while ((!mNextVideoDecoder.isDecoderEOS()) && transitionPts.nextPts > mNextVideoDecoder.getCurrentTimeUs()) {
                    mNextVideoDecoder.decodeFrame();
                }
                mSample.mTextureId1 = mNextVideoDecoder.getTextureId();
                mNextVideoDecoder.getTransformMatrix(mSample.mTransform1);
            } else {
                mSample.mTextureId1 = OpenGlUtils.NO_TEXTURE;
            }

            mSample.mTextureId = mCurrentVideoDecoder.getTextureId();
            mCurrentVideoDecoder.getTransformMatrix(mSample.mTransform);
        } else {
            OpenGlUtils.updataTexture(mRGBAByteBuffer, mOutputWidth, mOutputHeight, mTexture);
            mSample.mTextureId = mTexture;
        }

        mSample.mWidth = mOutputWidth;
        mSample.mHeight = mOutputHeight;
        System.arraycopy(OpenGlUtils.IDENTITY_MATRIX, 0, mSample.mTransform, 0, mSample.mTransform.length);
        mSample.mTimestampMs = unityPts  / 1000;

        mSample.mRgbaBytes = new byte[mRGBAByteBuffer.remaining()];
        mRGBAByteBuffer.get(mSample.mRgbaBytes);

        mFFmpegFilterGroup.processMediaSample(mSample);
        mFFmpegFilterGroup.readRgbaPixel(mRGBAByteBuffer);

        synchronized (mDrawFrameClock) {
            mDrawFrameClock.notify();
        }
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        if (mFFmpegFilterGroup != null) {
            mFFmpegFilterGroup.setMediaInfoRequireListener(listener);
        }
    }
}
