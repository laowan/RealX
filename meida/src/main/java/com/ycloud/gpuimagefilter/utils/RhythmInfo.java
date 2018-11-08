package com.ycloud.gpuimagefilter.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 节拍信息
 * Created by jinyongqing on 2018/2/28.
 */

public class RhythmInfo {
    public List<RhythmInfoBeat> rhythmInfoBeatList = new ArrayList<>();
    public List<RhythmInfoPcm> rhythmInfoPcmList = new ArrayList<>();

    public RhythmInfoPcm findRhythmInfoPcm(long currentTimestampMs) {
        if (rhythmInfoPcmList == null) {
            return null;
        }
        int start = 0;
        int end = rhythmInfoPcmList.size() - 1;
        int mid;
        while ((start + 1) < end) {
            mid = start + (end - start) / 2;
            RhythmInfoPcm midRhythmInfoPcm = rhythmInfoPcmList.get(mid);
            if (midRhythmInfoPcm.time < currentTimestampMs) {
                start = mid;
            } else if (midRhythmInfoPcm.time > currentTimestampMs) {
                end = mid;
            } else {
                end = mid;
            }
        }
        RhythmInfoPcm startRhythmInfoPcm = rhythmInfoPcmList.get(start);
        if (startRhythmInfoPcm.time == currentTimestampMs) {
            return startRhythmInfoPcm;
        }

        RhythmInfoPcm endRhythmInfoPcm = rhythmInfoPcmList.get(end);
        if (Math.abs(endRhythmInfoPcm.time - currentTimestampMs) < 40) {
            return endRhythmInfoPcm;
        }

        if (Math.abs(currentTimestampMs - startRhythmInfoPcm.time) < 40) {
            return startRhythmInfoPcm;
        }
        return null;
    }

    public RhythmInfoBeat findRhythmInfoBeat(long currentTimestampMs) {
        if (rhythmInfoBeatList == null) {
            return null;
        }
        int start = 0;
        int end = rhythmInfoBeatList.size() - 1;
        int mid;
        while ((start + 1) < end) {
            mid = start + (end - start) / 2;
            RhythmInfoBeat midRhythmInfoBeat = rhythmInfoBeatList.get(mid);
            if (midRhythmInfoBeat.time < currentTimestampMs) {
                start = mid;
            } else if (midRhythmInfoBeat.time > currentTimestampMs) {
                end = mid;
            } else {
                end = mid;
            }
        }
        RhythmInfoBeat startRhythmInfoBeat = rhythmInfoBeatList.get(start);
        if (startRhythmInfoBeat.time == currentTimestampMs) {
            return startRhythmInfoBeat;
        }

        RhythmInfoBeat endRhythmInfoBeat = rhythmInfoBeatList.get(end);
        if (Math.abs(endRhythmInfoBeat.time - currentTimestampMs) < 40) {
            return endRhythmInfoBeat;
        }

        if (Math.abs(currentTimestampMs - startRhythmInfoBeat.time) < 40) {
            return startRhythmInfoBeat;
        }
        return null;
    }

    public class RhythmInfoBeat {
        public float time;
        public float quality;
    }


    public class RhythmInfoPcm {
        public float time;
        public float strength;
        public float strength_ratio;
        public float smooth_strength_ratio;
    }
}
