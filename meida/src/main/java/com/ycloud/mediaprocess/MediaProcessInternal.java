package com.ycloud.mediaprocess;

import android.text.TextUtils;

import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProcess;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.utils.MediaUtils;

/**
 *
 */

public class MediaProcessInternal extends MediaBase {
    public static final String TAG = MediaProcessInternal.class.getSimpleName();
    private static final String CMD_PREFIX = "ffmpeg -y -i ";
    private static final String CMD_PREFIX_EX = "ffmpeg ";

    public MediaProcessInternal() {
        setExcuteCmdId(MediaNative.libffmpeg_cmd_video_cut);
    }

    public boolean extractAudioTrack(String inputPath, String outputPath) {
        String cmd = CMD_PREFIX + "\"" + inputPath + "\" -vn \"" + outputPath + "\"";
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
        String cmd = CMD_PREFIX + "\"" + inputPath + "\" -ss " + startTime + " -t " + duration + " \"" + outputPath + "\"";
        return executeCmd(cmd);
    }

    public boolean clipVideo(String inputPath, String outputPath, double startTime, double duration) {
        if (TextUtils.isEmpty(inputPath) || TextUtils.isEmpty(outputPath)) {
            return false;
        }

        MediaInfo mediaInfo = MediaUtils.getMediaInfo(inputPath);
        if (mediaInfo != null) {
            setTotalFrame((int) (mediaInfo.frame_rate * duration));
        }

        String cmd = CMD_PREFIX_EX + " -ss " + startTime + " -t " + duration + " -y -i \"" + inputPath + "\"  -acodec copy -vcodec copy "+ outputPath + "\"";
        return executeCmd(cmd);
    }
}

