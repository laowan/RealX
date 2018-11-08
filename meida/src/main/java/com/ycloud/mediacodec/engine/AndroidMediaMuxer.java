package com.ycloud.mediacodec.engine;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import com.ycloud.mediacodec.IMediaMuxer;
import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/1/11.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AndroidMediaMuxer implements IMediaMuxer
{
    private static final String TAG = "AndroidMediaMuxer";
    private MediaMuxer mMediaMuxer = null;
    private int mVideoTrack = -1;
    private long mLastTimestampUs = 0;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public AndroidMediaMuxer(String path, int format) throws IOException {
        mMediaMuxer = new MediaMuxer(path, format);
    }

    @Override
    public void start() {
        mMediaMuxer.start();
    }

    @Override
    public void stop() {
        mMediaMuxer.stop();
    }

    @Override
    public void release() {
        mMediaMuxer.release();
    }

    @Override
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo,long dtsMs) {
        // It also supports muxing B-frames in MP4 since Android Nougat.
        // Crash: E MPEG4Writer: timestampUs 13539725 < lastTimestampUs 15175759 for Video track
        if(trackIndex == mVideoTrack ) {
            if (bufferInfo.presentationTimeUs < mLastTimestampUs) {
                YYLog.error(TAG, "timestampUs " + bufferInfo.presentationTimeUs +" < lastTimestampUs " + mLastTimestampUs + " Drop it!");
                return;
            }
//            YYLog.info(TAG, " pts " + bufferInfo.presentationTimeUs);
            mLastTimestampUs = bufferInfo.presentationTimeUs;
        }
        mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
    }

    @Override
    public int addTrack(MediaFormat format) {
        int track = mMediaMuxer.addTrack(format);
        if (format.getString(MediaFormat.KEY_MIME).equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            mVideoTrack = track;
            YYLog.info(TAG, "addTrack video : " + mVideoTrack);
        }
        return track;
    }

    @Override
    public void setOrientationHint(int orientationHint) {
        mMediaMuxer.setOrientationHint(orientationHint);
    }
}
