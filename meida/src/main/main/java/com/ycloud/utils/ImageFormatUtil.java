package com.ycloud.utils;

import java.nio.ByteBuffer;

/**
 * Created by kele on 2017/4/17.
 */

public class ImageFormatUtil {
    public static native void RBGAtoYUV(byte[] argb, int width, int height, byte[] yuv);
    public static native void RAGABufferToYUV(ByteBuffer argb, int byteOffset, int with, int height, byte[] yuv);
}
