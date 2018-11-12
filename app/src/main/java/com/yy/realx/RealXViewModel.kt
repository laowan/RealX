package com.yy.realx

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel

class RealXViewModel : ViewModel() {
    /**
     * 视频配置
     */
    var video = MutableLiveData<VideoSettings>()

    /**
     * 音频配置
     */
    var audio = MutableLiveData<AudioSettings>()

    /**
     * 流程配置
     */
    var stage = MutableLiveData<Stage>()

    /**
     * 状态切换
     */
    fun transitTo(value: Stage) {
        this.stage.value = value
    }
}

data class VideoSettings(val path: String, val looping: Boolean = true) {
    var export: String? = null
}

data class AudioSettings(val path: String, val start: Int = 0) {
    var mode: String? = null
    var tuner: String? = null
}

enum class Stage {
    PERMISSION, RECORD, EDIT, SHARE
}