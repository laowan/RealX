package com.ycloud.api.videorecord;

/**
 * 闪光灯模式
 */
public class FlashMode {
    // Values for flash mode settings.
    /**
     * Flash will not be fired.
     */
    public static final String FLASH_MODE_OFF = "off";

    /**
     * Flash will be fired automatically when required. The flash may be fired
     * during preview, auto-focus, or snapshot depending on the driver.
     */
    public static final String FLASH_MODE_AUTO = "auto";

    /**
     * Flash will always be fired during snapshot. The flash may also be
     * fired during preview or auto-focus depending on the driver.
     */
    public static final String FLASH_MODE_ON = "on";

    /**
     * Flash will be fired in red-eye reduction mode.
     */
    public static final String FLASH_MODE_RED_EYE = "red-eye";

    /**
     * Constant emission of light during preview, auto-focus and snapshot.
     * This can also be used for video recording.
     */
    public static final String FLASH_MODE_TORCH = "torch";
}
