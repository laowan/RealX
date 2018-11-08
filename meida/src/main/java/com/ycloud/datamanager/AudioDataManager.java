package com.ycloud.datamanager;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.SparseArray;

import com.ycloud.mediarecord.audio.AudioRecordWrapper;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.abs;

/**
 * Created by Administrator on 2018/1/2.
 *
 */

public class AudioDataManager {

    private static final String TAG = "AudioDataManager";
    private static AudioDataManager mInstance = null;
    private static final byte[] SYNC_FLAG = new byte[1];
    private long mStartPTS = 0;                                     // global start pts of all the Audio segments
    private long mEndPTS = 0;                                       // global end pts of all the Audio segments
    private int mCurWriteSegIndex = 0;                              // global write index for Audio segments
    private int mCurReadSegIndex = 0;                               // global read index for Audio segments
    private int mCurReadSegIndexForExport = 0;                      // global read index for Export of Audio segments
    private int mWriteIndex = 0;                                    // global write index in each Audio segment, start from 0
    private int mReadIndex = 0;                                     // global read index in each Audio segment,start from 0
    private int mReadindexForExport = 0;
    private int mTotalFrameCnt = 0;
    private int mTotalSegmentCnt = 0;
    private long mDuration = 0;
    private long mTimePointToDelete = -1;
    private int mAudioIndexToDelete = -1;
    private int mAudioSegmentIndexToDelete = -1;
    private AudioSegment mToDeleteSegment = null;
    private AudioSegment mCurWriteSegment = null;
    private AudioSegment mLastWriteSegment = null;
    private AudioSegment mCurReadSegment = null;
    private AudioSegment mCurReadSegmentForExport = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);

    private class AudioSegment {
        int mStartIndex;                                             // start index of a Audio segment
        int mEndIndex;                                               // end index of a Audio segment
        int mReadIndex;
        int mSegmentIndex;
        long mStartPts;
        long mEndPts;
        SparseArray<YYAudioPacket> mAudioDataMap;
    }

    private MediaFormat mAudioMediaFormat = null;
    private LinkedList<AudioSegment> mAudioSegmentList = null;

    public static AudioDataManager instance() {
        if (mInstance == null) {
            synchronized (SYNC_FLAG) {
                if (mInstance == null) {
                    mInstance = new AudioDataManager();
                }
            }
        }
        return mInstance;
    }

    private AudioDataManager() {
        mAudioSegmentList = new LinkedList<>();
    }

    public void startRecord() {
        AudioSegment segment = new AudioSegment();
        segment.mAudioDataMap = new SparseArray<>();
        segment.mStartIndex = 0;
        segment.mEndIndex = 0;
        segment.mReadIndex = 0;
        segment.mStartPts = -1;
        segment.mEndPts = -1;
        segment.mSegmentIndex = mCurWriteSegIndex;
        mCurWriteSegment = segment;
        mWriteIndex = 0;
        mInited.set(true);
        YYLog.info(TAG, "Audio segment [" + mCurWriteSegIndex + "] record start.");
    }

    public long calculateTotalDuration() {
        long duration = 0;
        AudioSegment first = mAudioSegmentList.peekFirst();
        AudioSegment last = mAudioSegmentList.peekLast();
        if (first != null && last != null) {
            duration = last.mEndPts - first.mStartPts;
        }
        return duration;
    }

    public long getDuration() {
        return mDuration;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopRecord() {
        if (mCurWriteSegment == null) {
            return;
        }
        if (mWriteIndex == 0) {  // started, but no frame write
            mCurWriteSegment = null;
            YYLog.info(TAG, "Audio segment [" + mCurWriteSegIndex + "] record stop with no frame write, drop it.");
            return;
        }
        SparseArray<YYAudioPacket> AudioDataMap = mCurWriteSegment.mAudioDataMap;
        YYAudioPacket packet = AudioDataMap.get(mCurWriteSegment.mStartIndex);
        if (packet != null) {
            mCurWriteSegment.mStartPts = packet.pts;
        }

        mCurWriteSegment.mEndIndex = mWriteIndex - 1;
        packet = AudioDataMap.get(mCurWriteSegment.mEndIndex);
        if (packet != null) {
            mCurWriteSegment.mEndPts = packet.pts;
            YYLog.info(TAG, "mCurWriteSegment [" + mCurWriteSegIndex + "] end  pts " + packet.pts);
        }
        mAudioSegmentList.add(mCurWriteSegment);

        if (mAudioMediaFormat != null) {
            mDuration = calculateTotalDuration();
            mAudioMediaFormat.setLong(MediaFormat.KEY_DURATION, mDuration);
            YYLog.info(TAG, "Audio segment [" + mCurWriteSegIndex + "] end index " + mCurWriteSegment.mEndIndex +
                    " duration " + (mCurWriteSegment.mEndPts - mCurWriteSegment.mStartPts) + " Total duration " + mDuration + " frame count " + mTotalFrameCnt);
        }
        mLastWriteSegment = mCurWriteSegment;
        mCurWriteSegIndex++;
        mCurWriteSegment = null;         // prevent write data after stop record.
        mTotalSegmentCnt = mCurWriteSegIndex;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void writeMediaFormat(MediaFormat format) {
        if (format == null) {
            YYLog.error(TAG, "writeMediaFormat error ! format == null");
            return ;
        }

        if(mAudioMediaFormat == null) {
            int sample_rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            mAudioMediaFormat = MediaUtils.createAacFormat(sample_rate, channels);
        }
    }

    public int write(YYAudioPacket packet) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return -1;
        }
        if (mCurWriteSegment == null) {
            return -1;
        }

        //long origPts = packet.pts;
        if (packet.mBufferSize > 0) {
            // concat the pts of the last Audio segment.
            if (null != mLastWriteSegment && mLastWriteSegment.mEndPts != -1) {
                packet.pts += mLastWriteSegment.mEndPts + AudioRecordWrapper.US_PER_FRAME;
            }

            if (mCurWriteSegment.mAudioDataMap != null) {
                mCurWriteSegment.mAudioDataMap.put(mWriteIndex, packet);
                mTotalFrameCnt++;
            }

            // update pts of all Audio segments
            if (mStartPTS == 0) {
                mStartPTS = packet.pts;
            }
            mEndPTS = packet.pts;

            // update pts of each Audio segment
            if (mCurWriteSegment.mStartPts == -1) {
                mCurWriteSegment.mStartPts = packet.pts;
                YYLog.info(TAG, "mCurWriteSegment.mStartPts " + packet.pts);
            }
            mCurWriteSegment.mEndPts = packet.pts;
            mCurWriteSegment.mEndIndex = mWriteIndex;
//            YYLog.info(TAG, "Write Seg " + mCurWriteSegIndex +" index " + mWriteIndex + " len " +
//                    packet.mBufferSize + " pts " + packet.pts + " ori pts " + origPts);
            mWriteIndex++;

            //Calculate total duration
            mDuration = mEndPTS - mStartPTS;
            AudioSegment segFirst = mAudioSegmentList.peekFirst();
            if (segFirst != null) {
                mDuration = mEndPTS - segFirst.mStartPts;
            }
        }
        return mWriteIndex - 1;
    }

    private AudioSegment findAudioSegmentByPts(long pts) {
        for (AudioSegment seg : mAudioSegmentList) {
            if (pts >= seg.mStartPts && pts <= seg.mEndPts) {
                return seg;
            }
        }
        return null;
    }

    private int findAudioPacketByPts(AudioSegment segment, long pts) {
        if (segment == null || segment.mAudioDataMap == null) {
            return -1;
        }
        int l = 0, r = segment.mEndIndex;
        while (l <= r) {
            int index = l + (r - l) / 2;
            YYAudioPacket packet = segment.mAudioDataMap.get(index);
            if (packet.pts == pts) {
                return index;
            }
            if (packet.pts > pts) {
                r = index - 1;
            } else if (packet.pts < pts) {
                l = index + 1;
            }
        }

        long labs = abs(segment.mAudioDataMap.get(l).pts - pts);
        long rabs = abs(segment.mAudioDataMap.get(r).pts - pts);
        //YYLog.info(TAG, "jtzhu findAudioPacketByPts l " + l + " pts " + mAudioDataMap.get(l).pts + " r " + r + " pts " + mAudioDataMap.get(r).pts + " target " + pts + " result " + result);
        return labs > rabs ? r : l;
    }

