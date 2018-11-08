package com.ycloud.api.process;

import com.ycloud.mediaprocess.AudioTranscodeInternal;

/**
 * Created by DZHJ on 2017/4/14.
 */

public class AudioTranscode {
    AudioTranscodeInternal mAudioTranscode;

    public AudioTranscode() {
        mAudioTranscode = new AudioTranscodeInternal();
    }

    public void setMediaListener(IMediaListener listener) {
        mAudioTranscode.setMediaListener(listener);
    }

    public void setMediaTime(double startTime, double totalTime) {
        mAudioTranscode.setMediaTime(startTime, totalTime);
    }

    public void setPath(String inputPath, String outputPath) {
        mAudioTranscode.setPath(inputPath, outputPath);
    }

    public void transcode() {
        mAudioTranscode.transcode();
    }
}
