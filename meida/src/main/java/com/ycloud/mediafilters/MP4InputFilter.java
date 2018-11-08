package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Looper;
import android.os.Message;

import com.ycloud.api.common.SampleType;
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

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

/**
 * Created by Kele on 2018/1/2.
 */
public class MP4InputFilter extends AbstractInputFilter implements YMRThread.Callback, MediaBufferQueue.InputCallback
{
    private String      mp4Filename;
    YMRThread            mReadThread = null;
    Context              mAndroidContext = null;
    MediaFilterContext   mediaFilterContext = null;

    MediaExtractor       mExtractor = null;
    MediaFormat          mVideoFormat = null;
    MediaFormat          mAudioFormat = null;

    int                 mVideoTrackIndex = -1;
    int                 mAudioTrackIndex = -1;
    int                 mVideoWidth = 0;
    int                 mVideoHeight = 0;
    int                 mVideoFrameCnt = 0;
    int                 mAudioFrameCnt = 0;

    boolean             mVideoEndOfStream = false;
    boolean             mAudioEndOfStream = false;

    private static final int READ_FRAME_MSG = 1;
    private static final int VIDEO_SEEKTO_MSG = 2;
    private static final int kVideoSampleStartId = 0;
    private AtomicInteger mVideoIds = new AtomicInteger(kVideoSampleStartId);
    private static final int kVideoEncodeBaseSize = 50*1024; //15K bytes for 544*960, 2.5M biterate, it is enough.
    private static final int kAudioAACBaseSzie = 1*1024; // 1k bytes for 44.1k sampe_rate, 2 channel audio

    private long mLastVideoReadPts = -1;

    private ByteBuffer   mReadBuffer = null;

