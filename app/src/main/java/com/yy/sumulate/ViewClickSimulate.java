package com.yy.sumulate;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;

public class ViewClickSimulate extends SimulateTask {
    private static final String TAG = ViewClickSimulate.class.getSimpleName();
    private final View target;

    /**
     * 构造函数
     *
     * @param base
     */
    public ViewClickSimulate(Activity base, View view) {
        super(base);
        this.target = view;
        if (null == target) {
            throw new IllegalArgumentException("View target cannot be null.");
        }
    }

    @Override
    InputEvent createInputEvent(int seq) {
        Log.d(TAG, "createInputEvent():" + seq);
        long sync = SystemClock.uptimeMillis();
        long time = SystemClock.uptimeMillis();
        float x = target.getX() + target.getWidth() / 2;
        float y = target.getY() + target.getHeight() / 2;
        MotionEvent event = null;
        if (seq == 0) {
            event = MotionEvent.obtain(sync, time, MotionEvent.ACTION_DOWN, x, y, 0);
        } else if (seq == 1) {
            event = MotionEvent.obtain(sync, time, MotionEvent.ACTION_UP, x, y, 0);
        }
        return event;
    }
}
