package com.ycloud.utils;

/**
 * Created by zhangbin on 2016/11/28.
 */

public class Timestamp {
    /*微秒 us*/
    public static long getCurTimeInMicroSencods(){
        return   System.nanoTime() / 1000L;
    }
    /*毫秒millisecond*/
    public static long getCurTimeInMillSencods(){
        return   System.currentTimeMillis();
    }
}
