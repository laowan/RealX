package com.ycloud.utils;

/**
 * Created by kele on 2016/10/17.
 */

public class TimeUtil {

    public static  int getTickCount()
    {
        return (int)(System.nanoTime() / 1000000L);   // as C language clock_gettime(), as MediaLibrary::GetTickCount()
    }

    public static long getTickCountLong()
    {
        return System.nanoTime() / 1000000L;   // as C language clock_gettime(), as MediaLibrary::GetTickCount()
    }
}
