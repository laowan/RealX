package com.ycloud.svplayer;

import java.nio.ByteBuffer;

/**
 * Created by DZHJ on 2017/8/26.
 */

public class FrameInfo {
    int bufferIndex;
    ByteBuffer data;
    public long presentationTimeUs;
    public long unityPtsUs;
    boolean endOfStream;
    boolean representationChanged;
    public boolean needDrawImage;
    public boolean drawWithTwoSurface;

    public FrameInfo() {
        clear();
    }

    public void clear() {
        bufferIndex = -1;
        data = null;
        presentationTimeUs = -1;
        endOfStream = false;
        representationChanged = false;
    }

    @Override
    public String toString() {
        return "FrameInfo{" +
                "bufferIndex=" + bufferIndex +
                ", data=" + data +
                ", presentationTimeUs=" + presentationTimeUs +
                ", endOfStream=" + endOfStream +
                ", representationChanged=" + representationChanged +
                '}';
    }
}
