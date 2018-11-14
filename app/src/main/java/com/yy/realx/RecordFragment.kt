package com.yy.realx

import android.app.Activity
import android.app.ProgressDialog
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import com.ycloud.api.process.VideoConcat
import com.ycloud.api.videorecord.IMediaInfoRequireListener
import com.ycloud.api.videorecord.IVideoRecord
import com.ycloud.api.videorecord.IVideoRecordListener
import com.ycloud.camera.utils.CameraUtils
import com.ycloud.gpuimagefilter.utils.FilterIDManager
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.concurrent.schedule

class RecordFragment : Fragment() {
    companion object {
        private var TAG = RecordFragment::class.java.simpleName
        private val TunerMode = arrayOf(
            "VeoNone", "VeoEthereal", "VeoThriller", "VeoLuBan", "VeoLorie",
            "VeoUncle", "VeoDieFat", "VeoBadBoy", "VeoWarCraft", "VeoHeavyMetal",
            "VeoCold", "VeoHeavyMechinery", "VeoTrappedBeast", "VeoPowerCurrent"
        )
        private val TunerName = arrayOf(
            "原声", "空灵", "惊悚", "鲁班", "萝莉",
            "大叔", "死肥仔", "熊孩子", "魔兽农民", "重金属",
            "感冒", "重机械", "困兽", "强电流"
        )
        private val SpeedMode = arrayOf(
            0.2f, 0.5f, 1.0f, 2.0f, 4.0f
        )
        private const val REQUEST_AVATAR = 0x0f02
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
        private var duration = 0f

        override fun onProgress(seconds: Float) {
            //更新数据
            duration = seconds
            //刷新界面
            activity!!.runOnUiThread {
                record_ms.text = String.format(Locale.getDefault(), "%.2fs", seconds + total)
            }
        }

        private var total = 0f

        override fun onStart(successed: Boolean) {
            //更新数据
            duration = 0f
            //刷新界面
            activity!!.runOnUiThread {
                total = (mModel.video.value?.duration ?: 0).toFloat() / 1000
                record_ms.text = String.format(Locale.getDefault(), "%.2fs", total)
                toggle_record.setImageResource(R.drawable.btn_stop_record)
            }
        }

        override fun onStop(successed: Boolean) {
            //更新数据
            val video = mModel.video.value ?: return
            val segment = video.segmentLast()
            segment.duration = (duration * 1000).toInt()
            activity!!.runOnUiThread {
                //刷新界面
                btn_finish.isEnabled = video.segments.isNotEmpty()
                record_ms.text = ""
                toggle_record.setImageResource(R.drawable.btn_start_record)
            }
        }
    }

    private val mModel: RealXViewModel by lazy {
        ViewModelProviders.of(activity!!).get(RealXViewModel::class.java)
    }

    private val isInitialed = AtomicBoolean(false)
    private val tuner = AtomicInteger(0)

    /**
     * 授权成功后回调
     */
    private fun preparePreview() {
        Log.d(TAG, "preparePreview():${lifecycle.currentState}")
        mRecordConfig = MediaConfig.Builder().attach(video_view).build()
        mVideoRecord = MediaUtils.prepare(context, mRecordConfig) {
            Log.d(TAG, "onPreviewStart()")
            isInitialed.set(true)
        }
        //事件绑定
        toggle_camera.setOnClickListener {
            Log.d(TAG, "SwitchCamera.OnClick()")
            if (!isInitialed.get()) {
                return@setOnClickListener
            }
            mVideoRecord.switchCamera()
            if (mRecordConfig.cameraId == VideoRecordConstants.FRONT_CAMERA) {
                mRecordConfig.cameraId = VideoRecordConstants.BACK_CAMERA
            } else {
                mRecordConfig.cameraId = VideoRecordConstants.FRONT_CAMERA
            }
        }
        var frames = 0
        var amplitude = 0
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
                Log.d(TAG, "onRequireMediaInfo():$amplitude, $frames, $pts")
            }

