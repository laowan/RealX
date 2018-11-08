package com.ycloud.svplayer;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2017/7/27.
 */
public final class NativeFfmpeg {
    private static final String TAG = NativeFfmpeg.class.getSimpleName();

    static {
        try {
            System.loadLibrary("audioengine");
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
            nativeClassInit();
        } catch (UnsatisfiedLinkError e) {
            YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
        }
    }

    public static native void nativeClassInit();
    private native void native_setup();
    private native void native_release();
    private native int native_create(int codec, MediaFormat format);
    private native void native_destroy();
    private native int native_decode(ByteBuffer input, ByteBuffer output, boolean keyFrame);

    /**
     *
     * @param output
     * @return. -1: error; -2 : end of file; 0 : EGAIN; 1: success.
     */
    private native int native_flush(ByteBuffer output);

    private long mNativeHandle = 0;
    private Callback mCallback = null;

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public interface Callback {
        void onFormatChanged(final MediaInfo info);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public int create(int codec, MediaFormat format) {
        ByteBuffer csd0Buf =format.getByteBuffer("csd-0");
        ByteBuffer csd1Buf =format.getByteBuffer("csd-1");
        ByteBuffer buffer = ByteBuffer.allocateDirect(csd0Buf.capacity() + csd1Buf.capacity());
        buffer.clear();

        int origPos = csd0Buf.position();
        buffer.put(csd0Buf);
        csd0Buf.position(origPos);

        origPos = csd1Buf.position();
        buffer.put(csd1Buf);
        csd1Buf.position(origPos);

        format.setByteBuffer("extra-data",buffer);
        if(mNativeHandle == 0) {
            return native_create(codec, format);
        }
        return 0;
    }

    public void destroy() {
        if(mNativeHandle != 0) {
            native_destroy();
        }
        mNativeHandle = 0;
    }

    public int decode(ByteBuffer input, ByteBuffer output, boolean keyFrame) {
        if(mNativeHandle != 0) {
            return native_decode(input, output, keyFrame);
        }
        return -1;
    }

    public int flush(ByteBuffer output) {
        if(mNativeHandle != 0) {
            return native_flush(output);
        }
        return -1;
    }

    private void onFormatChanged(MediaInfo info) {
        if(mCallback != null) {
            mCallback.onFormatChanged(info);
        }
    }
}
