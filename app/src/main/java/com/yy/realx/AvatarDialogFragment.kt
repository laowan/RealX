package com.yy.realx

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.util.Log
import android.view.*
import com.ycloud.facedetection.STMobileFaceDetectionWrapper
import kotlinx.android.synthetic.main.fragment_avatar.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.schedule

class AvatarDialogFragment : DialogFragment() {
    companion object {
        private val TAG = AvatarDialogFragment::class.java.simpleName
        private const val KEY_PATH = "avatar_path"
        private const val KEY_WIDTH = "avatar_width"
        private const val KEY_HEIGHT = "avatar_height"

        /**
         * static函数
         */
        fun newInstance(path: String, width: Int, height: Int): AvatarDialogFragment {
            val fragment = AvatarDialogFragment()
            val bundle = Bundle()
            bundle.putString(KEY_PATH, path)
            bundle.putInt(KEY_WIDTH, width)
            bundle.putInt(KEY_HEIGHT, height)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_avatar, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        super.onActivityCreated(savedInstanceState)
        dialog?.apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setOnKeyListener { dialog, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.WHITE))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            }
        }
        //准备检测人脸数据
        if (!prepareAvatarView()) {
            dismiss()
        }
    }

    private val mDetection by lazy {
        STMobileFaceDetectionWrapper.getPictureInstance(context!!).apply {
            isCheckFace = true
        }
    }

    private val mTimer by lazy {
        Timer("Avatar_Timer", false)
    }

    /**
     * 可行么？
     */
    private fun prepareAvatarView(): Boolean {
        Log.d(TAG, "prepareAvatarView()")
        val bundle = arguments ?: return false
        val path = bundle.getString(KEY_PATH) ?: return false
        avatar_image.setImageURI(Uri.fromFile(File(path)))
        mTimer.schedule(0) {
            doDetectionOn(path)
        }
        return true
    }

    /**
     * 图片检测人脸
     */
    private fun doDetectionOn(path: String) {
        Log.d(TAG, "doDetectionOn():$path")
        val bitmap = BitmapFactory.decodeFile(path)
        checkNotNull(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val byteBuffer = ByteBuffer.allocate(width * height * 4)
        checkNotNull(byteBuffer)
        byteBuffer.clear()
        bitmap.copyPixelsToBuffer(byteBuffer)
        var tryCount = 3
        var point: STMobileFaceDetectionWrapper.FacePointInfo?
        do {
            Log.d(TAG, "doDetectionOn():$tryCount")
            mDetection.onVideoFrame(byteBuffer.array(), 0, width, height, true)
            point = mDetection.currentFacePointInfo
            tryCount--
        } while (tryCount > 0 && (point?.mFaceCount == null || point.mFaceCount <= 0))
        //输出数据
        Log.d(TAG, "doDetectionOn():$tryCount, ${point?.mFaceCount}")
    }
}