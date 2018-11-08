package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Looper;
import android.os.Message;

import com.ycloud.api.common.SampleType;
import com.ycloud.datamanager.AudioDataManager;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.datamanager.YYAudioPacket;
import com.ycloud.datamanager.YYVideoPacket;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.svplayer.MediaExtractor;
import com.ycloud.utils.YMRThread;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Administrator on 2018/1/16.
 */
public class MemInputFilter extends AbstractInputFilter implements YMRThread.Callback, MediaBufferQueue.InputCallback {
    private static final String TAG = "MemInputFilter";
    private YMRThread mReadThread = null;
    private MediaFilterContext mediaFilterContext = null;
    private MediaExtractor mVideoExtractor = null;
    private MediaExtractor mAudioExtractor = null;
    private MediaFormat mVideoFormat = null;
    private MediaFormat mAudioFormat = null;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private boolean mVideoEndOfStream = false;
    private boolean mAudioEndOfStream = false;
    private static final int READ_FRAME_MSG = 1;
    private static final int VIDEO_SEEKTO_MSG = 2;
    private static final int kVideoSampleStartId = 0;
    private static final int kVideoEncodeBaseSize = 1024 * 1024;   //512 K bytes for 544*960, 2.5M biterate, it is enough.
    private static final int kAudioAACBaseSzie = 128 * 1024;   //512 K bytes for 544*960, 2.5M biterate, it is enough.
    private ByteBuffer mVideoBuffer = null;
    private ByteBuffer mAudioBuffer = null;

    private AtomicInteger mVideoIds = new AtomicInteger(kVideoSampleStartId);

