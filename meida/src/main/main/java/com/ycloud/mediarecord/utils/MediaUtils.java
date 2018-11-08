package com.ycloud.mediarecord.utils;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.svplayer.AVStream;

import java.nio.ByteBuffer;

/**
 * Created by DZHJ on 2017/2/9.
 */

public class MediaUtils {

    public static MediaInfo getMediaInfo(String videoPath) {
        MediaInfo info = MediaProbe.getMediaInfo(videoPath, false);
        return info;
    }

    public static final byte[] NALU_START_CODE = {0, 0, 0, 1};

    private static final int[] MPEG4_SAMPLING_FREQUENCE_INDEX = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
    };

    private static final String[] SUPPORT_AUDIO_ENCODE_FORMAT = {
            "mp3",
            "wav",
            "aac",
            "pcm"
    };

    public static long roundup(long x, long align) {
        return ((x + (align - 1)) & ~(align - 1));
    }

    public static long roundown(long x, long align) {
        return (x & ~(align - 1));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaFormat createAacFormat(int sampleRate, int channels) {
        int index = 0;
        for(index = 0;index < MPEG4_SAMPLING_FREQUENCE_INDEX.length;++index) {
            if(sampleRate == MPEG4_SAMPLING_FREQUENCE_INDEX[index]) {
                break;
            }
        }
        if(index >= MPEG4_SAMPLING_FREQUENCE_INDEX.length) {
            return null;
        }
        final int profile = 1;
        byte[] data = new byte[2];
        data[0] = (byte)(((profile << 4) & 0xf0) | ((index >> 1) & 0x0f));
        data[1] = (byte)(((index & 0x01) << 7) | (channels << 3));

        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(data));
        return format;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaFormat createAvcFormat(int width, int height, ByteBuffer buffer) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setByteBuffer("csd-0", yyH264GetSps(buffer));
        format.setByteBuffer("csd-1", yyH264GetPps(buffer));
        format.setByteBuffer("extra-data", yyH264GetExtraData(buffer));
        return format;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaFormat createAAcMediaFormat(AVStream stream) {
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, stream.m_sample_rate, stream.m_channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, stream.m_profile);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int)stream.m_bit_rate);
        format.setInteger("channel-layout", (int)stream.m_channel_layout);
        format.setInteger("sample-fmt", stream.m_format);
        format.setInteger("samples", stream.m_frame_size);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, stream.m_channels);
        ByteBuffer buffer = ByteBuffer.allocateDirect(stream.m_codecSpecDescription.remaining());
        buffer.put(stream.m_codecSpecDescription);
        buffer.flip();
        format.setByteBuffer("csd-0", buffer);
        return format;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaFormat createHevcFormat(int width, int height, ByteBuffer buffer) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        buffer = yyH265GetExtraData(buffer);
        format.setByteBuffer("csd-0", buffer);
        format.setByteBuffer("extra-data", buffer);
        return format;
    }

    public static ByteBuffer yyH264GetExtraData(ByteBuffer buffer) {
/*
 大端4字节sps/pps长度
 sps/pps部分：
 6字节：0x01 sps[0] sps[1] sps[2] 0xff 0xe1
 2字节：大端数据 sps 长度
 sps 数据
 1字节：0x01
 2字节：大端数据 pps 长度
 pps数据
*/
        // mark
        final int limit = buffer.mark().limit();
        int length = buffer.getInt();
        buffer.limit(buffer.position() + length);
        ByteBuffer output = ByteBuffer.allocateDirect(length);
        output.put(buffer);
        // rollback
        buffer.reset().limit(limit);
        return output;
    }

    public static ByteBuffer yyH264GetSps(ByteBuffer buffer) {
/*
 大端4字节sps/pps长度
 sps/pps部分：
 6字节：0x01 sps[0] sps[1] sps[2] 0xff 0xe1
 2字节：大端数据 sps 长度
 sps 数据
 1字节：0x01
 2字节：大端数据 pps 长度
 pps数据
*/
        // mark
        buffer.mark();
        int length = buffer.getInt();
        // skip 6bytes
        buffer.position(buffer.position() + 6);
        //sps
        length = buffer.getShort();
        byte[] data = new byte[length + NALU_START_CODE.length];
        System.arraycopy(NALU_START_CODE, 0, data, 0, NALU_START_CODE.length);
        buffer.get(data, NALU_START_CODE.length, length);
        // rollback
        buffer.reset();
        return ByteBuffer.wrap(data);
    }

    public static ByteBuffer yyH264GetPps(ByteBuffer buffer) {
/*
 大端4字节sps/pps长度
 sps/pps部分：
 6字节：0x01 sps[0] sps[1] sps[2] 0xff 0xe1
 2字节：大端数据 sps 长度
 sps 数据
 1字节：0x01
 2字节：大端数据 pps 长度
 pps数据
*/
        // mark
        buffer.mark();
        int length = buffer.getInt();
        // skip 6bytes
        buffer.position(buffer.position() + 6);
        //sps
        length = buffer.getShort();
        // skip sps + 1byte
        buffer.position(buffer.position() + length + 1);
        //pps
        length = buffer.getShort();
        byte[] data = new byte[length + NALU_START_CODE.length];
        System.arraycopy(NALU_START_CODE, 0, data, 0, NALU_START_CODE.length);
        buffer.get(data, NALU_START_CODE.length, length);
        // rollback
        buffer.reset();
        return ByteBuffer.wrap(data);
    }

    public static int yyH264ConvertFrame(ByteBuffer src, ByteBuffer dst, boolean keyFrame) {
        // mark
        final int limit = src.mark().limit();

        int length = 0, dataLen = 0;
        if(keyFrame) {
            length = src.getInt();
            // skip 6bytes
            src.position(src.position() + 6);
            //sps
            length = src.getShort();
            src.limit(src.position() + length);
            dst.put(NALU_START_CODE).put(src);
            src.limit(limit);
            dataLen += length + NALU_START_CODE.length;

            // skip 1byte
            src.get();
            //pps
            length = src.getShort();
            src.limit(src.position() + length);
            dst.put(NALU_START_CODE).put(src);
            src.limit(limit);
            dataLen += length + NALU_START_CODE.length;
        }
        while(src.position() < limit) {
            length = src.getInt();
            src.limit(src.position() + length);
            dst.put(NALU_START_CODE).put(src);
            src.limit(limit);
            dataLen += length + NALU_START_CODE.length;
        }
        // rollback
        src.reset().limit(limit);
        return dataLen;
    }

    public static ByteBuffer yyAacGenerateHeader(int sampleRate, int channels, int length) {
        int index = 0;
        for(index = 0;index < MPEG4_SAMPLING_FREQUENCE_INDEX.length;++index) {
            if(sampleRate == MPEG4_SAMPLING_FREQUENCE_INDEX[index]) {
                break;
            }
        }
        if(index >= MPEG4_SAMPLING_FREQUENCE_INDEX.length) {
            return null;
        }
        final int profile = 1;
        ByteBuffer buffer = ByteBuffer.allocateDirect(7);
        length += buffer.capacity();

        buffer.put((byte)0xff);
        buffer.put((byte)0xf1);
        byte b = (byte)((profile << 6) & 0xC0);
        b |= (byte)((index << 2) & 0x3C);
        b |= (byte)((channels >> 2) & 0x01);
        buffer.put(b);
        b = (byte)((channels << 6) & 0xC0);
        b |= (byte)((length >> 11) & 0x03);
        buffer.put(b);
        b = (byte)((length >> 3) & 0xFF);
        buffer.put(b);
        b = (byte)((length << 5) & 0x70);
        buffer.put(b);
        buffer.put((byte)0);
        buffer.rewind();
        return buffer;
    }

    public static ByteBuffer yyH265GetExtraData(ByteBuffer buffer) {
        // mark
        final int limit = buffer.mark().limit();
        int length = buffer.getInt();
        buffer.limit(buffer.position() + length);
        ByteBuffer output = ByteBuffer.allocateDirect(length);
        output.put(buffer);
        // rollback
        buffer.reset().limit(limit);
        return output;
    }

    /**
     * 判断当前音频是否是sdk支持的格式
     *
     * @param audioPath 音频路径
     * @return
     */
    public static boolean isSupportAudioFormat(String audioPath) {
        MediaInfo mediaInfo = MediaProbe.getMediaInfo(audioPath, true);
        if (mediaInfo != null) {
            for (String codecName : SUPPORT_AUDIO_ENCODE_FORMAT) {
                if (mediaInfo.audio_codec_name != null && mediaInfo.audio_codec_name.contains(codecName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
