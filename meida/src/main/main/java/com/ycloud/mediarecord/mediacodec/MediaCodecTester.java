package com.ycloud.mediarecord.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.ycloud.utils.YYLog;

import java.security.spec.RSAOtherPrimeInfo;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Administrator on 2017/5/16.
 */

public class MediaCodecTester {

    public static final String TAG = "MediaCodecTester";

    public static AtomicBoolean  mHard264Enable = new AtomicBoolean(false);
    public static AtomicBoolean  mHard264TestOnce = new AtomicBoolean(false);
    public static AtomicBoolean  sTestReady = new AtomicBoolean(false);
    public static long  sMaxWaitTimeMs = 2*1000;  //最大等待时间.

    public static  boolean testHard264Enable() {
        synchronized (mHard264Enable) {
            if (mHard264TestOnce.getAndSet(true)) {
                return mHard264Enable.get();
            }

            testVideoEncoderCrashSync();
        }

        boolean ret = mHard264Enable.get();
        YYLog.info(TAG, "testHard264Enable return:  " + ret);
        return ret;
    }

    private static void testVideoEncoderCrashSync() {
            //wait for minutes
        YYLog.info(TAG, "testVideoEncoderCrashSync begin");
        Thread  testThread = new Thread(new Runnable() {
            @Override
            public void run() {
                testVideoEncoderCrash();
                synchronized (sTestReady) {
                    sTestReady.set(true);
                    sTestReady.notifyAll();
                }
            }
        });

        testThread.start();
        synchronized (sTestReady) {
            if(!sTestReady.get()) {
                try {
                    sTestReady.wait(sMaxWaitTimeMs);
                } catch (InterruptedException e) {
                    YYLog.error(TAG, "testVideoEncoderCrashSync wait exception: "+e.toString());
                    e.printStackTrace();
                }
            }
        }

        YYLog.info(TAG, "testVideoEncoderCrashSync end");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static boolean testVideoEncoderCrash() {
        YYLog.info(TAG, "testVideoEncoderCrash begin");

        boolean crashed = false;
        if(Build.VERSION.SDK_INT <Build.VERSION_CODES.JELLY_BEAN_MR2){
            mHard264Enable.set(false);
            return true;
        }
        MediaCodec encoder = null;
        Surface surface = null;
        String mEncoderH264Name;
        String mime = "video/avc";

        try {
            MediaFormat throwable = MediaFormat.createVideoFormat(mime, 720, 1280);
            throwable.setInteger("color-format", 2130708361);
            throwable.setInteger("bitrate", 2000000);
            throwable.setInteger("frame-rate", 30);
            throwable.setInteger("i-frame-interval", 3);
            if(Build.VERSION.SDK_INT >= 21) {
                throwable.setInteger("bitrate-mode", 2);
            }

            YYLog.i(TAG, "testVideoEncoder mime:" + mime + ", MediaCodec format:" + throwable);
            encoder = MediaCodec.createEncoderByType(mime);
            String encoderName = encoder.getName();
            mEncoderH264Name = encoderName;

            YYLog.i(TAG, "testVideoEncoder mime:" + mime + ", MediaCodec encoder:" + encoderName);
            encoder.configure(throwable, (Surface)null, (MediaCrypto)null, 1);
            surface = encoder.createInputSurface();
            encoder.start();
        } catch (Throwable var14) {
            mHard264Enable.set(false);
            crashed = true;
            YYLog.e(TAG, "testVideoEncoderCrash, mime:" + mime + ", reason:" + var14);
        } finally {
            if(encoder != null) {
                try {
                    if(surface != null) {
                        surface.release();
                    }

                    encoder.stop();
                    encoder.release();
                } catch (Throwable var13) {
                    YYLog.e("YYVideoCodec", "release test encoder error! mime:" + mime + ", reason:" + var13);
                }
            }
        }

        if(crashed) {
            mHard264Enable.set(false);
        } else {
            mHard264Enable.set(true);
        }

        YYLog.info(TAG, "testVideoEncoderCrash end");
        return crashed;
    }
}
