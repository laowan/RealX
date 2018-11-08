package com.ycloud.mediacodec.videocodec;

import android.media.MediaFormat;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by lookatmeyou on 2015/1/17.
 */
public class H264DecRender extends HardDecRender {
    static final String mCodecType = "video/avc";
    static String mCodecName = "";
    public static final String crashTsFirst = "H264DecRenderCrashTsFirst";
    public static final String crashTsSecond = "H264DecRenderCrashTsSecond";

    private static final String[] supportedH264HwCodecPrefixes =
            {"OMX.qcom.video.", "OMX.TI.", "OMX.SEC.", "OMX.Exynos.", "OMX.Nvidia.", "OMX.IMG.",
                    "OMX.amlogic", "OMX.MTK.", "OMX.k3."};

    private static final String[] unSupportedH264HwCodecPrefixes =
            {"OMX.Nvidia.h264.decode.secure", "OMX.SEC.avcdec", "OMX.TI.DUCATI1.VIDEO.DECODER", "OMX.SEC.AVC.Decoder"};

    static {
//        mCodecName = findCodecName(mCodecType, supportedH264HwCodecPrefixes, unSupportedH264HwCodecPrefixes, false);
        OMXDecoderRank.DecoderInfo decoderInfo = OMXDecoderRank.instance().getBestDecoder();
        if (decoderInfo != null) {
            mCodecName = decoderInfo.name();
        }
    }

    public H264DecRender(Surface surface, int width, int height) {
        this.mSurface = surface;
        this.mWidth = width;
        this.mHeight = height;
        InitFields();
        reset(mSurface, mWidth, mHeight);
    }

    public H264DecRender(Surface surface) {
        this.mSurface = surface;
        InitFields();
        reset(mSurface, mWidth, mHeight);
    }

    public static boolean upDateCodecIgnoreCodecWhiteList() {
        mCodecName = findCodecName(mCodecType, supportedH264HwCodecPrefixes, unSupportedH264HwCodecPrefixes, true);
        return null != mCodecName;
    }

    public static boolean IsAvailable() {
        return IsAvailable(mCodecName);
    }

    public static String getCodecName() {
        return mCodecName;
    }

    void InitFields() {
        mSecondTsWriten = new AtomicBoolean(false);
        mCrashTsFirst = crashTsFirst;
        mCrashTsSecond = crashTsSecond;
        mNoFrameCnt = 0;
    }

    @Override
    public int reset() {
        return reset(mSurface, mWidth, mHeight);
    }

    @Override
    public int reset(Surface surface, int width, int height) {
        return reset(surface, mCodecName, mCodecType, width, height);
    }

    @Override
    public int reset(Surface surface, MediaFormat inputFormat) {
        return reset(surface,mCodecName,inputFormat);
    }

    @Override
    public long PushFrame(Surface surface, byte[] bf, long pts, boolean isHeader) {
        return PushFrame(surface, mCodecName, mCodecType, bf, pts, isHeader);
    }
}
