package com.ycloud.audio;

import java.util.Arrays;

public class SilenceWaveFileGenerator {
    public SilenceWaveFileGenerator() {
    }

    static public void write(String path, int sampleRate, int channelCount, long lenMS) {
        WavFileWriter wavFileWriter = new WavFileWriter();
        wavFileWriter.open(path, sampleRate, channelCount);
        int frameLenMS = 20;
        long frameLen = sampleRate * channelCount * 2 * frameLenMS / 1000;
        byte[] frame = new byte[(int) frameLen];
        Arrays.fill(frame, (byte) 0);
		// debug only
        //for(int i = frame.length-1; i >= 0; i--) {
        //    frame[i] = (byte)(i + 1024);
        //}

        while (lenMS > frameLenMS) {
            wavFileWriter.write(frame, 0, (int) frameLen);
            lenMS -= frameLenMS;
        }

        long leftLen = sampleRate * channelCount * 2 * lenMS / 1000;
        if (leftLen > 0) {
            wavFileWriter.write(frame, 0, (int) leftLen);
        }
        wavFileWriter.close();
    }

    static public void write(String path, long lenMS) {
        write(path, AudioTrackWrapper.kSAMPLE_RATE, AudioTrackWrapper.kCHANNEL_COUNT, lenMS);
    }
}
