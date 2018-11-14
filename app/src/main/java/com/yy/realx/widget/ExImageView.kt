package com.yy.realx.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log

class ExImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    RImageView(context, attrs, defStyleAttr) {

    companion object {
        private val TAG = ExImageView::class.java.simpleName
    }

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        //约束图片缩放模式
        scaleType = ScaleType.CENTER_INSIDE
        //初始化变量
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.color = Color.LTGRAY
        paint.strokeWidth = 10f
    }

    val values = mutableListOf<Float>()

    /**
     * 标记点坐标信息
     */
    fun setValues(values: List<Float>) {
        Log.d(TAG, "setValues():${values.size}")
        this.values.clear()
        this.values.addAll(values)
        post {
            parseCoordinates()
            invalidate()
        }
    }

    private var coordinates = mutableListOf<Float>()

    /**
     * 拆解坐标数据
     */
    private fun parseCoordinates() {
        Log.d(TAG, "parseCoordinates():${values.size}")
        if (values.isEmpty()) {
            return
        }
        Log.d(TAG, "parseCoordinates():$width, $height")
        if (width <= 0 || height <= 0) {
            return
        }
        if (coordinates.isNullOrEmpty()) {
            coordinates.addAll(values)
        }
        Log.d(TAG, "parseCoordinates():${coordinates.size}")
        values.mapIndexed { index, value ->
            if (index % 2 == 0) {
                coordinates[index] = value * width
            } else {
                coordinates[index] = value * height
            }
        }
    }

    override fun setScaleType(scaleType: ScaleType?) {
        if (scaleType != ScaleType.CENTER_INSIDE) {
            throw IllegalArgumentException("Only support scaleType ScaleType.CENTER_INSIDE.")
        }
        super.setScaleType(scaleType)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d(TAG, "onDraw():${coordinates.isEmpty()}, ${values.isNotEmpty()}")
        if (coordinates.isNotEmpty()) {
            canvas.save()
            canvas.drawPoints(coordinates.toFloatArray(), paint)
            canvas.restore()
        } else if (values.isNotEmpty()) {
            post {
                parseCoordinates()
                invalidate()
            }
        }
    }
}
