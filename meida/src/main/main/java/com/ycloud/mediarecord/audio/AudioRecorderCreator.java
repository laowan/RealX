package com.ycloud.mediarecord.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.ycloud.utils.YYLog;

/**
 * Created by zhangbin1 on 2016/12/19.
 */

public class AudioRecorderCreator {

    public static final String TAG = AudioRecorderCreator.class.getSimpleName();
    private static boolean DEBUG = false;/*TODO set false on release */
    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
    };

    private static AudioRecord createAudioRecord(int sampleRate,int channelConfig,int buffer_size,int source){
        YYLog.info("[ymrsdk]", "[audio]createAudioRecord begin");
        AudioRecord audioRecord = null;
        try {
            audioRecord = new AudioRecord(
                    source, sampleRate,
                    channelConfig, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord = null;
                YYLog.error("[ymrsdk]", "audio record is not initialized, source:" + source);
            }
        } catch (final Exception e) {
            YYLog.error("[ymrsdk]", "createAudioRecord exception: "+e.toString());
            e.printStackTrace();
            audioRecord = null;
        }
        YYLog.info("[ymrsdk]", "[audio] createAudioRecord success");
        return audioRecord;
    }

    public static AudioRecord create(int sampleRate,int channelConfig,int buffer_size)
    {
        AudioRecord audioRecord = null;
        /*int audioSource;
        if(MediaRecorder.getAudioSourceMax()>=MediaRecorder.AudioSource.VOICE_RECOGNITION){
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            audioRecord=createAudioRecord(sampleRate,channelConfig,buffer_size,audioSource);
        }else*/{
            for (final int source : AUDIO_SOURCES) {
                YYLog.info("[ymrsdk]","USE AUDIO SOURCE ["+source+"]");
                audioRecord=createAudioRecord(sampleRate,channelConfig,buffer_size,source);
                if (audioRecord != null) break;
            }
        }
        return audioRecord;
    }
}
