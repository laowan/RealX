package com.ycloud.api.process;

import java.util.List;

/**
 * 配乐文件的路径，相应的声音 和 相应的偏移时间，以及是否消除原声
 */

public class MergeAudioInfo {
    /**
     * 配音文件
     */
    public List<String> inputFiles;

    /**
     * 配音声音设置 0~1f
     */
    public List<Float> volumes;

    /**
     * 自定义偏移时间
     */
    public List<Integer> startTimes;

    /**
     * 是否消除原声
     */
    public boolean replaceAudio;
}
