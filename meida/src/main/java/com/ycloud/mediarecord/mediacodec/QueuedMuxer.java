/*
 * Copyright (C) 2015 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ycloud.mediarecord.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.api.common.SampleType;
import com.ycloud.api.videorecord.IVideoRecordListener;
import com.ycloud.mediacodec.IMediaMuxer;
import com.ycloud.mediarecord.audio.AudioRecordConstant;
import com.ycloud.utils.YYLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class queues until all output track formats are determined.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class QueuedMuxer {
    private static final String TAG = QueuedMuxer.class.getSimpleName();
    //TODO: I have no idea whether this value is appropriate or not...,1.5M
    private static final int BUFFER_SIZE = 1536 * 1024;
    private IMediaMuxer mMediaMuxer;
    private Listener mListener;
    private MediaFormat mVideoFormat;
    private MediaFormat mAudioFormat;
    private int mVideoTrackIndex;
    private int mAudioTrackIndex;
    private ByteBuffer mByteBuffer;
    private final List<SampleInfo> mSampleInfoList;
    /*是否启用音频录制*/
    private boolean mEnableAudio = true;
    private boolean mVideoFinished = false;
    boolean mAudioFinished = false;
    private AtomicBoolean mIsStarted;

    private IVideoRecordListener mRecordListener;

    private boolean mIsAddVideoTrackFlag = false;
    private boolean mNeedAddAudioTrackFlag = false;
    private long mAudioFrameCount;
    private long mVideoFrameCount;

    private long mLastAudioPts = -1;
    private long mLastVideoPts = -1;

    private boolean mVideoAudioSync = true; //音视频是否需要对齐，录制时候需要对齐，导出的时候已经是对齐的了，无需再做.
    private boolean mSingleStreamStopTriggerMode = true; //单个流停止即可启动muxer停止.

    private int mLogCnt = 0;

    private static final long AUDIO_FRAME_DURATION_US = AudioRecordConstant.SAMPLES_PER_FRAME*1000*1000/AudioRecordConstant.SAMPLE_RATE;
    private static final long MAX_SILENT_AUDIO_FRAME_COUNT = 5000000 / AUDIO_FRAME_DURATION_US; // 5s

    private static class SampleInfo {
        private final SampleType mSampleType;
        private final int mSize;
        private final long mPresentationTimeUs;
        private final long mDtsMs;
        private final int mFlags;


        private SampleInfo(SampleType sampleType, int size, MediaCodec.BufferInfo bufferInfo, long dtsMs) {
            mSampleType = sampleType;
            mSize = size;
            mPresentationTimeUs = bufferInfo.presentationTimeUs;
            mFlags = bufferInfo.flags;
            mDtsMs = dtsMs;
        }

        private void writeToBufferInfo(MediaCodec.BufferInfo bufferInfo, int offset) {
            bufferInfo.set(offset, mSize, mPresentationTimeUs, mFlags);
        }
    }
    public QueuedMuxer(IMediaMuxer muxer, Listener listener,boolean enable_audio) {
        mMediaMuxer = muxer;
        mListener = listener;
        mSampleInfoList = new ArrayList<SampleInfo>();

        mIsStarted = new AtomicBoolean(false);
        mEnableAudio=enable_audio;
        mAudioFrameCount = 0;
        mVideoFrameCount =0;
    }
    public void setEnableAudioRecord(boolean enableAudio){
        YYLog.info(this, "setEnableAudioRecord enableAudio:"+enableAudio);
        mEnableAudio=enableAudio;

        if(mEnableAudio == false) {
            //尝试启动mediaStart.
            startMediaMuxer();
        }
    }

    public boolean getAudioEnable() {
        return mEnableAudio;
    }

    public void setVideoAudioSync(boolean enable) {
        mVideoAudioSync = enable;
    }

    public void setSingleStreamOfEndMode(boolean enable) {
        mSingleStreamStopTriggerMode = enable;
    }

    private synchronized void startMediaMuxer() {
        if (checkEndOfStream() || mIsStarted.get()) {
            YYLog.error(TAG, "startMediaMuxer error because stream reach end!");
            return;
        }

        if (mEnableAudio) {
            if (mVideoFormat == null || mAudioFormat == null) {
                YYLog.info(this, "setOutputFormat fail, VideoFormat:" + mVideoFormat + ",AudioFormat:" + mAudioFormat);
                return;
            }
        } else {
            mAudioFinished = true;
            if (mVideoFormat == null) {
                YYLog.info(this, "setOutputFormat fail, VideoFormat:null");
                return;
            }
        }

        mListener.onDetermineOutputFormat(mVideoFormat, mAudioFormat);
        YYLog.info(TAG, "determine Output format success!!");

        try {
            mMediaMuxer.start();
        } catch (Throwable t) {
            YYLog.error(TAG, "[muxer] MediaMuxer start exception" + t.toString());
        }
        mIsStarted.set(true);

        YYLog.info(TAG, "[muxer] MediaMuxer start success!!");

        if (mByteBuffer == null) {
            mByteBuffer = ByteBuffer.allocate(0);
        }
        mByteBuffer.flip();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int offset = 0;
        for (SampleInfo sampleInfo : mSampleInfoList) {
            sampleInfo.writeToBufferInfo(bufferInfo, offset);
            int trackIndex = getTrackIndexForSampleType(sampleInfo.mSampleType);
            mMediaMuxer.writeSampleData(trackIndex, mByteBuffer, bufferInfo, sampleInfo.mDtsMs);
            offset += sampleInfo.mSize;
        }
        mSampleInfoList.clear();
        mByteBuffer = null;
    }

    /*设置实际编码采用的格式*/
    public synchronized void setOutputFormat(SampleType sampleType, MediaFormat format) {
        if (checkEndOfStream() || mIsStarted.get()) {
            YYLog.error(TAG, "setOutputFormat error because stream reach end!");
            return;
        }

        switch (sampleType) {
            case VIDEO:
                mVideoFormat = format;
                YYLog.info(TAG, "add video track");
                mVideoTrackIndex = addTrack(mVideoFormat);
                mIsAddVideoTrackFlag = true;

                //如果audio sample先到,等到video设置后再设置audio
                if (mNeedAddAudioTrackFlag) {
                    YYLog.info(TAG, "add audio track after video track added");
                    mAudioTrackIndex = addTrack(mAudioFormat);
                    mNeedAddAudioTrackFlag = false;
                }
                break;
            case AUDIO:
                mAudioFormat = format;
                if (mIsAddVideoTrackFlag) {
                    YYLog.info(TAG, "add audio track");
                    mAudioTrackIndex = addTrack(mAudioFormat);
                } else {
                    //如果video track还没有添加，先记录下来audio track的信息，等video track添加后再添加
                    YYLog.info(TAG, "add audio track will be delay for video track has not been added");
                    mNeedAddAudioTrackFlag = true;
                    return;
                }
                break;
            default:
                throw new AssertionError();
        }

        startMediaMuxer();
        YYLog.info(TAG, "[muxer] setOutputFormat, end!!");
    }

    public int addTrack(final MediaFormat format) {
        if (mIsStarted.get()) {
            YYLog.error(TAG, "muxer already started");
        }

        try {
            final int trackIx = mMediaMuxer.addTrack(format);
            return trackIx;
        } catch (Throwable t) {
            YYLog.error(TAG, "[muxer] add track exception: " + t.toString());
            return -1;
        }
    }


    public synchronized void writeSampleData(SampleType sampleType, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, long dtsMs) {
        if(sampleType.equals(SampleType.AUDIO)){
            mAudioFrameCount++;
            mLastAudioPts = bufferInfo.presentationTimeUs;
        }else if(sampleType.equals(SampleType.VIDEO)){
            mVideoFrameCount++;
            mLastVideoPts = bufferInfo.presentationTimeUs;
        } else {
            return;
        }

        try {
            if (mIsStarted.get()) {
                int trackIndex=getTrackIndexForSampleType(sampleType);
                mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo, dtsMs);
            } else {
                //copy the buffer info.
                byteBuf.limit(bufferInfo.offset + bufferInfo.size);
                byteBuf.position(bufferInfo.offset);
                if (mByteBuffer == null) {
                    mByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
                }

                int capacity = mByteBuffer.capacity()-mByteBuffer.position();
                if(bufferInfo.size > capacity) {
                    YYLog.error(this, "write sample data to queue before muxer start!!!, but cache is not enough"
                                                + ", sampleSize: " + bufferInfo.size
                                                + ", cache_capacity: " + capacity);
                }

                mByteBuffer.put(byteBuf);
                /*在这里加入数据*/
                mSampleInfoList.add(new SampleInfo(sampleType, bufferInfo.size, bufferInfo, dtsMs));
                if(mLogCnt++ %30 == 0) {
                    YYLog.info(TAG, "write sample data to queue before muxer start!!!sample type:"
                                                + sampleType + ",size:" + bufferInfo.size
                                                + " totalCacheSize:" + mByteBuffer.position());
                }
            }

            if (mRecordListener != null) {
                if (sampleType.equals(SampleType.VIDEO)) {
                    mRecordListener.onProgress(bufferInfo.presentationTimeUs * 1f / 1000000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getTrackIndexForSampleType(SampleType sampleType) {
        switch (sampleType) {
            case VIDEO:
                return mVideoTrackIndex;
            case AUDIO:
                return mAudioTrackIndex;
            default:
                throw new AssertionError();
        }
    }

    public boolean isRecording(){
        return mIsStarted.get();
    }

    public void setRecordListener(IVideoRecordListener recordProgressListener) {
        mRecordListener = recordProgressListener;
    }

    public interface Listener {
        void onDetermineOutputFormat(MediaFormat videoMediaFormat, MediaFormat audioMediaFormat);
    }

    private boolean checkEndOfStream() {
        if(mSingleStreamStopTriggerMode) {
            return (mVideoFinished || (mAudioFinished && mEnableAudio));
        } else {
            return (mVideoFinished && mAudioFinished);
        }
    }

    public synchronized boolean stop(SampleType type) {
        switch (type) {
            case VIDEO:
                mVideoFinished = true;
                break;
            case AUDIO:
                mAudioFinished = true;
                break;
            default:
                break;
        }

        if (checkEndOfStream() && mIsStarted.get()) {
            YYLog.info(TAG, "[muxer] QueuedMuxer stop begin");
            try {
                if (mEnableAudio && mAudioFrameCount == 0) {
                    ByteBuffer bufferx = ByteBuffer.allocate(185);
                    YYLog.info(TAG, "insert slient audio frame");
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int trackIndex = getTrackIndexForSampleType(SampleType.AUDIO);
                    bufferInfo.set(0, 185, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    mMediaMuxer.writeSampleData(trackIndex, bufferx, bufferInfo, bufferInfo.presentationTimeUs/1000);
                }

                if (mEnableAudio) {
                    if (mLastAudioPts > 0 && mLastVideoPts > 0) {
                        long diffPts = mLastVideoPts - mLastAudioPts;
                        if (diffPts > AUDIO_FRAME_DURATION_US  && mVideoAudioSync) {
                            long audioFrameNum = diffPts / AUDIO_FRAME_DURATION_US;
                            audioFrameNum = MAX_SILENT_AUDIO_FRAME_COUNT > audioFrameNum ? audioFrameNum : MAX_SILENT_AUDIO_FRAME_COUNT;
                            YYLog.info(TAG, "diffPts:" + diffPts + ",lastVideoPts:" + mLastVideoPts + ",lastAudioPts:" + mLastAudioPts);
                            for (int i = 0; i < audioFrameNum; i++) {
                                ByteBuffer bufferx = ByteBuffer.allocate(185);
                                YYLog.info(TAG, "insert slient audio frame");
                                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                                int trackIndex = getTrackIndexForSampleType(SampleType.AUDIO);
                                bufferInfo.set(0, 185, mLastAudioPts + (i + 1) * AUDIO_FRAME_DURATION_US, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                                mMediaMuxer.writeSampleData(trackIndex, bufferx, bufferInfo, bufferInfo.presentationTimeUs / 1000);
                            }
                        }
                    }
                }
            }catch (Exception e){
                YYLog.error(TAG,"insert slient audio frame error:" + e.getMessage());
            }

            boolean isStopOK = true;
            try {
                //if not in started state, MediaMuxer throw exceiton, just catch it
                mMediaMuxer.stop();
            } catch (IllegalStateException e) {
                YYLog.error(TAG,"MediaMuxer stop failed,"+e.getMessage());
                isStopOK =false;
                if (mRecordListener != null) {
                    mRecordListener.onStop(false);
                }
            }

            YYLog.info(TAG, "MediaMuxer stop audioFrameCount:" + mAudioFrameCount + ",videoFrameCount" + mVideoFrameCount);

            if(isStopOK){
                YYLog.info(TAG,"MediaMuxer stop OK");
            }

            mIsStarted.set(false);
            try {
                mMediaMuxer.release();
            } catch (Exception e){
                e.printStackTrace();
            }

            mMediaMuxer = null;

            mListener = null;

            if (isStopOK && mRecordListener != null) {
                mRecordListener.onStop(true);
            }
            mRecordListener = null;

            YYLog.info(TAG, "QueuedMuxer stop end");
            return true;
        }

        return false;
    }
}
