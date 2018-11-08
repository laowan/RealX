package com.ycloud.svplayer;

import java.nio.ByteBuffer;

/**
 * Created by it on 2017/8/5.
 */

public final class MediaInfo {
    public int type = 0;
    public int width = 0;
    public int height = 0;
    public int planeWidth = 0;
    public int planeHeight = 0;
    public int planeSize = 0;
    public int sampleRate = 0;
    public int samples = 0;
    public int channels = 0;
    public int dataLen = 0;
    public ByteBuffer data = null;

    private MediaInfo() {

    }

    public static MediaInfo alloc() {
        MediaInfo info = new MediaInfo();
        info.reset();
        return info;
    }

    public static MediaInfo alloc(int type, int i1, int i2) {
        MediaInfo info = MediaInfo.alloc();
        info.type = type;
        if(isVideo(info)) {
            info.width = i1;
            info.height = i2;
        } else {
            info.sampleRate = i1;
            info.channels = i2;
            // default value
            info.samples = MediaConst.AAC_FRAME_SAMPLES;
        }
        return info;
    }

    public MediaInfo reset() {
        type = 0;
        width = height = 0;
        planeWidth = planeHeight = 0;
        sampleRate = 0;
        samples = channels = 0;
        planeSize = dataLen = 0;
        data = null;
        return this;
    }

    public static boolean isVideo(MediaInfo info) {
        switch (info.type) {
            case MediaConst.FRAME_TYPE_AAC:
            case MediaConst.FRAME_TYPE_PCM:
                return false;
            case MediaConst.FRAME_TYPE_H264:
            case MediaConst.FRAME_TYPE_HEVC:
            case MediaConst.FRAME_TYPE_I420:
            case MediaConst.FRAME_TYPE_NV12:
            case MediaConst.FRAME_TYPE_RGB24:
            case MediaConst.FRAME_TYPE_YYH264:
            case MediaConst.FRAME_TYPE_YYHEVC:
                return true;
            default: break;
        }
        throw new RuntimeException("unknown media type.");
    }

    public MediaInfo copy(MediaInfo info) {
        if (isVideo(info)) {
            type = info.type;
            width = info.width;
            height = info.height;
            planeWidth = (info.planeWidth > info.width) ? info.planeWidth : info.width;
            planeHeight = (info.planeHeight > info.height) ? info.planeHeight : info.height;
            planeSize = (info.planeSize > 0) ? info.planeSize : (planeWidth * planeHeight);
            dataLen = info.dataLen;
        } else {
            type = info.type;
            sampleRate = info.sampleRate;
            samples = info.samples;
            channels = info.channels;
            dataLen = info.dataLen;
        }
        return this;
    }

    public boolean isChanged(MediaInfo info) {
        if(type != MediaConst.FRAME_TYPE_NONE && info.type != type) {
            return true;
        }
        if(isVideo(info)) {
            return (width != info.width || height != info.height) || (planeWidth != info.planeWidth || planeHeight != info.planeHeight);
        } else {
            return (sampleRate != info.sampleRate || info.channels != channels);
        }
    }

    public final String toString() {
        if (isVideo(this)) {
            return String.format("type:%s, frameSize:%dx%d, planeSize:%dx%d", MediaConst.FRAME_TYPE_TEXT[type], width, height, planeWidth, planeHeight);
        } else {
            return String.format("type:%s, sampleRate:%d, samples:%d, channels:%d", MediaConst.FRAME_TYPE_TEXT[type], sampleRate, samples, channels);
        }
    }
}
