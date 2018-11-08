package com.ycloud.gpuimagefilter.utils;

import java.util.List;

/**
 * Created by jinyongqing on 2018/5/15.
 * 抠图封装类，将抠图到cache Data根据pts顺序保存在list中，支持查找与插入等操作
 */
public class SegmentCacheDetectWrapper {
    private static final int DEFAULT_THRESHOLD = 10;
    public List<SegmentCacheData> segmentCacheDataList;

    public SegmentCacheDetectRes findSegmentCacheInsertPos(long currentTimestamp) {
        return findSegmentCacheInsertPos(currentTimestamp, DEFAULT_THRESHOLD);
    }

    public SegmentCacheDetectRes findSegmentCacheInsertPos(long currentTimestamp, int threshold) {
        SegmentCacheDetectRes res = new SegmentCacheDetectRes();

        int start = 0;
        int end = segmentCacheDataList.size();
        int mid;

        while (start < end) {
            mid = (start + end) / 2;
            SegmentCacheData midSegCacheData = segmentCacheDataList.get(mid);
            if (currentTimestamp < midSegCacheData.timestamp) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        res.pos = start;

        //列表查询结果
        long deltaLeft = Integer.MAX_VALUE, deltaRight = Long.MAX_VALUE;
        if (res.pos < segmentCacheDataList.size()) {
            deltaRight = Math.abs(segmentCacheDataList.get(res.pos).timestamp - currentTimestamp);
        }
        if (res.pos > 0) {
            deltaLeft = Math.abs(segmentCacheDataList.get(res.pos - 1).timestamp - currentTimestamp);
        }

        if (Math.min(deltaLeft, deltaRight) < threshold) {
            res.isFound = true;
            if (deltaLeft < deltaRight) {
                res.pos -= 1;
            }
        }
        return res;
    }

    public void segmentCacheSave(SegmentCacheData outCacheData, int insertPos) {
        if (outCacheData != null && outCacheData.bytes > 0) {
            /*YYLog.info(TAG, "jyq test updateSegmentDataWithCache without inCacheData:currentPts=" + sample.mTimestampMs
                    + ",outPts=" + outCacheData.timestamp + ",insertPos=" + pos);*/
            segmentCacheDataList.add(insertPos, outCacheData);
        }
    }

    public static class SegmentCacheDetectRes {
        public boolean isFound;
        public int pos;

        public SegmentCacheDetectRes() {
            isFound = false;
            pos = -1;
        }
    }

    public static class SegmentCacheData {
        public long timestamp;
        public int bytes;
        public byte[] data;
        public int width;
        public int height;
        public int channel;
    }
}
