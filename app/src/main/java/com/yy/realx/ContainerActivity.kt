package com.yy.realx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

class ContainerActivity : AppCompatActivity() {
    companion object {
        private var TAG = ContainerActivity::class.java.simpleName
        private const val PERMISSION_CODE = 0x00001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contaner)
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
        } else {
            onPermissionGranted()
        }
    }

    /**
     * 权限授予回调
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult():$requestCode")
        if (grantResults.none {
                it != PackageManager.PERMISSION_GRANTED
            }) {
            onPermissionGranted()
        }
    }

    /**
     * 获取权限后回调
     */
    private fun onPermissionGranted() {
        Log.d(TAG, "onPermissionGranted()")
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, RecordFragment(), RecordFragment::class.java.simpleName)
            .commitAllowingStateLoss()
    }
}
