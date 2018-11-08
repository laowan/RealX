package com.ycloud.api.common;

import com.ycloud.common.GlobalConfig;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.YYLog;

import java.util.concurrent.Executor;

/**
 * Created by kele on 30/9/2017.
 */

public class SDKCommonCfg {
    private static final String TAG = "SDKCommonCfg";


    public static void setExecutorService(Executor  executor) {
        ExecutorUtils.setBaseSDKExecutor(executor);
    }

    /**
     * 当视频数据来源是拍摄页摄像头时，应该把内存模式打开。
     */
    public static void enableMemoryMode() {
        YYLog.info(TAG, "enableMemoryMode .");
        GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY = 1;
    }

    /**
     * 当视频数据来源是本地导入视频时，应该关闭内存模式，所有音视频数据存放在SD卡的MP4文件中。
     */
    public static void disableMemoryMode() {
        YYLog.info(TAG, "disableMemoryMode .");
        GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY = 0;
    }

    /**
     * 获取当前使用的内存模式
     *
     * @return 当前使用的内存模式
     */
    public static int getCurrentMemoryMode() {
        return GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY;
    }

    /**
     * 强制使用新的导出模式的开关，目前某些特效（比如正方形毛玻璃效果）只支持新的导出模式
     *
     * @param useExportSession 是否使用新的导出模式
     */
    public static void setUseMediaExportSession(boolean useExportSession) {
        YYLog.info(TAG, "setUseMediaExportSession:" + useExportSession);
        GlobalConfig.getInstance().getRecordConstant().USE_MEDIA_EXPORT_SESSION = useExportSession;
    }

    /**
     * 判断当前是否使用新的导出模式，默认都是false
     *
     * @return
     */
    public static boolean getUseMediaExportSession() {
        return GlobalConfig.getInstance().getRecordConstant().USE_MEDIA_EXPORT_SESSION;
    }
	
	/**
     * 设置SDK当前处于什么功能模式：视频录制模式 / 照片拍摄模式, 不同模式初始化不同资源
     * 在new NewVideoRecord 之前设置
     * @param mode 0 ：视频拍摄模式  1:照片拍摄模式
     */
    public static void setRecordModePicture(boolean mode) {
        YYLog.info(TAG, "setRecordMode :" + (mode ? " Picture .":"Video ."));
        GlobalConfig.getInstance().getRecordConstant().RECORD_MODE_PICTURE = mode;
    }

    public static boolean getRecordModePicture() {
        return GlobalConfig.getInstance().getRecordConstant().RECORD_MODE_PICTURE;
    }

    /**
     * 设置是否使用cpu版本的抠图
     *
     * @param useCpuSegmentMode
     */
    public static void setUseCpuSegmentMode(boolean useCpuSegmentMode) {
        YYLog.info(TAG, "setUseCpuSegmentMode:" + useCpuSegmentMode);
        GlobalConfig.getInstance().getRecordConstant().USE_CPU_SEGMENT_MODE = useCpuSegmentMode;
    }

    /**
     * 获取当前使用抠图的模式：cpu版本还是gpu版本
     *
     * @return
     */
    public static boolean getUseCpuSegmentMode() {
        return GlobalConfig.getInstance().getRecordConstant().USE_CPU_SEGMENT_MODE;
    }
}
