package com.ycloud.datamanager;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;
import android.util.SparseArray;

import com.ycloud.common.GlobalConfig;
import com.ycloud.gpuimagefilter.utils.BodiesDetectInfo;
import com.ycloud.gpuimagefilter.utils.FacesDetectInfo;
import com.ycloud.mediacodec.MediaFormatExtraConstants;
import com.ycloud.utils.YYLog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ycloud.mediacodec.VideoConstant.VideoFrameType.kVideoIDRFrame;
import static java.lang.Math.abs;

/**
 * Created by Administrator on 2017/12/29.
 * 非线程安全
 */

public class VideoDataManager {

    private static final String TAG = "VideoDataManager";
    private static VideoDataManager mInstance = null;
    private static final byte[] SYNC_FLAG = new byte[1];
    private long mStartPTS = -1;                                     // global start pts of all the video segments
    private long mEndPTS = -1;                                       // global end pts of all the video segments
    private int mCurWriteSegIndex = 0;                              // global write index for video segments
    private int mCurReadSegIndex = 0;                               // global read index for video segments
    private int mCurReadSegIndexForExport = 0;                      // global read index for Export of video segments
    private int mWriteIndex = 0;                                    // global write index in each video segment, start from 0
    private int mReadIndex = 0;                                     // global read index in each video segment,start from 0
    private int mReadindexForExport = 0;
    private int mTotalFrameCnt = 0;
    private int mTotalSegmentCnt = 0;
    private long mDuration = 0;
    private long mTimePointToDelete = -1;
    private int mVideoIndexToDelete = -1;
    private int mVideoSegmentIndexToDelete = -1;
    private VideoSegment mToDeleteSegment = null;
    private VideoSegment mCurWriteSegment = null;
    private VideoSegment mLastWriteSegment = null;
    private VideoSegment mCurReadSegment = null;
    private VideoSegment mCurReadSegmentForExport = null;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    private int mFrameRate = GlobalConfig.getInstance().getRecordConstant().EXPORT_FRAME_RATE;

    private class VideoSegment {
        int mStartIndex;                                             // start index of a video segment
        int mEndIndex;                                               // end index of a video segment
        int mReadIndex;
        int mSegmentIndex;
        long mStartPts;
        long mEndPts;
        long mFrameDuration;
        SparseArray<YYVideoPacket> mVideoDataMap;
    }

    private MediaFormat mVideoMediaFormat = null;
    private LinkedList<VideoSegment> mVideoSegmentList = null;

    public static VideoDataManager instance() {
        if (mInstance == null) {
            synchronized (SYNC_FLAG) {
                if (mInstance == null) {
                    mInstance = new VideoDataManager();
                }
            }
        }
        return mInstance;
    }

    private VideoDataManager() {
        mVideoSegmentList = new LinkedList<>();
    }

    public void startRecord() {
        VideoSegment segment = new VideoSegment();
        segment.mVideoDataMap = new SparseArray<>();
        segment.mStartIndex = 0;
        segment.mEndIndex = 0;
        segment.mReadIndex = 0;
        segment.mStartPts = -1;
        segment.mEndPts = -1;
        segment.mSegmentIndex = mCurWriteSegIndex;
        mCurWriteSegment = segment;
        mWriteIndex = 0;
        mInited.set(true);
        YYLog.info(TAG, "video segment [" + mCurWriteSegIndex + "] record start.");
    }

    private long calculateTotalDuration() {
        long duration = 0;
        VideoSegment segFirst = mVideoSegmentList.peekFirst();
        VideoSegment segLast = mVideoSegmentList.peekLast();
        if (segFirst != null && segLast != null) {
            duration = segLast.mEndPts - segFirst.mStartPts;
        }
        return duration;
    }

