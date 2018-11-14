package com.yy.realx.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import com.yy.realx.R

open class RImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppCompatImageView(context, attrs, defStyleAttr) {

    private val path = Path()
    private var radius = 0f

    init {
        if (null != attrs) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.RImageView, defStyleAttr, 0)
            radius = array.getDimensionPixelOffset(R.styleable.RImageView_radius, 0).toFloat()
            array.recycle()
        }
        prepareInitial(context)
    }

    /**
     * 初始化裁剪区域
     *
     * @param context
     */
    private fun prepareInitial(context: Context) {
        path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        path.reset()
        path.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, Path.Direction.CW)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)
        super.onDraw(canvas)
        canvas.restore()
    }
}
