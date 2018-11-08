package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.common.Constant;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.ByteVector;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2017/2/14.
 */
public class H265HardwareEncoderFilter extends H264HardwareEncoderFilter {

    public static final int SLICE_IDR = 1;
    public static final int SLICE_I = 2;
    public static final int SLICE_UNKNOW = 255;

    private  int m_outReadNum = 0;
    private  int m_outputFlagPresentFlag = 0;
    private  int m_DepSliceSegEn = 0;
    private  int m_numExtraSliceHeader = 0;

    private ByteVector mBytesVector = new ByteVector(16*1024);

    public H265HardwareEncoderFilter(MediaFilterContext filterContext) {
        super(filterContext);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onEncodedHeaderAvailableSample(ByteBuffer buffer, MediaCodec.BufferInfo buffInfo, long dtsMs, long ptsMs, MediaFormat mediaFormat) {
        byte[] frameData = new byte[buffInfo.size];
        buffer.get(frameData);
        parsePPS(frameData, buffInfo.size);

//        if(mFileRecorder != null) {
//            mFileRecorder.processMediaData(buffer, buffInfo.offset, buffInfo.size);
//        }

        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();

        /**使用mDataBytes传递数据下去.*/
        sample.mYYPtsMillions = 0;
        sample.mDtsMillions = 0;
        sample.mMediaFormat = mediaFormat;
        sample.mFrameFlag = buffInfo.flags;

        sample.mWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH); //TODO;
        sample.mHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT); //TODO;

        sample.mDataByteBuffer = ByteBuffer.wrap(frameData);
        sample.mBufferOffset =0;
        sample.mBufferSize = buffInfo.size;
        sample.mEncoderType = VideoEncoderType.HARD_ENCODER_H265;
        sample.mFrameType = VideoConstant.VideoFrameType.kVideoH265HeadFrame;