    public long getDuration() {
        return mDuration;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopRecord() {
        if (mCurWriteSegment == null) {
            return;
        }

        if (mWriteIndex == 0) {  // started, but no frame write
            mCurWriteSegment = null;
            YYLog.info(TAG, "video segment [" + mCurWriteSegIndex + "] record stop with no frame write, drop it.");
            return;
        }

        SparseArray<YYVideoPacket> VideoDataMap = mCurWriteSegment.mVideoDataMap;
        YYVideoPacket packet = VideoDataMap.get(mCurWriteSegment.mStartIndex);
        if (packet != null) {
            mCurWriteSegment.mStartPts = packet.pts;
        }

        mCurWriteSegment.mEndIndex = mWriteIndex - 1;
        packet = VideoDataMap.get(mCurWriteSegment.mEndIndex);
        if (packet != null) {
            mCurWriteSegment.mEndPts = packet.pts;
            YYLog.info(TAG, "mCurWriteSegment [" + mCurWriteSegIndex + "] end  pts " + packet.pts);
        }

        mVideoSegmentList.add(mCurWriteSegment);

        if (mVideoMediaFormat != null) {
            mDuration = calculateTotalDuration();
            mVideoMediaFormat.setLong(MediaFormat.KEY_DURATION, mDuration);
            YYLog.info(TAG, "video segment [" + mCurWriteSegIndex + "] end index " + mCurWriteSegment.mEndIndex +
                    " duration " + (mCurWriteSegment.mEndPts - mCurWriteSegment.mStartPts) + " Total duration " + mDuration + " TotalFrameCount " + mTotalFrameCnt);
        }
        int segmentFrameCount = mWriteIndex;
        if (segmentFrameCount > 0) {
            mCurWriteSegment.mFrameDuration = (mCurWriteSegment.mEndPts - mCurWriteSegment.mStartPts) / segmentFrameCount;
            YYLog.info(TAG, " frame duration %d", mCurWriteSegment.mFrameDuration);
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

        if (mVideoMediaFormat == null) {
            int width = format.getInteger(MediaFormat.KEY_WIDTH);
            int height = format.getInteger(MediaFormat.KEY_HEIGHT);
            String mime = format.getString(MediaFormat.KEY_MIME);
            mVideoMediaFormat = MediaFormat.createVideoFormat(mime, width, height);
        }

        ByteBuffer sps = format.getByteBuffer(MediaFormatExtraConstants.KEY_AVC_SPS);
        ByteBuffer mSps = ByteBuffer.allocateDirect(sps.array().length);
        mSps.order(ByteOrder.nativeOrder());
        mSps.put(sps);
        mSps.asReadOnlyBuffer();
        mSps.flip();
        sps.rewind();
        mVideoMediaFormat.setByteBuffer(MediaFormatExtraConstants.KEY_AVC_SPS, mSps);

        ByteBuffer pps = format.getByteBuffer(MediaFormatExtraConstants.KEY_AVC_PPS);
        ByteBuffer mPps = ByteBuffer.allocateDirect(pps.array().length);
        mPps.order(ByteOrder.nativeOrder());
        mPps.put(pps);
        mPps.asReadOnlyBuffer();
        mPps.flip();
        pps.rewind();
        mVideoMediaFormat.setByteBuffer(MediaFormatExtraConstants.KEY_AVC_PPS, mPps);
    }

    // 写入的PTS的单位是微妙（us)
    public int write(YYVideoPacket packet) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return -1;
        }
        if (mCurWriteSegment == null) {
            return -1;
        }

        //long origPts = packet.pts;
        if (packet.mBufferSize > 0) {
            // concat the pts of the last video segment.
            if (null != mLastWriteSegment && mLastWriteSegment.mEndPts != -1) {
                long org = packet.pts;
                packet.pts = (packet.pts + mLastWriteSegment.mEndPts + mLastWriteSegment.mFrameDuration);
                //Log.e("howard", "  pts " + org + " : " + packet.pts / 1000 + " >> " + mAvgVideoPtsDelta / 1000 + " >> " + mLastWriteSegment.mEndPts);
            }

            if (mCurWriteSegment.mVideoDataMap != null) {
                mCurWriteSegment.mVideoDataMap.put(mWriteIndex, packet);
                mTotalFrameCnt++;
            }

            // update pts of all video segments
            if (mStartPTS == -1) {
                mStartPTS = packet.pts;
            }
            mEndPTS = packet.pts;

            // update pts of each video segment
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
            VideoSegment segFirst = mVideoSegmentList.peekFirst();
            if (segFirst != null) {
                mDuration = mEndPTS - segFirst.mStartPts;
            }

            if (mDuration > 0 && mTotalFrameCnt > 0) {
                mFrameRate = (int)((long)mTotalFrameCnt * 1000000 / mDuration);
            }
        }
        return mWriteIndex - 1;
    }

