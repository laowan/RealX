package com.ycloud.mediacodec.videocodec;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import com.ycloud.common.Constant;
import com.ycloud.mediacodec.VideoConstant;
import com.ycloud.utils.YYLog;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by qingfeng on 16/3/17.
 * ref:
 * https://github.com/Bilibili/ijkplayer/blob/master/android/ijkplayer/ijkplayer-java/src/main/java/
 * tv/danmaku/ijk/media/player/IjkMediaCodecInfo.java
 */
public class OMXDecoderRank {

    static final String TAG = "ymrsdk";

    private static OMXDecoderRank mInstance;
    private static final int RANK_TESTED = 500;
    public static final int RANK_UNKNOWN = 400;
    private static final int RANK_SECURE = 300;
    private static final int RANK_SOFTWARE = 200;
    private static final int RANK_NOT_STANDARD = 100;
    private static final int RANK_NO_SENSE = 0;

    public static class DecoderInfo {
        private String mName;
        private int mRank;

        public DecoderInfo(final String name, final int rank) {
            mName = name;
            mRank = rank;
        }

        public int rank() {
            return mRank;
        }

        public String name() {
            return mName;
        }
    }

    private Map<String, Integer> mKnownCodecList;
    private DecoderInfo mBestDecoder; // cache, only calculate once
    private boolean mBestDecoderInitialized= false;

    static synchronized public OMXDecoderRank instance() {
        if (mInstance == null) {
            mInstance = new OMXDecoderRank();
        }
        return mInstance;
    }

    private OMXDecoderRank() {
        init();
    }

    private void init() {
        mKnownCodecList = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        // qcom
        mKnownCodecList.put("OMX.qcom.video.decoder.avc", RANK_TESTED);
        mKnownCodecList.put("OMX.ittiam.video.decoder.avc", RANK_NO_SENSE); // ?
        // mtk
        mKnownCodecList.put("OMX.MTK.VIDEO.DECODER.AVC", RANK_TESTED);
        // kirin
        mKnownCodecList.put("OMX.IMG.MSVDX.Decoder.AVC", RANK_TESTED);
        // hisilicon
        // huawei p7
        mKnownCodecList.put("OMX.k3.video.decoder.avc", RANK_TESTED);
        // nvidia
        // Nexus 7(grouper, 4.4(19))
        mKnownCodecList.put("OMX.Nvidia.h264.decode", RANK_TESTED);
        mKnownCodecList.put("OMX.Nvidia.h264.decode.secure", RANK_SECURE); // ?
        //
        mKnownCodecList.put("OMX.Exynos.avc.dec", RANK_TESTED);
        mKnownCodecList.put("OMX.Exynos.AVC.Decoder", RANK_TESTED - 1);
        mKnownCodecList.put("OMX.MARVELL.VIDEO.HW.CODA7542DECODER", RANK_TESTED);
        mKnownCodecList.put("OMX.MARVELL.VIDEO.HW.HANTRODECODER", RANK_TESTED);

        mKnownCodecList.put("OMX.SEC.avc.dec", RANK_TESTED);
        mKnownCodecList.put("OMX.SEC.AVC.Decoder", RANK_TESTED - 1);
        mKnownCodecList.put("OMX.SEC.avcdec", RANK_TESTED - 2); // ?
        mKnownCodecList.put("OMX.SEC.avc.sw.dec", RANK_SOFTWARE); // ?

        mKnownCodecList.put("OMX.Intel.VideoDecoder.AVC", RANK_TESTED);
        mKnownCodecList.put("OMX.Intel.hw_vd.h264", RANK_TESTED + 1);

        mKnownCodecList.put("OMX.rk.video_decoder.avc", RANK_TESTED);

        mKnownCodecList.put("OMX.TI.DUCATI1.VIDEO.DECODER", RANK_TESTED);

        mKnownCodecList.put("OMX.amlogic.avc.decoder.awesome", RANK_TESTED);

        mKnownCodecList.put("OMX.ffmpeg.h264.decoder", RANK_SOFTWARE);

        mKnownCodecList.put("OMX.bluestacks.hw.decoder", RANK_NO_SENSE);

        // unknown
        mKnownCodecList.remove("OMX.hantro.81x0.video.decoder");
        mKnownCodecList.remove("OMX.sprd.h264.decoder");
        mKnownCodecList.remove("OMX.BRCM.vc4.decoder.avc");
        mKnownCodecList.remove("OMX.allwinner.video.decoder.avc");
        mKnownCodecList.remove("OMX.brcm.video.h264.hw.decoder");
        mKnownCodecList.remove("OMX.ST.VFM.H264Dec");
        mKnownCodecList.remove("OMX.Action.Video.Decoder");
        mKnownCodecList.remove("OMX.MS.AVC.Decoder");
        mKnownCodecList.remove("OMX.hisi.video.decoder");
        mKnownCodecList.remove("OMX.Infotm.Video.Decoder");
        mKnownCodecList.remove("OMX.NU.Video.Decoder");
        mKnownCodecList.remove("OMX.brcm.video.h264.decoder");
        mKnownCodecList.remove("OMX.hisi.video.decoder.avc");

        // software
        mKnownCodecList.put("OMX.google.h264.decoder", RANK_SOFTWARE);
        mKnownCodecList.put("OMX.google.h264.lc.decoder", RANK_SOFTWARE);
        mKnownCodecList.put("OMX.k3.ffmpeg.decoder", RANK_SOFTWARE);
        mKnownCodecList.put("OMX.ffmpeg.video.decoder", RANK_SOFTWARE);
        mKnownCodecList.put("OMX.sprd.soft.h264.decoder", RANK_SOFTWARE);

        YYLog.info(TAG, Constant.MEDIACODE_DECODER+"OMXDecoderRank knownCodecList "+ Arrays.toString(mKnownCodecList.keySet().toArray()));
    }

