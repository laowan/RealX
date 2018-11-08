package com.ycloud.mediacodec.videocodec;

import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.ymrmodel.JVideoEncodedData;

import java.nio.ByteBuffer;
import java.util.HashSet;

import com.ycloud.utils.YYLog;
import com.ycloud.common.Constant;


public class X264SoftEncoder {
    private static HashSet<X264SoftEncoder> mEncoders = new HashSet<X264SoftEncoder>();
    private int mEncodeFrameCnt = 0;
    public final static int LOW_FRAMERATE = 15;

    private RecordConfig mRecordConfig;

    public static X264SoftEncoder createEncoder() {
        X264SoftEncoder encoder = new X264SoftEncoder();
        synchronized (mEncoders) {
            mEncoders.add(encoder);
        }
        return encoder;
    }

    public static void destroyEncoder(X264SoftEncoder encoder) {
        if (encoder == null) {
            return;
        }
        encoder.destroy();
        synchronized (mEncoders) {
            mEncoders.remove(encoder);
        }
    }

    private long mNativeEncoderHandle = 0;
    private VideoStreamFormat mVideoFormat = new VideoStreamFormat();
    private String mSoftConfig = null;
    /** [NOTICE] End!! 请不要更改此结构体的名称， 以及字段名字等，native层使用这些名字.!!!*/

    /**
     * pSps
     * pPps.
     */
    public X264SoftEncoder() {
        nativeCreateEncoder();
    }

    public void initEncoder(VideoEncoderConfig videoEncoderConfig, RecordConfig recordConfig) {
        mRecordConfig = recordConfig;
        //x264的码率是kbps
        mVideoFormat.iBitRate = videoEncoderConfig.mBitRate / 1024;
        mVideoFormat.iFrameRate = videoEncoderConfig.mFrameRate;
        mVideoFormat.iHeight = videoEncoderConfig.getEncodeHeight();
        mVideoFormat.iWidth = videoEncoderConfig.getEncodeWidth();
//		mVideoFormat.iCaptureOrientation = videoEncoderConfig.mDisplayRotation; //暂时无用.
        mVideoFormat.iPicFormat = VideoConstant.MediaLibraryPictureFormat.kMediaLibraryPictureFmtI420;
        if (videoEncoderConfig.mFrameRate < LOW_FRAMERATE) {
            mVideoFormat.iProfile = 0; //baseline in yysdk, change into main profile. 0-baseline 1 - main, 2-5 high profile
        } else {
            mVideoFormat.iProfile = 1; //baseline in yysdk, change into main profile. 0-baseline 1 - main, 2-5 high profile
        }
        mVideoFormat.iEncodePreset = VideoConstant.VideoEncodePreset.VIDEO_ENCODE_PRESET_DEFAULT;
        //mVideoFormat.iCodec =
        //mVideoFormat.iEncodePreset =
        //mVideoFormat.iRawCodecId =
        String param = videoEncoderConfig.mEncodeParameter + "crf=" + videoEncoderConfig.mQuality + ":";
        nativeInitEncoder(mVideoFormat, param.getBytes());
        mSoftConfig = videoEncoderConfig.mEncodeParameter; //TODO. copy;

        YYLog.info(this, Constant.MEDIACODE_ENCODER + "software encoder, configure:" + (videoEncoderConfig.mEncodeParameter == null ? "null" : videoEncoderConfig.toString()));
    }

    public JVideoEncodedData[] encode(ByteBuffer input, long iPts, int frameType) {
        //录制速度超过1.0时，需要均匀丢视频帧
        mEncodeFrameCnt++;
        float recordSpeed = mRecordConfig.getRecordSpeed();
        if (recordSpeed > 1.0f && mEncodeFrameCnt % (int) recordSpeed != 0) {
//            YYLog.info(this,"drop video frame cnt" + mEncodeFrameCnt);
            return null;
        }

        return nativeProcess(input.array(), input.remaining(), iPts, frameType);
    }

    public JVideoEncodedData[] flush() {
        return nativeFlush();
    }


    public void adjustBitRate(int bitRate) {
        if (mNativeEncoderHandle != 0) {
            nativeAdjuestBitRate(bitRate);
            mVideoFormat.iBitRate = bitRate;
        }
    }

    private void deInitEncoder() {
        if (mNativeEncoderHandle != 0) {
            nativeDeinitEncoder();
        }
    }

    public void setEnocderImageSize(int width, int height) {
        mVideoFormat.iWidth = width;
        mVideoFormat.iHeight = height;
    }

    //重置解码器.
    public void restartEncoder() {
        deInitEncoder();
        nativeInitEncoder(mVideoFormat, mSoftConfig.getBytes());
    }


    private void destroy() {
        nativeDeinitEncoder();  //确保删除libx264(ffmpeg-neno.so).库中的内存.
        nativeDestroyEncoder();
    }

    //process((const unsigned char *pData, unsigned int nDataLen, void* pInDes, void* pOutDes);
    private native JVideoEncodedData[] nativeProcess(byte[] input, int len, long pts, int jframeType);

    private native void nativeInitEncoder(VideoStreamFormat format, byte[] configs);

    private native void nativeDeinitEncoder();

    private native void nativeCreateEncoder();

    private native void nativeDestroyEncoder();

    private native void nativeAdjuestBitRate(int bitRate);

    private native JVideoEncodedData[] nativeFlush();


    private static native void nativeClassInit();

    static {
        nativeClassInit();
    }
}
