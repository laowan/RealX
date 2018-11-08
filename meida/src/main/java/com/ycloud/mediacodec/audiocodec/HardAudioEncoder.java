package com.ycloud.mediacodec.audiocodec;

/**
 * Created by kele on 2017/4/27.
 */

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.common.Constant;
import com.ycloud.mediacodec.compat.MediaCodecBufferCompatWrapper;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.mediarecord.audio.AudioRecordWrapper;
import com.ycloud.statistics.UploadStatManager;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class HardAudioEncoder implements AudioEncoder
{
    private static String TAG=HardAudioEncoder.class.getSimpleName();
    AudioEncodeListener mListener = null;
    private final MediaFormat mOutputFormat;
    private MediaCodec mEncoder;
    private boolean mInitialized = false;
    private MediaCodecBufferCompatWrapper mEncoderBuffers;
    private boolean mIsEncoderEOS;
    private MediaFormat mActualOutputFormat;//实际输出格式

    // create BufferInfo here for effectiveness(to reduce GC)
    private  MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    /**
     * previous presentationTimeUs for writing
     */
    private long mPrevOutputPTSUs = 0L;

    private boolean mDrainEncodeStart = false;

    public HardAudioEncoder(MediaFormat audioOutputFormat) {
        mOutputFormat=audioOutputFormat;
    }

    public void setEncodeListener(AudioEncodeListener listener) {
        mListener = listener;
    }

    public void init() throws Exception{

        if (!mInitialized) {
            initEncoder();
        }

        mEncoderBuffers = new MediaCodecBufferCompatWrapper(mEncoder);
    }

    public int pushToEncoder(YYMediaSample sample) throws Exception {
        long ptsUs = sample.mAndoridPtsNanos / 1000;
        encodeAudioFrame(sample.mDataBytes, ptsUs);
        return 1;
    }


    /*输入编码器并编码,时间戳从外部输入*/
    private void encodeAudioFrame(byte[] data, long presentationTimeUs) throws Exception{
        if (mIsEncoderEOS) {/*编码结束*/
            YYLog.info(TAG, "AudioEncoder EOS !!!");
        }
        /*填充输入数据给编码器*/
//        YYLog.info(TAG, Constant.MEDIACODE_PTS_SYNC + "audio pts before encode:" + presentationTimeUs / 1000);
        writeInputBuffer(data, presentationTimeUs);
        drainEncoder(0);
    }
    private void writeInputBuffer(byte[] data, long presentationTimeUs) throws Exception {
        int inputBufferIndex;
        do {
            inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
        } while (inputBufferIndex < 0);

        ByteBuffer inputBuffer = mEncoderBuffers.getInputBuffer(inputBufferIndex);
        inputBuffer.clear();
        int size = inputBuffer.remaining();
        if (size < data.length) {
            YYLog.e(TAG, " input data length is greater than buffer size !!! " + size + " : " + data.length);
        }
        size = size > data.length ? data.length : size;
        inputBuffer.put(data, 0, size);
        mEncoder.queueInputBuffer(inputBufferIndex, 0, size, presentationTimeUs, 0);
    }


    private void drainEncoder(long timeoutUs) throws Exception {
        while (true) {
        /*拿到编码器填充的mBufferInfo*/
            int index = mEncoder.dequeueOutputBuffer(mBufferInfo, timeoutUs/*毫秒*/);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mActualOutputFormat != null) {
                    /* should happen before receiving buffers, and should only happen once*/
                    throw new RuntimeException("Video output format changed twice.");
                }
                /* get output format from codec and pass them to muxer*/
                mActualOutputFormat = mEncoder.getOutputFormat();
                if (mActualOutputFormat == null) {/*这个可能性应该没有*/
                    YYLog.error(this, "audio encoder actual output format is null");
                    throw new RuntimeException("Could not determine actual output format.");
                }
                /*告知muxer,通知muxer启动*/
                if (mListener != null) {
                    mListener.onEncoderFormatChanged(mActualOutputFormat);
                }
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mEncoderBuffers = new MediaCodecBufferCompatWrapper(mEncoder);
            } else {
                ByteBuffer encodedData = mEncoderBuffers.getOutputBuffer(index);
                if (encodedData == null) {
                    throw new RuntimeException("audio encoderOutputBuffer " + index + " was null");
                }
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                //当首个编码后的audio frame pts不是0时，认为异常，强制置为0.后续会优化
                if(!mDrainEncodeStart) {
                    mBufferInfo.presentationTimeUs = 0;
                    mDrainEncodeStart = true;
                }

                // adjust the ByteBuffer values to match BufferInfo (not needed?)

                UploadStatManager.getInstance().endEncode((int) (mBufferInfo.presentationTimeUs / 1000));

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mBufferInfo.presentationTimeUs = mPrevOutputPTSUs + AudioRecordWrapper.US_PER_FRAME;
                }else {
                    mPrevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }

                mListener.onEncodeOutputBuffer(encodedData, mBufferInfo, mBufferInfo.presentationTimeUs, mEncoder.getOutputFormat());

                mEncoder.releaseOutputBuffer(index, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    YYLog.debug(this, "[audio] encoder end of stream!!");
                    mIsEncoderEOS = true;
                    break;      // out of while
                }
            }
        }
    }

    protected long getPTSUs(long pts) {
        long result = pts;//System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < mPrevOutputPTSUs)
            result = (mPrevOutputPTSUs - result) + result;
        return result;
    }
    private boolean isFinished() {
        return mIsEncoderEOS;
    }

    /*编码器的资源在这里释放*/
    private void release() {
        handleStopRecording();
    }

    /*置EOS*/
    private void signalEndOfInputStream() {
        int inputBufferIndex;
        try {
            do {
                inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
            } while (inputBufferIndex < 0);

            ByteBuffer inputBuffer = mEncoderBuffers.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            mEncoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM);
        } catch (IllegalStateException e) {
            YYLog.error(this,  Constant.MEDIACODE_ENCODER+"[exception] signalEndOfInputStream: " + e.toString());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopAudioRecord(){
        handleStopRecording();
        YYLog.info(this, "HardAudioEncoder stopAudioRecord");
    }

    /**
     * Handles a request to stop encoding.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void handleStopRecording() {
        YYLog.info(this,  Constant.MEDIACODE_ENCODER+"handleStopRecording");
        if(mEncoder != null) {
            signalEndOfInputStream();
        }

        synchronized (this) {
            try {
                drainEncoder(5*1000);  //5ms
            } catch (Exception e) {
                YYLog.error(TAG,"handleStopRecording drainEncoder error:" + e.getMessage());
                mBufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mListener.onEncodeOutputBuffer(null, mBufferInfo, mBufferInfo.presentationTimeUs,null);
            }

            if(!mIsEncoderEOS) {
                mListener.onEndOfInputStream();
            }
            mIsEncoderEOS = true;
        }

        deInit();

        mEncoderBuffers = null;
        mBufferInfo = null;/*added*/
    }

    private void initEncoder() throws Exception{
        if (mEncoder == null) {
            try {
                mEncoder = MediaCodec.createEncoderByType(mOutputFormat.getString(MediaFormat.KEY_MIME));
            } catch (IOException e) {
                YYLog.error(this, "HardAudioEncoder.initEncoder exception: " + e.toString());
                throw new IllegalStateException(e);
            }
        }

        mEncoder.configure(mOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
        YYLog.info(this,  Constant.MEDIACODE_ENCODER+"[audio] initEncoder");
        mInitialized = true;
    }

    private void deInit() {

        try {
            if (mEncoder != null) {
                mEncoder.stop();
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + " mEncoder.stop");
            }
        } catch (Throwable e) {
            YYLog.error(TAG, "[exception]" + e.getMessage());
        } finally {
            if (mEncoder != null) {
                mEncoder.release();
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + " mEncoder.release");
            }
            mEncoder = null;
        }

        mInitialized = false;
        //initEncoder();
    }

    public void releaseEncoder() {
        try {
            if (mEncoder != null) {
                mEncoder.stop();
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + " mEncoder.stop");
            }
        } catch (Throwable e) {
            YYLog.error(TAG, "[exception]" + e.getMessage());
        } finally {
            if (mEncoder != null) {
                mEncoder.release();
                YYLog.info(TAG, Constant.MEDIACODE_ENCODER + " mEncoder.release");
            }
            mEncoder = null;
        }

        mInitialized = false;
    }
}
