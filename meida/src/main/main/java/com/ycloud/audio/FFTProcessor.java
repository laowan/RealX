package com.ycloud.audio;

import com.ycloud.utils.YYLog;

/**
 * Created by Administrator on 2018/5/15.
 */

public class FFTProcessor {
    private long mNativePointer;
    private boolean mEnable;

    private native long create(int fftLen);
    private native void destroy(long pointer);
    private native void flush(long pointer);
    private native void process(long pointer, byte[] data, int offset, int len, int stride);
    private native int frequencyData(long pointer, float[] data, int len);

    public FFTProcessor() {

    }

    public void init(int fftLen) {
        synchronized (this) {
            mNativePointer = create(fftLen);
        }
    }

    public boolean isEnable() {
        synchronized (this) {
            return mEnable;
        }
    }

    public void setEnable(boolean enable) {
        synchronized (this) {
            mEnable = enable;
        }
    }

    public void deinit() {
        synchronized (this) {
            if (mNativePointer != 0) {
                destroy(mNativePointer);
                mNativePointer = 0;
            }
        }
    }

    public void flush() {
        synchronized (this) {
            if (mNativePointer != 0) {
                flush(mNativePointer);
            }
        }
    }

    public void process(byte[] data, int offset, int len, int stride) {
        synchronized (this) {
            if (mEnable && mNativePointer != 0) {
                process(mNativePointer, data, offset, len, stride);
            }
        }
    }

    public int frequencyData(float[] data, int len) {
        synchronized (this) {
            if (mEnable && mNativePointer != 0) {
                return frequencyData(mNativePointer, data, len);
            }
            return 0;
        }
    }
}
