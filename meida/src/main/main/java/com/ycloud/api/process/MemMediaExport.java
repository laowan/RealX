package com.ycloud.api.process;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import com.ycloud.datamanager.AudioDataManager;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.datamanager.YYAudioPacket;
import com.ycloud.datamanager.YYVideoPacket;
import com.ycloud.utils.YYLog;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/2/24.
 *
 */

public class MemMediaExport {
    private static final String TAG = "MemMediaExport";

    public static int exportMarkedSegment(String path) {
        return exportAVFromMemToMp4(path, -1);  // -1 表示导出从段起始位置到标记删除位置间的内容
    }

    /**
     * 从内存中导出某一段音视频到MP4文件
     * @param path  MP4 文件路径
     * @param segIndex 视频段索引，从0开始
     */
    public static int exportToMp4BySegment(String path, int segIndex) {
        return exportAVFromMemToMp4(path, segIndex);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static int exportAVFromMemToMp4(String path, int segIndexInput) {
        boolean bAudioEnable = false;
        boolean bVideoEnable = false;
        boolean bAudioFinish = false;
        boolean bVideoFinish = false;
        int audioTrackIndex = -1;
        int videoTrackIndex = -1;
        MediaCodec.BufferInfo audioBufferInfo = null;
        MediaCodec.BufferInfo videoBufferInfo = null;
        long vEndPts = -1;
        long aEndPts = -1;
        int segIndex = -1;
        if (segIndexInput == -1) {
            segIndex = VideoDataManager.instance().getMarkDeleteSegIndex();
            if (segIndex == -1) {
                YYLog.error(TAG, "getMarkDeleteSegIndex -1, error! return .");
                return -1;
            }
        } else {
            segIndex = segIndexInput;
        }
        YYLog.info(TAG, "start exportAVFromMemToMp4 path " + path + " segIndexInput " + segIndexInput + " segIndex " + segIndex);
        try {
            long time = System.currentTimeMillis();
            MediaMuxer muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaFormat audioFormat = AudioDataManager.instance().getAudioMediaFormat();
            if (audioFormat != null) {
                bAudioEnable = true;
                audioTrackIndex = muxer.addTrack(audioFormat);
                audioBufferInfo = new MediaCodec.BufferInfo();
                long pts = AudioDataManager.instance().getPtsBySegIndex(segIndex, true);
                if (segIndexInput == -1) {
                    aEndPts = AudioDataManager.instance().getTimePointToDelete();
                } else {
                    aEndPts = AudioDataManager.instance().getPtsBySegIndex(segIndex, false);
                }
                if (pts == -1 || aEndPts == -1) {
                    YYLog.error(TAG, "audio pts == -1 || vEndPts == -1 , return.");
                    return -1;
                }
                YYLog.info(TAG, "audio pts " + pts + " aEndPts " + aEndPts);
                AudioDataManager.instance().seekToForExport(pts,0);
            } else {
                YYLog.warn(TAG, "audioFormat == null");
                bAudioFinish = true;
            }

            MediaFormat videoFormat = VideoDataManager.instance().getVideoMediaFormat();
            if (videoFormat != null) {
                bVideoEnable = true;
                videoTrackIndex = muxer.addTrack(videoFormat);
                videoBufferInfo = new MediaCodec.BufferInfo();
                long pts = VideoDataManager.instance().getPtsBySegIndex(segIndex, true);
                if (segIndexInput == -1) {
                    vEndPts = VideoDataManager.instance().getTimePointToDelete();
                } else {
                    vEndPts = VideoDataManager.instance().getPtsBySegIndex(segIndex, false);
                }
                if (pts == -1 || vEndPts == -1) {
                    YYLog.error(TAG, "video pts == -1 || vEndPts == -1 , return.");
                    return -1;
                }
                YYLog.info(TAG, "video pts " + pts + " vEndPts " + vEndPts);
                VideoDataManager.instance().seekToForExport(pts,0);
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
                    if (writeVideoToMp4(muxer, videoBufferInfo, videoTrackIndex, vEndPts) < 0) {
                        bVideoFinish = true;
                    }
                }
                if (bAudioEnable && !bAudioFinish) {
                    if (writeAudioToMp4(muxer, audioBufferInfo, audioTrackIndex, aEndPts) < 0) {
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
            YYLog.info(TAG, "exportToMp4 cost " + (System.currentTimeMillis() - time));  // s8+  20s cost 254ms
            return 0;
        } catch (IOException e) {
            YYLog.error(TAG, "IOException : " + e.getMessage());
        }
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static int writeAudioToMp4(MediaMuxer muxer, MediaCodec.BufferInfo audioBufferInfo, int audioTrackIndex, long aEndPts) {
        YYAudioPacket packet = AudioDataManager.instance().readSampleDataForExport();
        if (null != packet && packet.pts <= aEndPts) {
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
            AudioDataManager.instance().advanceForExport();
            return 0;
        } else {
            return -1;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static int writeVideoToMp4(MediaMuxer muxer, MediaCodec.BufferInfo videoBufferInfo, int videoTrackIndex, long vEndPts) {
        YYVideoPacket packet = VideoDataManager.instance().readSampleDataForExport();
        if (null != packet && packet.pts <= vEndPts) {
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
