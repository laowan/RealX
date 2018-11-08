package com.ycloud.audio;

/**
 * Created by Administrator on 2018/1/23.
 */

public class AudioPlaybackRateProcessor {
    private long mNativePointer;
    private native long create(int sampleRate, int channels, boolean voice);
    private native void destroy(long pointer);
    private native void push(long pointer, byte[] data, int len);
    private native int pull(long pointer, byte[] data, int offset, int len);
    private native void setRate(long pointer, float rate);
    private native void flush(long pointer);
    private native void clear(long pointer);
    private native int numOfMSAvailable(long pointer);
    private native int numOfMSUnprocess(long pointer);
    private native int numOfBytesAvailable(long pointer);
    private native int numOfBytesUnprocess(long pointer);

    static {
        try {
            System.loadLibrary("audioengine");
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    public void init(int sampleRate, int channels, boolean voice) {
        synchronized (this) {
            mNativePointer = create(sampleRate, channels, voice);
        }
    }

    public void unint() {
        synchronized (this) {
            destroy(mNativePointer);
            mNativePointer = 0;
        }
    }

    public void setRate(float rate) {
        synchronized (this) {
            if (mNativePointer != 0) {
                setRate(mNativePointer, rate);
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

    public void clear() {
        synchronized (this) {
            if (mNativePointer != 0) {
                clear(mNativePointer);
            }
        }
    }

    public void push(byte[] data, int len) {
        synchronized (this) {
            if (mNativePointer != 0) {
                push(mNativePointer, data, len);
            }
        }
    }

    public int pull(byte[] data, int offset, int len) {
        synchronized (this) {
            if (mNativePointer != 0) {
                return pull(mNativePointer, data, offset, len);
            }
        }
        return 0;
    }

    public int numOfMSAvailable() {
        synchronized (this) {
            if (mNativePointer != 0) {
                return numOfMSAvailable(mNativePointer);
            }
        }
        return 0;
    }

    public int numOfMSUnprocess() {
        synchronized (this) {
            if (mNativePointer != 0) {
                return numOfMSUnprocess(mNativePointer);
            }
        }
        return 0;
    }

    public int numOfBytesAvailable() {
        synchronized (this) {
            if (mNativePointer != 0) {
                return numOfBytesAvailable(mNativePointer);
            }
        }
        return 0;
    }

    public int numOfBytesUnprocess() {
        synchronized (this) {
            if (mNativePointer != 0) {
                return numOfBytesUnprocess(mNativePointer);
            }
        }
        return 0;
    }
}
