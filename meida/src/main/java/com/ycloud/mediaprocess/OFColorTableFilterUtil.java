package com.ycloud.mediaprocess;

import android.content.Context;

import com.ycloud.utils.OpenGlUtils;

import java.util.LinkedHashMap;

/**
 * Created by Administrator on 2017/6/20.
 */
public class OFColorTableFilterUtil {
    private static final String OFCOLORTABLE_FILTER_BLACK_FILENAME = "black.png";
    private static final String OFCOLORTABLE_FILTER_BLUEROSE_FILENAME = "bluerose.png";
    private static final String OFCOLORTABLE_FILTER_GREENSTRONG_FILENAME = "greenstrong.png";
    private static final String OFCOLORTABLE_FILTER_JAPAN2_FILENAME = "japan2.png";
    private static final String OFCOLORTABLE_FILTER_OLD_FILENAME = "old.png";
    private static final String OFCOLORTABLE_FILTER_YELLOWGREEN_FILENAME = "yellowgreen.png";
    private static final String OFCOLORTABLE_FILTER_T4S_FILENAME = "t4s.png";
    private static final String OFCOLORTABLE_FILTER_F1_FILENAME = "f1.png";

    // 后置滤镜
    public static final int OFCOLORTABLE_FILTER_NO = 0;             // 无
    public static final int OFCOLORTABLE_FILTER_BLACK = 1;          // 黑白
    public static final int OFCOLORTABLE_FILTER_BLUEROSE = 2;       // 蓝色妖姬
    public static final int OFCOLORTABLE_FILTER_GREENSTRONG = 3;    // 青强对比
    public static final int OFCOLORTABLE_FILTER_JAPAN2 = 4;         // 日系清新
    public static final int OFCOLORTABLE_FILTER_OLD = 5;             // 复古胶片
    public static final int OFCOLORTABLE_FILTER_YELLOWGREEN = 6;    // 糖水草木绿

    // 前置滤镜
    public static final int OFCOLORTABLE_FILTER_T4S = 100;           // 开启美颜
    public static final int OFCOLORTABLE_FILTER_F1 = 101;            // 不开启美颜

    public static LinkedHashMap<Integer, String> FILEPATH_MAP = new LinkedHashMap<>();

    public static void initFilePathUseContext(Context context) {
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_BLACK, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_BLACK_FILENAME));
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_BLUEROSE, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_BLUEROSE_FILENAME));
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_GREENSTRONG, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_GREENSTRONG_FILENAME));
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_JAPAN2, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_JAPAN2_FILENAME));
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_OLD, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_OLD_FILENAME));
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_YELLOWGREEN, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_YELLOWGREEN_FILENAME));

        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_T4S, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_T4S_FILENAME));
        FILEPATH_MAP.put(OFColorTableFilterUtil.OFCOLORTABLE_FILTER_F1, OpenGlUtils.copyAssetsResToSdcard(context, OFColorTableFilterUtil.OFCOLORTABLE_FILTER_F1_FILENAME));
    }
}
