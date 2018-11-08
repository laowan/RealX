package com.ycloud.gpuimagefilter.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by liuchunyu on 2017/7/27.
 */

public class FilterIDManager {
    static public int NO_ID = -1;

    static private AtomicInteger mFilterID = new AtomicInteger(0);

    static private AtomicInteger mSessionID = new AtomicInteger(0);

    static private AtomicInteger mParameterID = new AtomicInteger(0);


    static public int getFilterID() {
        return mFilterID.getAndIncrement();
    }

    public static void updateFilterID(int filterID) {
        mFilterID.set(filterID);
    }

    static public int getSessionID() {
        return mSessionID.getAndIncrement();
    }

    static public int getParamID() {
        return mParameterID.getAndIncrement();
    }
}