    private VideoSegment findVideoSegmentByPts(long pts) {
        for (VideoSegment seg : mVideoSegmentList) {
            if (pts >= seg.mStartPts && pts <= seg.mEndPts) {
                return seg;
            }
        }
        return null;
    }

    private int findVideoPacketByPts(VideoSegment segment, long pts) {
        if (segment == null || segment.mVideoDataMap == null) {
            return -1;
        }
        int l = 0, r = segment.mEndIndex;
        while (l <= r) {
            int index = l + (r - l) / 2;
            YYVideoPacket packet = segment.mVideoDataMap.get(index);
            if (packet.pts == pts) {
                return index;
            }
            if (packet.pts > pts) {
                r = index - 1;
            } else if (packet.pts < pts) {
                l = index + 1;
            }
        }

        long labs = segment.mVideoDataMap.get(l) == null ? 0 : abs(segment.mVideoDataMap.get(l).pts - pts);
        long rabs = segment.mVideoDataMap.get(r) == null ? 0 : abs(segment.mVideoDataMap.get(r).pts - pts);
        //YYLog.info(TAG, "jtzhu findVideoPacketByPts l " + l + " pts " + mVideoDataMap.get(l).pts + " r " + r + " pts " + mVideoDataMap.get(r).pts + " target " + pts + " result " + result);
        return labs > rabs ? r : l;
    }

//    private YYVideoPacket readByPts(long pts) {
//        if (pts > mEndPTS || pts < mStartPTS) {
//            YYLog.error(TAG, " read error ! pts " + pts + " start pts " + mStartPTS + " end pts " + mEndPTS);
//            return null;
//        }
//        VideoSegment seg = findVideoSegmentByPts(pts);
//        if (seg == null) {
//            return null;
//        }
//        int index = findVideoPacketByPts(seg, pts);
//        return seg.mVideoDataMap.get(index);
//    }

    private YYVideoPacket readByIndex(int index) {

        if (mCurReadSegment == null) {
            for (VideoSegment seg : mVideoSegmentList) {
                if (seg.mSegmentIndex == mCurReadSegIndex) {
                    mCurReadSegment = seg;
                    break;
                }
            }
        }
        if (mCurReadSegment == null) {
            YYLog.error(TAG, " mCurReadSegment == null ");
            return null;
        }

        if (index < 0 || index > mCurReadSegment.mEndIndex || mCurReadSegment.mVideoDataMap == null) {
            YYLog.warn(TAG, " end of video segment [" +mCurReadSegIndex + "] index " + index +
                    " start index " + 0 + " end index " + mCurReadSegment.mEndIndex);
            return null;
        }

        if (mVideoSegmentIndexToDelete != -1 && mVideoIndexToDelete != -1) {
            if (mCurReadSegIndex == mVideoSegmentIndexToDelete && index >= mVideoIndexToDelete) {
                YYLog.warn(TAG, " end of video segment [" +mCurReadSegIndex + "] index " + index +
                        " mVideoSegmentIndexToDelete " + mVideoSegmentIndexToDelete + " mVideoIndexToDelete " + mVideoIndexToDelete);
                return null;
            }
        }

        return mCurReadSegment.mVideoDataMap.get(index);
    }

