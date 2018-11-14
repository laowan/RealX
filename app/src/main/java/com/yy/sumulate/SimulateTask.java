package com.yy.sumulate;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.CallSuper;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class SimulateTask implements Runnable {
    private static final String TAG = SimulateTask.class.getSimpleName();
    protected final Activity base;
    private Instrumentation inst;
    private AtomicInteger seq = new AtomicInteger(0);

    /**
     * 构造函数
     *
     * @param base
     */
    SimulateTask(Activity base) {
        if (null == base) {
            throw new IllegalArgumentException("Activity host cannot be null.");
        }
        this.base = base;
        //反射获取inst
        try {
            Field field = base.getClass().getDeclaredField("mInstrumentation");
            field.setAccessible(true);
            Object object = field.get(base);
            if (object instanceof Instrumentation) {
                inst = (Instrumentation) object;
            } else {
                inst = null;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            inst = null;
        }
    }

    @CallSuper
    @Override
    public void run() {
        Log.d(TAG, "SimulateTask.run()");
        InputEvent event = createInputEvent(seq.getAndIncrement());
        if (null != event) {
            if (event instanceof KeyEvent) {
                final KeyEvent keyEvent = (KeyEvent) event;
                if (null != inst) {
                    inst.sendKeySync(keyEvent);
                    inst.waitForIdleSync();
                } else {
                    base.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            base.dispatchKeyEvent(keyEvent);
                        }
                    });
                }
            } else if (event instanceof MotionEvent) {
                final MotionEvent motionEvent = (MotionEvent) event;
                if (null != inst) {
                    inst.sendPointerSync(motionEvent);
                    inst.waitForIdleSync();
                } else {
                    base.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            base.dispatchTouchEvent(motionEvent);
                        }
                    });
                }
            }
            //loop next
            handler.postDelayed(this, 20);
        }
    }

    /**
     * 创建事件，返回null时退出loop
     *
     * @param seq
     * @return
     */
    abstract InputEvent createInputEvent(int seq);

    private HandlerThread thread;
    private Handler handler;

    /**
     * 准备开始分发事件
     */
    public void simulate() {
        Log.d(TAG, "SimulateTask.simulate():" + seq.get());
        //创建线程
        if (null == handler) {
            thread = new HandlerThread(toString());
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        handler.post(this);
    }
}
