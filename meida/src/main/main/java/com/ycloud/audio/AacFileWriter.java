package com.ycloud.audio;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Administrator on 2018/2/28.
 */

public class AacFileWriter {
    private FileOutputStream mFileOutputStream;
    private byte[] mAdts = new byte[7];

    public boolean open(String path, int sampleRate, int channels) {
        try {
            mFileOutputStream = new FileOutputStream(path);
            adts_hdr(mAdts, sampleRate, channels);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mFileOutputStream = null;
            return false;
        }
    }

    public int write(byte[] data, int len) {
        adts_hdr_up(mAdts, len);
        if (mFileOutputStream != null) {
            try {
                mFileOutputStream.write(mAdts);
                mFileOutputStream.write(data, 0, len);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public void close() {
        try {
            mFileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static int[] aac_sampling_freq = {96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000,
            0, 0, 0, 0}; /* filling */
    static int _id = 0;
    static int profile = 1;

    static void adts_hdr(byte[] adts, int sampleRate, int nChannelsOut) {
        int srate_idx = 15, i;

        /* sync word, 12 bits */
        adts[0] = (byte) 0xff;
        adts[1] = (byte) 0xf0;

        /* ID, 1 bit */
        adts[1] |= _id << 3;
    /* layer: 2 bits = 00 */

        /* protection absent: 1 bit = 1 (ASSUMPTION!) */
        adts[1] |= 1;

    /* profile, 2 bits */
        adts[2] = (byte) (profile << 6);

        for (i = 0; i < 16; i++)
            if (sampleRate >= (aac_sampling_freq[i] - 1000)) {
                srate_idx = i;
                break;
            }

    /* sampling frequency index, 4 bits */
        adts[2] |= srate_idx << 2;

    /* private, 1 bit = 0 (ASSUMPTION!) */

    /* channels, 3 bits */
        adts[2] |= (byte) ((nChannelsOut & 4) >> 2);
        adts[3] = (byte) ((nChannelsOut & 3) << 6);

    /* adts buffer fullness, 11 bits, 0x7ff = VBR (ASSUMPTION!) */
        adts[5] |= (byte) 0x1f;
        adts[6] = (byte) 0xfc;
    }

    static void adts_hdr_up(byte[] adts, int size) {
    /* frame length, 13 bits */
        int len = size + 7;
        adts[3] |= len >> 11;
        adts[4] = (byte) ((len >> 3) & 0xff);
        adts[5] = (byte) ((len & 7) << 5);
    }
}
