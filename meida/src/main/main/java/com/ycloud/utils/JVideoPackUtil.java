package com.ycloud.utils;

/**
 * Created by kele on 2016/9/30.
 */
public class JVideoPackUtil {

    //主要是剥掉mp4等相关协议头.
    public static native byte[] unpackHeader(byte[] header, int size);
    public static native byte[] unpackFrame(byte[] frame, int size);
    public static native byte[] packHeader(byte[] sps, int spsLen, byte[] pps, int ppsLen );
    public static native void nativeClassInit();

    static {
        nativeClassInit();
    }
}