    public MP4InputFilter(String fileName, MediaFilterContext filterContext) {
        mp4Filename  = fileName;
        mAndroidContext = filterContext.getAndroidContext();
        mediaFilterContext = filterContext;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean open_stream() {
        //read some frame from the mp4 file.
        YYLog.info(this, "MP4InputFilter.open_stream begin, mp4Filename:" + mp4Filename);
        if(mExtractor == null) {
            mExtractor = new MediaExtractor(MediaConst.MEDIA_EXTRACTOR_TYPE_SYSTEM);
            try {
                mExtractor.setDataSource(mp4Filename, null);
            } catch (IOException e) {
                e.printStackTrace();
                YYLog.error(this, "[exception] setDataSource: " + e.toString());
                stop();
                return false;
            }

            //select the video and audio track.
            int videoTrackIndex = mExtractor.getTrackIndex("video/");
            if(videoTrackIndex == -1) {
                YYLog.error(this, " mp4 file has no video track, filename="+mp4Filename);
                stop();
                return false;
            }
            mExtractor.selectTrack(videoTrackIndex);
            //get the video format.
            mVideoFormat = mExtractor.getTrackFormat(videoTrackIndex);

            YYMediaSample  sample = YYMediaSampleAlloc.instance().alloc();
            sample.mSampleType = SampleType.VIDEO;
            sample.mMediaFormat = mVideoFormat;
            sample.mWidth = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            sample.mHeight = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            sample.mSampleId = mVideoIds.getAndAdd(1);

            YYLog.debug(this, "MP4InputFilter.videotrack. width="+sample.mWidth + " height="+sample.mHeight);
            mVideoWidth = sample.mWidth;
            mVideoHeight = sample.mHeight;
            try {
                deliverToDownStream(sample);
            }catch (Exception e) {
                YYLog.error(this, "MP4InputFitler.videoTrack mediaforat exception: " + e.toString());
            }
            sample.decRef();

            if(mMediaSession != null) {
                mMediaSession.setInputVideoFormat(mVideoFormat);
            }

            //deliver the format information
            /*
            int audioTrackIndex = mExtractor.getTrackIndex("audio/");
            if(audioTrackIndex == -1) {
                YYLog.info(this, " mp4 file has no audio track, filename="+mp4Filename);
            } else {
                mExtractor.selectTrack(audioTrackIndex);
                mAudioFormat = mExtractor.getTrackFormat(audioTrackIndex);

                YYMediaSample  audioSample = YYMediaSampleAlloc.instance().alloc();
                audioSample.mSampleType = SampleType.AUDIO;
                audioSample.mMediaFormat = mVideoFormat;

                YYLog.debug(this, "MP4InputFitler.audiotrack selected!!");

                deliverToDownStream(audioSample);
                audioSample.decRef();

                if(mMediaSession != null) {
                mMediaSession.setInputVideoFormat(mAudioFormat);
            }
            }
            */
        }
        return true;
    }

    private void doVideoSeekTo(long timeUs) {
        mVideoOutputBufferQueue.clear();
        mVideoIds.set(kVideoSampleStartId);
        mExtractor.seekTo(timeUs, SEEK_TO_PREVIOUS_SYNC);
        YYLog.info(this, "[seek] doVideoSeekTo: " + timeUs);
        mVideoFrameCnt = 0;
        readFrame(SampleType.VIDEO);
    }

    private void readFrame(SampleType type) {
        //TODO[lsh] ByteBuffer pool, 分配大内存， 通过offset来控制.
        //mExtractor.readSampleData(ByteBuffer.allocate());

        int loop = 0; //一次读取2个帧.
        int videoIllegalexceptionCnt = 0;
        while(loop++ < 32) {
            //int currentTrackIndex = mExtractor.getSampleTrackIndex();
            if (/*currentTrackIndex == mVideoTrackIndex &&*/ !mVideoEndOfStream) {
                //TODO[lsh] 分配一块大的内存.
                if(mReadBuffer == null) {
                    mReadBuffer = ByteBuffer.allocate(mVideoWidth*mVideoHeight*4);
                }
                mReadBuffer.clear();

                try {
                    int sampleSize = mExtractor.readSampleData(mReadBuffer, 0);
                    YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
                    sample.mSampleType = SampleType.VIDEO;
                    if (sampleSize == -1) {

                        YYLog.debug(this, "MP4InputFilter video end of stream");
                        sample.mEndOfStream = true;
                        sample.mDataByteBuffer = null;
                        sample.mBufferOffset = 0;
                        sample.mBufferSize = 0;
                        sample.mSampleId = mVideoIds.getAndAdd(1);
                    } else {
                        ByteBuffer  buffer = ByteBuffer.allocate(sampleSize);
                        buffer.put(mReadBuffer.array(), 0, sampleSize);
                        buffer.flip();
                        sample.mWidth = mVideoWidth;
                        sample.mHeight = mVideoHeight;
                        sample.mMediaFormat = mVideoFormat;
                        sample.mDataByteBuffer = buffer;
                        sample.mBufferOffset = 0;
                        sample.mBufferSize = sampleSize;
                        sample.mSampleId = mVideoIds.getAndAdd(1);
                        sample.mAndoridPtsNanos = mExtractor.getSampleTime() * 1000;
                        sample.mYYPtsMillions = sample.mAndoridPtsNanos/1000000;
                        sample.mDataByteBuffer.rewind();
                    }

                    if(mVideoOutputBufferQueue != null && mVideoOutputBufferQueue.add(sample)) {

                        if(sampleSize != -1) {
                            mediaFilterContext.getMediaStats().onVideoFrameInput();
                        }
                        mVideoFrameCnt++;
//                        YYLog.info(this, "[input] MP4InputFilter get a video frame, sampleSize " + sampleSize
//                                            + " frameCnt =" +mVideoFrameCnt + " pts="+sample.mAndoridPtsNanos/(1000*1000)
//                                            + " sampleId =" + sample.mSampleId);

                        mExtractor.advance();
                        if(sampleSize == -1) {
                            StateMonitor.instance().NotifyInputEnd(MediaConst.MEDIA_TYPE_VIDEO);
                            mVideoEndOfStream = true;
                            YYLog.info(this, "[input] MP4InputFilter get a video frame, sampleSize " + sampleSize
                                            + " frameCnt =" +(mVideoFrameCnt-1) + " pts="+mLastVideoReadPts);
                        } else {
                            StateMonitor.instance().NotifyInput(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);
                            mLastVideoReadPts = sample.mAndoridPtsNanos/(1000*1000);
                        }
                        sample.decRef();
                    } else {
                        mVideoIds.decrementAndGet();
                        sample.decRef();
                        break;
                    }
                } catch (IllegalArgumentException exception) {
                    //try again, may be buffer is not enough.
                    YYLog.error(this, "[exception] readFrame video: " + exception.toString());
                    if(videoIllegalexceptionCnt++ < 2) {
                        continue;
                    } else {
                        return;
                    }
                    //may be
                } catch (Exception e) {
                    YYLog.error(this, "[exception]: video track" + e.toString());
                }
            }
            /*
            else if (currentTrackIndex == mAudioTrackIndex && !mAudioEndOfStream) {
                //TODO[lsh] 先按照600来获取数据, aac 441000, may be 600
                ByteBuffer buffer = ByteBuffer.allocate((int) (600));
                try {
                    int sampleSize = mExtractor.readSampleData(buffer, 0);
                    if (sampleSize == -1) {
                        mAudioEndOfStream = true;
                        YYLog.debug(this, "MP4InputFilter audio end of stream");
                    } else {
                        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
                        sample.mSampleType = SampleType.AUDIO;
                        sample.mMediaFormat = mAudioFormat;
                        sample.mDataByteBuffer = buffer;
                        sample.mBufferOffset = 0;
                        sample.mBufferSize = buffer.remaining();

                        YYLog.debug(this, "MP4InputFilter get a audio frame, sampleSize：" + sampleSize);

                        deliverToDownStream(sample);
                        sample.decRef();
                    }
                    mExtractor.advance();
                } catch (IllegalArgumentException exception) {
                    //try again, may be buffer is not enough.
                    YYLog.error(this, "[exception] readFrame audio: " + exception.toString());
                } catch (Exception e) {
                    YYLog.error(this, "[exception]: audio track" + e.toString());
                }
            }
            */

            if(mVideoEndOfStream && mAudioEndOfStream) {
                mReadThread.stop();
                return;
            }
        }
        //10ms. 120 fps
        //mReadThread.sendEmptyMessageDelayed(READ_FRAME_MSG, 100);
    }

    //有音视频同步吗？
    @Override
    public void start() {
        YYLog.debug(this, "MP4InputFilter.start begin");
        mReadThread = new YMRThread("ymrsdk_mp4Input");
        mReadThread.setCallback(this);
        mReadThread.start();
        YYLog.debug(this, "MP4InputFilter start end");
    }

    @Override
    public void stop() {
        if(mReadThread != null) {
            mReadThread.stop();
        }
    }

    @Override
    public void videoSeekTo(long timeUs) {
        YYLog.info(this, "[seek] Mp4InputFilter.seekVideoToBegin");
        if (mReadThread == null || mReadThread.getHandler() == null) {
            YYLog.info(this, "Mp4InputFilter.seekVideoToBegin, input thread is null");
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
    public void deInit() {
        super.deInit();
    }

    @Override
    public void setVideoOutputQueue(MediaBufferQueue queue) {
        super.setVideoOutputQueue(queue);
        if(queue != null)
            queue.setInputCallback(this);
    }

    @Override
    public void setAudioOutputQueue(MediaBufferQueue queue) {
        super.setAudioOutputQueue(queue);
    }

    @Override
    public void onStart() {
        if(open_stream()) {
            readFrame(SampleType.VIDEO);
            readFrame(SampleType.AUDIO);
        }
    }

    @Override
    public void onStop() {
        //release the extractor.
        if(mExtractor != null) {
            mExtractor.release();
        }
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
        if(msg.what == READ_FRAME_MSG) {
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
        if(mReadThread == null)
            return ;
        if (mReadThread.getHandler() == null) {
            return;
        }
        if(Looper.myLooper() == mReadThread.getHandler().getLooper()) {
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
}
