package com.ycloud.gpuimagefilter.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jinyongqing on 2018/3/5.
 */

public class BodiesDetectInfo {
    public long mTimeStamp;
    public List<BodyDetectInfo> mBodyDetectInfoList;

    public BodiesDetectInfo(long timeStamp) {
        mTimeStamp = timeStamp;
        mBodyDetectInfoList = new ArrayList<>();
    }


    public static class BodyDetectInfo {
        public List<Float> mBodyPointList;
        public List<Float> mBodyPointsScoreList;

        public BodyDetectInfo() {
            mBodyPointList = new ArrayList<>();
            mBodyPointsScoreList = new ArrayList<>();
        }
    }

}



