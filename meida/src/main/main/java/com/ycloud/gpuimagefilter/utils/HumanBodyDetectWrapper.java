package com.ycloud.gpuimagefilter.utils;

import java.util.List;

/**
 * Created by jinyongqing on 2018/3/6.
 */

public class HumanBodyDetectWrapper {
    public List<BodiesDetectInfo> bodiesDetectInfoList;

    public HumanBodyDetectRes findBodiesDetectInsertPos(long currentTimestamp) {
        HumanBodyDetectRes res = new HumanBodyDetectRes();

        int start = 0;
        int end = bodiesDetectInfoList.size();
        int mid;

        while (start < end) {
            mid = (start + end) / 2;
            BodiesDetectInfo midFrameDataInfo = bodiesDetectInfoList.get(mid);
            if (currentTimestamp < midFrameDataInfo.mTimeStamp) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        res.pos = start;

        //列表查询结果
        if (res.pos < bodiesDetectInfoList.size() && Math.abs(bodiesDetectInfoList.get(res.pos).mTimeStamp - currentTimestamp) < 40) {
            res.isFound = true;
        } else if (res.pos > 0 && Math.abs(bodiesDetectInfoList.get(res.pos - 1).mTimeStamp - currentTimestamp) < 40) {
            res.isFound = true;
            res.pos -= 1;
        }
        return res;
    }

    public static class HumanBodyDetectRes {
        public boolean isFound;
        public int pos;

        public HumanBodyDetectRes() {
            isFound = false;
            pos = -1;
        }
    }
}
