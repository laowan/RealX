package com.ycloud.datamanager;

import android.annotation.TargetApi;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import com.ycloud.utils.YYLog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ycloud.mediacodec.VideoConstant.VideoFrameType.kVideoBFrame;
import static com.ycloud.mediacodec.VideoConstant.VideoFrameType.kVideoIDRFrame;
import static com.ycloud.mediacodec.VideoConstant.VideoFrameType.kVideoPFrame;
import static com.ycloud.mediacodec.VideoConstant.VideoFrameType.kVideoUnknowFrame;

/**
 * Created by Administrator on 2018/1/8.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaDataExtractor {
    private static final String TAG = "MediaDataExtractor";
    private android.media.MediaExtractor mediaExtractor;
    private int audioTrackIndex = -1;
    private int videoTrackIndex = -1;
    private int maxBufferSize = 1024 * 1024;
    private AtomicBoolean mInited = new AtomicBoolean(false);
    public enum MediaDataType {
        MEDIA_DATA_TYPE_VIDEO,
        MEDIA_DATA_TYPE_AUDIO,
    };

    public int init(String MP4_PATH) {
        try {
            mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(MP4_PATH);
            int trackCount = mediaExtractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                String mineType = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mineType.startsWith("video/") && videoTrackIndex == -1) {  // only select the first match one
                    videoTrackIndex = i;
                    YYLog.info(TAG,"find video index: " + videoTrackIndex);
                    VideoDataManager.instance().writeMediaFormat(trackFormat);
                }
                if (mineType.startsWith("audio/") && audioTrackIndex == -1) {  // only select the first match one
                    audioTrackIndex = i;
                    YYLog.info(TAG,"find audio index: " + audioTrackIndex);
                    AudioDataManager.instance().writeMediaFormat(trackFormat);
                }
            }
            mInited.set(true);
        } catch (IOException e) {
            YYLog.error(TAG, " Exception :" + e.toString() + " what " + e.getMessage());
            return -1;
        }
        if (videoTrackIndex == -1 && audioTrackIndex == -1) {
            YYLog.error(TAG, "Not Found video / audio in file " + MP4_PATH);
            return -1;
        }
        return 0;
    }

    private int selectTrackIndex(MediaDataType type) {
        int trackIndex = (type == MediaDataType.MEDIA_DATA_TYPE_VIDEO ? videoTrackIndex : audioTrackIndex);
        if (trackIndex == -1) {
            return -1;
        }
        mediaExtractor.selectTrack(trackIndex);
        return 0;
    }

    private int fetchFrameType(int value) {
        int type = value & 0x1f;
        int frametype = kVideoUnknowFrame;
        switch (type) {
            case 1:
                if (value == 1) {
                    frametype = kVideoBFrame;
                    break;
                }
            case 2:
            case 3:
            case 4:
                frametype = kVideoPFrame;
                break;
            case 5:
                frametype = kVideoIDRFrame;
                break;
            case 9:
                break;
            default:
                frametype = kVideoIDRFrame;
                break;
        }
        return frametype;
    }

    private void setFrameType(YYVideoPacket packet) {
        packet.mDataByteBuffer.position(packet.mBufferOffset);
        if(packet.mDataByteBuffer.remaining() > 4) {
            int frameTypeValue = packet.mDataByteBuffer.get(4);
            packet.mFrameType = fetchFrameType(frameTypeValue);
        } else {
            packet.mFrameType = kVideoUnknowFrame;
        }
        packet.mDataByteBuffer.position(packet.mBufferOffset);
    }

    private void saveMediaData(MediaDataType type, ByteBuffer buffer,int offset, int size, int flag, long pts) {
        ByteBuffer newBuffer = ByteBuffer.allocate(size);

        //YYLog.info(TAG, "saveMediaData offset " + offset + " size " + size);

        newBuffer.put(buffer.array(),offset, size);
        //newBuffer.put(buffer.array());
        newBuffer.asReadOnlyBuffer();
        newBuffer.position(0);
        newBuffer.limit(size);
        if (type == MediaDataType.MEDIA_DATA_TYPE_AUDIO) {
            YYAudioPacket packet = new YYAudioPacket();
            packet.mDataByteBuffer = newBuffer;
            packet.mBufferFlag = flag;
            packet.mBufferSize = size;
            packet.mBufferOffset = 0;
            packet.pts = pts;
            AudioDataManager.instance().write(packet);
            //YYLog.info(TAG, " saveMediaData audio size " + size + " flag " + flag + " pts " + pts);
        } else if (type == MediaDataType.MEDIA_DATA_TYPE_VIDEO) {
            YYVideoPacket packet = new YYVideoPacket();
            packet.mDataByteBuffer = newBuffer;
            packet.mBufferFlag = flag;
            packet.mBufferSize = size;
            packet.mBufferOffset = 0;
            packet.pts = pts;
            setFrameType(packet);

            VideoDataManager.instance().write(packet);
            //YYLog.info(TAG, "jtzhu saveMediaData video size " + size + " flag " + flag + " pts " + pts);
        }
    }

    public int ExtractorMediaData(MediaDataType type) {
        if (!mInited.get() || selectTrackIndex(type) != 0) {
            return -1;
        }
        int offset = 0;
        long pts = -1;
        ByteBuffer byteBuffer = ByteBuffer.allocate(maxBufferSize);
        while (true) {
            byteBuffer.clear();
            int readSampleSize = mediaExtractor.readSampleData(byteBuffer, offset);
            if (readSampleSize < 0) {
                break;
            }
            if (pts == -1) {
                pts = mediaExtractor.getSampleTime();
            }
            int flag = mediaExtractor.getSampleFlags();
            // ONLY supported above Android 8.0 (API level 26)
//            if( (flag & android.media.MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0 ) {
//                offset += readSampleSize;
//                continue;
//            }
            //readSampleSize += offset;  // 总大小
            saveMediaData(type, byteBuffer,offset, readSampleSize,flag,pts);
            byteBuffer.clear();
            mediaExtractor.advance();
            offset = 0;
            pts = -1;
        }
        return 0;
    }

    public void deInit() {
        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }
    }

}
