package com.ycloud.utils;

import com.ycloud.api.common.TransitionInfo;
import com.ycloud.gpuimagefilter.filter.OFEditStickerEffectFilter;
import com.ycloud.player.TransitionPts;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by DZHJ on 2017/7/22.
 */

public class TransitionTimeUtils {

    public static long ptsToUnityPts(long presentationTimeUs, List<TransitionInfo> transitionList, int videoIndex) {

        if(transitionList == null) {
            return  presentationTimeUs;
        }

        long unityPts = 0;
        for (int i = 0; i < videoIndex; i++) {
            unityPts += transitionList.get(i).mVideoDuration * 1000 * 1000;
            unityPts -= (transitionList.get(i).mTransitionDuration * 1000*1000);
        }

        unityPts+=presentationTimeUs;
        unityPts -= (transitionList.get(videoIndex).mTransitionDuration * 1000*1000);

        return unityPts;
    }

    public static TransitionPts unityPtsToPts(long unityPts, List<TransitionInfo> transitionList) {
        if (null == transitionList || 0 == transitionList.size()) {
            return null;
        }

        long unityTransitionStartPts =0;
        int videoIndex = 0;

        while (unityTransitionStartPts < unityPts && ((videoIndex+1) < transitionList.size()) ){
            videoIndex++;
            unityTransitionStartPts += transitionList.get(videoIndex-1).mVideoDuration * 1000 * 1000;
            unityTransitionStartPts -= (transitionList.get(videoIndex).mTransitionDuration * 1000*1000);

        }

        if (videoIndex>0 && unityTransitionStartPts>unityPts) {
            unityTransitionStartPts -= transitionList.get(videoIndex-1).mVideoDuration * 1000 * 1000;
            unityTransitionStartPts += (transitionList.get(videoIndex).mTransitionDuration * 1000*1000);
            videoIndex--;
        }

        long ptsDiff = unityPts - unityTransitionStartPts;

        TransitionPts transitionPts = new TransitionPts();

        if(ptsDiff< transitionList.get(videoIndex).mTransitionDuration*1000*1000){
            //恰好在转场区域
            transitionPts.videoIndex = (videoIndex-1);
            transitionPts.currentPts = (long)(transitionList.get(videoIndex-1).mVideoDuration*1000*1000 - transitionList.get(videoIndex).mTransitionDuration*1000*1000)+ptsDiff;
            transitionPts.nextPts = ptsDiff;
        } else {
            //不在转场区域
            transitionPts.videoIndex = videoIndex;
            transitionPts.currentPts = ptsDiff;
            transitionPts.nextPts = -1;
        }

        return transitionPts;
    }

    public static long getTotalDuration(List<TransitionInfo> transitionList){
        if(transitionList == null) {
            return 0;
        }

        int totalDuration =0;

        for (int i = 0; i < transitionList.size(); i++) {
            totalDuration += transitionList.get(i).mVideoDuration * 1000 * 1000;
            totalDuration -= transitionList.get(i).mTransitionDuration * 1000 * 1000;
        }
        return totalDuration;
    }
}
