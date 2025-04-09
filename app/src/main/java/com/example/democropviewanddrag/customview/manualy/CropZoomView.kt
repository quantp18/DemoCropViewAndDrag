package com.example.democropviewanddrag.customview.manualy

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.view.GestureDetectorCompat
import kotlin.math.atan2

class CropZoomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backgroundRect = RectF(100f, 100f, 300f, 300f) // Khung nền mặc định
    private var imageBitmap: Bitmap? = null // Ảnh overlay
    private var imageMatrix = Matrix() // Ma trận điều chỉnh ảnh
    private val framePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val imagePaint = Paint()

    // Gesture detectors
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetectorCompat(context, GestureListener())

    // Trạng thái kéo thả khung
    private var isDraggingFrame = false
    private var dragEdge = -1 // 0: left, 1: top, 2: right, 3: bottom
    private val edgeThreshold = 20f // Khoảng cách để xác định chạm vào cạnh

    // Trạng thái điều chỉnh ảnh
    private var isDraggingImage = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var rotation = 0f

    init {
        setWillNotDraw(false)
    }

    // Thiết lập ảnh từ resource ID
    fun setImageFromResource(resourceId: Int) {
        imageBitmap = BitmapFactory.decodeResource(resources, resourceId)
        imageBitmap?.let { bitmap ->
            imageMatrix.reset()
            val scale = Math.min(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)
            invalidate()
        }
    }

    // Thiết lập ảnh từ bên ngoài
    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        imageMatrix.reset()
        val scale = Math.min(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Vẽ khung nền
        canvas.drawRect(backgroundRect, framePaint)

        // Vẽ ảnh overlay
        imageBitmap?.let {
            canvas.drawBitmap(it, imageMatrix, imagePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Kiểm tra xem có chạm vào cạnh khung không
                dragEdge = getTouchedEdge(x, y)
                if (dragEdge >= 0) {
                    isDraggingFrame = true
                } else if (backgroundRect.contains(x, y)) {
                    isDraggingImage = true
                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingFrame) {
                    adjustFrame(x, y)
                    invalidate()
                } else if (isDraggingImage) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    imageMatrix.postTranslate(dx, dy)
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isDraggingFrame = false
                isDraggingImage = false
            }
        }
        return true
    }

    // Xác định cạnh khung bị chạm
    private fun getTouchedEdge(x: Float, y: Float): Int {
        return when {
            Math.abs(x - backgroundRect.left) < edgeThreshold && y in backgroundRect.top..backgroundRect.bottom -> 0
            Math.abs(y - backgroundRect.top) < edgeThreshold && x in backgroundRect.left..backgroundRect.right -> 1
            Math.abs(x - backgroundRect.right) < edgeThreshold && y in backgroundRect.top..backgroundRect.bottom -> 2
            Math.abs(y - backgroundRect.bottom) < edgeThreshold && x in backgroundRect.left..backgroundRect.right -> 3
            else -> -1
        }
    }

    // Điều chỉnh khung nền khi kéo
    private fun adjustFrame(x: Float, y: Float) {
        when (dragEdge) {
            0 -> backgroundRect.left = x.coerceIn(0f, backgroundRect.right - 50f)
            1 -> backgroundRect.top = y.coerceIn(0f, backgroundRect.bottom - 50f)
            2 -> backgroundRect.right = x.coerceIn(backgroundRect.left + 50f, width.toFloat())
            3 -> backgroundRect.bottom = y.coerceIn(backgroundRect.top + 50f, height.toFloat())
        }
    }

    // Xử lý phóng to/thu nhỏ
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            imageMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    }

    // Xử lý kéo thả và xoay
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isDraggingImage && e1 != null && e2.pointerCount == 2) {
                // Xoay khi có 2 ngón tay
                val dx = e2.getX(1) - e2.getX(0)
                val dy = e2.getY(1) - e2.getY(0)
                val newRotation = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                imageMatrix.postRotate(newRotation - rotation, backgroundRect.centerX(), backgroundRect.centerY())
                rotation = newRotation
                invalidate()
            }
            return true
        }
    }

    // Thiết lập ratio cố định (ví dụ: 1:1, 16:9)
    fun setAspectRatio(widthRatio: Float, heightRatio: Float) {
        val currentWidth = backgroundRect.width()
        val newHeight = currentWidth * heightRatio / widthRatio
        backgroundRect.bottom = backgroundRect.top + newHeight
        invalidate()
    }
}