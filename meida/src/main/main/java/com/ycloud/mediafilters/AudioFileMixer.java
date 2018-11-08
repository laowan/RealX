package com.ycloud.mediafilters;

import android.media.MediaFormat;

import com.ycloud.api.common.SampleType;
import com.ycloud.audio.AacFileWriter;
import com.ycloud.audio.AudioSimpleMixer;
import com.ycloud.audio.AudioTrackWrapper;
import com.ycloud.audio.WavFileReader;
import com.ycloud.mediaprocess.AudioMixInternal;
import com.ycloud.mediaprocess.AudioTranscodeInternal;
import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2018/1/15.
 */

public class AudioFileMixer {
    final static String TAG= "AudioFileMixer";
    String mCacheDir;
    String mBgmMusicPath;
    float mBgmVolume = 1.0f;
    String mMagicAudioPath;
    String mPureAudioPath;
    float mVideoVolume = 1.0f;
    double mDuration;
    LinkedBlockingQueue<byte[]> mEncodedFrame = new LinkedBlockingQueue<>();
    private Object mFinishLock = new Object();
    private boolean mIsFinish = false;
    private AudioEncoderFilter mAudioEncoderFilter = null;
    private EncodedFrameReceiver mEncodedFrameReceiver = new EncodedFrameReceiver();
    MediaFormat mMediaFormat;
    AudioFilterContext mAudioFilterContext;
    //private static final int kSAMPLE_RATE  = 44100;
    //private static final int kSAMPLE_CHANNELS  = 2;
    private AacFileWriter aacFileWriter;

    MediaMuxerFilter  mMediaMuxFilter =null;
    MixedAudioDataManagerFilter mMusicDataManagerFilter = new MixedAudioDataManagerFilter();

    public AudioFileMixer(String cacheDir, AudioFilterContext context) {
        mCacheDir = cacheDir;
        mAudioFilterContext = context;
        mAudioEncoderFilter = new AudioEncoderFilter(context);
    }

    public void enableDumpRawMp4(boolean enable) {
        if (mMusicDataManagerFilter != null) {
            if (enable) {
                mAudioEncoderFilter.addDownStream(mMusicDataManagerFilter);
            } else {
                mAudioEncoderFilter.removeDownStream(mMusicDataManagerFilter);
            }
        }
    }

    public void setDuration(double duration) {
        mDuration = duration;
    }

    public void setVideoVolume(float videoVolume) {
        mVideoVolume = videoVolume;
    }

    public void setBgmMusicPath(String bgmMusicPath, float volume) {
        mBgmMusicPath = bgmMusicPath;
        mBgmVolume = volume;
    }

    public void setMediaMuxer(MediaMuxerFilter mediaMuxerFilter) {
        mMediaMuxFilter = mediaMuxerFilter;
    }

    public void setMagicAudioPath(String magicAudioPath) {
        mMagicAudioPath = magicAudioPath;
    }

    public void setPureAudioPath(String pureAudioPath) {
        mPureAudioPath = pureAudioPath;
    }

    public boolean haveAudio() {
        return mBgmMusicPath != null || mPureAudioPath != null || mMagicAudioPath != null;
    }

    public void mix() {
        //aacFileWriter = new AacFileWriter();
        //aacFileWriter.open("/sdcard/m.aac", 44100, 2);
        if (mMusicDataManagerFilter != null) {
            mMusicDataManagerFilter.init();
            mMusicDataManagerFilter.startRecord();
        }

        if (mDuration > 0.001) {
            int audioCount = 0;
            synchronized (mFinishLock) {
                mIsFinish = false;
            }
            if (mMediaMuxFilter != null) {
                mMediaMuxFilter.setEnableAudio(true);
            }
            if (mAudioFilterContext != null) {
                mAudioFilterContext.getRecordConfig().setEnableAudioRecord(true);
            }

            if (mBgmMusicPath != null) {
                audioCount++;
            }
            if (mMagicAudioPath != null) {
                audioCount++;
            }

            if (mPureAudioPath != null) {
                audioCount++;
            }

            YYLog.info(TAG, " duration %d volume %f mix Bgm:%s Magic: %s Pure: %s", (int) mDuration, mVideoVolume, mBgmMusicPath != null ? mBgmMusicPath : "", mMagicAudioPath != null ? mMagicAudioPath : "", mPureAudioPath != null ? mPureAudioPath : "");

            //Reset bgm first
            String transcodeAudioPath = mCacheDir + "transcodeAudio.wav";
            boolean ret;
            if (audioCount > 1) {
                //Should mix audio
                AudioMixInternal audioMixInternal = new AudioMixInternal();
                String mixAudioPath = mCacheDir + "mixAudio.wav";
                audioMixInternal.setOutputPath(mixAudioPath);
                boolean needClip = true;
                if (mPureAudioPath != null) {
                    audioMixInternal.addAudioMixBean(mPureAudioPath, 0, 0, mVideoVolume);
                    needClip = false;
                }

                if (mBgmMusicPath != null) {
                    audioMixInternal.addAudioMixBean(mBgmMusicPath, 0, 0, mBgmVolume);
                }

                if (mMagicAudioPath != null) {
                    audioMixInternal.addAudioMixBean(mMagicAudioPath, 0, 0, 1);
                }

                ret = audioMixInternal.executeInternal();

                if (ret == false) {
                    YYLog.error(TAG, "AudioMixInternal fail!");
                } else {
                    if (needClip) {
                        ret = audioToWav(mixAudioPath, transcodeAudioPath, mDuration);
                        if (ret) {
                            processAudioFile(transcodeAudioPath, 1.0f);
                        } else {
                            YYLog.error(TAG, "audioToWav fail!");
                        }
                    } else {
                        processAudioFile(mixAudioPath, 1.0f);
                    }
                }
            } else if(audioCount == 1){
                ret = false;
                if (mBgmMusicPath != null) {
                    ret = audioToWav(mBgmMusicPath, transcodeAudioPath, mDuration);
                    if (ret) {
                        processAudioFile(transcodeAudioPath, mBgmVolume);
                    }
                }
                if (mMagicAudioPath != null) {
                    ret = audioToWav(mMagicAudioPath, transcodeAudioPath, mDuration);
                    if (ret) {
                        processAudioFile(transcodeAudioPath, 1.0f);
                    }
                }
                if (mPureAudioPath != null) {
                    processAudioFile(mPureAudioPath, mVideoVolume);
                }
            }else {
                if (mMediaMuxFilter != null) {
                    mMediaMuxFilter.setEnableAudio(false);
                    mAudioFilterContext.getRecordConfig().setEnableAudioRecord(false);
                }
            }
            synchronized (mFinishLock) {
                mIsFinish = true;
                //aacFileWriter.close();
            }
        }
        if (mMusicDataManagerFilter != null) {
            mMusicDataManagerFilter.stopRecord();
            mMusicDataManagerFilter.deInit();
        }
    }

