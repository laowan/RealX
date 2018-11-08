package com.ycloud.audio;

/**
 * Created by Administrator on 2018/1/16.
 */

public class AudioSimpleMixer {

    public static short byte2short(byte[] data, int offset) {
        return (short) (((data[offset + 1] << 8)) | ((data[offset] & 0xff)));
    }

    public static void mix(byte[] src, float srcVolume, short[] dst, float dstVolume,  int len) {
        if (Float.compare(srcVolume, 0.0f) == 0 && Float.compare(dstVolume, 1.0f) == 0) {
            return;
        }
        int samples = len / 2;
        for (int i = 0; i < samples; i++) {
            int tmp = (int) (dst[i] * dstVolume + byte2short(src, i * 2) * srcVolume);
            if (tmp > 32767) {
                dst[i] = 32767;
            }else if(tmp < -32768) {
                dst[i] = -32768;
            }else {
                dst[i] = (short) tmp;
            }
        }
    }

    public static void mix(byte[] src, float srcVolume, byte[] dst, float dstVolume, int len) {
        if (Float.compare(srcVolume, 0.0f) == 0 && Float.compare(dstVolume, 1.0f) == 0) {
            return;
        }
        for (int i = 0; i < len / 2; i++) {
            int s = (int) (byte2short(src, i * 2) * srcVolume);
            int d = (int) (byte2short(dst, i * 2) * dstVolume);
            int tmp = s + d;
            if (tmp > 32767) {
                tmp = 32767;
            }else if(tmp < -32768) {
                tmp = -32768;
            }
            dst[i * 2 + 1] = (byte) ((tmp & 0xFF00) >> 8);
            dst[i * 2] = (byte) (tmp & 0x00FF);
        }
    }

    public static void stero2mono(byte[] src, byte[] dst, int len ) {
        int samples = len / 2 / 2;
        for (int i = 0; i < samples; i++) {
            short l = byte2short(src, i * 4);
            short r = byte2short(src, i * 4 + 2 );
            int t = (l + r) / 2;
            dst[i * 2] = (byte) (t & 0x00FF);
            dst[i * 2 + 1] = (byte) ((t & 0xFF00) >> 8);
        }
    }

    public static void mono2stereo(byte[] src, byte[] dst, int len ) {
        int samples = len / 2 ;
        for (int i = 0; i < samples; i++) {
            short t = byte2short(src, i * 2);
            dst[i * 4] = (byte) (t & 0x00FF);
            dst[i * 4 + 1] = (byte) ((t & 0xFF00) >> 8);
            dst[i * 4 + 2] = dst[ i * 4];
            dst[i * 4 + 3] = dst[ i * 4 + 1];
        }
    }

    public static void scale(byte[]src, int offset, int len, float volume) {
        if (Float.compare(volume, 1.0f) != 0) {
            for (int i = offset; i < len;) {
                int value = byte2short(src, i);
                int tmp = (int) (value * volume);
                if (tmp > 32767) {
                    tmp = 32767;
                } else if (tmp < -32768) {
                    tmp = -32768;
                }
                src[i + 1] = (byte) ((tmp & 0xFF00) >> 8);
                src[i] = (byte) (tmp & 0x00FF);
                i += 2;
            }
        }
    }
}
