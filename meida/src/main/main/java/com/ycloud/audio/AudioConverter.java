package com.ycloud.audio;

/**
 * Created by Administrator on 2018/1/18.
 */

public class AudioConverter {
    private long mNativePointer;
    private native long create(int srcSampleRate, int srcChannels, int dstSampleRate, int dstChannels);
    private native void destory(long nativePointer);
    private native int process(long nativePointer, byte[] src, int srcLen, byte[] dst, int dstLen);

    public boolean init(int srcSampleRate, int srcChannels, int dstSampleRate, int dstChannels) {
        mNativePointer = create(srcSampleRate, srcChannels, dstSampleRate, dstChannels);
        return mNativePointer != 0;
    }

    public void unint() {
        if (mNativePointer != 0) {
            destory(mNativePointer);
        }
    }

    /**
     *
     * @param src
     * @param srcLen  must be '10MS' times, that is srcLen  = (srcSampleRate * srcChannels * 2 / 100 * N)
     * @param dst
     * @param dstLen
     * @return
     */
    public int process(byte[] src, int srcLen, byte[] dst, int dstLen) {
        if (mNativePointer != 0) {
            return process(mNativePointer, src, srcLen, dst, dstLen);
        }
        return 0;
    }

}
