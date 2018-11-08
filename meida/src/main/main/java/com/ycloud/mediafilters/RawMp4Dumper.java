package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.datamanager.YYAudioPacket;
import com.ycloud.datamanager.YYVideoPacket;
import com.ycloud.utils.YYLog;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/4/13.
 */

public class RawMp4Dumper {

    private String TAG = "RawMp4Dumper";

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int exportAVFromMemToMp4(String path) {
        boolean bAudioEnable = false;
        boolean bVideoEnable = false;
        boolean bAudioFinish = false;
        boolean bVideoFinish = false;
        int audioTrackIndex = -1;
        int videoTrackIndex = -1;
        MediaCodec.BufferInfo audioBufferInfo = null;
        MediaCodec.BufferInfo videoBufferInfo = null;
        YYLog.info(TAG, "start exportAVFromMemToMp4 path " + path);
        try {
            long time = System.currentTimeMillis();
            MediaMuxer muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaFormat audioFormat = MixedAudioDataManager.instance().getAudioMediaFormat();
            if (audioFormat != null) {
                bAudioEnable = true;
                audioTrackIndex = muxer.addTrack(audioFormat);
                audioBufferInfo = new MediaCodec.BufferInfo();
                MixedAudioDataManager.instance().seekToForExport(0,0);
            } else {
                YYLog.warn(TAG, "audioFormat == null");
                bAudioFinish = true;
            }

            MediaFormat videoFormat = VideoDataManager.instance().getVideoMediaFormat();
            if (videoFormat != null) {
                bVideoEnable = true;
                videoTrackIndex = muxer.addTrack(videoFormat);
                videoBufferInfo = new MediaCodec.BufferInfo();
                VideoDataManager.instance().seekToForExport(0,0);
            } else {
                YYLog.warn(TAG, "videoFormat == null");
                bVideoFinish = true;
            }

            if (!bVideoEnable && !bAudioEnable) {
                YYLog.error(TAG, " bVideoEnable " + bVideoEnable+ " bAudioEnable " + bAudioEnable);
                return -1;
            }

            try {
                muxer.start();
            } catch (IllegalStateException e) {
                YYLog.error(TAG,"MediaMuxer start failed,"+e.getMessage());
            }

            while (true) {
                if (bVideoEnable && !bVideoFinish) {
                    if (writeVideoToMp4(muxer, videoBufferInfo, videoTrackIndex) < 0) {
                        bVideoFinish = true;
                    }
                }
                if (bAudioEnable && !bAudioFinish) {
                    if (writeAudioToMp4(muxer, audioBufferInfo, audioTrackIndex) < 0) {
                        bAudioFinish = true;
                    }
                }
                if (bVideoFinish && bAudioFinish) {
                    break;
                }
            }

            try {
                muxer.stop();
            } catch (IllegalStateException e) {
                YYLog.error(TAG,"MediaMuxer stop failed,"+e.getMessage());
            }

            muxer.release();
            YYLog.info(TAG, "exportToMp4 cost " + (System.currentTimeMillis() - time));
            return 0;
        } catch (IOException e) {
            YYLog.error(TAG, "IOException : " + e.getMessage());
        }
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int writeAudioToMp4(MediaMuxer muxer, MediaCodec.BufferInfo audioBufferInfo, int audioTrackIndex) {
        YYAudioPacket packet = MixedAudioDataManager.instance().readSampleDataForExport();
        if (null != packet) {
            packet.mDataByteBuffer.rewind();
            //YYLog.error(TAG, "write to Mp4 size : " + packet.mBufferSize + " offset " + packet.mBufferOffset + " pts " + packet.pts );
            ByteBuffer inputBuffer = ByteBuffer.allocate(packet.mBufferSize);
            inputBuffer.clear();
            inputBuffer.put(packet.mDataByteBuffer.array(), packet.mBufferOffset, packet.mBufferSize);
            inputBuffer.rewind();
            packet.mDataByteBuffer.rewind();
            audioBufferInfo.flags = packet.mBufferFlag;
            audioBufferInfo.offset = packet.mBufferOffset;
            audioBufferInfo.presentationTimeUs = packet.pts;
            audioBufferInfo.size = packet.mBufferSize;
            try {
                muxer.writeSampleData(audioTrackIndex, inputBuffer, audioBufferInfo);
            }catch (IllegalStateException e) {
                YYLog.error(TAG, "IllegalStateException " + e.toString() + " " + e.getMessage());
            } catch (IllegalArgumentException e) {
                YYLog.error(TAG, "IllegalArgumentException " + e.toString() + " " + e.getMessage());
            }
            MixedAudioDataManager.instance().advanceForExport();
            return 0;
        } else {
            return -1;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int writeVideoToMp4(MediaMuxer muxer, MediaCodec.BufferInfo videoBufferInfo, int videoTrackIndex) {
        YYVideoPacket packet = VideoDataManager.instance().readSampleDataForExport();
        if (null != packet) {
            packet.mDataByteBuffer.rewind();
            //YYLog.error(TAG, "write to Mp4 size : " + packet.mBufferSize + " offset " + packet.mBufferOffset + " pts " + packet.pts );
            ByteBuffer inputBuffer = ByteBuffer.allocate(packet.mBufferSize);
            inputBuffer.clear();
            inputBuffer.put(packet.mDataByteBuffer.array(), packet.mBufferOffset, packet.mBufferSize);
            inputBuffer.rewind();
            packet.mDataByteBuffer.rewind();
            videoBufferInfo.flags = packet.mBufferFlag;
            videoBufferInfo.offset = packet.mBufferOffset;
            videoBufferInfo.presentationTimeUs = packet.pts;
            videoBufferInfo.size = packet.mBufferSize;
            try {
                muxer.writeSampleData(videoTrackIndex, inputBuffer, videoBufferInfo);
            }catch (IllegalStateException e) {
                YYLog.error(TAG, "IllegalStateException " + e.toString() + " " + e.getMessage());
            } catch (IllegalArgumentException e) {
                YYLog.error(TAG, "IllegalArgumentException " + e.toString() + " " + e.getMessage());
            }
            VideoDataManager.instance().advanceForExport();
            return 0;
        } else {
            return -1;
        }
    }

}
