package com.example.democropviewanddrag.customview.v2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.democropviewanddrag.R
import kotlin.math.atan2
import androidx.core.graphics.createBitmap

class CropZoomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backgroundRect = RectF(100f, 100f, 300f, 300f) // Vùng chứa ảnh
    private var imageBitmap: Bitmap? = null // Ảnh hiển thị
    private var imageMatrix = Matrix() // Ma trận biến đổi ảnh
    private val imagePaint = Paint()

    // Border và icon
    private var isSelected = false // Trạng thái được chọn
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#12C138")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var rotateIcon: Bitmap? = null // Icon xoay
    private var zoomIcon: Bitmap? = null // Icon zoom
    private val iconSize = 50f // Kích thước icon

    // Trạng thái thao tác
    private var isDraggingView = false // Đang kéo thả view
    private var isRotating = false // Đang xoay
    private var isZooming = false // Đang zoom
    private var dragStartX = 0f // Tọa độ bắt đầu kéo
    private var dragStartY = 0f
    private var initialRotation = 0f // Góc xoay ban đầu
    private var initialScale = 1f // Tỷ lệ zoom ban đầu
    private var newBitmap : Bitmap? = null // Ảnh biến đổi

    init {
        setWillNotDraw(false)
        // Load icon từ drawable (thay bằng icon của bạn)
        rotateIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_rotate)
        zoomIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_zoom)
    }

    // Đặt ảnh từ resource
    fun setImageFromResource(resourceId: Int) {
        imageBitmap = BitmapFactory.decodeResource(resources, resourceId)
        imageBitmap?.let { bitmap ->
            // Tính toán tỷ lệ của ảnh
            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // Điều chỉnh backgroundRect để có cùng tỷ lệ với ảnh
            // Giữ chiều cao, điều chỉnh chiều rộng
            val newWidth = backgroundRect.height() * imageRatio
            backgroundRect.right = backgroundRect.left + newWidth

            // Thiết lập ma trận để ảnh vừa với backgroundRect
            imageMatrix.reset()
            val scale = minOf(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)

            // Cập nhật giao diện
            invalidate()
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Vẽ ảnh
        imageBitmap?.let {
            newBitmap = it
            canvas.drawBitmap(it, imageMatrix, imagePaint)
        }

        // Vẽ border và icon khi được chọn
        if (isSelected) {
            canvas.drawRect(backgroundRect, borderPaint)

            // Vẽ icon rotate (top-left)

            rotateIcon?.let {
                val rotateRect = RectF(
                    backgroundRect.left - iconSize / 2, backgroundRect.top - iconSize / 2,
                    backgroundRect.left + iconSize / 2, backgroundRect.top + iconSize / 2
                )

                canvas.drawBitmap(it, null, rotateRect, Paint().apply { color = Color.YELLOW })
            }

            // Vẽ icon zoom (bottom-right)
            zoomIcon?.let {
                val zoomRect = RectF(
                    backgroundRect.right - iconSize / 2, backgroundRect.bottom - iconSize / 2,
                    backgroundRect.right + iconSize / 2, backgroundRect.bottom + iconSize / 2
                )

                canvas.drawBitmap(it, null, zoomRect, Paint().apply { color = Color.YELLOW })
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.e("TAG", "onTouchEvent: inRotate::${isInRotateIcon(x, y)} -- inZoom${isInZoomIcon(x, y)}", )
                when{
                    isInRotateIcon(x, y) -> {
                        isSelected = true
                        isRotating = true
                        initialRotation = calculateRotationAngle(x, y)
                    }

                    (isInZoomIcon(x, y)) -> {
                        isSelected = true
                        isZooming = true
                        initialScale = 1f
                    }
                    (backgroundRect.contains(x, y)) -> {
                        isSelected = true
                        isDraggingView = true
                        dragStartX = x - backgroundRect.left
                        dragStartY = y - backgroundRect.top
                    }
                    else -> {
                        isSelected = false
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                Log.e("TAG", "onTouchEvent: ACTION_MOVE inRotate::${isInRotateIcon(x, y)} -- inZoom${isInZoomIcon(x, y)}", )
                Log.e("TAG", "onTouchEvent: ACTION_MOVE isRotating::${isRotating} -- isZooming${isZooming}", )

                if (isRotating) {
                    // Xoay ảnh
                    val newRotation = calculateRotationAngle(x, y)
                    val deltaRotation = newRotation - initialRotation
                    imageMatrix.postRotate(deltaRotation, backgroundRect.centerX(), backgroundRect.centerY())
                    initialRotation = newRotation
                    updateBackgroundRect()
                    invalidate()
                } else if (isZooming) {
                    // Phóng to/thu nhỏ ảnh
                    val deltaX = x - backgroundRect.centerX()
                    val scaleFactor = 1f + (deltaX / 100f) // Điều chỉnh hệ số zoom
                    imageMatrix.postScale(
                        scaleFactor / initialScale, scaleFactor / initialScale,
                        backgroundRect.centerX(), backgroundRect.centerY()
                    )
                    initialScale = scaleFactor
                    updateBackgroundRect()
                    invalidate()
                } else if (isDraggingView) {
                    // Kéo thả view
                    val newLeft = x - dragStartX
                    val newTop = y - dragStartY
                    val dx = newLeft - backgroundRect.left
                    val dy = newTop - backgroundRect.top
                    backgroundRect.offset(dx, dy)
                    imageMatrix.postTranslate(dx, dy) // Di chuyển ảnh theo
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                // Kết thúc thao tác
                isRotating = false
                isZooming = false
                isDraggingView = false
            }
        }
        return true
    }

    // Kiểm tra vị trí click có nằm trong icon rotate không
    private fun isInRotateIcon(x: Float, y: Float): Boolean {
        val rotateRect = RectF(
            backgroundRect.left - iconSize / 2, backgroundRect.top - iconSize / 2,
            backgroundRect.left + iconSize / 2, backgroundRect.top + iconSize / 2
        )
        return rotateRect.contains(x, y)
    }

    // Kiểm tra vị trí click có nằm trong icon zoom không
    private fun isInZoomIcon(x: Float, y: Float): Boolean {
        val zoomRect = RectF(
            backgroundRect.right - iconSize / 2, backgroundRect.bottom - iconSize / 2,
            backgroundRect.right + iconSize / 2, backgroundRect.bottom + iconSize / 2
        )
        return zoomRect.contains(x, y)
    }

    private fun updateBackgroundRect() {
        // Lấy kích thước gốc của ảnh
        val rect = RectF(0f, 0f, imageBitmap?.width?.toFloat() ?: 0f, imageBitmap?.height?.toFloat() ?: 0f)
        // Áp dụng ma trận để tính kích thước mới
        imageMatrix.mapRect(rect)
        // Cập nhật backgroundRect
        backgroundRect.set(rect)
        // Yêu cầu vẽ lại view
//        invalidate()
    }

    // Tính góc xoay dựa trên vị trí chạm
    private fun calculateRotationAngle(x: Float, y: Float): Float {
        val centerX = backgroundRect.centerX()
        val centerY = backgroundRect.centerY()
        val dx = x - centerX
        val dy = y - centerY
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    fun getBitmap(): Bitmap? {
        imageBitmap?.let { bitmap ->
            // Tạo bitmap mới với kích thước gốc của ảnh
            val resultBitmap = createBitmap(bitmap.width+ 60, bitmap.height + 30)
            val canvas = Canvas(resultBitmap)

            // Áp dụng ma trận biến đổi lên canvas
            canvas.setMatrix(imageMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)

            return resultBitmap
        }
        return null
    }
//    fun getBitmapPosition(): RectF {
//        val rect = RectF(0f, 0f, imageBitmap?.width?.toFloat() ?: 0f, imageBitmap?.height?.toFloat() ?: 0f)
//        imageMatrix.mapRect(rect)
//        return rect
//    }

    fun getBitmapPosition(): RectF {
        val rect = RectF(backgroundRect)
        imageMatrix.mapRect(rect)
        return rect
    }

    fun adjustToRatio(ratio: Float) {
        // Điều chỉnh backgroundRect để có cùng tỷ lệ với vùng crop
//        val newWidth = backgroundRect.height() * ratio
//        backgroundRect.right = backgroundRect.left + newWidth

        // Cập nhật ma trận hình ảnh để khớp với backgroundRect mới
        imageBitmap?.let { bitmap ->
            imageMatrix.reset()
            val scale = minOf(backgroundRect.width() / bitmap.width, backgroundRect.height() / bitmap.height)
            imageMatrix.postScale(scale, scale)
            imageMatrix.postTranslate(backgroundRect.left, backgroundRect.top)
        }
        invalidate()
    }

}