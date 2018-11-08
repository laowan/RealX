package com.ycloud.mediarecord.audio;

import com.ycloud.utils.YYLog;

/**
 * Created by jinyongqing on 2017/6/23.
 */

public class AudioVoiceChangerToolbox {
    static final String TAG = "AudioVoiceChangerToolbox";

    static {
        try {
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
        }
    }

    public static final int VeoNone = 0;
    public static final int VeoEthereal = 1;
    public static final int VeoThriller = 2;
    public static final int VeoHeavyMetal = 3;
    public static final int VeoLorie = 4;
    public static final int VeoUncle = 5;
    public static final int VeoDieFat = 6;
    public static final int VeoBadBoy = 7;
    public static final int VeoWarCraft = 8;

    private long handle;
    private static AudioVoiceChangerToolbox mAudioVoiceChangerToolbox;

    public static synchronized AudioVoiceChangerToolbox getInstance(){
        if(mAudioVoiceChangerToolbox == null){
            mAudioVoiceChangerToolbox = new AudioVoiceChangerToolbox();
        }
        return mAudioVoiceChangerToolbox;
    }

    private AudioVoiceChangerToolbox() {
        handle = 0;
    }

    public long initWithSampleRate(int sampleRate, int channels) {
        handle = create(sampleRate, channels);
        return  handle;
    }

    public void deInit() {
        destroy(handle);
        handle = 0;
    }

    public int audioEngineProcess(byte[] inOutData) {
        return  process(handle, inOutData);
    }

    public boolean setEffectMode(int mode) {
        return  setVoiceEffectOption(handle, mode);
    }

    private native long create(int sampleRate, int channels);

    private native void destroy(long handle);

    private native int process(long handle, byte[] inOutData);

    private native boolean setVoiceEffectOption(long handle, int mode);

}