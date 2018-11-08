package com.ycloud.mediaprocess;

import android.text.TextUtils;

import com.ycloud.mediarecord.MediaBase;

/**
 * Created by jinyongqing on 2017/11/10.
 */

public class AudioProcessInternal extends MediaBase {
    public static final String TAG = AudioProcessInternal.class.getSimpleName();
    private static final String CMD_PREFIX = "ffmpeg -y -i ";

    public boolean extractAudioTrack(String inputPath, String outputPath) {
        String cmd = CMD_PREFIX + "\"" + inputPath + "\" -vn -ar 44100 \"" + outputPath + "\"";
        return executeCmd(cmd);
    }

    public boolean extractOrigAudioTrack(String inputPath, String outputPath) {
        String cmd = CMD_PREFIX + "\"" + inputPath + "\" -vn -acodec copy \"" + outputPath + "\"";
        return executeCmd(cmd);
    }

    public boolean replaceAudioTrack(String videoPath, String newAudioPath, String outputPath) {
        String cmd = CMD_PREFIX + "\"" + videoPath + "\" -i \"" + newAudioPath + "\" -map 0:v:0 -map 1:a:0 -c copy\"" + outputPath + "\"";
        return executeCmd(cmd);
    }

    public boolean clipAudio(String inputPath, String outputPath, double startTime, double duration) {
        if (TextUtils.isEmpty(inputPath) || TextUtils.isEmpty(outputPath)) {
            return false;
        }
        String cmd = CMD_PREFIX + "\"" + inputPath + "\" -ar 44100 -ss " + startTime + " -t " + duration + " \"" + outputPath + "\"";
        return executeCmd(cmd);
    }
}

