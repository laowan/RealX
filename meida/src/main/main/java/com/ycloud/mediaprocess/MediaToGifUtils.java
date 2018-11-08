package com.ycloud.mediaprocess;

import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.utils.YYLog;

public class MediaToGifUtils extends MediaBase {
    private final static String TAG = "MediaToGifUtils";

    public boolean mp4ToGif(String mp4Path, String gifOutPath, int width, int height, int startPos, int duration, int fps) {
        setTotalFrame(fps * duration);
        setExcuteCmdId(MediaNative.libffmpeg_cmd_transcode);
        StringBuilder cmd = new StringBuilder("ffmpeg ");
        if (startPos > 0 && duration > 0) {
            cmd.append("-y -ss ").append(startPos).append(" -t ").append(duration);
        }
        cmd.append(" -i ").append(mp4Path);
        cmd.append(" -filter_complex ").append("\"").append("[0:v] fps=").append(fps).
                append(",scale=").append(width).append(":").append(height).
                append(",split [a][b];[a] palettegen [p];[b][p] paletteuse").append("\"");
        cmd.append(" -f ").append("gif ").append(gifOutPath);
        String cmdStr = cmd.toString();
        YYLog.debug(TAG, "mp4ToGif:" + cmdStr);

        return executeCmd(cmd.toString());
    }
}


