package com.ycloud.api.videorecord;

import com.ycloud.api.config.AspectRatioType;

/**
 * Created by Administrator on 2018/5/29.
 */

public interface IChangeAspectRatioListener {
    void onChangeAspectRatioFinish(AspectRatioType type);
}
