package com.ycloud.mediacodec.videocodec;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ycloud.common.Constant;
import com.ycloud.mediafilters.GlManager;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.lang.ref.WeakReference;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Created by Administrator on 2017/1/3.
 */
public class TextureMoiveEncoderAsync extends AbstractTextureMoiveEncoder implements Runnable
{
    private static  final String TAG =TextureMoiveEncoderAsync.class.getSimpleName();
    private static final boolean VERBOSE = false;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;
    private Thread  mEncodeThread = null;


    public TextureMoiveEncoderAsync(GlManager glMgr, HardEncodeListner listner) {
        super(glMgr, listner);
        synchronized (mReadyFence) {
            if (mRunning) {
                YYLog.warn(this,  Constant.MEDIACODE_ENCODER+"Encoder thread already running");
                return;
            }
            mRunning = true;
            mEncodeThread = new Thread(this, SDK_NAME_PREFIX +TAG);
            mEncodeThread.start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void onEncodedFrameFinished(YYMediaSample sample) {
        //send the msg into encode thread.
        synchronized (mReadyFence) {
            if (!mReady) {
                return ;
            }
        }

        sample.addRef();
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE, sample));
    }

    @Override
    public void stopEncoder() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    @Override
    public synchronized void releaseEncoder() {
        if (mHandler == null) {
            YYLog.warn(TAG, "encoder has been released before");
            return;
        }

        if (mInputWindowSurface != null) {
            mGlManager.resetContext();
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(true);
            mFullScreen = null;
        }

        mInputWidth = 0;
        mInputHeight = 0;
        mClipWidth = 0;
        mClipHeight = 0;

        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        //join...
        if (mEncodeThread != null) {
            try {
                mEncodeThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                YYLog.error(this, Constant.MEDIACODE_ENCODER + "[exception] releaseEncoder: " + e.toString());
            }
            mEncodeThread = null;
            mHandler = null;
        }
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        YYLog.info(this,  Constant.MEDIACODE_ENCODER+"run before prepare");
        Looper.prepare();
        synchronized (mReadyFence) {

            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
            YYLog.info(this,  Constant.MEDIACODE_ENCODER+"run notify ready");
        }
        Looper.loop();

        YYLog.info(this,  Constant.MEDIACODE_ENCODER+" Video Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }


    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<TextureMoiveEncoderAsync> mWeakEncoder;

        public EncoderHandler(TextureMoiveEncoderAsync encoder) {
            mWeakEncoder = new WeakReference<TextureMoiveEncoderAsync>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMoiveEncoderAsync encoder = mWeakEncoder.get();
            if (encoder == null) {
                YYLog.warn(this,  Constant.MEDIACODE_ENCODER+"EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    if (Looper.myLooper() != null){
                        Looper.myLooper().quit();
                    }
                    break;
                case MSG_FRAME_AVAILABLE:
                    encoder.handleFrameAvailable((YYMediaSample)obj);
                    ((YYMediaSample) obj).decRef();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    /**
     * Handles notification of an available frame.
     */
    public void handleFrameAvailable(YYMediaSample sample) {
        if (VERBOSE) YYLog.debug(this,  Constant.MEDIACODE_ENCODER+"handleFrameAvailable");
        mVideoEncoderImpl.drainEncoder(sample, false);
    }

    /**
     * Handles a request to stop encoding.
     */
    public void handleStopRecording() {
        YYLog.info(this,  Constant.MEDIACODE_ENCODER+"handleStopRecording");
        if(mVideoEncoderImpl != null) {
            mVideoEncoderImpl.drainEncoder(null, true);
            mVideoEncoderImpl.deInit();
            mVideoEncoderImpl = null;
        }
    }
}
