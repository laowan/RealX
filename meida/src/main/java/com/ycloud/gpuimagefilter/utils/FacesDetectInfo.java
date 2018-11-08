package com.ycloud.gpuimagefilter.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jinyongqing on 2018/4/9.
 */

public class FacesDetectInfo {
    public long mTimeStamp;
    public List<FaceDetectInfo> mFaceDetectInfoList;

    public FacesDetectInfo(long timeStamp) {
        mTimeStamp = timeStamp;
        mFaceDetectInfoList = new ArrayList<>();
    }


    public static class FaceDetectInfo {
        public List<Float> mFacePointList;

        public FaceDetectInfo() {
            mFacePointList = new ArrayList<>();
        }
    }
}
