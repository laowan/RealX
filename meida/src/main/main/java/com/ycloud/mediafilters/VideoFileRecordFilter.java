package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ycloud.common.Constant;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Created by kele on 2016/12/8.
 */

public class VideoFileRecordFilter extends AbstractYYMediaFilter implements Runnable {
    public static int mFileIndex = 1;
    public final static String sVideoDir = "YYVideo";

    private static final int MSG_FRAME_AVAIL = 1;
    private static final int MSG_QUIT = 2;

    public Thread mTaskThread = null;
    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady = false;
    private Handler mHandler = null;
    DataOutputStream mDataOutput = null;
    private int mCnt = 0;

    private String mH264Name = null;

    public void setH264Name(String name) {
        mH264Name = name;
    }

    //filesname. "Image"+pid+index.
    public static String getVideoFileName() {
        String logFileName = null;
        String videoPath = null;
        File path = Environment.getExternalStorageDirectory(); //取得sdcard文件路径
        videoPath = path.toString();
        videoPath += File.separator + sVideoDir;

        File file = new File(videoPath);
        if (!file.exists() && !file.isDirectory()) {
            file.mkdir();
        }

        logFileName = videoPath + File.separator + "yyvideo-" + Thread.currentThread().getId() + (mFileIndex++);
        return logFileName;
    }


    public void init() {
        mTaskThread = new Thread(this, SDK_NAME_PREFIX +"H264FileStore");
        mTaskThread.start();
        synchronized (mReadyFence) {
            while (!mReady) {
                try {
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "[thdsync] ready fence waitting");
                    mReadyFence.wait();
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "[thdsync] got ready fence ");
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    public void deInit() {
        mHandler.post(new Runnable() {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void run() {
                // TODO Auto-generated method stub
                Looper loop = Looper.myLooper();
                if (null != loop) {
                    loop.quitSafely();
                }
            }
        });

        try {
            mTaskThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        YYLog.info(this, Constant.MEDIACODE_ENCODER + "[tracer] run before prepare");

        Looper.prepare();

        try {
            synchronized (mReadyFence) {
                mReady = true;
                mReadyFence.notify();
                YYLog.info(this, Constant.MEDIACODE_ENCODER + "[tracer] run notify ready");
            }

            mHandler = new OutputStreamHandler(this);

            //open data ooutput.
            mDataOutput = new DataOutputStream(new FileOutputStream(mH264Name == null ? getVideoFileName() : mH264Name));
            Looper.loop();
        } catch (Throwable t) {
            t.printStackTrace();
            YYLog.error(this, Constant.MEDIACODE_ENCODER + "[exception] exception occur, " + t.toString());
        } finally {
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "[tracer] Encoder thread exiting");

            try {
                mDataOutput.flush();
                mDataOutput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            synchronized (mReadyFence) {
                mReady = false;
                mHandler = null;
            }
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (mHandler == null || sample.mBufferSize <= 0 || mCnt > 24 * 60*10)
            return false;

        mCnt++;
        //YYLog.info(this, "[tracer] processMediaSample, size="+sample.mBufferSize + " offset="+sample.mBufferOffset);
        byte[] data = new byte[sample.mBufferSize];
        sample.mDataByteBuffer.position(sample.mBufferOffset);
        sample.mDataByteBuffer.get(data, 0, sample.mBufferSize);

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAIL, data));
        // YYLog.info(this, "[tracer] processMediaSample end");
        return false;
    }

    public boolean processMediaData(ByteBuffer buffer, int offset, int bufsize) {
        if (mHandler == null || buffer == null || bufsize <= 0 || mCnt > 60 * 60*10)
            return false;

        mCnt++;

        //YYLog.info(this, "[tracer] processMediaData, size="+bufsize + " offset="+offset);
        byte[] data = new byte[bufsize];
        int pos = buffer.position();
        buffer.position(offset);
        buffer.get(data, 0, bufsize);
        buffer.position(pos);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAIL, data));
        //YYLog.info(this, "[tracer] processMediaData end");
        return false;
    }

    public void OutputStreamToFile(byte[] data) {
        try {
            mDataOutput.write(data, 0, data.length);
            mDataOutput.flush();
            //YYLog.info(this, "[tracer] OutputStreamToFile, output end, size="+data.length);
        } catch (IOException e) {
            YYLog.error(this, Constant.MEDIACODE_ENCODER + "[exception] OutputStreamToFile: " + e.toString());
            ;
            e.printStackTrace();
        }
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class OutputStreamHandler extends Handler {
        private WeakReference<VideoFileRecordFilter> mWeakRecorder;

        public OutputStreamHandler(VideoFileRecordFilter recorder) {
            mWeakRecorder = new WeakReference<VideoFileRecordFilter>(recorder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;

            VideoFileRecordFilter recorder = mWeakRecorder.get();
            if (recorder == null) {
                YYLog.warn(this, "OutputStreamHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_FRAME_AVAIL:
                    recorder.OutputStreamToFile((byte[]) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    if (Looper.myLooper() != null) {
                        Looper.myLooper().quit();
                    }
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }
}
