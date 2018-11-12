package com.yy.realx

import android.app.ProgressDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ycloud.api.common.FilterGroupType
import com.ycloud.api.common.FilterType
import com.ycloud.api.common.SDKCommonCfg
import com.ycloud.api.process.IMediaListener
import com.ycloud.api.process.MediaProcess
import com.ycloud.api.videorecord.IMediaInfoRequireListener
import com.ycloud.api.videorecord.IVideoRecord
import com.ycloud.api.videorecord.IVideoRecordListener
import com.ycloud.camera.utils.CameraUtils
import com.ycloud.gpuimagefilter.utils.FilterOPType
import com.ycloud.mediarecord.VideoRecordConstants
import com.ycloud.utils.FileUtils
import com.ycloud.ymrmodel.MediaSampleExtraInfo
import com.yy.media.MediaConfig
import com.yy.media.MediaUtils
import kotlinx.android.synthetic.main.fragment_record.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.concurrent.schedule

class RecordFragment : Fragment() {
    companion object {
        private var TAG = RecordFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_record, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        preparePreview()
    }

    private lateinit var mRecordConfig: MediaConfig
    private lateinit var mVideoRecord: IVideoRecord

    private var isRecording = false
    private var mRecordListener = object : IVideoRecordListener {
        override fun onProgress(seconds: Float) {
            activity!!.runOnUiThread {
                record_ms.text = String.format(Locale.getDefault(), "%.2fs", seconds)
            }
        }

        override fun onStart(successed: Boolean) {
            activity!!.runOnUiThread {
                record_ms.text = String.format(Locale.getDefault(), "%.2fs", 0f)
                toggle_record.setImageResource(R.drawable.btn_stop_record)
            }
        }

        override fun onStop(successed: Boolean) {
            activity!!.runOnUiThread {
                record_ms.text = ""
                toggle_record.setImageResource(R.drawable.btn_start_record)
                mModel.video.value = VideoSettings(mRecordConfig.videoPath)
            }
        }
    }

    private val mModel: RealXViewModel by lazy {
        ViewModelProviders.of(activity!!).get(RealXViewModel::class.java)
    }

    var frames = 0
    var amplitude = 0

    /**
     * 授权成功后回调
     */
    private fun preparePreview() {
        Log.d(TAG, "preparePreview():${lifecycle.currentState}")
        mRecordConfig = MediaConfig.Builder().attach(video_view).build()
        mVideoRecord = MediaUtils.prepare(context, mRecordConfig)
        lifecycle
        //事件绑定
        toggle_camera.setOnClickListener {
            mVideoRecord.switchCamera()
            if (mRecordConfig.cameraId == VideoRecordConstants.FRONT_CAMERA) {
                mRecordConfig.cameraId = VideoRecordConstants.BACK_CAMERA
            } else {
                mRecordConfig.cameraId = VideoRecordConstants.FRONT_CAMERA
            }
        }
        //表示是否mute
        mVideoRecord.setEnableAudioRecord(true)
        mVideoRecord.setAudioRecordListener { avgAmplitude, maxAmplitude ->
            Log.d(TAG, "onVolume():$avgAmplitude, $maxAmplitude")
            synchronized(mModel) {
                frames++
                amplitude += avgAmplitude
            }
        }
        mVideoRecord.setMediaInfoRequireListener(object : IMediaInfoRequireListener {
            override fun onRequireMediaInfo(info: MediaSampleExtraInfo?, pts: Long) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onRequireMediaInfo(info: MediaSampleExtraInfo?) {
                Log.d(TAG, "onRequireMediaInfo():$amplitude, $frames")
                synchronized(mModel) {
                    if (frames <= 0) {
                        info!!.rhythmSmoothRatio = 0f
                    } else {
                        info!!.rhythmSmoothRatio = (amplitude / frames).toFloat()
                    }
                }
            }
        })
        toggle_mute.setOnClickListener {
            val enable = mRecordConfig.audioEnable
            mVideoRecord.setEnableAudioRecord(!enable)
            val resId = if (enable) R.mipmap.btn_mic_mute else R.mipmap.btn_mic_not_mute
            toggle_mute.setImageResource(resId)
            mRecordConfig.audioEnable = !enable
        }
        //录制一段视频
        SDKCommonCfg.disableMemoryMode()
        toggle_record.setOnClickListener {
            if (isRecording) {
                mVideoRecord.stopRecord()
            } else {
                var path = mRecordConfig.videoPath ?: ""
                if (path.isNotBlank()) {
                    FileUtils.deleteFileSafely(File(path))
                }
                path = CameraUtils.getOutputMediaFile(CameraUtils.MEDIA_TYPE_VIDEO).absolutePath
                Log.d(TAG, "startRecord(): $path")
                mVideoRecord.setOutputPath(path)
                mVideoRecord.setRecordListener(mRecordListener)
                mVideoRecord.startRecord(false)
                mRecordConfig.videoPath = path
            }
            isRecording = !isRecording
        }
        btn_finish.isEnabled = false
        btn_finish.setOnClickListener {
            extractAudioFirst()
        }
        btn_avatar.setOnClickListener {
            prepareAndAttach("avatar_yy_bear")
        }
        mModel.video.observe(this, Observer {
            btn_finish.isEnabled = it!!.path.isNotBlank()
        })
    }

