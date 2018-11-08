package com.ycloud.gpuimagefilter.utils;

/**
 * Created by jinyongqing on 2018/4/8.
 */

public class FilterOPType {
    /**
     * common operation
     */
    public static final int NO_OP = 0;
    public static final int OP_SET_EFFECT_PATH = 1;
    public static final int OP_SET_UICONFIG = 2;                //设置uiConfig，并保存视频帧uiConfig，没有保存uiConfig的视频帧使用pts在它之前最近一帧的uiConfig
    public static final int OP_SET_VISIBLE = 4;
    public static final int OP_SET_UICONFIG_NOT_TRACE = 8192;   //设置全局uiConfig，最后设置的uiConfig参数作用整个特效区间

    /**
     * game operation
     */
    public static final int OP_START_GAME = 8;   //
    public static final int OP_PAUSE_GAME = 16;   //
    public static final int OP_RESUME_GAME = 32;   //
    public static final int OP_STOP_GAME = 64;   //
    public static final int OP_SET_GAME_CALLBACK = 128;   //
    public static final int OP_SEND_GAME_EVENT = 256;   //
    public static final int OP_DESTROY_GAME = 512;

    /**
     * editSticker operation
     */
    public static final int OP_CHANGE_MATRIX = 8;             //  改变贴纸位置
    public static final int OP_CHANGE_TIME = 16;               //  改变贴纸显示时间
    public static final int OP_ADD_TRACK = 32;                 //  定住贴纸
    public static final int OP_ADD_TEXT = 64;                 //  添加文字
    public static final int OP_KEEP_MATRIX = 128;              //  保存贴纸移动轨迹
    public static final int OP_CHANGE_SCALE = 256;            //  改变粒子大小
    public static final int OP_CHANGE_COLOR = 512;            //  改变粒子颜色
    public static final int OP_CHANGE_ROTATION = 1024;         //  改变粒子角度
    public static final int OP_SET_REPEAT_RENDER = 2048;       //  设置贴纸特效不断播放动画
    public static final int OP_CHANGE_RATIO_TO_BACKGROUND = 4096; //设置贴纸与背景画面的比例

    /**
     * OFStretchFilter
     */
    public static final int OP_CHANGE_LEVEL = 8;             //   改变拉伸程度

    /**
     * OFDoubleColorFilter
     */
    public static final int OP_CHANGE_RATIO = 8;               //改变色表比例
    public static final int OP_CHANGE_WITH_VERTICAL = 16;      //修改是否允许竖屏滑动

    /**
     * Beauty Face
     */
    public static final int OP_CHANGE_INTENSITY = 8;            // 改变美颜强度

    /**
     * EffectFilter
     */
    public static final int OP_SET_SUPPORT_SEEK = 8;             // 是否支持SEEK
    public static final int OP_SET_SEEK_TIME_OFFSET = 16;            // YOYI 设置特效贴纸seek位置
}
