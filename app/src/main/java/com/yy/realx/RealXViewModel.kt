package com.yy.realx

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import java.util.concurrent.atomic.AtomicInteger

class RealXViewModel : ViewModel() {
    /**
     * 视频配置
     */
    var video = MutableLiveData<VideoSettings>()

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

data class VideoSettings(val path: String) {
    private val generator = AtomicInteger(0)
    val segments = mutableListOf<VideoSegment>()
    val duration: Long
        get() {
            var total: Long = 0
            segments.forEach {
                total += it.duration
            }
            return total
        }

    //编辑区域数据
    val audio: AudioSettings
        get() {
            return AudioSettings(path.replace(".mp4", ".wav"), 0)
        }
    val export: String = path.replace(".mp4", "_export.mp4")

    /**
     * 获取segment
     * index小于0的时候，返回新的segment
     */
    fun segmentAt(index: Int): VideoSegment {
        if (index < 0) {
            val id = generator.getAndIncrement()
            segments.add(VideoSegment(id, path.replace(".mp4", "_P$id.mp4")))
        } else if (index >= segments.size) {
            throw NoSuchElementException("Index is out of bound.($index/${segments.size})")
        }
        return if (index < 0) segments.last() else segments[index]
    }

    /**
     * 返回最后一个segment
     */
    fun segmentLast(): VideoSegment {
        return segments.last()
    }
}

data class VideoSegment(val index: Int, val path: String) {
    var tuner: String = ""
    var res: String = ""
    var duration: Long = 0
}

data class AudioSettings(val path: String, val start: Int = 0) {
    var mode: String? = null
    var tuner: String? = null
}

enum class Stage {
    PERMISSION, RECORD, EDIT, SHARE
}