package com.ycloud.svplayer.surface;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;

import com.ycloud.api.common.FilterGroupType;
import com.ycloud.api.common.FilterType;
import com.ycloud.api.common.TransitionInfo;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.gles.core.EglContextWrapper;
import com.ycloud.gles.core.EglHelperFactory;
import com.ycloud.gles.core.GLBuilder;
import com.ycloud.gles.core.IEglHelper;
import com.ycloud.gpuimagefilter.filter.FilterCenter;
import com.ycloud.gpuimagefilter.filter.FilterSession;
import com.ycloud.gpuimagefilter.filter.PlayerFilterGroup;
import com.ycloud.mediaprocess.VideoFilter;
import com.ycloud.svplayer.FrameInfo;
import com.ycloud.svplayer.MediaPlayer;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.yy.mediaframeworks.gpuimage.adapter.GlPboReader;

import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class PlayerGLManager implements IPlayerGLManager{
    private static final String TAG = PlayerGLManager.class.getSimpleName();

    private GLBuilder.EGLConfigChooser mConfigChooser = null;
    private GLBuilder.EGLContextFactory mEglContextFactory = null;
    private GLBuilder.EGLWindowSurfaceFactory mEglWindowSurfaceFactory = null;
    private IEglHelper mEglHelper = null;
    private EglContextWrapper mEglContext = EglContextWrapper.EGL_NO_CONTEXT_WRAPPER;

    //PlayerGLManager的输出surface，此处应该对应播放器VideoView的surface
    private Surface mOutputSurface = null;
    /*mInputSurface1，mInputSurface2作为PlayerGLManager的输入surface，
     依次对应两个解码器的输出surface,
     在非转场情况下仅使用其中一个;
     转场情况下两个surface同时使用*/
    private InputSurface mInputSurface1;
    private InputSurface mInputSurface2;

    private boolean isSurface1Used = false;
    private boolean isSurface2Used = false;

    private int mOutputWidth;
    private int mOutputHeight;
    private int mVideoViewWidth;
    private int mVideoViewHeight;
    private FilterSession mFilterSession = null;
    private PlayerFilterGroup mPlayerFilterGroup = null;
    private YYMediaSample mSample = null;
    private YYMediaSample mLastSample;
    private YYMediaSample mUpStreamSample;

    private VideoFilter mVideoFilter = null;

    private PlayerGLThread mPlayerGLThread;

    private Object mReleaseSyncLock = new Object();
    private Object mInitReady = new Object();

    private boolean mStartRender;
    private MediaPlayer.EventHandler mEventHandler;
    private FrameInfo frameInfoForRotate;
    private int surfaceIndexForRotate;
    private boolean mEnableRotate =false;
    private boolean mClockWise = false;
    private int mBackgroundColor = -1;
    private Bitmap mBackgoundBitmap = null;

    /**
     * Creates an PlayerGLManager using the current EGL context (rather than establishing a
     * new one).  Creates a Surface that can be passed to MediaCodec.configure().
     */
    public PlayerGLManager(Context context, Surface videoViewSurface, int outputWidth, int outputHeight,
                           int filterSid, int videoViewW, int videoViewH, boolean enableRotate, boolean clockwise, int color, Bitmap bitmap) {
        if (videoViewSurface == null) {
            YYLog.error(TAG, "PlayerGLManager construct failed, videoViewSurface is null");
            throw new NullPointerException();
        }
        GlPboReader.checkPboSupport(context);

        mOutputSurface = videoViewSurface;
        if (outputWidth <= 0 || outputHeight <= 0) {
            throw new IllegalArgumentException();
        }

        mEnableRotate = enableRotate;
        mClockWise = clockwise;
        mBackgroundColor = color;
        mBackgoundBitmap = bitmap;
        mOutputWidth = outputWidth;
        mOutputHeight = outputHeight;

        mVideoViewWidth = videoViewW;
        mVideoViewHeight = videoViewH;

        mInputSurface1 = new InputSurface();
        mInputSurface2 = new InputSurface();

        mPlayerGLThread = new PlayerGLThread("PlayerGLThread");
        mPlayerGLThread.start();

        mFilterSession = FilterCenter.getInstance().createFilterSession(filterSid);
        mPlayerFilterGroup = new PlayerFilterGroup(context, mFilterSession.getSessionID(), mPlayerGLThread.getLooper());

        mStartRender = false;

        synchronized (mInitReady) {

            mPlayerGLThread.init();

            try {
                mInitReady.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        YYLog.info(TAG, "PlayerGLManager ready");
    }

    public void setViewPortSize(int w, int h) {
        mPlayerFilterGroup.setViewPortSize(w, h);
    }

    public void setLayoutMode(int mode) {
        mPlayerFilterGroup.setLayoutMode(mode);
    }

    public void setVideoRotate(int rotateAngle) {
        mPlayerFilterGroup.setVideoRotate(rotateAngle);
    }

    public void setLastVideoRotate(int rotateAngle) {
        mPlayerFilterGroup.setLastVideoRotate(rotateAngle);
    }

    public void setBackgroundMusicRhythmInfo(String path, int start) {
        if (mPlayerFilterGroup != null) {
            mPlayerFilterGroup.setRhythmInfo(path, start);
        }
    }

    private void eglSetup() {
        if (mConfigChooser == null) {
            mConfigChooser = new GLBuilder.SimpleEGLConfigChooser(true, 2);
        }
        if (mEglContextFactory == null) {
            mEglContextFactory = new GLBuilder.DefaultContextFactory(2);
        }
        if (mEglWindowSurfaceFactory == null) {
            mEglWindowSurfaceFactory = new GLBuilder.DefaultWindowSurfaceFactory();
        }

        mEglHelper = EglHelperFactory.create(mConfigChooser, mEglContextFactory, mEglWindowSurfaceFactory);
        mEglContext = mEglHelper.start(mEglContext);
        mEglHelper.createSurface(mOutputSurface);
    }

    private void makeCurrent() {
        if (mEglHelper != null) {
            mEglHelper.makeCurrent();
        }
    }

    /**
     * 设置滤镜，需要在GL中调用；每次设置时会清除旧的滤镜
     *
     * @param videoFilter
     */
    public void setVideoFilter(VideoFilter videoFilter) {
        mPlayerGLThread.setVideoFilter(videoFilter);
    }

    /**
     * Creates instances of TextureRender and SurfaceTexture, and a Surface associated
     * with the SurfaceTexture.
     */
    private void setup() {
        mInputSurface1.setup();
        mInputSurface2.setup();

        mPlayerFilterGroup.init(mOutputWidth, mOutputHeight, true, mVideoViewWidth, mVideoViewHeight,
                                    mEnableRotate, mClockWise, mBackgroundColor, mBackgoundBitmap);
        mPlayerFilterGroup.startListen();

        /*// 读取拍摄filter的配置
        try {
            String json_name = "sdcard/filter.json";
            FileInputStream inputStream = new FileInputStream(json_name);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String json;
            while ((json = bufferedReader.readLine()) != null) {
                mFilterSession.addFilter(json);
            }

            inputStream.close();
            inputStreamReader.close();
            bufferedReader.close();
        } catch (IOException e) {
            YYLog.error(this, "[exception] PlayerGLManager.setup: "+e.toString());
            e.printStackTrace();
        }
        */

        mSample = new YYMediaSample();
        mSample.mWidth = mOutputWidth;
        mSample.mHeight = mOutputHeight;
        mLastSample = new YYMediaSample();
        mUpStreamSample = new YYMediaSample();
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
        if (mPlayerGLThread != null) {
            synchronized (mReleaseSyncLock) {

                try {
                    if (mPlayerGLThread != null) {
                        // Schedule release on the playback thread
                        mPlayerGLThread.release();
                        mPlayerGLThread = null;
                        // Wait for the release on the playback thread to finish
                        YYLog.info(TAG, "PlayerGLManager release wait");
                        mReleaseSyncLock.wait();
                    }
                } catch (InterruptedException e) {
                    // nothing to do here
                }
            }
        }
    }

    public static class SurfaceWrapper {
        public SurfaceWrapper(Surface surface, SurfaceTexture surfaceTexture,int surfaceIndex, int textureID) {
            mSurface = surface;
            mSurfaceTexture = surfaceTexture;
            mSurfaceIndex = surfaceIndex;
            mTextureID = textureID;
        }

        public Surface mSurface;
        public SurfaceTexture mSurfaceTexture;
        public int mSurfaceIndex;
        public int mTextureID;
    }

    /*
    获取一个PlayerGLManager的输入surface作为解码器的输出surface
     */
    public synchronized SurfaceWrapper getInputSurface() {
        if (isSurface1Used == false) {
            isSurface1Used = true;
            return new SurfaceWrapper(mInputSurface1.getSurface(),mInputSurface1.getSurfaceTexture(), 1, mInputSurface1.getTextureId());
        } else if (isSurface2Used == false) {
            isSurface2Used = true;
            return new SurfaceWrapper(mInputSurface2.getSurface(),mInputSurface2.getSurfaceTexture(), 2, mInputSurface2.getTextureId());
        }

        return null;
    }

    public void returnSurface(int surfaceIndex) {

        if (surfaceIndex == 1) {
            isSurface1Used = false;
        } else if (surfaceIndex == 2) {
            isSurface2Used = false;
        }
    }

    private void renderUpdateSurface(Surface videoViewSurface) {
        mOutputSurface = videoViewSurface;
        if (videoViewSurface == null) {
            mEglHelper.destroySurface();
            mEglHelper.makeNoSurface();
            YYLog.info(TAG, "renderUpdateSurface with surface null");
        } else {
            mEglHelper.createSurface(videoViewSurface);
            YYLog.info(TAG, "renderUpdateSurface with new surface");
        }
    }

    /**
     * 处理封面
     *
     * @param imageBasePath 图片根目录
     * @param imageRate     图片每秒截图数量
     */
    public void processImages(String imageBasePath, int imageRate) {
        mPlayerGLThread.processImages(imageBasePath, imageRate);
    }

    public void renderFrame(FrameInfo frameInfo, int surfaceIndex) {
        if (mPlayerGLThread == null || frameInfo == null) {
            return;
        }
        mPlayerGLThread.renderFrame(frameInfo.unityPtsUs, frameInfo.presentationTimeUs, frameInfo.needDrawImage, frameInfo.drawWithTwoSurface, surfaceIndex);
        frameInfoForRotate = frameInfo;
        surfaceIndexForRotate = surfaceIndex;
    }

    public void repeatRenderFrame() {
        mPlayerGLThread.repeatRenderFrame();
    }

    public void renderLastFrame() {
        mPlayerGLThread.renderLastFrame();
    }

    public void stopRepeatRenderFrame() {
        mPlayerGLThread.stopRepeatRenderFrame();
    }

    public void asyncUpdateSurface(Surface videoViewSurface) {
        mPlayerGLThread.updateSurface(videoViewSurface);
    }

    public void addFilterJson(String filterJson) {
        if (filterJson != null) {
            mFilterSession.addFilter(filterJson, true);
        }
    }

    public void setEventHandler(MediaPlayer.EventHandler eventHandler) {
        mEventHandler = eventHandler;
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        if (mPlayerFilterGroup != null) {
            mPlayerFilterGroup.setMediaInfoRequireListener(listener);
        }
    }

    public void RenderForRotate() {
        if (frameInfoForRotate != null) {
            frameInfoForRotate.needDrawImage = true;
            renderFrame(frameInfoForRotate, surfaceIndexForRotate);
        }
    }

    public void StartRotate() {
        if (mPlayerFilterGroup != null) {
            mPlayerFilterGroup.StartRotate();
        }
    }

    public void setFlutterRotateAngel(int angle) {
        if (mPlayerFilterGroup != null) {
            mPlayerFilterGroup.setFlutterRotateAngel(angle);
        }
    }

    public float getCurrentRotateAngle() {
        if (mPlayerFilterGroup != null) {
            return mPlayerFilterGroup.getCurrentRotateAngle();
        }
        return 0;
    }

    public RectF getCurrentVideoRect() {
        if (mPlayerFilterGroup != null) {
            return mPlayerFilterGroup.getCurrentVideoRect();
        }
        return null;
    }


    private class PlayerGLThread extends HandlerThread implements Handler.Callback {
        private static final int GL_INIT = 1;
        private static final int GL_RENDER_FRAME = 2;
        private static final int GL_RELEASE = 3;
        private static final int GL_SET_FILTER = 4;
        private static final int GL_PROCESS_IMAGES = 5;
        private static final int GL_REPEAT_RENDER_FRAME = 6;
        private static final int GL_STOP_REPEAT_RENDER_FRAME = 7;
        private static final int GL_UPDATE_SURFACE = 8;
        private static final int GL_RENDER_LAST_FRAME = 9;

        private static final String UNITY_PTS_US = "unityPtsUs";
        private static final String PRESENTATION_TIME_US = "presentationTimeUs";
        private static final String SURFACE_INDEX = "surfaceIndex";
        private static final String NEED_DRAW_IMAGE = "need_draw_image";
        private static final String DRAW_WITH_TWO_SURFACE = "draw_with_two_surface";

        private boolean mIsReleased = false;

        private Handler mHandler;

        public PlayerGLThread(String name) {
            super(name);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case GL_INIT:
                    initInternal();
                    YYLog.error(TAG, "initInternal handleMessage GL_INIT:" + Thread.currentThread().getName());
                    break;
                case GL_RENDER_FRAME:
                    Bundle bundle = msg.getData();
                    long unityPtsUs = bundle.getLong(UNITY_PTS_US);
                    long presentationTimeUs = bundle.getLong(PRESENTATION_TIME_US);
                    int surfaceIndex = bundle.getInt(SURFACE_INDEX);
                    boolean needDrawImage = bundle.getBoolean(NEED_DRAW_IMAGE);
                    boolean drawWithTwoSurface = bundle.getBoolean(DRAW_WITH_TWO_SURFACE);
                    renderFrameInternal(unityPtsUs, presentationTimeUs, needDrawImage, drawWithTwoSurface, surfaceIndex);

                    break;
                case GL_SET_FILTER:
                    VideoFilter videoFilter = (VideoFilter) msg.obj;
                    setVideoFilterInternal(videoFilter);
                    break;
                case GL_RELEASE:
                    releaseInternal();
                    // post interrupt to avoid all further execution of messages/events in the queue
                    interrupt();
                    // quit message processing and exit thread
                    quit();
                    break;
                case GL_PROCESS_IMAGES:
                    //TODO:封面处理
                    break;
                case GL_REPEAT_RENDER_FRAME:
                    repeatRenderFrameInternal();
                    break;
                case GL_STOP_REPEAT_RENDER_FRAME:
                    stopRepeatRenderFrameInternal();
                    break;
                case GL_UPDATE_SURFACE:
                    renderUpdateSurface((Surface)msg.obj);
                    break;
                case GL_RENDER_LAST_FRAME:
                    renderLastFrameInternal();
                    break;
                default:
                    break;
            }

            return true;
        }

        @Override
        public synchronized void start() {
            super.start();
            // Create the handler that will process the messages on the handler thread
            mHandler = new Handler(this.getLooper(), this);
        }

        public void setVideoFilter(VideoFilter videoFilter) {
            Message msg = Message.obtain();
            msg.what = GL_SET_FILTER;
            msg.obj = videoFilter;
            mHandler.sendMessage(msg);
        }

        public void renderFrame(long unityPtsUs, long presentationTimeUs, boolean needDrawImage, boolean drawWithTwoSurface, int surfaceIndex) {
            if (mIsReleased) {
                YYLog.info(TAG, "playerglmanager renderFrame is released!");
                return;
            }
            Message msg = Message.obtain();
            msg.what = GL_RENDER_FRAME;

            Bundle bundle = new Bundle();
            bundle.putLong(UNITY_PTS_US, unityPtsUs);
            bundle.putLong(PRESENTATION_TIME_US, presentationTimeUs);
            bundle.putInt(SURFACE_INDEX, surfaceIndex);
            bundle.putBoolean(NEED_DRAW_IMAGE, needDrawImage);
            bundle.putBoolean(DRAW_WITH_TWO_SURFACE, drawWithTwoSurface);

            msg.setData(bundle);
            mHandler.removeMessages(GL_RENDER_FRAME);
            mHandler.sendMessage(msg);
        }

        public void repeatRenderFrame() {
            if (mIsReleased) {
                YYLog.info(TAG, "playerglmanager repeatRenderFrame is released!");
                return;
            }
            mHandler.removeMessages(GL_STOP_REPEAT_RENDER_FRAME);
            mHandler.sendEmptyMessage(GL_REPEAT_RENDER_FRAME);
        }

        public void renderLastFrame() {
            if (mIsReleased) {
                return;
            }
            mHandler.sendEmptyMessage(GL_RENDER_LAST_FRAME);
        }

        public void stopRepeatRenderFrame() {
            mHandler.sendEmptyMessage(GL_STOP_REPEAT_RENDER_FRAME);
        }

        public void release() {
            mIsReleased = true;
            mHandler.removeMessages(GL_RENDER_FRAME);
            mHandler.removeMessages(GL_REPEAT_RENDER_FRAME);
            mHandler.sendEmptyMessage(GL_RELEASE);
        }

        public void processImages(String imageBasePath, int imageRate) {
            Message msg = Message.obtain();
            msg.what = GL_PROCESS_IMAGES;
            msg.obj = imageBasePath;
            msg.arg1 = imageRate;
            mHandler.sendMessage(msg);
        }

        public void init() {
            Message msg = Message.obtain();
            msg.what = GL_INIT;
            mHandler.sendMessage(msg);
        }

        public void updateSurface(Surface videoViewSurface) {
            mHandler.removeMessages(GL_RENDER_FRAME);
            mHandler.removeMessages(GL_REPEAT_RENDER_FRAME);
            mHandler.removeMessages(GL_UPDATE_SURFACE);
            Message msg = Message.obtain();
            msg.what = GL_UPDATE_SURFACE;
            msg.obj = videoViewSurface;
            mHandler.sendMessage(msg);
        }


        /**
         * Draws the data from SurfaceTexture onto the current EGL surface.
         */

        private void drawImageInternal(long timestamp, int surfaceIndex, boolean drawWithTwoSurface) {
            //reset new sample body detect data
            mSample.mBodyFrameDataArr = null;

            mSample.mTimestampMs = timestamp;
            if (surfaceIndex == 1) {
                mSample.mTextureId = mInputSurface1.getTextureId();
                mSample.mTextureTarget = mInputSurface1.getTextureTarget();
                mInputSurface1.getTransformMatrix(mSample.mTransform);
                if (drawWithTwoSurface) {
                    mSample.mTextureId1 = mInputSurface2.getTextureId();
                    mInputSurface2.getTransformMatrix(mSample.mTransform1);
                } else {
                    mSample.mTextureId1 = OpenGlUtils.NO_TEXTURE;
                }
            } else {
                mSample.mTextureId = mInputSurface2.getTextureId();
                mSample.mTextureTarget = mInputSurface2.getTextureTarget();
                mInputSurface2.getTransformMatrix(mSample.mTransform);
                if (drawWithTwoSurface) {
                    mSample.mTextureId1 = mInputSurface1.getTextureId();
                    mInputSurface1.getTransformMatrix(mSample.mTransform1);
                } else {
                    mSample.mTextureId1 = OpenGlUtils.NO_TEXTURE;
                }
            }

            mLastSample.assigne(mSample);
            mPlayerFilterGroup.processMediaSample(mSample, this);
        }

        private void repeatRenderFrameInternal() {
            renderLastFrame();

            mHandler.removeMessages(GL_REPEAT_RENDER_FRAME);
            //之前的黑屏已经不存在,之前是surface绑定有问题,不需要重复repeat
            mHandler.sendEmptyMessageDelayed(GL_REPEAT_RENDER_FRAME, 34);
        }

        private void renderLastFrameInternal() {
            if (mOutputSurface == null || mLastSample.mTextureId == -1) {
                return;
            }

            mUpStreamSample.assigne(mLastSample);
            mPlayerFilterGroup.processMediaSample(mUpStreamSample, this);
            mEglHelper.swap();
        }

        private void initInternal() {
            eglSetup();
            makeCurrent();
            setup();
            synchronized (mInitReady) {
                mInitReady.notify();
            }
        }

        private void renderFrameInternal(long unityPtsUs, long presentationTimeUs, boolean needDrawImage, boolean drawWithTwoSurface, int surfaceIndex) {
            if (mOutputSurface == null) {
                return;
            }

            try {
                if (surfaceIndex == 1) {
                    mInputSurface1.updateTexImage();
                } else {
                    mInputSurface2.updateTexImage();
                }

            } catch (Exception e) {
                YYLog.error(TAG, "renderFrameInternal when updateTextureImage:" + e.getMessage());
                return;
            }

            if (needDrawImage == true) {

                drawImageInternal(unityPtsUs / 1000, surfaceIndex, drawWithTwoSurface);
                mEglHelper.setPresentationTime(presentationTimeUs * 1000);
                mEglHelper.swap();

                if(!mStartRender) {
                    mStartRender = true;

                    //客户端收到通知移除封面，可能出现opengl还没有渲染完，客户端已经收到了这个消息，移除了封面，这样会导致闪屏一下.
                    //延迟发送10ms左右.
                    //mEventHandler.sendMessage(mEventHandler.obtainMessage(MediaPlayer.MEDIA_RENDER_START));
                    mEventHandler.sendMessageDelayed(mEventHandler.obtainMessage(MediaPlayer.MEDIA_RENDER_START), 10);
                }
            }
        }


        private void setVideoFilterInternal(VideoFilter videoFilter) {
            mVideoFilter = videoFilter;

            //TODO：先接入专场filter
            {
                List<TransitionInfo> videosTransitionInfo = mVideoFilter.getTransitionList();
                if (videosTransitionInfo != null) {
                    for (int i = 0; i < videosTransitionInfo.size(); i++) {
                        switch (videosTransitionInfo.get(i).mTransitionType) {
                    /*case Fade:
                        return FadeFragString;
                    case Fold:
                        return FoldFragString;
                    case WaveGraffiti:
                        return WaveGraffitiFragString;
                    case Crosswarp:
                        return CrossWarpFragString;
                    case Radial:
                        return RadialFragString;
                    case PinWheel:
                        return PinWheelFragString;*/
                            default: {
                                mFilterSession.addFilter(FilterType.GPUFILTER_FADE_BLEND, FilterGroupType.DEFAULT_FILTER_GROUP);
                            }
                        }
                        break;
                    }
                }
            }
        }

        private void releaseInternal() {
            YYLog.info(TAG, "releaseInternal begin");
            if (mPlayerFilterGroup != null) {
                mPlayerFilterGroup.destroy();
                FilterCenter.getInstance().removeFilterObserver(mPlayerFilterGroup, mFilterSession.getSessionID());
                mPlayerFilterGroup = null;
                YYLog.info(TAG, "releaseInternal mPlayerFilterGroup destroy");
            }

            mFilterSession = null;

            if (mInputSurface1 != null) {
                mInputSurface1.release();
                mInputSurface1 = null;
                YYLog.info(TAG, "releaseInternal mInputSurface1 release");
            }

            if (mInputSurface2 != null) {
                mInputSurface2.release();
                mInputSurface2 = null;
                YYLog.info(TAG, "releaseInternal mInputSurface2 release");
            }

            mVideoFilter = null;

            mEglHelper.destroySurface();
            mEglHelper.finish();
            YYLog.info(TAG, "releaseInternal mEglHelper destroySurface and finish");
            mOutputSurface = null;

            if (mReleaseSyncLock != null) {
                synchronized (mReleaseSyncLock) {
                    if (mReleaseSyncLock != null) {
                        YYLog.info(TAG, "PlayerGLManager releaseInternal notify. ");
                        mReleaseSyncLock.notify();
                        mReleaseSyncLock = null;
                    }
                }
            }

            YYLog.info(TAG, "releaseInternal end");
        }

        private void stopRepeatRenderFrameInternal(){
            mHandler.removeMessages(GL_REPEAT_RENDER_FRAME);
        }
    }
}
