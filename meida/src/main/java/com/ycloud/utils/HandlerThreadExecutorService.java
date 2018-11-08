package com.ycloud.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Created by kele on 2017/9/27.
 */

public class HandlerThreadExecutorService implements Executor
{
    private static final long s_MaxIdleTimeMs  = 1*60*1000;  //1 min
    private static final long  k15SecInMills = 15*1000;

    private static Map<String, HandlerThreadExecutorService> sHandlerLooperServiceMap = new HashMap<>();

    private static final int MSG_TICKE_15SEC = 0;
    private static final int MSG_RUNNABLE_TASK = 1;

    private HandlerThread mHandlerThread = null;
    private Handler  mHandler = null;
    private long mIdleTick = 0;
    private String mThreadName = "";

    public static HandlerThreadExecutorService getBackgroundExecutor(final String name) {
        HandlerThreadExecutorService handlerSvc = sHandlerLooperServiceMap.get(name);
        if(handlerSvc == null ){
            handlerSvc = new HandlerThreadExecutorService(name);
            sHandlerLooperServiceMap.put(name, handlerSvc);
        }
        return handlerSvc;
    }

    HandlerThreadExecutorService(String name)
    {
        mThreadName = name;
        initThread();
    }

    public void initThread() {
        mIdleTick= 0;
        mHandlerThread = new HandlerThread(SDK_NAME_PREFIX +mThreadName);
        mHandlerThread.start();
        mHandler  = new Handler(mHandlerThread.getLooper())
        {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSG_TICKE_15SEC:
                        synchronized (this)
                        {
                            //lock the condition.
                            if(mIdleTick++ > 2) {
                                //quit the thread.
                                Handler  handler = mHandler;
                                HandlerThread    thread = mHandlerThread;

                                mHandlerThread = null;
                                mHandler = null;

                                Log.i("[ymrsdk]", "Handler Thread Executor sevice quit: "+thread.getThreadId() + " threadname="+mThreadName);
                                thread.quitSafely();
                                break;
                            }
                        }
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TICKE_15SEC), k15SecInMills);
                        break;
                    case MSG_RUNNABLE_TASK:
                        Runnable r = (Runnable)msg.obj;
                        r.run();
                        mIdleTick = 0;
                    default:
                        break;
                }
            }
        };

        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_TICKE_15SEC), k15SecInMills);
    }

    @Override
    public void execute(Runnable command) {

        Log.i("[ymrsdk]", "Handler Thread Executor sevice: execute a task : "+mThreadName);
        synchronized (this) {
            mIdleTick = 0;
            if(mHandler == null) {
                initThread();
            }
            if(mHandler != null) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_RUNNABLE_TASK, 0, 0, command));
            }
        }
    }
}