    public MediaFormat getVideoMediaFormat() {
        return mVideoMediaFormat;
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

        VideoSegment segment = findVideoSegmentByPts(timeUs);
        if (segment != null) {
            mCurReadSegment = segment;
            mCurReadSegIndex = segment.mSegmentIndex;
            mReadIndex = findVideoPacketByPts(mCurReadSegment, timeUs);
            // 某些机型可能会有不是全IDR帧的情况
            while (true) {
                YYVideoPacket packet = readByIndex(mReadIndex);
                if (packet != null && packet.mFrameType != kVideoIDRFrame) {
                    YYLog.warn(TAG, "Not IDR frame, find Next frame.");
                    mReadIndex++;
                } else {
                    break;
                }
            }

            YYLog.info(TAG, " seekto " + original + "segment index " + mCurReadSegIndex + " mReadIndex " + mReadIndex +
                    " mStartPTS " + mStartPTS + " mEndPTS " + mEndPTS + " mode " + mode);
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
        VideoSegment segment = null;
        for (VideoSegment seg : mVideoSegmentList) {
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

    public YYVideoPacket readSampleData() {
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
            VideoSegment newSeg = null;
            while (mCurReadSegIndex < mTotalSegmentCnt) { // move to the next available video segment, may be deleted
                mCurReadSegIndex++;

                for (VideoSegment seg : mVideoSegmentList) {
                    if (seg.mSegmentIndex == mCurReadSegIndex) {
                        newSeg = seg;
                        break;
                    }
                }
                if (newSeg != null) break;
            }
            if (newSeg == null) {
                YYLog.info(TAG, "end of video,mCurReadSegIndex " + mCurReadSegIndex + " mReadIndex " + mReadIndex);
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
        if (mReadIndex == 0 && mCurReadSegIndex == 0) {  // the first video frame
            return 0;
        }
        YYVideoPacket packet = readByIndex(mReadIndex);
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
        YYVideoPacket packet = readByIndex(mReadIndex);
        if (packet == null) {
            return 0;
        }
        return packet.mBufferFlag;
    }

    /**
     * TODO.目前只支持删除最后一段，删除中间任意段会有问题
     * @param segIndex
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void removeSegmentByIndex(int segIndex) {
        YYLog.info(TAG, "removeSegmentByIndex segIndex " + segIndex);
        if (segIndex < 0) {
            YYLog.error(TAG, "removeSegmentByIndex segIndex " + segIndex + " error !");
            return;
        }
        VideoSegment segment = null;
        VideoSegment lastSegBeforeDeleteSegemet = null;
        for (VideoSegment seg : mVideoSegmentList) {

            if (segIndex-1 >= 0 && segIndex-1 == seg.mSegmentIndex) {  // 记录被删除视频段的前一段
                lastSegBeforeDeleteSegemet = seg;
            }

            if (segIndex == seg.mSegmentIndex) {   // 记录即将被删除的视频段
                segment = seg;
                break;
            }
        }
        if (segment != null) {
            if (mVideoMediaFormat != null && mVideoMediaFormat.containsKey(MediaFormat.KEY_DURATION)) {
                long duration = mVideoMediaFormat.getLong(MediaFormat.KEY_DURATION);
                mVideoMediaFormat.setLong(MediaFormat.KEY_DURATION, duration - (segment.mEndPts - segment.mStartPts));
            }
            adjustPts(segment);
            YYLog.info(TAG, " removeSegmentByIndex " + segIndex + " OK. ");

            if (segment == mLastWriteSegment) {     // 删除一段后，更新最后一段的位置
                mLastWriteSegment = lastSegBeforeDeleteSegemet;
                if (mLastWriteSegment != null) {
                    mCurWriteSegIndex = mLastWriteSegment.mSegmentIndex + 1;
                    YYLog.info(TAG, " new mLastWriteSegment index " + mLastWriteSegment.mSegmentIndex + " new writeSegIndex " + mCurWriteSegIndex);
                } else {
                    YYLog.info(TAG, " new mLastWriteSegment index  -1. ");
                }
            }
            mVideoSegmentList.remove(segment);
        } else {
            YYLog.info(TAG, "removeSegmentByIndex not found segment for segIndex " + segIndex);
        }
    }

    private void adjustPts(VideoSegment segmentToDelete) {
        long ptsOffset = (segmentToDelete.mEndPts - segmentToDelete.mStartPts);
        int deleteSegIndex = segmentToDelete.mSegmentIndex;

        // segmentToDelete 后的所有视频段PTS往前调整靠拢
        for (VideoSegment segment : mVideoSegmentList) {
            if (segment.mSegmentIndex > deleteSegIndex) {
                int startIndex = segment.mStartIndex;
                int endIndex = segment.mEndIndex;
                for (int i = startIndex ; i <= endIndex; i++) {
                    YYVideoPacket packet = segment.mVideoDataMap.get(i);
                    if (packet != null) {
                        packet.pts = packet.pts - ptsOffset;
                    }
                }
            }
        }
    }

    public void reset() {
        mStartPTS = -1;
        mEndPTS = -1;
        mCurWriteSegIndex = 0;
        mCurReadSegIndex = 0;
        mWriteIndex = 0;
        mReadIndex = 0;
        mTotalFrameCnt = 0;
        mFrameRate = GlobalConfig.getInstance().getRecordConstant().EXPORT_FRAME_RATE;
        mCurWriteSegment = null;
        mLastWriteSegment = null;
        mCurReadSegment = null;
        mCurReadSegmentForExport = null;
        mVideoMediaFormat = null;
        mVideoSegmentList.clear();
        mInited.set(false);
        YYLog.info(TAG, "reset.");
    }
    public boolean advanceForExport() {
        if(mCurReadSegmentForExport == null) {
            return false;
        }
        mReadindexForExport++;
        if (mReadindexForExport > mCurReadSegmentForExport.mEndIndex) {
            VideoSegment newSeg = null;
            while (mCurReadSegIndexForExport < mTotalSegmentCnt) { // move to the next available Audio segment, may be deleted
                mCurReadSegIndexForExport++;

                for (VideoSegment seg : mVideoSegmentList) {
                    if (seg.mSegmentIndex == mCurReadSegIndexForExport) {
                        newSeg = seg;
                        break;
                    }
                }
                if (newSeg != null) break;
            }
            if (newSeg == null) {
                YYLog.info(TAG, "end of Video,mCurReadSegIndexForExport " + mCurReadSegIndexForExport + " mReadIndexForExport " + mReadindexForExport);
                return false;
            }

            mCurReadSegmentForExport = newSeg;
            mReadindexForExport = 0;
            YYLog.info(TAG, "new Read segment index for Export " + mCurReadSegIndexForExport + " mReadIndexForExport " + mReadindexForExport);
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

        VideoSegment segment = findVideoSegmentByPts(timeUs);
        if (segment != null) {
            mCurReadSegmentForExport = segment;
            mCurReadSegIndexForExport = segment.mSegmentIndex;
            mReadindexForExport = findVideoPacketByPts(mCurReadSegmentForExport, timeUs);
            YYLog.info(TAG, " seekToForExport " + original + "segment index for Export " + mCurReadSegIndexForExport + " mReadIndexForExport " + mReadindexForExport +
                    " mStartPTS " + mStartPTS + " mEndPTS " + mEndPTS + " mode " + mode );
        } else {
            YYLog.error(TAG, "seekTo " + timeUs + " error! ");
        }
    }

    public YYVideoPacket readSampleDataForExport() {

        //YYLog.info(TAG, "jtzhu export segment:" + mCurReadSegIndexForExport + " index : " + mReadindexForExport );
        return readByIndexForExport(mReadindexForExport);
    }

    private YYVideoPacket readByIndexForExport(int index) {
        if (mCurReadSegmentForExport == null) {
            for (VideoSegment seg : mVideoSegmentList) {
                if (seg.mSegmentIndex == mCurReadSegIndexForExport) {
                    mCurReadSegmentForExport = seg;
                    break;
                }
            }
        }
        if (mCurReadSegmentForExport == null) {
            return null;
        }
        if (index < 0 || index > mCurReadSegmentForExport.mEndIndex || mCurReadSegmentForExport.mVideoDataMap == null) {
            YYLog.warn(TAG, " end of Video segment [" +mCurReadSegIndexForExport + "] index " + index +
                    " start index " + 0 + " end index " + mCurReadSegmentForExport.mEndIndex);
            return null;
        }

        return mCurReadSegmentForExport.mVideoDataMap.get(index);
    }

    public long getSampleTimeForExport() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return 0;
        }
        if (mReadindexForExport == 0 && mCurReadSegIndexForExport == 0) {  // the first video frame
            return 0;
        }
        YYVideoPacket packet = readByIndexForExport(mReadindexForExport);
        if (packet == null) {
            return -1;
        }
        return packet.pts;
    }

    public int getSampleFlagsForExport() {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return 0;
        }
        YYVideoPacket packet = readByIndexForExport(mReadindexForExport);
        if (packet == null) {
            return 0;
        }
        return packet.mBufferFlag;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void exportVideoDataToFile(String filePath) {
        if (mVideoMediaFormat == null) {
            return;
        }
        /**
         * 1. save sps pps
         */
        ByteBuffer sps = mVideoMediaFormat.getByteBuffer(MediaFormatExtraConstants.KEY_AVC_SPS);
        saveStreamToFile(filePath, sps.array(), sps.array().length);
        sps.rewind();

        ByteBuffer pps = mVideoMediaFormat.getByteBuffer(MediaFormatExtraConstants.KEY_AVC_PPS);
        saveStreamToFile(filePath, pps.array(), pps.array().length);
        pps.rewind();

        /**
         * save video data
         */
        seekToForExport(0,0);
        while(true) {
            YYVideoPacket packet = readSampleDataForExport();
            if (packet == null ) {
                break;
            }
            packet.mDataByteBuffer.rewind();
            saveStreamToFile(filePath, packet.mDataByteBuffer.array(), packet.mDataByteBuffer.array().length);
            advanceForExport();
        }
        YYLog.info(TAG,"exportVideoDataToFile " + filePath + " Success.");
    }

    private void saveStreamToFile(String filePath, byte[] data, int length) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filePath, true);
            fos.write(data, 0, length);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            YYLog.error(TAG, "init save 264 stream error:IOException" + e);
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                YYLog.error(TAG, "save 264 stream error:close fail" + e);
            }
        }
    }

    public void resetMarkTimePoint() {
        mTimePointToDelete = -1;
        mVideoSegmentIndexToDelete = -1;
        mVideoIndexToDelete = -1;
        mToDeleteSegment = null;
        YYLog.info(TAG, "resetMarkTimePoint success.");
    }

    // 标记要删除timeMs后面的所有帧，播放器播放到timeMs时，开始从头开始播放
    // timeMs 为一段视频中的相对值，不是整个视频的全局PTS,业务端是按段来处理的
    public void markTimePointToDelete(int segIndex, long timeMs) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return ;
        }
        YYLog.info(TAG, "markTimePointToDelete segIndex " + segIndex + " timeMs " + timeMs);
        for (VideoSegment seg : mVideoSegmentList) {
            if (seg.mSegmentIndex == segIndex) {
                timeMs = timeMs * 1000;  // 类内部PTS单位是微妙
                timeMs += seg.mStartPts;
                if (timeMs <= seg.mEndPts) {
                    mTimePointToDelete = timeMs;
                    mVideoSegmentIndexToDelete = segIndex;
                    mVideoIndexToDelete = findVideoPacketByPts(seg, timeMs);
                    mToDeleteSegment = seg;
                    YYLog.info(TAG, "markTimePointToDelete " + mTimePointToDelete + " mVideoIndexToDelete " + mVideoIndexToDelete);
                } else {
                    YYLog.info(TAG, "markTimePointToDelete error " + timeMs +
                            "["+ seg.mStartPts + "," + seg.mEndPts+"]");
                }
                break;
            }
        }
    }

    private void deleteToEndInSeg(VideoSegment seg, int startIndex) {
        int index = seg.mEndIndex;
        int segIndex = seg.mSegmentIndex;
        while (index >= 0) {
            if (index >= startIndex) {
                seg.mVideoDataMap.removeAt(index);
            } else {
                break;  // 尾部已删完，break
            }
            index--;
        }
        seg.mEndIndex = index;
        if (index >= 0) {
            seg.mEndPts = seg.mVideoDataMap.get(index).pts;
        } else {
            removeSegmentByIndex(segIndex);
        }

        mDuration = calculateTotalDuration();
        mVideoMediaFormat.setLong(MediaFormat.KEY_DURATION, mDuration);
        YYLog.info(TAG, "doDeleteInSegment segIndex " + segIndex + " new seg.mEndIndex " + seg.mEndIndex + " endPts " + seg.mEndPts);
    }

    // 删除mTimePointToDelete标记的后面的帧
    public void doDeleteInLastSegment() {
        if (mTimePointToDelete == -1 || !mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return;
        }

        YYLog.info(TAG, "doDeleteInSegment segIndex " + mVideoSegmentIndexToDelete + " videoIndex " + mVideoIndexToDelete);
        VideoSegment seg = mToDeleteSegment;
        if (seg != null) {
            deleteToEndInSeg(seg, mVideoIndexToDelete);
        }
        mTimePointToDelete = -1;
        mVideoSegmentIndexToDelete = -1;
        mVideoIndexToDelete = -1;
        mToDeleteSegment = null;    // 导出音视频到MP4草稿后再清理
    }

    public long getPtsBySegIndex(int segIndex, boolean start) {
        if (!mInited.get()) {
            YYLog.error(TAG, "Should init first !");
            return -1;
        }
        for(VideoSegment seg : mVideoSegmentList) {
            if (seg.mSegmentIndex == segIndex) {
                return start ? seg.mStartPts : seg.mEndPts;
            }
        }
        return -1;
    }

    public int getMarkDeleteSegIndex() {
        return mVideoSegmentIndexToDelete;
    }

    public long getTimePointToDelete() {
        return mTimePointToDelete;
    }

    //获取YYPacket中保存的body data info
    public void getBodyDetectInfo(List<BodiesDetectInfo> infos) {
        for (VideoSegment videoSegment : mVideoSegmentList) {
            for (int i = 0; i < videoSegment.mVideoDataMap.size(); i++) {
                YYVideoPacket packet = videoSegment.mVideoDataMap.valueAt(i);
                if (packet.mBodyFrameDataArr != null) {
                    BodiesDetectInfo bodiesDetectInfo = new BodiesDetectInfo(packet.pts / 1000);
                    if (packet.mBodyFrameDataArr.length > 0) {
                        for (int j = 0; j < packet.mBodyFrameDataArr.length; j++) {
                            BodiesDetectInfo.BodyDetectInfo bodyDetectInfo = new BodiesDetectInfo.BodyDetectInfo();
                            for (int k = 0; k < packet.mBodyFrameDataArr[j].bodyPointsScore.length; k++) {
                                bodyDetectInfo.mBodyPointsScoreList.add(packet.mBodyFrameDataArr[j].bodyPointsScore[k]);
                            }
                            for (int k = 0; k < packet.mBodyFrameDataArr[j].bodyPoints.length; k++) {
                                bodyDetectInfo.mBodyPointList.add(packet.mBodyFrameDataArr[j].bodyPoints[k]);
                            }
                            bodiesDetectInfo.mBodyDetectInfoList.add(bodyDetectInfo);
                        }
                    }
                    infos.add(bodiesDetectInfo);
                }
            }
        }
    }


    //获取YYPacket中保存的face data info
    public void getFaceDetectInfo(List<FacesDetectInfo> infos) {
        for (VideoSegment videoSegment : mVideoSegmentList) {
            for (int i = 0; i < videoSegment.mVideoDataMap.size(); i++) {
                YYVideoPacket packet = videoSegment.mVideoDataMap.valueAt(i);
                if (packet.mFaceFrameDataArr != null) {
                    FacesDetectInfo facesDetectInfo = new FacesDetectInfo(packet.pts / 1000);
                    if (packet.mFaceFrameDataArr.length > 0) {
                        for (int j = 0; j < packet.mFaceFrameDataArr.length; j++) {
                            FacesDetectInfo.FaceDetectInfo faceDetectInfo = new FacesDetectInfo.FaceDetectInfo();
                            for (int k = 0; k < packet.mFaceFrameDataArr[j].facePoints.length; k++) {
                                faceDetectInfo.mFacePointList.add(packet.mFaceFrameDataArr[j].facePoints[k]);
                            }
                            facesDetectInfo.mFaceDetectInfoList.add(faceDetectInfo);
                        }
                    }
                    infos.add(facesDetectInfo);
                }
            }
        }
    }
}
