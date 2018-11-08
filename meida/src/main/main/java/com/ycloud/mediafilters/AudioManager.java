package com.ycloud.mediafilters;

import android.os.Handler;
import android.os.HandlerThread;

import com.ycloud.utils.YYLog;

import java.util.ArrayList;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Created by DZHJ on 2017/9/14.
 */

public class AudioManager {

    public static  final String TAG = AudioManager.class.getSimpleName();
    private Handler mHandler;
    private HandlerThread mAudioThread =null;
    private ArrayList<AbstractYYMediaFilter> mFilterArray = new ArrayList<>();
    private IMediaSession   mMediaSession = null;

    public AudioManager(){
        mAudioThread = new HandlerThread(SDK_NAME_PREFIX +TAG);
        mAudioThread.start();
        mHandler = new Handler(mAudioThread.getLooper());
    }

    public boolean post(Runnable task) {
        boolean ret = false;
        try {
            ret = mHandler.post(task);
        } catch (Throwable t) {
            YYLog.error(this, "[exception] AudioManager PostRunnable exeception:" + t.toString());
        }
        return ret;
    }

    public void registerFilter(AbstractYYMediaFilter filter){
        YYLog.info(TAG,"registerFilter");
        synchronized (mFilterArray){
            mFilterArray.add(filter);
        }
    }

    public void setMediaSession(IMediaSession session) {
        mMediaSession = session;
    }

    public void quit() {
        YYLog.info(TAG, "[tracer] quit AudioManager thread.");
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mMediaSession.audioMgrCleanup();
                mAudioThread.quit();
            }
        });

    }
}
