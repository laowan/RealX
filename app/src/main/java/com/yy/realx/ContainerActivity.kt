package com.yy.realx

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
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
        mModel.stage.observe(this, Observer {
            transitWithStage(it!!)
        })
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

    private val mModel: RealXViewModel by lazy {
        ViewModelProviders.of(this@ContainerActivity).get(RealXViewModel::class.java)
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
        mModel.transitTo(Stage.RECORD)
    }

    /**
     * 根据stage切换fragment
     * @param stage
     */
    private fun transitWithStage(stage: Stage) {
        Log.d(TAG, "transitWithStage():$stage")
        when (stage) {
            Stage.PERMISSION -> {
                transaction(PermissionFragment(), PermissionFragment::class.java.simpleName)
            }
            Stage.RECORD -> {
                transaction(RecordFragment(), RecordFragment::class.java.simpleName)
            }
            Stage.EDIT -> {
                transaction(EditFragment(), EditFragment::class.java.simpleName)
            }
            Stage.SHARE -> {
                transaction(ShareFragment(), ShareFragment::class.java.simpleName)
            }
        }
    }

    /**
     * 切换fragment
     */
    private fun transaction(fragment: Fragment, tag: String) {
        Log.d(TAG, "transaction():$tag, $fragment")
        var target = supportFragmentManager.findFragmentByTag(tag)
        if (null == target) {
            target = fragment
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, target, tag).commitAllowingStateLoss()
    }

    /**
     * 按返回按键处理
     */
    override fun onBackPressed() {
        Log.d(TAG, "transitWithStage():${mModel.stage.value}")
        when (mModel.stage.value) {
            Stage.RECORD -> {
                super.onBackPressed()
            }
            Stage.EDIT -> {
                mModel.transitTo(Stage.RECORD)
            }
            Stage.SHARE -> {
                mModel.transitTo(Stage.EDIT)
            }
            else -> {
                mModel.transitTo(Stage.RECORD)
            }
        }
    }
}
