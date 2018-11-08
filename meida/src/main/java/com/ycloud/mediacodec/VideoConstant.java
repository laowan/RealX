package com.ycloud.mediacodec;

/**
 * Created by kele on 2017/4/17.
 */

public class VideoConstant {

    public static final class MediaLibraryPictureFormat {
        public final static int kMediaLibraryPictureFmtUnknown = 0;
        public final static int kMediaLibraryPictureFmtI410= 1;  /* Planar YUV 4:1:0 Y:U:V */
        public final static int kMediaLibraryPictureFmtI411= 2;  /* Planar YUV 4:1:1 Y:U:V */
        public final static int kMediaLibraryPictureFmtI420= 3;  /* Planar YUV 4:2:0 Y:U:V 8-bit */
        public final static int kMediaLibraryPictureFmtI422= 4;  /* Planar YUV 4:2:2 Y:U:V 8-bit */
        public final static int kMediaLibraryPictureFmtI440= 5;  /* Planar YUV 4:4:0 Y:U:V */
        public final static int kMediaLibraryPictureFmtI444= 6;  /* Planar YUV 4:4:4 Y:U:V 8-bit */
        public final static int kMediaLibraryPictureFmtNV12= 7;  /* 2 planes Y/UV 4:2:0 */
        public final static int kMediaLibraryPictureFmtNV21= 8; /* 2 planes Y/VU 4:2:0 */
        public final static int kMediaLibraryPictureFmtNV16= 9; /* 2 planes Y/UV 4:2:2 */
        public final static int kMediaLibraryPictureFmtNV61= 10; /* 2 planes Y/VU 4:2:2 */
        public final static int kMediaLibraryPictureFmtYUYV= 11; /* Packed YUV 4:2:2, Y:U:Y:V */
        public final static int kMediaLibraryPictureFmtYVYU= 12; /* Packed YUV 4:2:2, Y:V:Y:U */
        public final static int kMediaLibraryPictureFmtUYVY= 13; /* Packed YUV 4:2:2, U:Y:V:Y */
        public final static int kMediaLibraryPictureFmtVYUY= 14; /* Packed YUV 4:2:2, V:Y:U:Y */
        public final static int kMediaLibraryPictureFmtRGB15 = 15; /* 15 bits RGB padded to 16 bits */
        public final static int kMediaLibraryPictureFmtRGB16 = 16; /* 16 bits RGB */
        public final static int kMediaLibraryPictureFmtRGB24 = 17; /* 24 bits RGB */
        public final static int kMediaLibraryPictureFmtRGB32 = 18; /* 24 bits RGB padded to 32 bits */
        public final static int kMediaLibraryPictureFmtRGBA = 19;  /* 32 bits RGBA */
    }

    public static final class VideoFrameType {
        public final static int kVideoUnknowFrame = 0xFF;   // 8bits
        public final static int kVideoIFrame = 0;
        public final static int kVideoPFrame = 1;
        public final static int kVideoBFrame = 2;
        public final static int kVideoPFrameSEI = 3;        // 0 - 3 is same with YY video packet's frame type.
        public final static int kVideoIDRFrame = 4;
        public final static int kVideoSPSFrame = 5;
        public final static int kVideoPPSFrame = 6;
        public final static int kVideoHeaderFrame = 7;
        public final static int kVideoEncodedDataFrame = 8;

        public static final int kVideoH265HeadFrame = 9;

        //rgb or yuv data
        public static final int kVideoFrameNodeYV12 = 100;
        public static final int kVideoFrameNodeNV12 = 101;
        public static final int kVideoFrameNodeNV21 = 102;
    }

    public static  class VideoEncodePreset
    {
        public static final long VIDEO_ENCODE_PRESET_DEFAULT										= 0;
        public static final long VIDEO_ENCODE_PRESET_ULTRAFAST										= 1;
        public static final long VIDEO_ENCODE_PRESET_SUPERFAST										= 2;
        public static final long VIDEO_ENCODE_PRESET_VERYFAST										= 3;
        public static final long VIDEO_ENCODE_PRESET_FASTER											= 4;
        public static final long VIDEO_ENCODE_PRESET_FAST											= 5;
        public static final long VIDEO_ENCODE_PRESET_MEDIUM											= 6;
        public static final long VIDEO_ENCODE_PRESET_SLOW											= 7;
    };

    public static float[] mtxIdentity = {
            1.0f, 0, 0, 0,
            0, 1.0f, 0, 0,
            0, 0, 1.0f, 0,
            0, 0, 0, 1.0f
    };

    public enum ScaleMode {
        FillParent, AspectFit, ClipToBounds
    }

    public static final String H264_MIME = "video/avc";
    public static final String H265_MIME = "video/hevc";
}
