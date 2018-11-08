package com.ycloud.gpuimagefilter.utils;

import java.util.List;

/**
 * Created by jinyongqing on 2018/4/9.
 */

public class FaceDetectWrapper {
    public List<FacesDetectInfo> facesDetectInfoList;

    public FaceDetectRes findFacesDetectInsertPos(long currentTimestamp) {
        FaceDetectRes res = new FaceDetectRes();

        int start = 0;
        int end = facesDetectInfoList.size();
        int mid;

        while (start < end) {
            mid = (start + end) / 2;
            FacesDetectInfo midFrameDataInfo = facesDetectInfoList.get(mid);
            if (currentTimestamp < midFrameDataInfo.mTimeStamp) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        res.pos = start;

        //列表查询结果
        if (res.pos < facesDetectInfoList.size() && Math.abs(facesDetectInfoList.get(res.pos).mTimeStamp - currentTimestamp) < 40) {
            res.isFound = true;
        } else if (res.pos > 0 && Math.abs(facesDetectInfoList.get(res.pos - 1).mTimeStamp - currentTimestamp) < 40) {
            res.isFound = true;
            res.pos -= 1;
        }
        return res;
    }

    public static class FaceDetectRes {
        public boolean isFound;
        public int pos;

        public FaceDetectRes() {
            isFound = false;
            pos = -1;
        }
    }
}
