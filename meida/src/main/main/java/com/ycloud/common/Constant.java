package com.ycloud.common;

public final class Constant {

    public static final String SDK_NAME_PREFIX = "ymrsdk_";
	
    public enum ScaleMode {
        /** 保持图片的比例不变化，可以不显示全部图片，但是layouot区域没有空白边距 */
        AspectFill,
        /** 保持图片比例不变化， 需要显示全部图片， layout可以有空白边距*/
        AspectFit,
        /** 可以改变图片的长宽比列， 拉伸图片， 填充整个layoout区域*/
        ScacleToFill
    }

    public enum WaterMarkOrigin {
        LeftTop, LeftBottom, RightTop, RightBottom
    }

    public static final class MediaNativeResult {
        public static final int FFMPEG_EXECUTE_SUCCESS = 0;
        public static final int FFMPEG_EXECUTE_FAIL = 1;
        public static final int FFMPEG_PRE_CMD_RUNNING = 2;
        public static final int FFMPEG_EXECUTE_ERROR = -1;
    }

    public static final class CaptureVideoOrientation {
        public static final int Portrait = 1;
        public static final int PortraitUpsideDown = 2;
        public static final int LandscapeRight = 3;
        public static final int LandscapeLeft = 4;
    }
    
    public static final class MediaLibraryVideoCodec {
		public final static int kMediaLibraryVideoCodecUnknown  = 0;
        public final static int kMediaLibraryVideoCodecPicture  = 1;
        public final static int kMediaLibraryVideoCodecH264     = 2;
        public final static int kMediaLibraryVideoCodecVP8      = 4;
    	public final static int kMediaLibraryVideoCodecH265     = 5;
    }


    public enum OrientationType {
        Normal, //Normal-不旋转,
        Auto, //；Auto-按长宽比旋转，使得裁剪掉的视频面积最小
        Forace, //强制旋转
    };

    public static class EncoderState {
        public static final int EncoderStateInit = 0;
        public static final int EncoderStateStarting = 1;
        public static final int EncoderStateStarted =2;
        public static final int EncoderStateStoped =3;
        public static final int EncoderStateError =4;

        public static boolean isStart(int state) {
            return (state == EncoderStateStarting || state == EncoderStateStarted);
        }

        public static boolean isStoped(int state) {
            return (state==EncoderStateStoped);
        }

        public static boolean blockStream(int state) {
            return (state==EncoderStateStoped || state == EncoderStateInit);
        }
    }

    public static final String MEDIACODE_DECODER = "[Decoder]";
    public static final String MEDIACODE_RENDER = "[Render]";
    public static final String MEDIACODE_ENCODER = "[Encoder]";
    public static final String MEDIACODE_CAP = "[Capture]";
    public static final String MEDIACODE_PREPRO = "[Preprocess]";
    public static final String MEDIACODE_CAMERA = "[Camera]";
    public static final String MEDIACODE_VIEW = "[View]"; //UI view的变化，譬如说Preview
    public static final String MEDIACODE_STAT = "[VideoStat]"; //统计相关
    public static final String MEDIACODE_TRANSCODE = "[Transcode]"; //转码
    public static final String MEDIACODE_PLAYER_FILTER = "[PlayerFilter]"; //播放器filter
    public static final String MEDIACODE_SNAPSHOT = "[Snapshot]"; //截图
    public static final String MEDIACODE_MUXER = "[MediaMuxer]"; //media muxer
    public static final String MEDIACODE_PTS_SYNC = "[PtsSync]"; //音视频同步相关日志
    public static final String MEDIACODE_SERVER_PARAM = "[DynParam]"; //服务端下方参数
    public static final String MEDIACODE_PTS_EXPORT = "[PtsExport]"; //导出pts

}
