package com.yy.realx

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.ycloud.api.videorecord.IVideoRecord
import com.ycloud.api.videorecord.IVideoRecordListener
import com.ycloud.camera.utils.CameraUtils
import com.ycloud.mediarecord.VideoRecordConstants
import com.ycloud.utils.FileUtils
import com.yy.media.MediaConfig
import com.yy.media.MediaUtils
import kotlinx.android.synthetic.main.activity_splash.*
import java.io.File

class SplashActivity : AppCompatActivity() {
    companion object {
        private var TAG = SplashActivity::class.java.simpleName
        private const val PERMISSION_CODE = 0x00001
    }

    private lateinit var mRecordConfig: MediaConfig
    private lateinit var mVideoRecord: IVideoRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        preparePreview()
        //权限请求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
                ), PERMISSION_CODE
            )
        }
    }

    private var isRecording = false
    private var mRecordListener = object : IVideoRecordListener {
        override fun onProgress(seconds: Float) {
            runOnUiThread {

            }
        }

        override fun onStart(successed: Boolean) {
            runOnUiThread {
                toggle_record.setImageResource(R.drawable.btn_stop_record)
            }
        }

        override fun onStop(successed: Boolean) {
            runOnUiThread {
                toggle_record.setImageResource(R.drawable.btn_start_record)
            }
        }
    }

    /**
     * 授权成功后回调
     */
    private fun preparePreview() {
        Log.d(TAG, "preparePreview():${lifecycle.currentState}")
        mRecordConfig = MediaConfig.Builder().attach(video_view).build()
        mVideoRecord = MediaUtils.prepare(this, mRecordConfig)
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
        toggle_mute.setOnClickListener {
            val enable = mRecordConfig.audioEnable
            mVideoRecord.setEnableAudioRecord(!enable)
            val resId = if (enable) R.mipmap.btn_mic_mute else R.mipmap.btn_mic_not_mute
            toggle_mute.setImageResource(resId)
            mRecordConfig.audioEnable = !enable
        }
        //录制一段视频
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
    }
}
