package com.ycloud.mediaprocess;

/**
 * Created by DZHJ on 2017/6/1.
 */

public interface IVideoFilter {

    // private final String CMD_FILTER_WHITE_BALANCE = "unsharp=13:5:-0.5:5:13:-1,hue=b=0.8:h=1:s=1";

    String CMD_FILTER_COMMON = "filterseffect=mode=";
    /** 美白 */
    String CMD_FILTER_WHITENING = "gpubeauty=name='BeautyFace'"; // "gpubeauty=name='BeautyFace@0.1@sdcard/table.txt'"
    /** 黑白 */
    String CMD_FILTER_BLACKWHITE = "colorbalance=rm=0:bm=0:gm=0,eq=brightness=0:saturation=0";
    /** 复古（怀旧） */
    String CMD_FILTER_RETRO = "colorbalance=rm=0.28:bm=-0.34:gm=-0.07,eq=brightness=0:saturation=0.35";
    /** 哥特 */
    String CMD_FILTER_GOTHIC = CMD_FILTER_COMMON + "3";
    /** 锐化 */
    String CMD_FILTER_SHARPENING = CMD_FILTER_COMMON + "4";
    /** 淡雅 */
    String CMD_FILTER_ELEGANT = CMD_FILTER_COMMON + "5";
    /** 酒红 */
    String CMD_FILTER_RED_WINE = CMD_FILTER_COMMON + "6";
    /** 清宁 */
    String CMD_FILTER_NING = CMD_FILTER_COMMON + "7";
    /** 浪漫 */
    String CMD_FILTER_ROMANTIC = CMD_FILTER_COMMON + "8";
    /** 光晕 */
    String CMD_FILTER_HALO = CMD_FILTER_COMMON + "9";
    /** 蓝调 */
    String CMD_FILTER_BLUE = "colorbalance=rm=0:bm=0.59:gm=-0.06,eq=brightness=0:saturation=0.37";
    /** 梦幻 */
    String CMD_FILTER_DREAM = CMD_FILTER_COMMON + "11";
    /** 夜色 */
    String CMD_FILTER_NIGHT = CMD_FILTER_COMMON + "12";
    /** 磨皮 */
    String CMD_FILTER_SKIN_RETOUCH = "mopi";
    /** 美化 */
    String CMD_FILTER_BEAUTY = "screenfusion=w=200,mopi";
    /** 美化2 */
    String CMD_FILTER_BEAUTY2 = "screenfusion=w=150,nlm=d=150";
    /** 日系 */
    String CMD_FILTER_JAPANESE = "colorbalance=rm=0.15:bm=0:gm=0,eq=brightness=0.24:saturation=0.24";
    /** 午茶 */
    String CMD_FILTER_NOON_TEA = "colorbalance=rm=0.36:bm=0:gm=0,eq=brightness=0.2:saturation=0.58";
    /** 复古2 */
    String CMD_GFILTER_RETRO = "gpubeauty=name='Sepia'";//GPUImageSepiaFilter
    /** 油画 */
    String CMD_GFILTER_OIL_PAINTING = "gpubeauty=name='Smooth Toon'";//GPUImageSmoothToonFilter
    /** 轮廓 */
    String CMD_GFILTER_OUTLINE = "gpubeauty=name='Sketch'";//GPUImageSketchFilter
    /** 二次元 */
    String CMD_GFILTER_QUADRATIC_ELEMENT = "gpubeauty=name='Sobel Edge Detection'";//GPUImageSobelEdgeDetection
    /** 回忆 */ /** 甜美 */ /** 文艺 */
    String CMD_GFILTER_COMMON_ACV = "gpubeauty=name='ToneCurve";//GPUImageToneCurveFilter

    /** 空滤镜 */
    static int VIDEO_FILTER_NONE = -1;
    /** 美白 */
    int VIDEO_FILTER_WHITENING = 0;
    /** 黑白 */
    int VIDEO_FILTER_BLACKWHITE = 1;
    /** 复古 */
    int VIDEO_FILTER_RETRO = 2;
    /** 哥特 */
    int VIDEO_FILTER_GOTHIC = 3;
    /** 锐化 */
    int VIDEO_FILTER_SHARPENING = 4;
    /** 淡雅 */
    int VIDEO_FILTER_ELEGANT = 5;
    /** 酒红 */
    int VIDEO_FILTER_RED_WINE = 6;
    /** 清宁 */
    int VIDEO_FILTER_NING = 7;
    /** 浪漫 */
    int VIDEO_FILTER_ROMANTIC = 8;
    /** 光晕 */
    int VIDEO_FILTER_HALO = 9;
    /** 蓝调 */
    int VIDEO_FILTER_BLUE = 10;
    /** 梦幻 */
    int VIDEO_FILTER_DREAM = 11;
    /** 夜色 */
    int VIDEO_FILTER_NIGHT = 12;
    /** 磨皮 */
    int VIDEO_FILTER_SKIN_RETOUCH = 13;
    /** 美化 */
    int VIDEO_FILTER_BEAUTY = 14;
    /** 美化2 */
    int VIDEO_FILTER_BEAUTY2 = 15;
    /** 日系 */
    int VIDEO_FILTER_JAPANESE = 16;
    /** 午茶 */
    int VIDEO_FILTER_NOON_TEA = 17;
    /** 复古2 */
    int VIDEO_GFILTER_RETRO = 18;
    /** 油画 */
    int VIDEO_GFILTER_OIL_PAINTING = 19;
    /** 轮廓 */
    int VIDEO_GFILTER_OUTLINE = 20;
    /** 二次元 */
    int VIDEO_GFILTER_QUADRATIC_ELEMENT = 21;
    /** 回忆  need a acv file*/
    int VIDEO_GFILTER_MEMORY_ACV = 22;
    /** 甜美  need a acv file*/
    int VIDEO_GFILTER_SWEETNESS_ACV = 23;
    /** 文艺  need a acv file*/
    int VIDEO_GFILTER_LITERATURE_ACV = 24;
}