    private boolean audioToWav(String inputPath, String outputPath, double duration) {
        AudioTranscodeInternal audioTranscode = new AudioTranscodeInternal();
        audioTranscode.setPath(inputPath, outputPath);
        audioTranscode.setMediaTime(0, duration);
        boolean ret = audioTranscode.execute();

        return ret;
    }

    private void processAudioFile(String path, float volume) {
        File inputFile = new File(path);
        if (!inputFile.exists()) {
            YYLog.error(TAG, " file not exist %s ", path);
            return;
        }

        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
        WavFileReader wavFileReader = new WavFileReader();
        wavFileReader.open(inputFile.getPath());
        int channels = wavFileReader.getInChannels();
        int sampleRate = wavFileReader.getInSampleRate();

        YYLog.info(TAG, "processAudioFile begin path: %d %d %s", sampleRate, channels, path);

        mAudioEncoderFilter.init(sampleRate, AudioTrackWrapper.kCHANNEL_COUNT);
        mAudioEncoderFilter.startAudioEncode();
        mAudioEncoderFilter.addDownStream(mEncodedFrameReceiver);

        int bytesPerFrame = AudioRecordConstant.SAMPLES_PER_FRAME * channels * 2;
        int bytesPerSecond = sampleRate * channels * 2;
        long bytesHaveRead = 0;
        byte[] frame = new byte[bytesPerFrame];
        byte[] stereoFrame = null;
        if (channels == 1) {
            stereoFrame = new byte[bytesPerFrame * 2];
        }
        try {
            while (true) {
                int readLen = wavFileReader.read(frame, bytesPerFrame);
                if (readLen < 0) {
                    mAudioEncoderFilter.stopAudioEncode(true);
                    break;
                }
                AudioSimpleMixer.scale(frame, 0, readLen, volume);
                if (channels == 1) {
                    AudioSimpleMixer.mono2stereo(frame, stereoFrame, readLen);
                    sample.mDataBytes = stereoFrame;
                    sample.mBufferSize = readLen * 2;
                }else {
                    sample.mDataBytes = frame;
                    sample.mBufferSize = readLen;
                }
                sample.mSampleType = SampleType.AUDIO;
                sample.mAndoridPtsNanos = bytesHaveRead * 1000000000 / bytesPerSecond;
                sample.mDeliverToEncoder = true;
                mAudioEncoderFilter.processMediaSample(sample, null);
                bytesHaveRead += readLen;
            }
            sample.decRef();
            mAudioEncoderFilter.deInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int mDebugCnt = 0;
    private class EncodedFrameReceiver extends AbstractYYMediaFilter {
        public boolean processMediaSample(YYMediaSample sample, Object upstream) {
            if (sample.mDataByteBuffer == null) {
                mMediaFormat = sample.mMediaFormat;
                YYLog.debug(this, "[audio] AudioFileMixer recv audio encode fomart data!!");
            }

            /*
            if (sample.mDataByteBuffer == null) {
                mMediaFormat = sample.mMediaFormat;
            }else {
                int p = sample.mDataByteBuffer.position();
                int l = sample.mDataByteBuffer.limit();
                int len = sample.mDataByteBuffer.remaining();
                byte[] frame = new byte[len];
                sample.mDataByteBuffer.get(frame);
                sample.mDataByteBuffer.position(p);
                sample.mDataByteBuffer.limit(l);
                //先实验直接输出到audio文件中.
                aacFileWriter.write(frame, len);
                Log.e(AudioFileMixer.TAG, "write aac frame " + mDebugCnt + " > " + len);

                //mEncodedFrame.offer(frame);
            }
            */

            if(mMediaMuxFilter != null) {
                //TODO. remove this
                if(mDebugCnt++ % 50 == 0) {
                    YYLog.debug(this, "[audio] AudioFileMixer recv audio encode data");
                }
                mMediaMuxFilter.processMediaSample(sample, this);
            }

            return true;
        }
    }

    public MediaFormat getFormat() {
        return mMediaFormat;
    }

    public boolean isFinish() {
        return mIsFinish;
    }

    public byte[] readFrame() {
        synchronized (mFinishLock) {
            if (mIsFinish && mEncodedFrame.isEmpty()) {
                return null;
            }
        }
        try {
            return mEncodedFrame.poll(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