    public DecoderInfo getRank(final String codecName) {
        if (codecName == null || "".equals(codecName)) {
            return new DecoderInfo(codecName, RANK_NO_SENSE);
        }
        String name = codecName.toLowerCase(Locale.US);
        int rank;
        if (!name.startsWith("omx.")) {
            rank = RANK_NOT_STANDARD;
        } else if (name.startsWith("omx.pv")) {
            rank = RANK_SOFTWARE;
        } else if (name.startsWith("omx.google.")) {
            rank = RANK_SOFTWARE;
        } else if (name.startsWith("omx.ffmpeg.")) {
            rank = RANK_SOFTWARE;
        } else if (name.startsWith("omx.k3.ffmpeg.")) {
            rank = RANK_SOFTWARE;
        } else if (name.startsWith("omx.avcodec.")) {
            rank = RANK_SOFTWARE;
        } else if (name.startsWith("omx.ittiam.")) {
            // unknown codec in qualcomm SoC
            rank = RANK_NO_SENSE;
        } else {
            Integer knownRank = mKnownCodecList.get(name);
            if (knownRank != null) {
                rank = knownRank;
            } else {
                rank = RANK_UNKNOWN;
            }
        }
        return new DecoderInfo(codecName, rank);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public DecoderInfo getBestDecoder() {
        if (mBestDecoderInitialized) {
            return mBestDecoder;
        }
        try {
            DecoderInfo decoderInfo = null;
            int currentRank = OMXDecoderRank.RANK_UNKNOWN;

            YYLog.debug(TAG, Constant.MEDIACODE_DECODER+"OMXDecoderRank Codec supported count " + MediaCodecList.getCodecCount());
            for (int i = MediaCodecList.getCodecCount() - 1; i >= 0; i--) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) {
                    continue;
                }
                if (!isSupportMime(info, VideoConstant.H264_MIME)) {
                    continue;
                }
                DecoderInfo decoderRankInfo = OMXDecoderRank.instance().getRank(info.getName());
                int rank = decoderRankInfo.rank();

                YYLog.debug(TAG, Constant.MEDIACODE_DECODER+"OMXDecoderRank codec: %s, rank: %d", info.getName(), rank);
                if (rank < OMXDecoderRank.RANK_UNKNOWN) {
                    continue;
                }
                if (rank >= currentRank) {
                    currentRank = rank;
                    decoderInfo = decoderRankInfo;
                    YYLog.info(TAG, Constant.MEDIACODE_DECODER+"OMXDecoderRank codec match: %s, rank: %d", info.getName(), rank);
                }
            }

            mBestDecoder = decoderInfo;
            mBestDecoderInitialized = true;
            return decoderInfo;
        } catch (Throwable e) {
            YYLog.error(TAG, Constant.MEDIACODE_DECODER+"OMXDecoderRank getBestDecoder throwable"+e.getMessage());
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean isSupportMime(MediaCodecInfo info, String mime) {
        String[] types = info.getSupportedTypes();
        for (int j = 0; j < types.length; j++) {
            if (mime.equalsIgnoreCase(types[j])) {
                return true;
            }
        }
        return false;
    }
}
