package com.ycloud.mediarecord.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;

import com.ycloud.api.videorecord.IAudioRecordListener;
import com.ycloud.api.videorecord.MediaRecordErrorListener;
import com.ycloud.audio.AudioSimpleMixer;
import com.ycloud.common.Constant;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecordWrapper{
	private String TAG = AudioRecordWrapper.class.getSimpleName();
	private AudioRecord mAudioRecord = null;/*音频源*/
	private MediaRecordErrorListener mInfoErrorListener = null;
	private IPcmFrameListener mAudioDataListener = null;
	// 44.1[KHz] is only setting guaranteed to be available on all devices.
	private static int SAMPLE_RATE = AudioRecordConstant.SAMPLE_RATE;
	private static int mChannels = AudioRecordConstant.CHANNELS;
	// AAC, frame/buffer/sec
	public static final int FRAMES_PER_BUFFER = AudioRecordConstant.FRAMES_PER_BUFFER;
	private int mFrameSize;
	public static final int SAMPLES_PER_FRAME = AudioRecordConstant.SAMPLES_PER_FRAME;	// AAC, bytes/frame/channel
    public static final int US_PER_FRAME = (int) (((long)AudioRecordConstant.SAMPLES_PER_FRAME * 1000000) / SAMPLE_RATE);	// AAC, bytes/frame/channel

    private AudioCaptureThread mAudioCaptureThread;
	AtomicBoolean mIsCapturing;

    private int mReadCnt = 0;
    private int mVolDetectFreq = AudioRecordConstant.VOLUME_DETECT_FREQ;
    private IAudioRecordListener mAudioRecordListener = null;


    private Object mStopReady = new Object();
    private Object mCaptureReady = new Object();

	public AudioRecordWrapper(){
        mIsCapturing = new AtomicBoolean(false);
		mAudioCaptureThread = null;
	}

    public void setAudioRecordListener(IAudioRecordListener audioRecordListener) {
        mAudioRecordListener = audioRecordListener;
    }

	public static int getSampleRate() {
		return SAMPLE_RATE;
	}

	public static int getChannels() {
		return mChannels;
	}

	public void setAudioDataListener(IPcmFrameListener listener) {
		mAudioDataListener = listener;
	}

	public void setAudioInfoErrorListerner(MediaRecordErrorListener listener) {
		mInfoErrorListener = listener;
	}

	public boolean createAudioRecord(){
		YYLog.info(TAG, "[audio] createAudioRecord begin");

		int minBufferSize = AudioRecord.getMinBufferSize(
				SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT);
		/*check min buffer size*/
		if (AudioRecord.ERROR_BAD_VALUE == minBufferSize) {
			YYLog.error(TAG, "[audio] createAudioRecord AUDIO_ERROR_GET_MIN_BUFFER_SIZE_NOT_SUPPORT");
			if (mInfoErrorListener != null)
				mInfoErrorListener.onVideoRecordError(MediaRecordErrorListener.AUDIO_ERROR_GET_MIN_BUFFER_SIZE_NOT_SUPPORT, null);
			return false;
		}

		/*计算实际所需缓冲值*/
		mFrameSize = SAMPLES_PER_FRAME * mChannels * 2;
		int buffer_size=mFrameSize*FRAMES_PER_BUFFER; /*缓冲25帧 AAC音频帧*/
		if (mFrameSize >= minBufferSize) {
			minBufferSize = mFrameSize * 2;
		}

		if (buffer_size < minBufferSize) {
			buffer_size = (minBufferSize / mFrameSize + 1) * mFrameSize * 2;
		}

		YYLog.info(TAG, "[audio] mSampleRate: " + SAMPLE_RATE + " "
				+ "minBufferSize: " + minBufferSize + " mFrameSize["+mFrameSize+"] buffer_size["+buffer_size+"]");
		mAudioRecord= AudioRecorderCreator.create(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,buffer_size);
		if (null == mAudioRecord) {
			YYLog.error(TAG, "[audio] createAudioRecord AUDIO_ERROR_CREATE_FAILED");

			if (mInfoErrorListener != null)
				mInfoErrorListener.onVideoRecordError(MediaRecordErrorListener.AUDIO_ERROR_CREATE_FAILED, null);
			return false;
		}

		YYLog.info(TAG, "createAudioRecord success");
		return true;
	}

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to encoder
     */
    private class AudioCaptureThread extends Thread {
        @Override
        public void run() {
            YYLog.info(TAG, "[audio] AudioCaptureThread begin");
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            Thread.currentThread().setName(Constant.SDK_NAME_PREFIX+"AudioRecordWrapper");

            if (createAudioRecord()) {
                try {
                    int frameSize = SAMPLES_PER_FRAME * 2 * mChannels;
                    byte[] frame = new byte[frameSize];

                    YYLog.info(TAG, "AudioRecord startRecording");
                    mAudioRecord.startRecording();

                    synchronized (mCaptureReady) {
                        mCaptureReady.notify();
                    }

                    int bytesInBuffer = 0;

                    while (mIsCapturing.get()) {
                        int bytesNeed = frameSize - bytesInBuffer;
                        int readBytes = mAudioRecord.read(frame, bytesInBuffer, bytesNeed);
                        if (readBytes > 0) {
                            bytesInBuffer += readBytes;
                            if (bytesInBuffer == frameSize) {
                                detectVolume(frame, frameSize);
                                mAudioDataListener.onGetPcmFrame(frame, frameSize);
                                bytesInBuffer = 0;
                            }
                        }
                    }

                    YYLog.info(TAG, "[audio] AudioRecord stop");
                    mAudioRecord.stop();
                } catch (Exception e) {
                    YYLog.error(TAG, "[audio] AudioRecord exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    YYLog.info(TAG, "[audio] AudioRecord release");
                    try {
                        mAudioRecord.release();
                    } catch (Exception e) {
                        YYLog.error(TAG, "[audio] releaseAudioRecord error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                synchronized (mCaptureReady) {
                    mCaptureReady.notify();
                }
                YYLog.error(TAG, "[audio] create AudioRecord fail");
            }

            synchronized (mStopReady) {
                mStopReady.notify();
            }

            YYLog.info(TAG, "[audio] AudioCaptureThread end");
        }
    }


    public synchronized void startAudioCapture() {

        if (mIsCapturing.get() == false) {

            mIsCapturing.set(true);

            if (mAudioCaptureThread == null) {
                mAudioCaptureThread = new AudioCaptureThread();
                mAudioCaptureThread.start();
                //等待Audio Record创建成功
                synchronized (mCaptureReady) {
                    try {
                        mCaptureReady.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public synchronized void stopAudioCapture() {
        YYLog.info(TAG, "AudioRecordWrapper request stopRecord!!");
        long startTime = System.currentTimeMillis();
        synchronized (mStopReady) {
            if (mIsCapturing.get() == true) {
                mIsCapturing.set(false);
                try {
                    mStopReady.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                YYLog.info(TAG,"stopAudioCapture time:" + (System.currentTimeMillis() - startTime));
            }
        }
    }

    public void release() {
        if(mAudioCaptureThread != null) {
            mAudioCaptureThread = null;
        }

		YYLog.info(TAG, "AudioRecordWrapper request release");
	}

	//音量振幅检测
    private void detectVolume(byte[] frame, int readBytes) {
        if (mAudioRecordListener != null) {
            mReadCnt++;
            if (mReadCnt >= mVolDetectFreq) {
                mReadCnt = 0;

                int sum = 0;
                int avgAmplitude = 0;
                int maxAmplitude = 0;
                int samples = readBytes / 2;
                for (int i = 0; i < samples; i++) {
                    int sampleValue = Math.abs(AudioSimpleMixer.byte2short(frame, i * 2));
                    sum += sampleValue;
                    maxAmplitude = (maxAmplitude > sampleValue ? maxAmplitude : sampleValue);
                }
                if (readBytes > 0) {
                    avgAmplitude = sum * 2 / readBytes;
                }
                mAudioRecordListener.onVolume(avgAmplitude, maxAmplitude);
            }
        }
    }
}
