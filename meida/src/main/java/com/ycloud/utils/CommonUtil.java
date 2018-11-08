package com.ycloud.utils;

/**
 * Created by Administrator on 2017/7/29.
 */

public class CommonUtil {
    public static int getHighQuad(long l) {
        return (int)(l >> 32);
    }

    public static int getLowQuad(long l) {
        return (int)(l & 0XFFFFFFFF);
    }

    public static long LongFrom(int lowQuad, int highQuad) {
        return (((long)highQuad << 32) | lowQuad);
    }
}
