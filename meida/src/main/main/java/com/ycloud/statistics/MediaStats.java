package com.ycloud.statistics;

import com.ycloud.utils.YYLog;

/**
 * Created by Administrator on 2018/1/11.
 */

public class MediaStats {
    public int mVideoInputFrameCnt = 0;
    public int mVideoDecocderOutputFrameCnt = 0;
    public int mVideoEncodeInputFrameCnt = 0;
    public int mVideoEncodOutputFrameCnt = 0;
    public int mVideoMuxInputFrameCnt = 0;
    public int mGlInputFrameCnt = 0;
    public int mGlOutputFrameCnt = 0;

    public long mGlReaderCostMs = 0;
    public long mGlProcessCostMs  = 0;  //include time of gl reader.
    public long mEncodeCostMs = 0;
    public long mTextureUpdateImageCost = 0;
    public long mTextureCliperCost = 0;

    public long mBeginTimeStampMs = 0;
    public long mEndTimeStampMs = 0;

    public long mSoftEncodeThreadIdleTimeMs = 0;
    public long mSoftEncodeThreadBreakTime = 0;

    public long mRBGAToYUVCostMs = 0;

    public void onVideoFrameInput() {
        mVideoInputFrameCnt++;
    }

    public  void onVideoFrameDecodeOutput() {
        mVideoDecocderOutputFrameCnt++;
    }

    public void onVideoEncodeInput() {
        mVideoEncodeInputFrameCnt++;
    }

    public void onVideoEncodeOutput(int frameCnt) {
        mVideoEncodOutputFrameCnt += frameCnt;
    }

    public void onVideoMuxInput() {
        mVideoMuxInputFrameCnt++;
    }

    public void onGLProcessInput() {
        mGlInputFrameCnt++;
    }

    public void onGLProcessOutput() {
        mGlOutputFrameCnt++;
    }

    public void setBeginTimeStamp(long timeStampMs) {
        mBeginTimeStampMs = timeStampMs;
    }

    public void setmEndTimeStamp(long timeStampMs) {
        mEndTimeStampMs = timeStampMs;
    }

    public void addGLReaderCost(long cost) {
        mGlReaderCostMs += cost;
    }

    public void addEncodeCost(long cost) {
        mEncodeCostMs += cost;
    }

    public void addRGBAToYUVCost(long cost) { mRBGAToYUVCostMs += cost;}

    public void addSoftEncodeThreadBreakCount() {
        mSoftEncodeThreadBreakTime++;
    }

    public void addSoftEncodeThreadIdleTime(long timeMs) {
        mSoftEncodeThreadIdleTimeMs += timeMs;
    }

    public void addGLProcessCost(long cost) {
        mGlProcessCostMs += cost;
    }

    public void addTextureUpdateImageCost(long cost) {
        mTextureUpdateImageCost += cost;
    }

    public void addClipTextureCost(long cost) {
        mTextureCliperCost += cost;
    }

    public void dump() {
        StringBuilder sb = new StringBuilder("MediaStats:");
        sb.append("InputFrameCnt - ").append( mVideoInputFrameCnt);
        sb.append(" DecoderOutputFrameCnt - ").append(mVideoDecocderOutputFrameCnt);
        sb.append(" GlInputFrameCnt - ").append(mGlInputFrameCnt);
        sb.append(" GlOutputFrameCnt - ").append(mGlOutputFrameCnt);
        sb.append( " EncodeInputFrameCnt - ").append(mVideoEncodeInputFrameCnt);
        sb.append(" EncodeOutputFrameCnt - ").append(mVideoEncodOutputFrameCnt);
        sb.append(" GlReaderCost - ").append(mGlReaderCostMs);
        sb.append(" mRGBA2YUVCost - ").append(mRBGAToYUVCostMs);
        sb.append(" GlTextureUpdateImageCost - ").append(mTextureUpdateImageCost);
        sb.append(" GlTextureCliperCost - ").append(mTextureCliperCost);
        sb.append(" GlProcessCost - ").append(mGlProcessCostMs);
        sb.append( " EncodeCost - ").append(mEncodeCostMs);
        sb.append(" SofTEncodeThreadIdle - ").append(mSoftEncodeThreadIdleTimeMs);
        sb.append(" SoftEncodeProcessdBreak - ").append(mSoftEncodeThreadBreakTime);
        sb.append(" TotalCost(except mux) - ").append(mEndTimeStampMs - mBeginTimeStampMs);

        YYLog.info(this, sb.toString());
    }

}
