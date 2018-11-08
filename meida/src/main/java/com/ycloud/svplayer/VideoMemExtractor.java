package com.ycloud.svplayer;

import android.media.MediaFormat;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.datamanager.YYVideoPacket;
import java.nio.ByteBuffer;


/**
 * Created by Administrator on 2017/12/30.
 */

public class VideoMemExtractor implements MemExtractor {

    private VideoDataManager mDataManager = null;
    private int mUseType = MediaConst.MEDIA_EXTRACTOR_FOR_PLAYER;

    public VideoMemExtractor() {
        mDataManager = VideoDataManager.instance();
    }

    public void setUseType(int type) {
        mUseType = type;
    }

    public MediaFormat getTrackFormat(int index) {
        return mDataManager.getVideoMediaFormat();
    }

    public void selectTrack(int index) {

    }

    public void unselectTrack(int index) {

    }

    public void seekTo(long timeUs, int mode) {
        if (mUseType == MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT) {
            mDataManager.seekToForExport(timeUs, mode);
        } else {
            mDataManager.seekTo(timeUs, mode);
        }
    }

    public boolean advance() {
        if (mUseType == MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT) {
            return mDataManager.advanceForExport();
        } else {
            return mDataManager.advance();
        }
    }

    public int readSampleData(ByteBuffer byteBuf, int offset) {
        YYVideoPacket packet;
        if (mUseType == MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT) {
            packet = mDataManager.readSampleDataForExport();
        } else {
            packet = mDataManager.readSampleData();
        }

        if(packet == null) {
            return -1;
        }
        byteBuf.clear();
        byteBuf.position(offset);
        byteBuf.put(packet.mDataByteBuffer.array());
        return packet.mBufferSize;
    }


    public int getSampleTrackIndex() {
        return 0;
    }

    public long getSampleTime() {
        if (mUseType == MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT) {
            return mDataManager.getSampleTimeForExport();
        } else {
            return mDataManager.getSampleTime();
        }
    }

    public long getCachedDuration() {
        return mDataManager.getCachedDuration();
    }

    public int getSampleFlags() {
        if (mUseType == MediaConst.MEDIA_EXTRACTOR_FOR_EXPORT) {
            return mDataManager.getSampleFlagsForExport();
        } else {
            return mDataManager.getSampleFlags();
        }
    }
}
