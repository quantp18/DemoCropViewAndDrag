package com.example.democropviewanddrag.customview.v3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CropFrameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val cropRect = RectF()
    private val handleSize = 20f // Size of corner handles

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Initialize crop frame to 80% of view size, centered
        val margin = 0.1f * minOf(w, h) // 10% margin
        cropRect.set(margin, margin, w - margin, h - margin)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the green frame
        canvas.drawRect(cropRect, paint)
        // Draw corner handles
        canvas.drawCircle(cropRect.left, cropRect.top, handleSize / 2, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, handleSize / 2, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleSize / 2, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleSize / 2, handlePaint)
    }
}