//    private YYAudioPacket readByPts(long pts) {
//        if (pts > mEndPTS || pts < mStartPTS) {
//            YYLog.error(TAG, " read error ! pts " + pts + " start pts " + mStartPTS + " end pts " + mEndPTS);
//            return null;
//        }
//        AudioSegment seg = findAudioSegmentByPts(pts);
//        if (seg == null) {
//            return null;
//        }
//        int index = findAudioPacketByPts(seg, pts);
//        return seg.mAudioDataMap.get(index);
//    }

    private YYAudioPacket readByIndex(int index) {

        if (mCurReadSegment == null) {
            for (AudioSegment seg : mAudioSegmentList) {
                if (seg.mSegmentIndex == mCurReadSegIndex) {
                    mCurReadSegment = seg;
                    break;
                }
            }
        }
        if (mCurReadSegment == null) {
            return null;
        }
        if (index < 0 || index > mCurReadSegment.mEndIndex || mCurReadSegment.mAudioDataMap == null) {
            YYLog.warn(TAG, " end of Audio segment [" +mCurReadSegIndex + "] index " + index +
                    " start index " + 0 + " end index " + mCurReadSegment.mEndIndex);
            return null;
        }

        if (mAudioSegmentIndexToDelete != -1 && mAudioIndexToDelete != -1) {
            if (mCurReadSegIndex == mAudioSegmentIndexToDelete && index >= mAudioIndexToDelete) {
                YYLog.warn(TAG, " end of Audio segment [" +mCurReadSegIndex + "] index " + index +
                        " mAudioSegmentIndexToDelete " + mAudioSegmentIndexToDelete + " mAudioIndexToDelete " + mAudioIndexToDelete);
                return null;
            }
        }
        return mCurReadSegment.mAudioDataMap.get(index);
    }

    public MediaFormat getAudioMediaFormat() {
        return mAudioMediaFormat;
    }

    public void seekTo(long timeUs, int mode) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return ;
        }

        long original = timeUs;
        if (timeUs < mStartPTS) {
            timeUs = mStartPTS;
        } else if (timeUs > mEndPTS) {
            timeUs = mEndPTS;
        }

        AudioSegment segment = findAudioSegmentByPts(timeUs);
        if (segment != null) {
            mCurReadSegment = segment;
            mCurReadSegIndex = segment.mSegmentIndex;
            mReadIndex = findAudioPacketByPts(mCurReadSegment, timeUs);
            YYLog.info(TAG, " seekto " + original + "segment index " + mCurReadSegIndex + " mReadIndex " + mReadIndex +
                    " mStartPTS " + mStartPTS + " mEndPTS " + mEndPTS + " mode " + mode );
        } else {
            YYLog.error(TAG, "seekTo " + timeUs + " error! ");
        }
    }

    // global index from segment 0 to segments n
    public void seekTo(int index) {
        if (index < 0) {
            mReadIndex = 0;
        } else if (index > mTotalFrameCnt) {
            index = mTotalFrameCnt;
        }

        int i = 0;
        AudioSegment segment = null;
        for (AudioSegment seg : mAudioSegmentList) {
            i += (seg.mEndIndex - seg.mStartIndex + 1);
            if (i > index) {
                segment = seg;
                break;
            }
        }
        if (segment == null) {
            return;
        }
        mCurReadSegment = segment;
        mCurReadSegIndex = segment.mSegmentIndex;
        mReadIndex = segment.mEndIndex - (i - index) + 1;
    }

    public YYAudioPacket readSampleData() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return null;
        }

        //YYLog.info(TAG, "jtzhu read sample index : " + mReadIndex );
        return readByIndex(mReadIndex);
    }

    public boolean advance() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return false;
        }
        mReadIndex++;
        if (mReadIndex > mCurReadSegment.mEndIndex) {
            AudioSegment newSeg = null;
            while (mCurReadSegIndex < mTotalSegmentCnt) { // move to the next available Audio segment, may be deleted
                mCurReadSegIndex++;

                for (AudioSegment seg : mAudioSegmentList) {
                    if (seg.mSegmentIndex == mCurReadSegIndex) {
                        newSeg = seg;
                        break;
                    }
                }
                if (newSeg != null) break;
            }
            if (newSeg == null) {
                YYLog.info(TAG, "end of Audio,mCurReadSegIndex " + mCurReadSegIndex + " mReadIndex " + mReadIndex);
                return false;
            }

            mCurReadSegment = newSeg;
            mReadIndex = 0;
            YYLog.info(TAG, "new Read segment index " + mCurReadSegIndex + " mReadIndex " + mReadIndex);
        }
        //long pts = getSampleTime();
        //YYLog.info(TAG, "advanced mCurReadSegIndex " + mCurReadSegIndex + " mReadIndex " + mReadIndex + " pts " + pts);
        return true;
    }

    public long getSampleTime() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return 0;
        }
        if (mReadIndex == 0 && mCurReadSegIndex == 0) {  // the first Audio frame
            return 0;
        }
        YYAudioPacket packet = readByIndex(mReadIndex);
        if (packet == null) {
            return -1;
        }
        return packet.pts;
    }

    public long getCachedDuration() {
        return -1;
        //return mEndPTS-mStartPTS;
    }

    public int getSampleFlags() {
        YYAudioPacket packet = readByIndex(mReadIndex);
        if (packet == null) {
            return 0;
        }
        return packet.mBufferFlag;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void removeSegmentByIndex(int segIndex) {
        YYLog.info(TAG, "removeSegmentByIndex segIndex " + segIndex);
        if (segIndex < 0) {
            YYLog.error(TAG, "removeSegmentByIndex segIndex " + segIndex + " error !");
            return;
        }
        AudioSegment segment = null;
        AudioSegment lastSegBeforeDeleteSegemet = null;
        for (AudioSegment seg : mAudioSegmentList) {
            if (segIndex-1 >= 0 && segIndex-1 == seg.mSegmentIndex) {  // 记录被删除音频段的前一段
                lastSegBeforeDeleteSegemet = seg;
            }
            if (segIndex == seg.mSegmentIndex) {
                segment = seg;
                break;
            }
        }
        if (segment != null) {
            if (mAudioMediaFormat != null && mAudioMediaFormat.containsKey(MediaFormat.KEY_DURATION)) {
                long duration = mAudioMediaFormat.getLong(MediaFormat.KEY_DURATION);
                mAudioMediaFormat.setLong(MediaFormat.KEY_DURATION, duration - (segment.mEndPts - segment.mStartPts));
            }
            adjustPts(segment);
            YYLog.info(TAG, " removeSegmentByIndex " + segIndex + " OK.");
            if (segment == mLastWriteSegment) {					// 删除一段后，更新最后一段的位置
                mLastWriteSegment = lastSegBeforeDeleteSegemet;
                if (mLastWriteSegment != null) {
                    mCurWriteSegIndex = mLastWriteSegment.mSegmentIndex + 1;
                    YYLog.info(TAG, " new mLastWriteSegment index " + mLastWriteSegment.mSegmentIndex + " new writeSegIndex " + mCurWriteSegIndex);
                }else {
                    YYLog.info(TAG, " new mLastWriteSegment index  -1. ");
                }
            }
            mAudioSegmentList.remove(segment);
        } else {
            YYLog.info(TAG, "removeSegmentByIndex not found segment for segIndex " + segIndex);
        }
    }

    private void adjustPts(AudioSegment segmentToDelete) {
        long ptsOffset = (segmentToDelete.mEndPts - segmentToDelete.mStartPts);
        int deleteSegIndex = segmentToDelete.mSegmentIndex;

        // segmentToDelete 后的所有音频段PTS往前调整靠拢
        for (AudioSegment segment : mAudioSegmentList) {
            if (segment.mSegmentIndex > deleteSegIndex) {
                int startIndex = segment.mStartIndex;
                int endIndex = segment.mEndIndex;
                for (int i = startIndex ; i <= endIndex; i++) {
                    YYAudioPacket packet = segment.mAudioDataMap.get(i);
                    if (packet != null) {
                        packet.pts = packet.pts - ptsOffset;
                    }
                }
            }
        }
    }

    public void reset() {
        mStartPTS = 0;
        mEndPTS = 0;
        mCurWriteSegIndex = 0;
        mCurReadSegIndex = 0;
        mWriteIndex = 0;
        mReadIndex = 0;
        mTotalFrameCnt = 0;
        mCurWriteSegment = null;
        mLastWriteSegment = null;
        mCurReadSegment = null;
        mAudioMediaFormat = null;
        mCurReadSegmentForExport = null;
        mAudioSegmentList.clear();
        mInited.set(false);
        YYLog.info(TAG, "reset.");
    }


    public boolean advanceForExport() {
        mReadindexForExport++;
        if (mReadindexForExport > mCurReadSegmentForExport.mEndIndex) {
            AudioSegment newSeg = null;
            while (mCurReadSegIndexForExport < mTotalSegmentCnt) { // move to the next available Audio segment, may be deleted
                mCurReadSegIndexForExport++;

                for (AudioSegment seg : mAudioSegmentList) {
                    if (seg.mSegmentIndex == mCurReadSegIndexForExport) {
                        newSeg = seg;
                        break;
                    }
                }
                if (newSeg != null) break;
            }
            if (newSeg == null) {
                YYLog.info(TAG, "end of Audio,mCurReadSegIndexForExport " + mCurReadSegIndexForExport + " mReadIndexForExport " + mReadindexForExport);
                return false;
            }

            mCurReadSegmentForExport = newSeg;
            mReadindexForExport = 0;
            YYLog.info(TAG, "new Read segment index " + mCurReadSegIndexForExport + " mReadIndexForExport " + mReadindexForExport);
        }
        //long pts = getSampleTime();
        //YYLog.info(TAG, "advanced mCurReadSegIndex " + mCurReadSegIndex + " mReadIndex " + mReadIndex + " pts " + pts);
        return true;
    }

    public void seekToForExport(long timeUs, int mode) {

        long original = timeUs;
        if (timeUs < mStartPTS) {
            timeUs = mStartPTS;
        } else if (timeUs > mEndPTS) {
            timeUs = mEndPTS;
        }

        AudioSegment segment = findAudioSegmentByPts(timeUs);
        if (segment != null) {
            mCurReadSegmentForExport = segment;
            mCurReadSegIndexForExport = segment.mSegmentIndex;
            mReadindexForExport = findAudioPacketByPts(mCurReadSegmentForExport, timeUs);
            YYLog.info(TAG, " seekToForExport " + original + "segment index for Export " + mCurReadSegIndexForExport + " mReadIndexForExport " + mReadindexForExport +
                    " mStartPTS " + mStartPTS + " mEndPTS " + mEndPTS + " mode " + mode );
        } else {
            YYLog.error(TAG, "seekTo " + timeUs + " error! ");
        }
    }

    public YYAudioPacket readSampleDataForExport() {

        //YYLog.info(TAG, "jtzhu export segment:" + mCurReadSegIndexForExport + " index : " + mReadindexForExport );
        return readByIndexForExport(mReadindexForExport);
    }

    private YYAudioPacket readByIndexForExport(int index) {
        if (mCurReadSegmentForExport == null) {
            for (AudioSegment seg : mAudioSegmentList) {
                if (seg.mSegmentIndex == mCurReadSegIndexForExport) {
                    mCurReadSegmentForExport = seg;
                    break;
                }
            }
        }
        if (mCurReadSegmentForExport == null) {
            return null;
        }
        if (index < 0 || index > mCurReadSegmentForExport.mEndIndex || mCurReadSegmentForExport.mAudioDataMap == null) {
            YYLog.warn(TAG, " end of Audio segment [" +mCurReadSegIndexForExport + "] index " + index +
                    " start index " + 0 + " end index " + mCurReadSegmentForExport.mEndIndex);
            return null;
        }

        return mCurReadSegmentForExport.mAudioDataMap.get(index);
    }

    public long getSampleTimeForExport() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return 0;
        }
        if (mReadindexForExport == 0 && mCurReadSegIndexForExport == 0) {  // the first video frame
            return 0;
        }
        YYAudioPacket packet = readByIndexForExport(mReadindexForExport);
        if (packet == null) {
            return -1;
        }
        return packet.pts;
    }

    public int getSampleFlagsForExport() {
        YYAudioPacket packet = readByIndexForExport(mReadindexForExport);
        if (packet == null) {
            return 0;
        }
        return packet.mBufferFlag;
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int exportAudioToMp4(String path) {
        if (mAudioSegmentList.size() <= 0) {
            YYLog.error(TAG, "No Audio Data in Memory !");
            return -1;
        }
        if (path == null) {
            YYLog.error(TAG, "path == null !");
            return -1;
        }

        int audioFrameCount = 0;
        try {
            long time = System.currentTimeMillis();
            MediaMuxer muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaFormat audioFormat = mAudioMediaFormat;
            if (audioFormat == null) {
                YYLog.error(TAG, "audioFormat == null ! ");
                return -1;
            }

            int audioTrackIndex = muxer.addTrack(audioFormat);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            try {
                muxer.start();
            } catch (IllegalStateException e) {
                YYLog.error(TAG, "muxer.start exception : "+ e.getMessage());
            }
            seekToForExport(0,0);
            while (true) {
                YYAudioPacket packet = readSampleDataForExport();
                if (null != packet) {
                    packet.mDataByteBuffer.rewind();
                    //YYLog.error(TAG, "write to Mp4 size : " + packet.mBufferSize + " offset " + packet.mBufferOffset + " pts " + packet.pts );
                    ByteBuffer inputBuffer = ByteBuffer.allocate(packet.mBufferSize);
                    inputBuffer.clear();
                    inputBuffer.put(packet.mDataByteBuffer.array(), packet.mBufferOffset, packet.mBufferSize);
                    inputBuffer.rewind();
                    packet.mDataByteBuffer.rewind();
                    bufferInfo.flags = packet.mBufferFlag;
                    bufferInfo.offset = packet.mBufferOffset;
                    bufferInfo.presentationTimeUs = packet.pts;
                    bufferInfo.size = packet.mBufferSize;
                    try {
                        muxer.writeSampleData(audioTrackIndex, inputBuffer, bufferInfo);
                    }catch (IllegalStateException e) {
                        YYLog.error(TAG, "IllegalStateException " + e.toString() + " " + e.getMessage());
                    } catch (IllegalArgumentException e) {
                        YYLog.error(TAG, "IllegalArgumentException " + e.toString() + " " + e.getMessage());
                    }
                    advanceForExport();
                    audioFrameCount++;
                } else {
                    break;
                }
            }
            try {
                muxer.stop();
            } catch (IllegalStateException e) {
                YYLog.error(TAG, "muxer.stop exception :" + e.getMessage());
            }
            muxer.release();
            YYLog.info(TAG, "exportAudioToMp4 cost " + (System.currentTimeMillis() - time));
        } catch (IOException e) {
            YYLog.error(TAG, "IOException : " + e.getMessage());
        }
        if (audioFrameCount > 0) {
            return 0;
        }
        return -1;
    }

    public void resetMarkTimePoint() {
        mTimePointToDelete = -1;
        mAudioSegmentIndexToDelete = -1;
        mAudioIndexToDelete = -1;
        mToDeleteSegment = null;
    }

    // 标记要删除timeMs后面的所有帧，播放器播放到timeMs时，开始从头开始播放
    // timeMs 为一段视频中的相对值，不是整个视频的全局PTS,业务端是按段来处理的
    public void markTimePointToDelete(int segIndex, long timeMs) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return ;
        }
        YYLog.info(TAG, "markTimePointToDelete segIndex " + segIndex + " timeMs " + timeMs);
        for (AudioSegment seg : mAudioSegmentList) {
            if (seg.mSegmentIndex == segIndex) {
                timeMs = timeMs * 1000;  // 类内部PTS单位是微妙
                timeMs += seg.mStartPts;
                if (timeMs <= seg.mEndPts) {
                    mTimePointToDelete = timeMs;
                    mAudioSegmentIndexToDelete = segIndex;
                    mAudioIndexToDelete = findAudioPacketByPts(seg, timeMs);
                    mToDeleteSegment = seg;
                    YYLog.info(TAG, "markTimePointToDelete " + mTimePointToDelete + " mAudioIndexToDelete " + mAudioIndexToDelete);
                } else {
                    YYLog.info(TAG, "markTimePointToDelete error " + timeMs +
                            "["+ seg.mStartPts + "," + seg.mEndPts+"]");
                }
                break;
            }
        }
    }

    private void deleteToEndInSeg(AudioSegment seg, int startIndex) {
        int index = seg.mEndIndex;
        int segIndex = seg.mSegmentIndex;
        while (index >= 0) {
            if (index >= startIndex) {
                seg.mAudioDataMap.removeAt(index);
            } else {
                break;  // 尾部已删完，break
            }
            index--;
        }
        seg.mEndIndex = index;
        if (index >= 0) {
            seg.mEndPts = seg.mAudioDataMap.get(index).pts;
        } else {
            removeSegmentByIndex(segIndex);
        }

        mDuration = calculateTotalDuration();
        mAudioMediaFormat.setLong(MediaFormat.KEY_DURATION, mDuration);
        YYLog.info(TAG, "doDeleteInSegment segIndex " + segIndex + " new seg.mEndIndex " + seg.mEndIndex + " endPts " + seg.mEndPts);
    }

    // 删除mTimePointToDelete标记的后面的帧
    public void doDeleteInLastSegment() {
        if (mTimePointToDelete == -1 || !mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return;
        }

        YYLog.info(TAG, "doDeleteInSegment segIndex " + mAudioSegmentIndexToDelete + " videoIndex " + mAudioIndexToDelete);
        AudioSegment seg = mToDeleteSegment;
        if (seg != null) {
            deleteToEndInSeg(seg, mAudioIndexToDelete);
        }
        mTimePointToDelete = -1;
        mAudioSegmentIndexToDelete = -1;
        mAudioIndexToDelete = -1;
        mToDeleteSegment = null;    
    }

    public long getPtsBySegIndex(int segIndex, boolean start) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return -1;
        }
        for(AudioSegment seg : mAudioSegmentList) {
            if (seg.mSegmentIndex == segIndex) {
                return start ? seg.mStartPts : seg.mEndPts;
            }
        }
        return -1;
    }

    public int getMarkDeleteSegIndex() {
        return mAudioSegmentIndexToDelete;
    }

    public long getTimePointToDelete() {
        return mTimePointToDelete;
    }
}