        deliverToDownStream(sample);
        handleEncodedFrameStats(sample.mBufferSize, getInputFrameByteSize(), sample.mFrameType);
        sample.decRef();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onEncodedDataAvailableSample(ByteBuffer buffer, MediaCodec.BufferInfo buffInfo, long dtsMs, long ptsMs, MediaFormat mediaFormat) {

        YYLog.debug(this, Constant.MEDIACODE_ENCODER+"H265SurfaceEndoerFilter.OnEncodeDataAvailableSample");
        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();

//        if(mFileRecorder != null) {
//            mFileRecorder.processMediaData(buffer, buffInfo.offset, buffInfo.size);
//        }

        sample.mDtsMillions = dtsMs;
        sample.mYYPtsMillions = ptsMs;
        sample.mMediaFormat = mediaFormat;
        sample.mFrameFlag = buffInfo.flags;

        sample.mWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH); //TODO;
        sample.mHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT); //TODO;

        sample.mDataByteBuffer = buffer;
        sample.mBufferOffset = buffInfo.offset;
        sample.mBufferSize = buffInfo.size;
        sample.mEncoderType = VideoEncoderType.HARD_ENCODER_H265;


        buffer.position(buffInfo.offset);
        mBytesVector.put(buffer, buffInfo.size);
        buffer.position(buffInfo.offset);

        /**
         * TODO. 优化多次视频数据内存copy.
         * 这里提取了buffer中的数据用来计算帧的类型，这个逻辑是要在本filter中来做,
         * 之后的TransmitUploaderFilter中又提取了一次buffer中的数据，用作透传给应用层,
         * 多了一次内存copy， 调试通过再优化. */
        int type = parseSliceType(mBytesVector.getBytes(), mBytesVector.size());
        if (type == SLICE_IDR || type == SLICE_I) {
            sample.mFrameType = VideoConstant.VideoFrameType.kVideoIDRFrame;
        } else {
            sample.mFrameType = VideoConstant.VideoFrameType.kVideoPFrame;
        }
        mBytesVector.clear();

        deliverToDownStream(sample);

        handleEncodedFrameStats(sample.mBufferSize, getInputFrameByteSize(), sample.mFrameType);
        sample.decRef();
    }

    @Override
    public VideoEncoderType getEncoderFilterType() {
        return VideoEncoderType.HARD_ENCODER_H265;
    }

    @Override
    public void onEncoderFormatChanged(MediaFormat mediaFormat) {
        super.onEncoderFormatChanged(mediaFormat);
    }

    @Override
    public void onError(long eid, String errMsg) {
        _OnError(eid, errMsg);
    }

    private static int test_bit(byte addr[], int index) {
        int i = index % 8;
        int num = (int) addr[index >> 3];
        return (int) (num >> (7 - i) & 1);
    }

    private static int read_bits(byte addr[], int start, int length) {
        int result = 0;
        while (length-- > 0)
            result = (result << 1) + test_bit(addr, start++);

        return result;
    }

    private int read_ue(byte addr[], int start) {
        int leadingZeroBits = -1;
        for (int b = 0; b == 0; leadingZeroBits++)
            b = test_bit(addr, start + leadingZeroBits + 1);

        int codeNum = (1 << leadingZeroBits) - 1 + read_bits(addr, start + leadingZeroBits + 1, leadingZeroBits);
        m_outReadNum = leadingZeroBits * 2 + 1;

        return codeNum;
    }


    public  void parsePPS(byte[] frameData, int len) {
        if (frameData == null || len <= 0)
            return;

        int pos = 0;
        for (pos = 0; pos + 5 <= len; ) {   // andd two bytes header
            int oldPos = pos;
            if (frameData[pos] == 0 && frameData[pos + 1] == 0 && frameData[pos + 2] == 0 && frameData[pos + 3] == 1) {
                pos = pos + 4;
            } else if (frameData[pos] == 0 && frameData[pos + 1] == 0 && frameData[pos + 3] == 1) {
                pos = pos + 3;
            }

            if (oldPos == pos) {
                pos++;
                continue;
            }

            int type = (frameData[pos] & 0x7E) >> 1;
            if (type == 34) {
                break;
            }
        }

        if (pos + 5 >= len)
            return;

        pos = (pos + 2) * 8;

        int picId = read_ue(frameData, pos);

        pos += m_outReadNum;
        int seqId = read_ue(frameData, pos);

        pos += m_outReadNum;

        m_DepSliceSegEn = read_bits(frameData, pos++, 1);
        m_outputFlagPresentFlag = read_bits(frameData, pos++, 1);
        m_numExtraSliceHeader = read_bits(frameData, pos, 3);
        pos += 3;

        YYLog.info(this, Constant.MEDIACODE_ENCODER+"H265SurfaceEncoder::parsePPS Type, picId:" + picId + ", seqId:" + seqId + ", m_DepSliceSegEn:" +
                m_DepSliceSegEn + ", m_outputFlagPresentFlag:" + m_outputFlagPresentFlag + ", m_numExtraSliceHeader:" + m_numExtraSliceHeader);
    }

    public  int parseSliceType(byte[] frameData, int len) {
        int ntype = naltype(frameData, len);
        if (ntype < 0) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER+"H265SurfaceEncoder::parseSliceType, unknown, ntype:" + ntype);
            return SLICE_UNKNOW;
        } else  if (ntype >= 16 && ntype <= 23) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER+"H265SurfaceEncoder::parseSliceType, IDR, ntype:" + ntype);
            return SLICE_IDR;
        } else if (ntype >= 32 && ntype <= 34) {
            YYLog.info(this, Constant.MEDIACODE_ENCODER+"H265SurfaceEncoder::parseSliceType, PPS/VPS/SPS, ntype:" + ntype);
            return SLICE_UNKNOW;
        }

        int pos = 0;
        if ((frameData[0] == 0) && (frameData[1] == 0) && (frameData[2] == 0) && (frameData[3] == 1)) {
            pos = 4 * 8;               // startcode : 0001
        } else if (((frameData[0] == 0) && (frameData[1] == 0) && (frameData[2] == 1))) {
            pos = 3 * 8;               // startcode : 001
        } else {
            YYLog.info(this, Constant.MEDIACODE_ENCODER+"H265SurfaceEncoder::parseSliceType, unknown slice type");
            return SLICE_UNKNOW;
        }

        pos += 2 * 8;

        int firstSliceSeg = read_bits(frameData, pos++, 1);
        //int picId = read_ue(frameData, pos);
        pos += m_outReadNum;

        int dependentSliceSeg = 0;
        if (firstSliceSeg == 0) {
            if (m_DepSliceSegEn != 0)
                dependentSliceSeg = read_bits(frameData, pos++, 1);

            read_ue(frameData, pos);
            pos += m_outReadNum;
        }

        if (dependentSliceSeg != 0)
            return SLICE_UNKNOW;

        for (int i = 0; i < m_numExtraSliceHeader; i++) {
            // FIXME:return value of read_bits ignored, comment added by Huangchengzong
            read_bits(frameData, pos++, 1);
        }

        int type = read_ue(frameData, pos);
        pos += m_outReadNum;

        return type == 2? SLICE_I: SLICE_UNKNOW;
    }

    public static int naltype(byte[] frameData, int len) {
        int type = -1;
        if ((frameData[0] == 0) && (frameData[1] == 0) && (frameData[2] == 0) && (frameData[3] == 1)) {
            type = (frameData[4] & 0x7E) >> 1;               // startcode : 0001
        } else if (((frameData[0] == 0) && (frameData[1] == 0) && (frameData[2] == 1))) {
            type = (frameData[3] & 0x7E) >> 1;               // startcode : 001
        }

        return type;
    }


}