    /**
     * 添加特效文件
     */
    private fun prepareAndAttach(name: String) {
        val dir = File(context!!.filesDir, name)
        val avatar = File(dir, "effect0.ofeffect")
        if (!avatar.exists()) {
            extractFromAssets(name)
        }
        val wrapper = mVideoRecord.recordFilterSessionWrapper
        val effect = wrapper.addFilter(FilterType.GPUFILTER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP)
        val config = hashMapOf<Int, Any>(
            FilterOPType.OP_SET_EFFECT_PATH to avatar.absolutePath
        )
        wrapper.updateFilterConf(effect, config)
    }

    /**
     * 解压特效文件
     */
    private fun extractFromAssets(name: String) {
        val dir = File(context!!.filesDir, name)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        var count: Int
        var buffer = ByteArray(2048)
        val input = ZipInputStream(context!!.assets.open("$name.zip"))
        var entry: ZipEntry? = input.nextEntry
        while (null != entry) {
            if (entry.isDirectory) {
                File(dir, entry.name).mkdirs()
            } else {
                val file = File(dir, entry.name)
                if (file.exists()) {
                    FileUtils.deleteFileSafely(file)
                }
                val output = FileOutputStream(file)
                while (true) {
                    count = input.read(buffer)
                    if (count <= 0) {
                        break
                    } else {
                        output.write(buffer, 0, count)
                    }
                }
                output.flush()
                output.close()
            }
            entry = input.nextEntry
        }
        input.close()
    }

    /**
     * 提取音轨
     */
    private fun extractAudioFirst() {
        Log.d(TAG, "extractAudioFirst()")
        if (!btn_finish.isEnabled) {
            return
        }
        val dialog = ProgressDialog.show(context, "", "处理中...", false)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { dialog, keyCode, event -> true }
        val path = mModel.video.value!!.path
        val audio = path.replace(".mp4", ".wav")
        val extractor = MediaProcess()
        extractor.setMediaListener(object : IMediaListener {
            override fun onProgress(progress: Float) {
                Log.d(TAG, "Audio.onProgress():$progress")
                dialog.progress = (100 * progress).toInt()
            }

            override fun onError(errType: Int, errMsg: String?) {
                Log.d(TAG, "Audio.onError():$errType, $errMsg")
                dialog.dismiss()
                extractor.cancel()
                extractor.release()
            }

            override fun onEnd() {
                Log.d(TAG, "Audio.onEnd()")
                dialog.dismiss()
                extractor.cancel()
                extractor.release()
                //跳转
                activity!!.runOnUiThread {
                    mModel.audio.value = AudioSettings(audio)
                    mModel.transitTo(Stage.EDIT)
                }
            }
        })
        Timer("Extract_Audio", false).schedule(0) {
            extractor.extractAudioTrack(path, audio)
        }
    }

    //----------------------------------生命周期---------------------------------//

    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()
        mVideoRecord.onResume()
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        mVideoRecord.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
        mVideoRecord.release()
    }
}