    public MemInputFilter(MediaFilterContext filterContext) {
        mediaFilterContext = filterContext;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean open_stream() {
        // Video MediaFormat
        if (mVideoExtractor == null) {
            mVideoExtractor = new MediaExtractor(MediaConst.MEDIA_EXTRACTOR_TYPE_VIDEO);
            mVideoExtractor.setUseType(MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT);
            mVideoFormat = mVideoExtractor.getTrackFormat(0);
            if (mVideoFormat == null) {
                YYLog.error(TAG,"mVideoFormat == null");
                return false;
            }
            mVideoExtractor.seekTo(0,0);
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mSampleType = SampleType.VIDEO;
            sample.mMediaFormat = mVideoFormat;
            sample.mSampleId = mVideoIds.getAndAdd(1);
            if (mVideoFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                sample.mWidth = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            }
            if (mVideoFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                sample.mHeight = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            }
            YYLog.info(this, "MemInputFilter.videotrack. width=" + sample.mWidth + " height=" + sample.mHeight);
            mVideoWidth = sample.mWidth;
            mVideoHeight = sample.mHeight;
            try {
                deliverToDownStream(sample);
            } catch (Exception e) {
                YYLog.error(this, "MemInputFilter.videoTrack mediaforat exception: " + e.toString());
            }
            sample.decRef();
            if (mMediaSession != null) {
                mMediaSession.setInputVideoFormat(mVideoFormat);
            }
            if (mVideoBuffer == null) {
                mVideoBuffer = ByteBuffer.allocate(kVideoEncodeBaseSize);
            }
        }
        // AUDIO MediaFormat
        if (mAudioExtractor == null) {
            mAudioExtractor = new MediaExtractor(MediaConst.MEDIA_EXTRACTOR_TYPE_AUDIO);
            mAudioExtractor.setUseType(MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT);
            mAudioFormat = mAudioExtractor.getTrackFormat(0);
            if (mAudioFormat == null) {
                YYLog.warn(TAG,"mAudioFormat == null, Have BackGround Music or Video only mode.");
            } else {
                mAudioExtractor.seekTo(0, 0);
                YYMediaSample audioSample = YYMediaSampleAlloc.instance().alloc();
                audioSample.mSampleType = SampleType.AUDIO;
                audioSample.mMediaFormat = mVideoFormat;
                deliverToDownStream(audioSample);
                audioSample.decRef();
                if (mMediaSession != null) {
                    mMediaSession.setInputAudioFormat(mAudioFormat);
                }
                if (mAudioBuffer == null) {
                    mAudioBuffer = ByteBuffer.allocate(kAudioAACBaseSzie);
                }
            }
        }

        StateMonitor.instance().NotifyInputStart(MediaConst.MEDIA_TYPE_VIDEO);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void doVideoSeekTo(long timeUs) {
        mVideoOutputBufferQueue.clear();

        if(mVideoExtractor != null) {
            mVideoIds.set(kVideoSampleStartId);
            mVideoExtractor.seekTo(timeUs,0);
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mSampleType = SampleType.VIDEO;
            sample.mMediaFormat = mVideoFormat;
            sample.mSampleId = mVideoIds.getAndAdd(1);
            if (mVideoFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                sample.mWidth = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            }
            if (mVideoFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                sample.mHeight = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            }
            YYLog.info(this, "MemInputFilter.videotrack. width=" + sample.mWidth + " height=" + sample.mHeight);
            mVideoWidth = sample.mWidth;
            mVideoHeight = sample.mHeight;
            try {
                deliverToDownStream(sample);
            } catch (Exception e) {
                YYLog.error(this, "MemInputFilter.videoTrack mediaforat exception: " + e.toString());
            }
            sample.decRef();
        }

        readFrameInternel(SampleType.VIDEO);
    }

    private void readFrameInternel(SampleType type) {
        MediaExtractor extractor;
        ByteBuffer dataBuffer;
        MediaFormat mediaFormat;
        MediaBufferQueue bufferQueue;

        if (type == SampleType.VIDEO) {
            extractor = mVideoExtractor;
            mediaFormat = mVideoFormat;
            dataBuffer = mVideoBuffer;
            bufferQueue = mVideoOutputBufferQueue;
        } else {
            extractor = mAudioExtractor;
            mediaFormat = mAudioFormat;
            dataBuffer = mAudioBuffer;
            bufferQueue = mAudioOutputBufferQueue;
        }

        if (extractor == null || dataBuffer == null) {
            return;
        }

        int sampleSize = extractor.readSampleData(dataBuffer, 0);
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();

        sample.mSampleType = type;
        if (sampleSize == -1) {
            YYLog.info(this, "MemInputFilter end of stream, type " + type);
            StateMonitor.instance().NotifyInputEnd(MediaConst.MEDIA_TYPE_VIDEO);
            sample.mEndOfStream = true;
            sample.mDataByteBuffer = null;
            sample.mBufferOffset = 0;
            sample.mBufferSize = 0;
            if (type == SampleType.VIDEO) {
                mVideoEndOfStream = true;
            } else {
                mAudioEndOfStream = true;
            }
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(sampleSize);
            buffer.clear();
            dataBuffer.rewind();
            dataBuffer.get(buffer.array(), 0, sampleSize);
            if (type == SampleType.VIDEO) {
                sample.mWidth = mVideoWidth;
                sample.mHeight = mVideoHeight;
                sample.mSampleId = mVideoIds.getAndAdd(1);
            }

            //check the key frame type.
            if(type == SampleType.VIDEO) {
                int flag = extractor.getSampleFlags();
                if((flag & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    sample.mFrameType = VideoConstant.VideoFrameType.kVideoIFrame;
                }
            }
            sample.mMediaFormat = mediaFormat;
            sample.mDataByteBuffer = buffer;
            sample.mBufferOffset = 0;
            sample.mBufferSize = sampleSize;
            sample.mAndoridPtsNanos = extractor.getSampleTime() * 1000;
            sample.mYYPtsMillions = sample.mAndoridPtsNanos / 1000000;
            sample.mDataByteBuffer.rewind();

        }

        if (bufferQueue != null && bufferQueue.add(sample)) {
            if (sampleSize != -1) {
                mediaFilterContext.getMediaStats().onVideoFrameInput();
            }

            sample.decRef();
            extractor.advance();
            StateMonitor.instance().NotifyInput(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);

        } else {
            sample.decRef();
        }
    }

    private void readFrame(SampleType type) {
        if (type == SampleType.VIDEO && !mVideoEndOfStream) {
            readFrameInternel(SampleType.VIDEO);
        } else if (type == SampleType.AUDIO && !mAudioEndOfStream) {
            readFrameInternel(SampleType.AUDIO);
        }
    }

    //有音视频同步吗？
    @Override
    public void start() {
        YYLog.info(this, "MemInputFilter.start begin");
        mReadThread = new YMRThread("ymrsdk_memInput");
        mReadThread.setCallback(this);
        mReadThread.start();
        YYLog.info(this, "MemInputFilter start end");
    }

    @Override
    public void videoSeekTo(long timeUs) {
        YYLog.info(this, "MemInputFilter.seekVideoToBegin");
        if (mReadThread == null || mReadThread.getHandler() == null) {
            YYLog.info(this, "MemInputFilter.seekVideoToBegin, input thread is null");
            return;
        }

        if (Looper.myLooper() == mReadThread.getHandler().getLooper()) {
            //same thread.
            doVideoSeekTo(timeUs);
        } else {
            Message msg = new Message();
            msg.what = VIDEO_SEEKTO_MSG;
            msg.obj = Long.valueOf(timeUs);
            mReadThread.sendMessage(msg);
        }
    }

    @Override
    public void stop() {
        if (mReadThread != null) {
            mReadThread.stop();
        }
    }

    @Override
    public void deInit() {
        super.deInit();
    }

    @Override
    public void setVideoOutputQueue(MediaBufferQueue queue) {
        super.setVideoOutputQueue(queue);
        if (queue != null)
            queue.setInputCallback(this);
    }

    @Override
    public void setAudioOutputQueue(MediaBufferQueue queue) {
        super.setAudioOutputQueue(queue);
    }

    @Override
    public void onStart() {
        if (open_stream()) {
            readFrame(SampleType.VIDEO);
            readFrame(SampleType.AUDIO);
        } else {
            YYLog.error(TAG, "open_stream failed!");
        }
    }

    @Override
    public void onStop() {
        //release the extractor.
        if (mVideoExtractor != null) {
            mVideoExtractor.release();
        }
        if (mAudioExtractor != null) {
            mAudioExtractor.release();
        }
        mVideoBuffer = null;
        mAudioBuffer = null;
    }

    @Override
    public void onPause() {
        //do nothing.
    }

    @Override
    public void onResume() {
        // do nothing
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == READ_FRAME_MSG) {
            if (msg.arg1 == 0) {
                readFrame(SampleType.VIDEO);
            } else if (msg.arg1 == 1) {
                readFrame(SampleType.AUDIO);
            }
        } else if(msg.what == VIDEO_SEEKTO_MSG) {
            doVideoSeekTo((Long)msg.obj);
        }
    }

    @Override
    public void getMediaSample(SampleType type) {
        if (mReadThread == null || mReadThread.getHandler() == null)
            return;

        if (Looper.myLooper() == mReadThread.getHandler().getLooper()) {
            //same thread.
            readFrame(type);
        } else {
            Message msg = new Message();
            msg.what = READ_FRAME_MSG;
            if (type == SampleType.VIDEO) {
                msg.arg1 = 0;
            } else if (type == SampleType.AUDIO) {
                msg.arg1 = 1;
            }
            mReadThread.sendMessage(msg);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int exportAVFromMemToMp4(String path) {
        boolean bAudioEnable = false;
        boolean bVideoEnable = false;
        boolean bAudioFinish = false;
        boolean bVideoFinish = false;
        int audioTrackIndex = -1;
        int videoTrackIndex = -1;
        MediaCodec.BufferInfo audioBufferInfo = null;
        MediaCodec.BufferInfo videoBufferInfo = null;
        YYLog.info(TAG, "start exportAVFromMemToMp4 path " + path);
        try {
            long time = System.currentTimeMillis();
            MediaMuxer muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaFormat audioFormat = AudioDataManager.instance().getAudioMediaFormat();
            if (audioFormat != null) {
                bAudioEnable = true;
                audioTrackIndex = muxer.addTrack(audioFormat);
                audioBufferInfo = new MediaCodec.BufferInfo();
                AudioDataManager.instance().seekToForExport(0,0);
            } else {
                YYLog.warn(TAG, "audioFormat == null");
                bAudioFinish = true;
            }

            MediaFormat videoFormat = VideoDataManager.instance().getVideoMediaFormat();
            if (videoFormat != null) {
                bVideoEnable = true;
                videoTrackIndex = muxer.addTrack(videoFormat);
                videoBufferInfo = new MediaCodec.BufferInfo();
                VideoDataManager.instance().seekToForExport(0,0);
            } else {
                YYLog.warn(TAG, "videoFormat == null");
                bVideoFinish = true;
            }

            if (!bVideoEnable && !bAudioEnable) {
                YYLog.error(TAG, " bVideoEnable " + bVideoEnable+ " bAudioEnable " + bAudioEnable);
                return -1;
            }

            try {
                muxer.start();
            } catch (IllegalStateException e) {
                YYLog.error(TAG,"MediaMuxer start failed,"+e.getMessage());
            }

            while (true) {
                if (bVideoEnable && !bVideoFinish) {
                    if (writeVideoToMp4(muxer, videoBufferInfo, videoTrackIndex) < 0) {
                        bVideoFinish = true;
                    }
                }
                if (bAudioEnable && !bAudioFinish) {
                    if (writeAudioToMp4(muxer, audioBufferInfo, audioTrackIndex) < 0) {
                        bAudioFinish = true;
                    }
                }
                if (bVideoFinish && bAudioFinish) {
                    break;
                }
            }

            try {
                muxer.stop();
                muxer.release();
            } catch (IllegalStateException e) {
                YYLog.error(TAG,"MediaMuxer stop failed,"+e.getMessage());
            }

            YYLog.info(TAG, "exportToMp4 cost " + (System.currentTimeMillis() - time));
            return 0;
        } catch (IOException e) {
            YYLog.error(TAG, "IOException : " + e.getMessage());
        }
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int writeAudioToMp4(MediaMuxer muxer, MediaCodec.BufferInfo audioBufferInfo, int audioTrackIndex) {
        YYAudioPacket packet = AudioDataManager.instance().readSampleDataForExport();
        if (null != packet) {
            packet.mDataByteBuffer.rewind();
            //YYLog.error(TAG, "write to Mp4 size : " + packet.mBufferSize + " offset " + packet.mBufferOffset + " pts " + packet.pts );
            ByteBuffer inputBuffer = ByteBuffer.allocate(packet.mBufferSize);
            inputBuffer.clear();
            inputBuffer.put(packet.mDataByteBuffer.array(), packet.mBufferOffset, packet.mBufferSize);
            inputBuffer.rewind();
            packet.mDataByteBuffer.rewind();
            audioBufferInfo.flags = packet.mBufferFlag;
            audioBufferInfo.offset = packet.mBufferOffset;
            audioBufferInfo.presentationTimeUs = packet.pts;
            audioBufferInfo.size = packet.mBufferSize;
            try {
                muxer.writeSampleData(audioTrackIndex, inputBuffer, audioBufferInfo);
            }catch (IllegalStateException e) {
                YYLog.error(TAG, "IllegalStateException " + e.toString() + " " + e.getMessage());
            } catch (IllegalArgumentException e) {
                YYLog.error(TAG, "IllegalArgumentException " + e.toString() + " " + e.getMessage());
            }
            AudioDataManager.instance().advanceForExport();
            return 0;
        } else {
            return -1;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int writeVideoToMp4(MediaMuxer muxer, MediaCodec.BufferInfo videoBufferInfo, int videoTrackIndex) {
        YYVideoPacket packet = VideoDataManager.instance().readSampleDataForExport();
        if (null != packet) {
            packet.mDataByteBuffer.rewind();
            //YYLog.error(TAG, "write to Mp4 size : " + packet.mBufferSize + " offset " + packet.mBufferOffset + " pts " + packet.pts );
            ByteBuffer inputBuffer = ByteBuffer.allocate(packet.mBufferSize);
            inputBuffer.clear();
            inputBuffer.put(packet.mDataByteBuffer.array(), packet.mBufferOffset, packet.mBufferSize);
            inputBuffer.rewind();
            packet.mDataByteBuffer.rewind();
            videoBufferInfo.flags = packet.mBufferFlag;
            videoBufferInfo.offset = packet.mBufferOffset;
            videoBufferInfo.presentationTimeUs = packet.pts;
            videoBufferInfo.size = packet.mBufferSize;
            try {
                if (packet.mFrameType != VideoConstant.VideoFrameType.kVideoBFrame) {
                    muxer.writeSampleData(videoTrackIndex, inputBuffer, videoBufferInfo);
                }
            } catch (IllegalStateException e) {
                YYLog.error(TAG, "IllegalStateException " + e.toString() + " " + e.getMessage());
            } catch (IllegalArgumentException e) {
                YYLog.error(TAG, "IllegalArgumentException " + e.toString() + " " + e.getMessage());
            }
            VideoDataManager.instance().advanceForExport();
            return 0;
        } else {
            return -1;
        }
    }






}
