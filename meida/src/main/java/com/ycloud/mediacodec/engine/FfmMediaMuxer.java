package com.ycloud.mediacodec.engine;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import com.ycloud.VideoProcessTracer;
import com.ycloud.mediacodec.IMediaMuxer;
import com.ycloud.mediafilters.X264SoftEncoderFilter;
import com.ycloud.utils.StringUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Created by Kele on 2017/5/23.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class FfmMediaMuxer implements IMediaMuxer {
    private static String TAG = FfmMediaMuxer.class.getSimpleName();

    static {
        try {
            System.loadLibrary("ffmpeg-neon");
            System.loadLibrary("ycmediayuv");
            System.loadLibrary("ycmedia");
        } catch (UnsatisfiedLinkError e) {
            YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
        }
    }

    private String mfilename = null;
    /* native层需要根据这个值来查找对应的muxer实例，不要改变这个变量的命名*/
    private long mMuxHandle = 0;

    private String mPath = null;

    public static final int MSG_WRITE_SAMPLE = 0x02;
    public static final int MSG_STOP = 0x03;

    public FfmMediaMuxer(String path) throws IOException {
        mfilename = path;
        nativeInitMuxer(path.getBytes());
        //nativeInitMuxer(mfilename.getBytes());
    }

    //addStream
    public int addTrack(MediaFormat format) {
        // format into str...
        String mediaFormat = marshallMediaFormat(format);
        YYLog.info(this, "[ffmux] add track, marshall media fomart - " + mediaFormat);

        nativeSetMeta(VideoProcessTracer.getInstace().generateComment() + "[MediaExportSession]");

        return nativeAddStream(mediaFormat.getBytes());
    }

    public String marshallMediaFormat(MediaFormat format) {
        StringBuilder sb = new StringBuilder();
        if (format.containsKey(MediaFormat.KEY_MIME) && format.getString(MediaFormat.KEY_MIME).startsWith("video")) {
            sb.append("media-type").append("=").append("video").append(":");
            sb.append(MediaFormat.KEY_WIDTH).append("=").append(format.getInteger(MediaFormat.KEY_WIDTH)).append(":");
            sb.append(MediaFormat.KEY_HEIGHT).append("=").append(format.getInteger(MediaFormat.KEY_HEIGHT)).append(":");

            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {

                sb.append(MediaFormat.KEY_FRAME_RATE).append("=").append(format.getInteger(MediaFormat.KEY_FRAME_RATE)).append(":");
            }

            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                sb.append(MediaFormat.KEY_BIT_RATE).append("=").append(format.getInteger(MediaFormat.KEY_BIT_RATE)).append(":");
            }

            if (format.containsKey("csd-0")) {
                ByteBuffer buffer = format.getByteBuffer("csd-0");
                byte[] data = new byte[buffer.remaining()];

                int oldPos = buffer.position();
                buffer.get(data);
                buffer.position(oldPos);
                sb.append("csd-0").append("=").append(StringUtils.bytesToHexString(data)).append(":");
            }

            if (format.containsKey("csd-1")) {
                ByteBuffer buffer = format.getByteBuffer("csd-1");
                byte[] data = new byte[buffer.remaining()];

                int oldPos = buffer.position();
                buffer.get(data);
                buffer.position(oldPos);
                sb.append("csd-1").append("=").append(StringUtils.bytesToHexString(data)).append(":");
            }
        } else if (format.containsKey(MediaFormat.KEY_MIME) && format.getString(MediaFormat.KEY_MIME).startsWith("audio")) {
            sb.append("media-type").append("=").append("audio").append(":");
            //TODO. configure parameter
        }
        return sb.toString();
    }

    public void release() {
        nativeRelease();
    }

    @Override
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, long dtsMs) {
        int keyFlag = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) ? 1 : 0;
        //byteBuffer is directbuffer, has no array.
        nativeWriteSampleData(trackIndex, byteBuf, bufferInfo.offset, bufferInfo.size, keyFlag, bufferInfo.presentationTimeUs / 1000, dtsMs);
    }

    public void setLocation(float latitude, float longitude) {

    }

    public void setOrientationHint(int degree) {

    }

    public void start() {
        nativeStart();
    }

    public void stop() {
        YYLog.info(this, "[ffmux] FFMediaMuxer stop!!");
        nativeStop();
    }

    private native void nativeInitMuxer(byte[] path);

    private native void nativeStart();

    private native void nativeStop();

    private native void nativeSetMeta(String meta);

    private native int nativeAddStream(byte[] mediaFormat);

    private native void nativeWriteSampleData(int streamID, ByteBuffer byteBuffer, int offset, int size, int keyFlag, long pts, long dts);

    private native void nativeRelease();

    private native static void nativeClassInit();

    static {
        nativeClassInit();
    }
}
