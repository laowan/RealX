package com.yy.realx

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.ycloud.api.videorecord.IVideoRecord
import com.yy.media.MediaConfig
import com.yy.media.MediaUtils
import kotlinx.android.synthetic.main.activity_splash.*

class SplashActivity : AppCompatActivity() {
    companion object {
        private var TAG = SplashActivity::class.java.simpleName
        private const val PERMISSION_CODE = 0x00001
    }

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

    /**
     * 授权成功后回调
     */
    private fun preparePreview() {
        Log.d(TAG, "preparePreview():${lifecycle.currentState}")
        val config = MediaConfig.Builder().attach(video_view).build()
        mVideoRecord = MediaUtils.prepare(this, config)
    }
}
