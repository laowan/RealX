package com.ycloud.mediacodec;

import android.content.Context;

import com.ycloud.mediacodec.utils.HwCodecConfig;

/**
 * Created by DZHJ on 2017/2/22.
 */

public class MeidacodecConfig {

    public static void loadConfig(Context context) {
        HwCodecConfig hwCodecConfig = new HwCodecConfig();
        hwCodecConfig.AsyncLoad(context);
    }

    public static void unLoadConfig() {
        HwCodecConfig.setContext(null);
    }
}