            override fun onRequireMediaInfo(info: MediaSampleExtraInfo?) {
                Log.d(TAG, "onRequireMediaInfo():$amplitude, $frames")
                var loudness = 0f
                synchronized(mModel) {
                    if (frames > 0) {
                        loudness = (amplitude / frames).toFloat()
                        frames = 0
                        amplitude = 0
                    }
                }
                info?.rhythmSmoothRatio = loudness
            }
        })
        //表示是否mute
        toggle_mute.setOnClickListener {
            Log.d(TAG, "MuteRecord.OnClick()")
            if (!isInitialed.get()) {
                return@setOnClickListener
            }
            val enable = mRecordConfig.audioEnable
            mVideoRecord.setEnableAudioRecord(!enable)
            val resId = if (enable) R.mipmap.btn_mic_mute else R.mipmap.btn_mic_not_mute
            toggle_mute.setImageResource(resId)
            mRecordConfig.audioEnable = !enable
        }
        //录制一段视频
        SDKCommonCfg.disableMemoryMode()
        toggle_record.setOnClickListener {
            Log.d(TAG, "RecordButton.OnClick()")
            if (!isInitialed.get()) {
                return@setOnClickListener
            }
            if (isRecording) {
                mVideoRecord.stopRecord()
            } else {
                var video = mModel.video.value
                if (null == video) {
                    var path = CameraUtils.getOutputMediaFile(CameraUtils.MEDIA_TYPE_VIDEO).absolutePath
                    video = VideoSettings(path)
                    mModel.video.value = video
                }
                checkNotNull(video)
                Log.d(TAG, "startRecord():path = ${video.path}")
                val segment = video.segmentAt(-1)
                checkNotNull(segment)
                segment.tuner = TunerMode[tuner.get() % TunerMode.size]
                segment.res = mModel.avatar.value?.path ?: ""
                Log.d(TAG, "startRecord():segment = ${segment.path}")
                mVideoRecord.setOutputPath(segment.path)
                mVideoRecord.setRecordListener(mRecordListener)
                mVideoRecord.startRecord(false)
            }
            isRecording = !isRecording
        }
        //
        btn_finish.isEnabled = false
        btn_finish.setOnClickListener {
            Log.d(TAG, "NextStage.OnClick()")
            if (!isInitialed.get()) {
                return@setOnClickListener
            }
            checkNotNull(mModel.video.value)
            concatVideoSegments()
        }
        //选择图片
        btn_avatar.setOnClickListener {
            Log.d(TAG, "AvatarEffect.OnClick()")
            if (!isInitialed.get()) {
                return@setOnClickListener
            }
            applyAvatar("face2danim")
        }
        //
        val speedModes = arrayOf(speed_mode_0, speed_mode_1, speed_mode_2, speed_mode_3, speed_mode_4)
        val listener = View.OnClickListener {
            Log.d(TAG, "SpeedModes.OnViewClick():$it")
            if (!isInitialed.get()) {
                return@OnClickListener
            }
            if (!it.isSelected) {
                speedModes.forEach { view ->
                    view.isSelected = (view == it)
                }
                val index = speedModes.indexOf(it)
                mVideoRecord.setRecordSpeed(SpeedMode[index])
            }
        }
        speedModes.forEach {
            Log.d(TAG, "SpeedModes.setOnClickListener():$it")
            it.setOnClickListener(listener)
            it.isSelected = false
        }
        speed_mode_2.performClick()
        //
        btn_voice.text = TunerName[0]
        btn_voice.setOnClickListener {
            Log.d(TAG, "VoiceTuner.OnClick()")
            if (!isInitialed.get()) {
                return@setOnClickListener
            }
            btn_voice.text = TunerName[tuner.incrementAndGet() % TunerName.size]
        }
    }

    private var effect = FilterIDManager.NO_ID

    /**
     * 添加特效文件
     */
    private fun applyAvatar(name: String) {
        val dir = File(context!!.filesDir, name)
        val avatar = File(dir, "effect0.ofeffect")
        if (!avatar.exists()) {
            extractFromAssets(name)
        }
        val wrapper = mVideoRecord.recordFilterSessionWrapper ?: return
        if (effect == FilterIDManager.NO_ID) {
            effect = wrapper.addFilter(FilterType.GPUFILTER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP)
            val config = hashMapOf<Int, Any>(
                FilterOPType.OP_SET_EFFECT_PATH to avatar.absolutePath
            )
            wrapper.updateFilterConf(effect, config)
            //初始化图片显示
            btn_avatar.setImageURI(Uri.fromFile(File(dir, "target.png")))
        } else {
//            wrapper.removeFilter(effect)
//            effect = FilterIDManager.NO_ID
            val intent = Intent(Intent.ACTION_PICK)
            intent.data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            startActivityForResult(intent, REQUEST_AVATAR)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult():$resultCode")
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        when (requestCode) {
            REQUEST_AVATAR -> {
                val uri = data?.data ?: return
                Log.d(TAG, "onActivityResult():$uri")
                mTimer.schedule(0) {
                    prepareAvatar(uri)
                }
            }
            else -> {
                Log.d(TAG, "onActivityResult():$requestCode")
            }
        }
    }

    /**
     * 人脸检测
     */
    private fun prepareAvatar(uri: Uri) {
        val projections = arrayOf(MediaStore.Video.Media.DATA)
        val cursor = context!!.contentResolver.query(uri, projections, null, null, null)
        checkNotNull(cursor)
        var path = ""
        if (cursor.moveToFirst()) {
            path = cursor.getString(cursor.getColumnIndexOrThrow(projections[0]))
        }
        cursor.close()
        Log.d(TAG, "prepareAvatar():$path")
        if (path.isBlank()) {
            return
        }
        activity!!.runOnUiThread {
            val avatar = AvatarDialogFragment.newInstance(path)
            avatar.showNow(childFragmentManager, AvatarDialogFragment::class.java.simpleName)
            avatar.dialog.setOnDismissListener {
                Log.d(TAG, "OnDismissListener.onDismiss():$path")
                btn_avatar.setImageURI(Uri.fromFile(File(path)))
            }
        }
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
        var buffer = ByteArray(4 * 1024)
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

    private val mTimer: Timer by lazy {
        Timer("Record_Timer", false)
    }

    /**
     * 先合并视频分段
     */
    private fun concatVideoSegments() {
        Log.d(TAG, "concatVideoSegments()")
        if (!btn_finish.isEnabled) {
            return
        }
        val video = mModel.video.value
        checkNotNull(video)
        val list = mutableListOf<String>()
        video.segments.forEach {
            list.add(it.path)
        }
        val dialog = ProgressDialog.show(context, "", "合并中...", false)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { dialog, keyCode, event -> true }
        val concat = VideoConcat(context, list as ArrayList<String>, video.path)
        concat.setMediaListener(object : IMediaListener {
            override fun onProgress(progress: Float) {
                Log.d(TAG, "VideoConcat.onProgress():$progress")
                dialog.progress = (50 * progress).toInt()
            }

            override fun onError(errType: Int, errMsg: String?) {
                Log.d(TAG, "VideoConcat.onError():$errType, $errMsg")
                concat.cancel()
                concat.release()
                dialog.dismiss()
            }

            override fun onEnd() {
                Log.d(TAG, "VideoConcat.onEnd()")
                concat.cancel()
                concat.release()
                //抽取音轨
                extractAudioFirst(dialog)
            }
        })
        mTimer.schedule(0) {
            concat.execute()
        }
    }

    /**
     * 提取音轨
     */
    private fun extractAudioFirst(dialog: ProgressDialog) {
        Log.d(TAG, "extractAudioFirst()")
        activity!!.runOnUiThread {
            dialog.setMessage("提取中...")
        }
        val video = mModel.video.value
        checkNotNull(video)
        val path = video.path
        val audio = video.audio.path
        val extractor = MediaProcess()
        extractor.setMediaListener(object : IMediaListener {
            override fun onProgress(progress: Float) {
                Log.d(TAG, "AudioExtract.onProgress():$progress")
                dialog.progress = 50 + (50 * progress).toInt()
            }

            override fun onError(errType: Int, errMsg: String?) {
                Log.d(TAG, "AudioExtract.onError():$errType, $errMsg")
                extractor.cancel()
                extractor.release()
                dialog.dismiss()
            }

            override fun onEnd() {
                Log.d(TAG, "AudioExtract.onEnd()")
                extractor.cancel()
                extractor.release()
                dialog.dismiss()
                //跳转
                activity!!.runOnUiThread {
                    mModel.transitTo(Stage.EDIT)
                }
            }
        })
        mTimer.schedule(0) {
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
        mTimer.cancel()
    }
}
