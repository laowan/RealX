package com.ycloud.utils;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by it on 2017/8/22.
 */
public final class YMRThread {
    private final String TAG = YMRThread.class.getSimpleName();
    private static final String[] YY_THREAD_STATUS_TXT = {"None", "Ready", "Running", "Paused", "Stopped"};

    public static final int YY_THREAD_READY   = 1;
    public static final int YY_THREAD_RUNNING = 2;
    public static final int YY_THREAD_PAUSED  = 3;
    public static final int YY_THREAD_STOPPED = 4;

    private static final int YY_THREAD_ON_START    = -10001;
    private static final int YY_THREAD_ON_STOP     = -10002;
    private static final int YY_THREAD_ON_PAUSE    = -10003;
    private static final int YY_THREAD_ON_RESUME   = -10004;

    private Handler mHandler = null;
    private HandlerThread mThread = null;
    private String mThreadName = "ymrthread";
    private int mThreadPriority = Process.THREAD_PRIORITY_FOREGROUND;
    private YMRThread.Callback mCallback = null;
    private AtomicInteger mThreadStatus = new AtomicInteger(YY_THREAD_READY);

    private final Handler.Callback mHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch(msg.what){
                case YY_THREAD_ON_START:
                    internalStart();
                    break;
                case YY_THREAD_ON_STOP:
                    internalStop();
                    break;
                case YY_THREAD_ON_PAUSE:
                    mCallback.onPause();
                    break;
                case YY_THREAD_ON_RESUME:
                    mCallback.onResume();
                    break;
                default:
                    mCallback.handleMessage(msg);
                    break;
            }
            return false;
        }
    };

    private static int matchPriority(int platformThreadPriority) {
        if(platformThreadPriority > Process.THREAD_PRIORITY_LOWEST) {
            platformThreadPriority = Process.THREAD_PRIORITY_LOWEST;
        } else if(platformThreadPriority < Process.THREAD_PRIORITY_URGENT_DISPLAY) {
            platformThreadPriority = Process.THREAD_PRIORITY_URGENT_DISPLAY;
        }
        final int r1 = Process.THREAD_PRIORITY_URGENT_DISPLAY - Process.THREAD_PRIORITY_LOWEST;
        final int r2 = Thread.MAX_PRIORITY - Thread.MIN_PRIORITY;
        return (platformThreadPriority - Process.THREAD_PRIORITY_LOWEST) * r2 / r1;
    }

    public YMRThread(String name) {
        if(name != null){
            mThreadName = name;
        }
    }

    public YMRThread(String name, int priority) {
        if(name != null){
            mThreadName = name;
        }
        mThreadPriority = priority;
    }

    public void setName(String name) {
        if(name != null){
            mThreadName = name;
        }
        synchronized (this) {
            if (mThread != null) {
                mThread.setName(mThreadName);
            }
        }
    }

    public void setPriority(int priority) {
        mThreadPriority = priority;
        synchronized (this) {
            if (mThread != null) {
                mThread.setPriority(mThreadPriority);
            }
        }
    }

    public void setCallback(YMRThread.Callback callback) {
        mCallback = callback;
        if(mCallback == null) {
            throw new RuntimeException("mCallback is not set!");
        }
    }

    private void internalStart() {
        final int id = Process.myTid();
        Thread t = Thread.currentThread();
        YYLog.info(this, String.format("[%s] sdk_ver:%d, tid:%d, %d, priority:%d, %d", t.getName(), Build.VERSION.SDK_INT, id, t.getId(), Process.getThreadPriority(id), t.getPriority()));
        mCallback.onStart();
    }

    private void internalStop() {
        mCallback.onStop();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // YY_THREAD_ON_STOP is the last message. then quit thread
            mThread.quit();
        }
        final int id = Process.myTid();
        Thread t = Thread.currentThread();
        YYLog.info(this, String.format("[%s] sdk_ver:%d, tid:%d, %d, priority:%d, %d", t.getName(), Build.VERSION.SDK_INT, id, t.getId(), Process.getThreadPriority(id), t.getPriority()));
    }

    public void start() {
        if(mCallback == null) {
            throw new RuntimeException("mCallback is null");
        }
        synchronized (this) {
            mThreadStatus.set(YY_THREAD_RUNNING);
            mThread = new HandlerThread(mThreadName, mThreadPriority);
            mThread.setPriority(matchPriority(mThreadPriority));
            mThread.start();
            mHandler = new Handler(mThread.getLooper(), mHandlerCallback);
            YYLog.info(this, String.format("[%s] %s", mThreadName, mHandler.toString()));
            mHandler.sendEmptyMessage(YY_THREAD_ON_START);
        }
    }

    public void stop() {
        int status = 0;
        synchronized (this) {
            status = mThreadStatus.get();
            if (mHandler != null && status != YY_THREAD_STOPPED) {
                mThreadStatus.set(YY_THREAD_STOPPED);
                mHandler.removeMessages(YY_THREAD_ON_STOP);
                mHandler.sendEmptyMessage(YY_THREAD_ON_STOP);
            } else {
                YYLog.warn(this, String.format("[%s] already stopped? mThreadStatus = %s", (mThread != null) ? mThread.getName() : mThreadName, YY_THREAD_STATUS_TXT[status]));
            }
        }
        if(status != YY_THREAD_STOPPED && mThread != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mThread.quitSafely();
                }
                int tid = mThread.getThreadId();
                mThread.join();
                YYLog.info(this, String.format("[%s] stop HandlerThread(%d).", mThread.getName(), tid));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mThread = null;
            mHandler = null;
        }
    }

    public void pause() {
        synchronized (this) {
            final int status = mThreadStatus.get();
            if (mHandler != null && status == YY_THREAD_RUNNING) {
                mThreadStatus.set(YY_THREAD_PAUSED);
                mHandler.removeMessages(YY_THREAD_ON_PAUSE);
                mHandler.sendEmptyMessage(YY_THREAD_ON_PAUSE);
            } else {
                YYLog.warn(this, String.format("[%s] already paused? mThreadStatus = %s", (mThread != null) ? mThread.getName() : mThreadName, YY_THREAD_STATUS_TXT[status]));
            }
        }
    }

    public void resume() {
        synchronized (this) {
            final int status = mThreadStatus.get();
            if (mHandler != null && status == YY_THREAD_PAUSED) {
                mThreadStatus.set(YY_THREAD_RUNNING);
                mHandler.removeMessages(YY_THREAD_ON_RESUME);
                mHandler.sendEmptyMessage(YY_THREAD_ON_RESUME);
            } else {
                YYLog.warn(this, String.format("[%s] already resumed? mThreadStatus = %s", (mThread != null) ? mThread.getName() : mThreadName, YY_THREAD_STATUS_TXT[status]));
            }
        }
    }

    public void setStatus(int status) {
        synchronized (this) {
            mThreadStatus.set(status);
        }
    }

    public int getStatus() {
        return mThreadStatus.get();
    }

    public Handler getHandler() {
        return mHandler;
    }

    public boolean sendEmptyMessage(int what) {
        return sendEmptyMessageDelayed(what, 0);
    }

    public boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        synchronized (this) {
            final int status = mThreadStatus.get();
            if (mHandler != null && (status == YY_THREAD_RUNNING || status == YY_THREAD_PAUSED)) {
                return mHandler.sendEmptyMessageDelayed(what, delayMillis);
            } else {
                YYLog.error(this, String.format("[%s] sendEmptyMessageDelayed(%d) failed. mThreadStatus = %s", (mThread != null) ? mThread.getName() : mThreadName, what, YY_THREAD_STATUS_TXT[status]));
            }
            return false;
        }
    }

    public boolean sendMessage(Message msg) {
        return sendMessageDelayed(msg, 0);
    }

    public boolean sendMessageDelayed(Message msg, long delayMillis) {
        synchronized (this) {
            final int status = mThreadStatus.get();
            if (mHandler != null && (status == YY_THREAD_RUNNING || status == YY_THREAD_PAUSED)) {
                return mHandler.sendMessageDelayed(msg, delayMillis);
            } else {
                YYLog.error(this, String.format("[%s] sendMessageDelayed() failed. mThreadStatus = %s", (mThread != null) ? mThread.getName() : mThreadName, YY_THREAD_STATUS_TXT[status]));
            }
            return false;
        }
    }

    public boolean sendMessage(Runnable runnable) {
        return sendMessage(Message.obtain(null, runnable));
    }

    public void removeMessages(int what) {
        synchronized (this) {
            if(mHandler != null) {
                mHandler.removeMessages(what);
            }
        }
    }

    public void clearMessageQueue() {
        synchronized (this) {
            if(mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    public interface Callback {
        void onStart();
        void onStop();
        void onPause();
        void onResume();
        void handleMessage(Message msg);
    }
}
