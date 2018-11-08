package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import com.ycloud.api.common.SampleType;
import com.ycloud.gles.I420ToRgbRender;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;



/**
 * Created by kele on 20/6/2018.
 */

public class Yuv2RgbAsyncFilter extends AbstractYYMediaFilter implements MediaBufferQueue.OutputCallback<YYMediaSample>
{
    private final int kMinBufferCnt = 2;
    private final int kMaxBufferCnt =5;

    private final int MSG_SAMPLE_AVAILABLE = 0x100;
    private final int MSG_INIT = 0x101;
    private final int MSG_QUIT = 0x102;

    private MediaFilterContext  mFilterContext = null;
    private MediaBufferQueue<YYMediaSample>    mInputSampleQueue = null;
    private Handler mHandler = null;
    private boolean mInit = false;

    I420ToRgbRender mRender = null;

    MediaFormat mMediaFormat = null;

    protected boolean   mInputEndOfStream = false;

    public Yuv2RgbAsyncFilter(MediaFilterContext filterContext, MediaFormat mediaFormat) {
        mFilterContext = filterContext;
        mHandler = new Handler(mFilterContext.getGLManager().getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_SAMPLE_AVAILABLE:
                        if(mInputSampleQueue != null || !mInputEndOfStream) {
                            do {
                                YYMediaSample sample = mInputSampleQueue.peek();
                                if(sample == null) {
                                    break;
                                }

                                if(sample.mEndOfStream) {
                                    YYLog.info(this, "I420ToRgb Render end of stream");
                                    mInputEndOfStream = true;
                                    deliverToDownStream(sample);
                                } else {
                                    //YYLog.info(this, "I420ToRgb Render, pts = " +sample.mYYPtsMillions);
                                    deliverToDownStream(Yuv2Rgb(sample));
                                }
                                mInputSampleQueue.remove();
                            } while(true);
                        }
                        break;
                    case MSG_QUIT:
                        doQuit();
                        break;
                    case MSG_INIT:
                        init();
                        break;
                    default:
                        break;
                }
            }
        };

        mMediaFormat =  mediaFormat;
        mInputSampleQueue = new MediaBufferQueue(kMinBufferCnt, kMaxBufferCnt, SampleType.VIDEO);
        mInputSampleQueue.setOutputCallback(this);
        mHandler.sendMessage(Message.obtain(mHandler, MSG_INIT));
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void init() {
        if(mInit)
            return;

        mInit = true;
        mRender = new I420ToRgbRender();

        int width = mMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mRender.init(width, height);
    }

    public MediaBufferQueue<YYMediaSample> getInputSampleQueue() {
        return mInputSampleQueue;
    }

    public void quit() {
        if(mHandler != null) {
            mHandler.sendMessage(Message.obtain(mHandler, MSG_QUIT));
        }
    }

    private void doQuit() {
        //clear the msg queue.
        if(mInputSampleQueue != null) {
            do {
                YYMediaSample sample = mInputSampleQueue.peek();
                if(sample != null) {
                    mInputSampleQueue.remove();
                } else {
                    break;
                }
            } while(true);
        }

        if(mRender != null) {
            mRender.destroy();
            mRender = null;
        }
    }

    private YYMediaSample Yuv2Rgb(YYMediaSample sample) {
        //sample has yuv sample data....
        if(!mInit) {
            YYLog.error(this, "Yuv2Rgb fail: not init");
            return null;
        }

        if(sample.mEndOfStream) {
            return sample;
        }

        if(mRender != null) {
            mRender.render(sample);
            sample.mTextureId = mRender.getFrameBufferTexture();
            sample.mTextureTarget = GLES20.GL_TEXTURE_2D;
            return sample;
        }

        return null;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        //queued msg queue, and send a message handler..
        if(mInputSampleQueue != null) {
            return mInputSampleQueue.add(sample);
        }
        return false;
    }

    @Override
    public void outputMediaSample(YYMediaSample sample) {
        if(mHandler != null) {
            mHandler.sendMessage(Message.obtain(mHandler, MSG_SAMPLE_AVAILABLE));
        }
    }
}
