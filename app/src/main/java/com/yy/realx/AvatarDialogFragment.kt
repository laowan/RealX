package com.yy.realx

import android.arch.lifecycle.ViewModelProviders
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.util.Log
import android.view.*
import com.ycloud.facedetection.STMobileFaceDetectionWrapper
import com.ycloud.utils.FileUtils
import kotlinx.android.synthetic.main.fragment_avatar.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule

class AvatarDialogFragment : DialogFragment() {
    companion object {
        private val TAG = AvatarDialogFragment::class.java.simpleName
        private const val KEY_PATH = "avatar_path"

        /**
         * static函数
         */
        fun newInstance(path: String): AvatarDialogFragment {
            val fragment = AvatarDialogFragment()
            val bundle = Bundle()
            bundle.putString(KEY_PATH, path)
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
                if (!isDetecting.get() && keyCode == KeyEvent.KEYCODE_BACK) {
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
            setFaceLimit(1)
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
            if (!isDetecting.get()) {
                isDetecting.set(true)
                doDetectionOn(path)
            }
        }
        return true
    }

    private val mModel: RealXViewModel by lazy {
        ViewModelProviders.of(activity!!).get(RealXViewModel::class.java)
    }

    var isDetecting = AtomicBoolean(false)

    /**
     * 图片检测人脸
     */
    private fun doDetectionOn(path: String) {
        Log.d(TAG, "doDetectionOn():$path")
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = resizeSampleRatio(options, 720, 1280)
        options.inJustDecodeBounds = false
        var bitmap = BitmapFactory.decodeFile(path, options)
        bitmap = restrictPortrait(path, bitmap) {
            replaceResourceFile(path, it)
        }
        checkNotNull(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        Log.d(TAG, "doDetectionOn():$width, $height")
        val byteBuffer = ByteBuffer.allocate(width * height * 4)
        checkNotNull(byteBuffer)
        byteBuffer.clear()
        bitmap.copyPixelsToBuffer(byteBuffer)
        bitmap.recycle()
        var tryCount = 3
        var point: STMobileFaceDetectionWrapper.FacePointInfo?
        do {
            Log.d(TAG, "doDetectionOn():$tryCount")
            mDetection.onVideoFrameEx(byteBuffer.array(), width, height, true, true)
            point = mDetection.currentFacePointInfo
            tryCount--
        } while (tryCount > 0 && (point?.mFaceCount == null || point.mFaceCount <= 0))
        byteBuffer.clear()
        //输出数据
        val count = point?.mFaceCount ?: 0
        Log.d(TAG, "doDetectionOn():$tryCount, $count")
        if (count > 0) {
            //todo: mark point here
        }
        mDetection.releaseFacePointInfo(point)
        isDetecting.set(false)
    }

    /**
     * 计算比例缩小
     */
    private fun resizeSampleRatio(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 如果方向不对，矫正
     */
    private fun restrictPortrait(path: String, bitmap: Bitmap, requireSaveBitmap: (bitmap: Bitmap) -> Unit): Bitmap {
        Log.d(TAG, "restrictPortrait():$bitmap")
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        var degree = 0
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                degree = 90
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                degree = 180
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                degree = 270
            }
        }
        Log.d(TAG, "restrictPortrait():$degree")
        if (degree <= 0) {
            requireSaveBitmap(bitmap)
            return bitmap
        }
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val _bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) ?: return bitmap
        if (bitmap != _bitmap) {
            bitmap.recycle()
        }
        requireSaveBitmap(_bitmap)
        Log.d(TAG, "restrictPortrait():$_bitmap")
        return _bitmap
    }

    /**
     * 把图片保存起来
     */
    private fun replaceResourceFile(path: String, bitmap: Bitmap) {
        FileUtils.deleteFileSafely(File(path))
        //save bitmap
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(File(path)))
    }
}