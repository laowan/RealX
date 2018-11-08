package com.ycloud.mediafilters;


import com.ycloud.mediacodec.VideoEncoderType;

/**
 * 开播SDK编码回调接口.
 */
public interface IEncoderListener {
    /**
     * 编码码率，帧率统计数据回调接口, 每3秒钟回调一次.
     * @param bitRate       实际编码输出的码率.
     * @param frameRate     实际编码输出的帧率.
     */
    void onEncodeStat(int bitRate, int frameRate);

    /**
     * 实际编码的分辨率回调接口， 每3秒钟回调一次.
     * @param width     实际编码分辨率宽度
     * @param height    实际编码分辨率高度.
     */
    void onEncodeResolution(int width, int height);

    /**
     * 编码头一帧回调接口.
     */
    void onEncodeFirstFrame();
    /**
     * 编码后一帧视频数据的输出回调函数.
     * @param data  编码后的视频数据
     * @param len   视频数据的长度
     * @param pts   视频数据的pts, 单位是ms
     * @param dts   视频数据的dts, 单位是ms
     * @param frameType 视频数据的帧类型，YY系统中的帧类型取值一样.
     * @param encodeType    视频数据的编码类型，这里是开播SDK中定义的encodeType，特别提醒这里和yysdk中传输定义的encodeType有不同.
     */
    void onEncodeFrameData(byte[] data, int len, long pts, long dts, int frameType, VideoEncoderType encodeType);

    /**
     * sdk内部编码参数通知.
     * @param param 编码参数,一般用于调试打印在屏幕，或者统计等等.
     */
    void onEncodeEncParam(String param);
}
